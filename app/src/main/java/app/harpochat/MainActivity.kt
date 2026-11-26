// SPDX-License-Identifier: MIT
package app.harpochat

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.harpochat.calculator.CalculatorScreen
import app.harpochat.calculator.CalculatorViewModel
import app.harpochat.navigation.PrivateNavHost
import app.harpochat.security.AppLock
import app.harpochat.security.AttemptOutcome
import app.harpochat.security.LockState
import app.harpochat.setup.SetupScreen
import app.harpochat.ui.SecureWindow
import app.harpochat.ui.theme.HarpoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activité unique. Ce qui s'affiche dépend UNIQUEMENT de l'état de [AppLock] :
 * configuration → calculatrice (verrouillée) → espace privé (déverrouillé).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Jamais de miniature dans les apps récentes (ni de la calculatrice, ni du reste).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)

        val appLock = (application as HarpoApp).appLock
        setContent {
            HarpoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(appLock)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(appLock: AppLock) {
    val state by appLock.state.collectAsStateWithLifecycle()
    // Captures bloquées partout sauf sur la façade (une calculatrice qui interdit les captures intrigue).
    SecureWindow(enabled = state !is LockState.Locked)

    Crossfade(targetState = state, animationSpec = tween(220), label = "lock-state") { s ->
        when (s) {
            LockState.NeedsSetup -> SetupScreen(onConfigure = appLock::setup)
            LockState.Locked -> LockedCalculator(appLock)
            is LockState.Unlocked -> PrivateNavHost(s.session, appLock)
        }
    }
}

@Composable
private fun LockedCalculator(appLock: AppLock) {
    val vm: CalculatorViewModel = viewModel()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    CalculatorScreen(
        viewModel = vm,
        onLongEquals = { digits ->
            scope.launch {
                when (appLock.attempt(digits)) {
                    AttemptOutcome.UNLOCKED -> vm.clearAll()
                    AttemptOutcome.WIPED -> {
                        // Confirmation discrète : écran remis à zéro + double vibration brève.
                        vm.clearAll()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        delay(140)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    AttemptOutcome.NO_MATCH -> Unit // comportement de calculatrice ordinaire
                }
            }
        }
    )
}
