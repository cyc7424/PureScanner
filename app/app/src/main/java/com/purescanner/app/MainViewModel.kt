package com.purescanner.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purescanner.app.discovery.ManualAddResult
import com.purescanner.app.discovery.ScannerDevice
import com.purescanner.app.discovery.ScannerRepository
import com.purescanner.app.discovery.mergeDevices
import com.purescanner.app.escl.ColorMode
import com.purescanner.app.escl.DocumentFormat
import com.purescanner.app.escl.EsclException
import com.purescanner.app.escl.InputSource
import com.purescanner.app.escl.PaperSize
import com.purescanner.app.escl.ScanSettings
import com.purescanner.app.save.ScanSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** One scanned page held in memory until the user saves or discards it. */
class ScanPage(val id: String, val bytes: ByteArray, val dpi: Int, val aspectRatio: Float)

/** What the Scan screen can offer, derived from what the scanner reports it supports. */
sealed interface SetupState {
    data object Loading : SetupState
    data class Failed(val message: String) : SetupState
    data class Ready(
        val source: InputSource,
        val sizes: List<PaperSize>,
        val resolutions: List<Int>,
        val size: PaperSize,
        val resolution: Int,
        val colorMode: ColorMode,
        val maxWidth: Int,
        val maxHeight: Int,
    ) : SetupState
}

