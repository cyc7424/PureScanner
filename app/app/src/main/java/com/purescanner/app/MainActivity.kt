package com.purescanner.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.purescanner.app.ui.devices.DevicesScreen
import com.purescanner.app.ui.manual.ManualDeviceScreen
import com.purescanner.app.ui.preview.PreviewScreen
import com.purescanner.app.ui.scan.ScanScreen
import com.purescanner.app.ui.theme.PureScannerTheme
import com.purescanner.app.ui.theme.Ui

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            PureScannerTheme { AppRoot() }
        }
    }
}

private object Route {
    const val Devices = "devices"
    const val Manual = "manual"
    const val Scan = "scan"
    const val Preview = "preview"
}

@Composable
private fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                UiEvent.OpenPreview -> nav.navigate(Route.Preview)
            }
        }
    }

    NavHost(navController = nav, startDestination = Route.Devices, modifier = Modifier.background(Ui.Background)) {
        composable(Route.Devices) {
            DevicesScreen(
                viewModel = viewModel,
                onSelect = { device ->
                    viewModel.selectDevice(device)
                    nav.navigate(Route.Scan)
                },
                onAddManually = {
                    viewModel.resetManual()
                    nav.navigate(Route.Manual)
                },
            )
        }
        composable(Route.Manual) {
            ManualDeviceScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Route.Scan) {
            ScanScreen(viewModel, onBack = { nav.popBackStack() })
        }
        composable(Route.Preview) {
            // Idempotent: pops back to the Scan screen whether called once or twice.
            PreviewScreen(viewModel, onLeave = { nav.popBackStack(Route.Scan, inclusive = false) })
        }
    }
}
