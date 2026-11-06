package com.purescanner.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purescanner.app.R
import com.purescanner.app.discovery.ScannerDevice
import com.purescanner.app.ui.theme.Ui

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Ui.Card)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** White pill with dark text: the main action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(51.dp)
            .clip(CircleShape)
            .background(Color.White)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.Black, fontSize = 16.sp)
    }
}

/** Grey pill with white text: secondary actions and option pickers. */
@Composable
fun SecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .height(51.dp)
            .clip(CircleShape)
            .background(Ui.Card)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

/** The circular-arrow icon, optionally turning (used for "searching" and "scanning"). */
@Composable
fun Spinner(spinning: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Icon(
        Icons.Filled.Refresh,
        contentDescription = if (spinning) "Working" else "Refresh",
        tint = Color.White,
        modifier = modifier.size(size).rotate(if (spinning) angle.value else 0f),
    )
}

@Composable
fun PrinterImage(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.printer_icon_1), contentDescription = null, modifier = modifier)
}

@Composable
fun PrinterCard(device: ScannerDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(141.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ui.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PrinterImage(Modifier.size(100.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(device.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text("ip : ${device.host}", color = Color.White, fontSize = 13.sp)
        }
    }
}

/** Printer picture, name and address, as at the top of the Scan screen. */
@Composable
fun PrinterHeader(device: ScannerDevice, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PrinterImage(Modifier.size(96.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(device.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text("ip : ${device.host}", color = Color.White, fontSize = 13.sp)
        }
    }
}
