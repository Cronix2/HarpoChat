package com.example.harpochat

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ui.ChatScreen
import com.example.harpochat.ui.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge (garde ce que tu avais)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = true
        insets.isAppearanceLightNavigationBars = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        // Empêche les captures d’écran (garde si tu veux)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme {
                Surface {
                    val vm: ChatViewModel = viewModel()

                    // Ouvre un fil par défaut au premier rendu
                    LaunchedEffect(Unit) {
                        vm.openThread(id = "thread-default", title = "Alice")
                    }

                    val messages by vm.messages.collectAsState(initial = emptyList())

                    ChatScreen(
                        messages   = messages,
                        onSend     = vm::send,
                        // onRetry peut relancer l’ouverture du fil (ou une synchro plus tard)
                        onRetry    = { vm.openThread(id = "thread-default", title = "Alice") },
                        title      = "Alice",
                        avatarInitial = "A"
                        // pas de onBack ici (c’est l’écran d’accueil/appli)
                    )
                }
            }
        }
    }
}
