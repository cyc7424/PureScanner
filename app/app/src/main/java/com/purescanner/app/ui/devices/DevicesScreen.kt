package com.purescanner.app.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.purescanner.app.MainViewModel
import com.purescanner.app.discovery.ScannerDevice
import com.purescanner.app.ui.components.PrimaryButton
import com.purescanner.app.ui.components.PrinterCard
import com.purescanner.app.ui.components.Spinner
import com.purescanner.app.ui.theme.Ui

@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onSelect: (ScannerDevice) -> Unit,
    onAddManually: () -> Unit,
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Devices", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Ui.Card)
                    .clickable(role = Role.Button, onClick = viewModel::refreshDevices),
                contentAlignment = Alignment.Center,
            ) {
                Spinner(spinning = searching, size = 20.dp)
            }
        }
        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(devices, key = { "${it.host}:${it.port}" }) { device ->
                PrinterCard(device, onClick = { onSelect(device) })
            }
            if (devices.isEmpty()) {
                item {
                    Text(
                        text = if (searching) "Looking for scanners on your Wi-Fi…"
                        else "No scanners found. Make sure your phone and the scanner are on the same Wi-Fi, " +
                            "or add one manually.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        PrimaryButton("Add device manually", onAddManually)
        Spacer(Modifier.height(40.dp))
    }
}
