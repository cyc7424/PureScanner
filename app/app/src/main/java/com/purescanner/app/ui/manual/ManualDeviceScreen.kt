package com.purescanner.app.ui.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.purescanner.app.MainViewModel
import com.purescanner.app.ui.components.BackButton
import com.purescanner.app.ui.components.PrimaryButton
import com.purescanner.app.ui.theme.Ui

@Composable
fun ManualDeviceScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.manual.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ui.Background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        BackButton(onClick = onBack)
        Spacer(Modifier.height(48.dp))

        UnderlineField("IP address", ip, { ip = it }, KeyboardType.Uri)
        Spacer(Modifier.height(32.dp))
        UnderlineField("Device name", name, { name = it }, KeyboardType.Text)

        state.error?.let {
            Spacer(Modifier.height(24.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = if (state.connecting) "Connecting…" else "Connect",
            onClick = { viewModel.addManual(ip, name, onAdded = onBack) },
            enabled = ip.isNotBlank() && !state.connecting,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun UnderlineField(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        )
        HorizontalDivider(color = Color.White)
    }
}
