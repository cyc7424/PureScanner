package com.purescanner.app.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.purescanner.app.MainViewModel
import com.purescanner.app.SetupState
import com.purescanner.app.ui.components.BackButton
import com.purescanner.app.ui.components.PrimaryButton
import com.purescanner.app.ui.components.PrinterHeader
import com.purescanner.app.ui.components.SecondaryPill
import com.purescanner.app.ui.components.Spinner
import com.purescanner.app.ui.theme.Ui

@Composable
fun ScanScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()

    val leave = {
        viewModel.cancelScan()
        onBack()
    }
    BackHandler(onBack = leave)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        BackButton(onClick = leave)
        Spacer(Modifier.height(8.dp))
        device?.let { PrinterHeader(it) }
        Spacer(Modifier.height(24.dp))

        when (val s = setup) {
            SetupState.Loading -> Centered {
                Spinner(spinning = true, size = 48.dp)
                Spacer(Modifier.height(12.dp))
                Text("Connecting to scanner…", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }

            is SetupState.Failed -> Centered {
                Text(s.message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                SecondaryPill("Try again", onClick = viewModel::loadSetup)
            }

            is SetupState.Ready -> {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    OptionPill(
                        text = "Size : ${s.size.label}",
                        options = s.sizes.map { it.label to it },
                        onSelect = viewModel::chooseSize,
                        enabled = !scanning,
                        modifier = Modifier.weight(1f),
                    )
                    OptionPill(
                        text = "Quality : ${s.resolution}dpi",
                        options = s.resolutions.map { "${it}dpi" to it },
                        onSelect = viewModel::chooseResolution,
                        enabled = !scanning,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    text = "Start scanning",
                    onClick = { viewModel.startScan() },
                    enabled = !scanning,
                )
                if (scanning) {
                    Centered {
                        Spinner(spinning = true, size = 48.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun <T> OptionPill(
    text: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SecondaryPill(text, onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        open = false
                    },
                )
            }
        }
    }
}
