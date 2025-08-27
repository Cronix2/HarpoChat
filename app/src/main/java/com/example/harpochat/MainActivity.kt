package com.example.harpochat

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ui.ChatScreen
import com.example.harpochat.ui.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // 2) Fallback pour API plus anciennes / OEM capricieux
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        // false = icônes blanches (clair) ; true = icônes noires (foncé)
        insets.isAppearanceLightStatusBars = true
        insets.isAppearanceLightNavigationBars = true

        // Optionnel: barre 100% transparente pour mieux voir l’effet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }


        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            MaterialTheme {
                Surface {
                    val vm: ChatViewModel = viewModel()
                    val messages by vm.messages.collectAsState()   // <-- observe le flow
                    ChatScreen(
                        messages = messages,
                        onSend = vm::send,
                        onRetry  = { vm.loadThread() }
                    )
                }
            }
        }
    }
}
