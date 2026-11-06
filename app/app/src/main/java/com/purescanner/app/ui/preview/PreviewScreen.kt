package com.purescanner.app.ui.preview

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.purescanner.app.MainViewModel
import com.purescanner.app.ScanPage
import com.purescanner.app.ui.components.BackButton
import com.purescanner.app.ui.components.PrimaryButton
import com.purescanner.app.ui.components.SecondaryPill
import com.purescanner.app.ui.theme.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PreviewScreen(viewModel: MainViewModel, onLeave: () -> Unit) {
    val page by viewModel.page.collectAsStateWithLifecycle()

    var confirmLeave by remember { mutableStateOf(false) }
    var chooseFormat by remember { mutableStateOf(false) }
    var baseName by remember { mutableStateOf("PureScan") }

    // The page was deleted: nothing left to preview.
    LaunchedEffect(page == null) {
        if (page == null) onLeave()
    }

    val tryLeave = { if (page == null) onLeave() else confirmLeave = true }
    BackHandler(onBack = tryLeave)

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri?.let(viewModel::savePdf)
    }
    val jpegPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
        uri?.let(viewModel::saveJpeg)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        BackButton(onClick = tryLeave)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            page?.let { p ->
                item(key = p.id) { PageCard(p, onDelete = viewModel::clearPage) }
            }
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = "Save as",
            onClick = {
                baseName = "PureScan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                chooseFormat = true
            },
            enabled = page != null,
        )
        Spacer(Modifier.height(40.dp))
    }

    if (chooseFormat) {
        AlertDialog(
            onDismissRequest = { chooseFormat = false },
            title = { Text("Save as") },
            text = {
                Column {
                    TextButton(onClick = {
                        chooseFormat = false
                        pdfPicker.launch("$baseName.pdf")
                    }) { Text("PDF") }
                    TextButton(onClick = {
                        chooseFormat = false
                        jpegPicker.launch("$baseName.jpg")
                    }) { Text("JPEG") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseFormat = false }) { Text("Cancel") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard scan?") },
            text = { Text("The scanned page hasn't been saved and will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.clearPage()
                    onLeave()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun PageCard(page: ScanPage, onDelete: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.size(36.dp).clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete page", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        AsyncImage(
            model = ImageRequest.Builder(context).data(page.bytes).memoryCacheKey(page.id).build(),
            contentDescription = "Scanned page",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(page.aspectRatio).background(Ui.Paper),
        )
    }
}
