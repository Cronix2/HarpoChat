// SPDX-License-Identifier: MIT
package app.harpochat.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.harpochat.messaging.AddContactScreen
import app.harpochat.messaging.ChatRoute
import app.harpochat.messaging.ConversationsScreen
import app.harpochat.security.AppLock
import app.harpochat.security.Session
import app.harpochat.settings.ChangeCodesScreen
import app.harpochat.settings.SettingsScreen

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{threadId}"
    const val ADD = "add"
    const val SETTINGS = "settings"
    const val CODES = "codes"
    fun chat(id: String) = "chat/$id"
}

/**
 * Espace privé (déverrouillé). Il n'existe que pendant une [Session] :
 * au verrouillage, ce graphe disparaît et sa pile de navigation avec lui.
 */
@Composable
fun PrivateNavHost(session: Session, appLock: AppLock) {
    val nav = rememberNavController()
    val protection = remember { appLock.protectionLevel() }
    NavHost(
        navController = nav,
        startDestination = Routes.CONVERSATIONS,
        enterTransition = { slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(220)) }
    ) {
        composable(Routes.CONVERSATIONS) {
            // Retour depuis la racine = on referme l'espace privé (retour à la calculatrice).
            BackHandler(onBack = appLock::lock)
            ConversationsScreen(
                session = session,
                protection = protection,
                onOpenThread = { nav.navigate(Routes.chat(it)) },
                onAddContact = { nav.navigate(Routes.ADD) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onLock = appLock::lock
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("threadId") { type = NavType.StringType })) { entry ->
            ChatRoute(session, entry.arguments?.getString("threadId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.ADD) {
            AddContactScreen(
                session = session,
                appLock = appLock,
                onBack = { nav.popBackStack() },
                onOpenThread = { id -> nav.navigate(Routes.chat(id)) { popUpTo(Routes.CONVERSATIONS) } }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(session, appLock, onBack = { nav.popBackStack() }, onChangeCodes = { nav.navigate(Routes.CODES) })
        }
        composable(Routes.CODES) { ChangeCodesScreen(appLock, onBack = { nav.popBackStack() }) }
    }
}