data class ManualState(val connecting: Boolean = false, val error: String? = null)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object OpenPreview : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScannerRepository(app)
    private val resolver = app.contentResolver

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    // ---------- Device list ----------

    private val _devices = MutableStateFlow<List<ScannerDevice>>(emptyList())
    val devices: StateFlow<List<ScannerDevice>> = _devices.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        discoveryJob?.cancel()
        _devices.value = mergeDevices(repo.manualDevices())
        discoveryJob = viewModelScope.launch {
            val self = coroutineContext.job
            _searching.value = true
            try {
                val found = mutableListOf<ScannerDevice>()
                repo.discover().collect {
                    found += it
                    _devices.value = mergeDevices(repo.manualDevices() + found)
                }
            } finally {
                // A newer refresh may already have replaced us; only the latest one owns the spinner.
                if (discoveryJob == null || discoveryJob === self) _searching.value = false
            }
        }
    }

    // ---------- Manual add ----------

    private val _manual = MutableStateFlow(ManualState())
    val manual: StateFlow<ManualState> = _manual.asStateFlow()

    fun resetManual() {
        _manual.value = ManualState()
    }

    fun addManual(ip: String, name: String, onAdded: () -> Unit) {
        if (_manual.value.connecting) return
        viewModelScope.launch {
            _manual.value = ManualState(connecting = true)
            when (repo.addManual(ip, name)) {
                is ManualAddResult.Added -> {
                    _manual.value = ManualState()
                    refreshDevices()
                    onAdded()
                }
                ManualAddResult.InvalidInput ->
                    _manual.value = ManualState(error = "That doesn't look like a valid IP address.")
                ManualAddResult.NotFound ->
                    _manual.value = ManualState(error = "No scanner answered at that address. Check the IP and that you're on the same Wi-Fi.")
            }
        }
    }

    // ---------- Selected scanner and scan options ----------

    private val _device = MutableStateFlow<ScannerDevice?>(null)
    val device: StateFlow<ScannerDevice?> = _device.asStateFlow()

    private val _setup = MutableStateFlow<SetupState>(SetupState.Loading)
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private var setupJob: Job? = null

    fun selectDevice(device: ScannerDevice) {
        _device.value = device
        clearPage()
        loadSetup()
    }

    fun loadSetup() {
        val device = _device.value ?: return
        setupJob?.cancel()
        _setup.value = SetupState.Loading
        setupJob = viewModelScope.launch {
            _setup.value = try {
                val caps = repo.client.getCapabilities(device.endpoint)
                val source = if (caps.platen != null) InputSource.Platen else InputSource.Feeder
                val sc = caps.source(source)
                when {
                    sc == null -> SetupState.Failed("This scanner didn't report any scan source.")
                    sc.formats.isNotEmpty() && DocumentFormat.Jpeg !in sc.formats ->
                        SetupState.Failed("This scanner can't output JPEG, which this app needs.")
                    else -> {
                        val sizes = sc.supportedPaperSizes().ifEmpty { listOf(PaperSize.A4) }
                        val resolutions = sc.resolutions.ifEmpty { listOf(300) }
                        SetupState.Ready(
                            source = source,
                            sizes = sizes,
                            resolutions = resolutions,
                            size = if (PaperSize.A4 in sizes) PaperSize.A4 else sizes.first(),
                            resolution = resolutions.minBy { kotlin.math.abs(it - 300) },
                            colorMode = if (sc.colorModes.isEmpty() || ColorMode.Color in sc.colorModes) ColorMode.Color else ColorMode.Gray,
                            maxWidth = sc.maxWidth.takeIf { it > 0 } ?: Int.MAX_VALUE,
                            maxHeight = sc.maxHeight.takeIf { it > 0 } ?: Int.MAX_VALUE,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EsclException) {
                SetupState.Failed(describe(e))
            } catch (e: Exception) {
                SetupState.Failed("Couldn't read the scanner's capabilities.")
            }
        }
    }

    fun chooseSize(size: PaperSize) = _setup.update { if (it is SetupState.Ready) it.copy(size = size) else it }

    fun chooseResolution(dpi: Int) = _setup.update { if (it is SetupState.Ready) it.copy(resolution = dpi) else it }

    // ---------- Scanning ----------

    private val _page = MutableStateFlow<ScanPage?>(null)
    val page: StateFlow<ScanPage?> = _page.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        val device = _device.value ?: return
        val setup = _setup.value as? SetupState.Ready ?: return
        if (_scanning.value) return

        val settings = ScanSettings(
            source = setup.source,
            paperSize = setup.size,
            resolution = setup.resolution,
            colorMode = setup.colorMode,
            format = DocumentFormat.Jpeg,
            maxWidth = setup.maxWidth.takeIf { it != Int.MAX_VALUE },
            maxHeight = setup.maxHeight.takeIf { it != Int.MAX_VALUE },
        )
        val aspect = minOf(setup.size.widthUnits, setup.maxWidth).toFloat() /
            minOf(setup.size.heightUnits, setup.maxHeight).toFloat()

        _scanning.value = true
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val self = coroutineContext.job
            try {
                var got = false
                repo.client.scan(device.endpoint, settings) { result ->
                    if (result.mime != "image/jpeg") {
                        throw EsclException.Unsupported("Scanner returned ${result.mime}")
                    }
                    _page.value = ScanPage(UUID.randomUUID().toString(), result.bytes, settings.resolution, aspect)
                    got = true
                }
                if (got) {
                    events.send(UiEvent.OpenPreview)
                } else {
                    events.send(UiEvent.Message("The scanner didn't return a page. Is the document in place?"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EsclException) {
                events.send(UiEvent.Message(describe(e)))
            } catch (e: Exception) {
                events.send(UiEvent.Message("Scan failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                // A newer scan may already own the flag; only the latest job resets it.
                if (scanJob === self) _scanning.value = false
            }
        }
        scanJob = job
        job.start()
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun clearPage() {
        cancelScan()
        _page.value = null
    }

    // ---------- Saving ----------

    fun savePdf(uri: Uri) {
        val page = _page.value ?: return
        save("Saved PDF") { ScanSaver.writePdf(resolver, uri, page) }
    }

    fun saveJpeg(uri: Uri) {
        val page = _page.value ?: return
        save("Saved JPEG") { ScanSaver.writeJpeg(resolver, uri, page) }
    }

    private fun save(successMessage: String, work: () -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { work() }
                successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't save: ${e.message ?: e.javaClass.simpleName}"
            }
            events.send(UiEvent.Message(message))
        }
    }

    private fun describe(e: EsclException): String = when (e) {
        is EsclException.Busy -> "The scanner is busy. Try again in a moment."
        is EsclException.Network -> "Can't reach the scanner. Check that your phone is on the same Wi-Fi."
        is EsclException.Unsupported -> "The scanner rejected these settings. Try another size or quality."
        is EsclException.Parse -> "The scanner sent a reply this app doesn't understand."
        is EsclException.Http -> when (e.code) {
            409 -> "The scanner is busy or its cover is open."
            else -> "The scanner reported an error (HTTP ${e.code})."
        }
    }
}
