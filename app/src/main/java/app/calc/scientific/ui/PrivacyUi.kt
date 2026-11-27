// SPDX-License-Identifier: MIT
package app.calc.scientific.ui

import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import app.calc.scientific.BuildConfig

/**
 * Le clavier ne doit rien apprendre de ce qui est tapé ici (mode « incognito » des claviers,
 * comme Signal) : pas de suggestions mémorisées, pas de dictionnaire personnel enrichi.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoKeyboardLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val modified = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val ic = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    return ic
                }
            }
            nextHandler.startInputMethod(modified)
        },
        content = content
    )
}

/**
 * Active FLAG_SECURE tant que [enabled] : captures d'écran et enregistrement bloqués,
 * contenu masqué lors du partage d'écran.
 */
@Composable
fun SecureWindow(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    val secure = enabled && !debugCaptureAllowed()
    DisposableEffect(secure) {
        val window = activity.window
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
}

/**
 * Builds DEBUG uniquement : `adb shell setprop debug.calc.capture 1` autorise les captures
 * (vérification visuelle et maquettes). Toujours faux en release (R8 supprime la branche).
 */
private fun debugCaptureAllowed(): Boolean {
    if (!BuildConfig.DEBUG) return false
    return runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("get", String::class.java).invoke(null, "debug.calc.capture") == "1"
    }.getOrDefault(false)
}
