package com.example.localledger.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.localledger.ui.theme.LocalLedgerTones

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tones = LocalLedgerTones.current
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickable),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, tones.hairline),
    ) {
        Column(content = content)
    }
}

@Composable
fun LedgerHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = LocalLedgerTones.current.hairline,
    )
}

@Composable
fun LedgerSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/** Light keyboard tick. Safe to call from click handlers. */
@Composable
fun rememberLedgerHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
