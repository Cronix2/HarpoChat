// SPDX-License-Identifier: MIT
package app.calc.scientific

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import app.calc.scientific.bg.BackgroundPrefs
import app.calc.scientific.bg.ResultService
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.calc.scientific.calculator.CalculatorScreen
import app.calc.scientific.calculator.CalculatorViewModel
import app.calc.scientific.navigation.PrivateNavHost
import app.calc.scientific.security.AppLock
import app.calc.scientific.security.AttemptOutcome
import app.calc.scientific.security.LockState
import app.calc.scientific.setup.SetupScreen
import app.calc.scientific.ui.CircleRevealShape
import app.calc.scientific.ui.SecureWindow
import app.calc.scientific.ui.theme.AppTheme
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

        val appLock = (application as CalcApp).appLock
        // Réception en arrière-plan, si l'utilisateur l'a activée (démarrée depuis l'écran, comme l'exige Android).
        if (BackgroundPrefs.isEnabled(this) && appLock.isConfigured()) ResultService.start(this)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(appLock)
                }
            }
        }
    }
}

/** Courbe de l'ouverture : départ vif, arrivée douce. */
private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun AppRoot(appLock: AppLock) {
    val state by appLock.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    // Captures bloquées partout sauf sur la façade (une calculatrice qui interdit les captures intrigue).
    SecureWindow(enabled = state !is LockState.Locked)

    var equalsCenter by remember { mutableStateOf(Offset.Unspecified) }
    val reveal = remember { Animatable(1f) }

    // Ouverture : le cercle part de la touche « = ». Le verrouillage, lui, est instantané :
    // la base est déjà fermée et rien de l'espace privé ne doit rester à l'écran.
    LaunchedEffect(state is LockState.Unlocked) {
        if (state is LockState.Unlocked) {
            reveal.snapTo(0f)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            reveal.animateTo(1f, tween(durationMillis = 450, easing = RevealEasing))
        }
    }

    when (val s = state) {
        LockState.NeedsSetup -> SetupScreen(onConfigure = appLock::setup)
        LockState.Locked -> LockedCalculator(appLock) { equalsCenter = it }
        is LockState.Unlocked -> Box(Modifier.fillMaxSize()) {
            if (reveal.value < 1f) LockedCalculator(appLock) { equalsCenter = it }
            Box(Modifier.fillMaxSize().clip(CircleRevealShape(reveal.value, equalsCenter))) {
                PrivateNavHost(s.session, appLock)
            }
        }
    }
}

@Composable
private fun LockedCalculator(appLock: AppLock, onEqualsPositioned: (Offset) -> Unit) {
    val vm: CalculatorViewModel = viewModel()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    CalculatorScreen(
        viewModel = vm,
        onEqualsPositioned = onEqualsPositioned,
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
