// SPDX-License-Identifier: MIT
package app.harpochat.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.harpochat.messaging.ChatRoute
import app.harpochat.messaging.CodepairScreen
import app.harpochat.messaging.ConversationsScreen
import app.harpochat.messaging.QrScreen
import app.harpochat.security.AppLock
import app.harpochat.security.Session
import app.harpochat.settings.SettingsScreen

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{threadId}"
    const val QR = "qr"
    const val CODEPAIR = "codepair"
    const val SETTINGS = "settings"
    fun chat(id: String) = "chat/$id"
}

/**
 * Espace privé (déverrouillé). Il n'existe que pendant une [Session] :
 * au verrouillage, ce graphe disparaît et sa pile de navigation avec lui.
 */
@Composable
fun PrivateNavHost(session: Session, appLock: AppLock) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            // Retour depuis la racine = on referme l'espace privé (retour à la calculatrice).
            BackHandler(onBack = appLock::lock)
            ConversationsScreen(
                repo = session.repo,
                onOpenThread = { nav.navigate(Routes.chat(it)) },
                onOpenQr = { nav.navigate(Routes.QR) },
                onOpenCodepair = { nav.navigate(Routes.CODEPAIR) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onLock = appLock::lock
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("threadId") { type = NavType.StringType })) { entry ->
            ChatRoute(session, entry.arguments?.getString("threadId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.QR) {
            QrScreen(
                session = session,
                appLock = appLock,
                onBack = { nav.popBackStack() },
                onOpenThread = { id ->
                    nav.navigate(Routes.chat(id)) { popUpTo(Routes.CONVERSATIONS) }
                }
            )
        }
        composable(Routes.CODEPAIR) { CodepairScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(session, appLock, onBack = { nav.popBackStack() }) }
    }
}
