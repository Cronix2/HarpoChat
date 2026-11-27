// SPDX-License-Identifier: MIT
package app.calc.scientific

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import app.calc.scientific.security.AppLock
import app.calc.scientific.security.KeystoreKeyBinder
import app.calc.scientific.security.PrefsVaultStore
import app.calc.scientific.security.Vault

/** Conteneur d'application : instances uniques partagées par tous les écrans. */
class CalcApp : Application() {

    lateinit var appLock: AppLock
        private set

    override fun onCreate() {
        super.onCreate()
        appLock = AppLock(this, Vault(PrefsVaultStore(this), KeystoreKeyBinder()))

        // Dès que l'app n'est plus visible (bouton accueil, écran éteint, autre app) : verrouillage.
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) appLock.onAppBackgrounded()
        })
    }
}
