// SPDX-License-Identifier: MIT
package app.calc.scientific.security

import android.content.Context
import app.calc.scientific.bg.BackgroundPrefs
import app.calc.scientific.bg.ResultService
import app.calc.scientific.data.AppDatabase
import app.calc.scientific.data.ChatRepository
import app.calc.scientific.messaging.PostOffice
import app.calc.scientific.net.MeshClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Une session déverrouillée : la base ouverte et ce qui en dépend. Fermée au verrouillage. */
class Session internal constructor(
    val db: AppDatabase,
    private val dbKey: ByteArray,
    val scope: CoroutineScope,
    dropBox: DropBox
) {
    val repo = ChatRepository(db, scope).also { it.startExpiryJob() }

    /** Lien au mesh : ouvert seulement pendant la session, coupé au verrouillage. */
    val mesh = MeshClient(scope)

    /** Courrier : contacts, demandes, messages. Branché sur le mesh avant son démarrage. */
    val post = PostOffice(db, scope, mesh, repo, dropBox).also { it.start() }

    init {
        mesh.start()
    }

    internal fun close() {
        mesh.stop()
        scope.cancel()
        db.close()
        dbKey.fill(0) // la clé ne survit pas au verrouillage
    }
}

sealed interface LockState {
    /** Premier lancement (ou après effacement) : choisir les PIN. */
    data object NeedsSetup : LockState
    /** Façade calculatrice. */
    data object Locked : LockState
    data class Unlocked(val session: Session) : LockState
}

enum class AttemptOutcome { UNLOCKED, WIPED, NO_MATCH }

/**
 * Source de vérité unique de l'état de verrouillage.
 *
 * - Toute mise en arrière-plan de l'app verrouille ([onAppBackgrounded]) : base fermée, clé effacée.
 * - Exception explicite : une activité externe lancée par nous (scanner QR) — voir [expectExternalActivity].
 */
class AppLock(
    private val context: Context,
    private val vault: Vault
) {
    private val _state = MutableStateFlow<LockState>(
        if (vault.isConfigured()) LockState.Locked else LockState.NeedsSetup
    )
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val dropBox = DropBox(context)
    @Volatile private var externalActivityInFlight = false

    /** Premier lancement : configure les PIN puis revient à la calculatrice (verrouillée). */
    suspend fun setup(secretPin: String, duressPin: String) = mutex.withLock {
        withContext(Dispatchers.Default) {
            wipeFiles() // repart d'une ardoise vierge (restes d'une installation précédente)
            val key = vault.setup(secretPin, duressPin)
            // Crée le fichier de base avec la nouvelle clé, puis le referme aussitôt.
            withContext(Dispatchers.IO) {
                AppDatabase.open(context, key).also { it.openHelper.writableDatabase }.close()
            }
            key.fill(0)
        }
        _state.value = LockState.Locked
    }

    /** Appui long sur « = ». Calculs coûteux hors du fil d'interface. */
    suspend fun attempt(pin: String): AttemptOutcome = mutex.withLock {
        if (_state.value !is LockState.Locked) return@withLock AttemptOutcome.NO_MATCH
        // Rien qui ressemble à un PIN valide : on ne fait même pas le calcul.
        if (pin.length !in PinPolicy.MIN_LENGTH..PinPolicy.MAX_LENGTH) return@withLock AttemptOutcome.NO_MATCH
        when (val r = withContext(Dispatchers.Default) { vault.unlock(pin) }) {
            is Vault.UnlockResult.Secret -> {
                val db = withContext(Dispatchers.IO) {
                    AppDatabase.open(context, r.dbKey).also { it.openHelper.writableDatabase }
                }
                _state.value = LockState.Unlocked(
                    Session(db, r.dbKey, CoroutineScope(SupervisorJob() + Dispatchers.IO), dropBox)
                )
                ResultService.clearResult(context)
                AttemptOutcome.UNLOCKED
            }
            Vault.UnlockResult.Duress -> {
                withContext(Dispatchers.IO) { wipeEverything() }
                // On reste sur la calculatrice (état Locked) : l'effacement est silencieux.
                // Le coffre n'étant plus configuré, tout nouvel essai échoue comme un PIN faux,
                // et au prochain démarrage l'app, vierge, proposera une configuration.
                AttemptOutcome.WIPED
            }
            Vault.UnlockResult.NoMatch -> AttemptOutcome.NO_MATCH
        }
    }

    suspend fun changePins(currentSecret: String, newSecret: String, newDuress: String): Boolean =
        withContext(Dispatchers.Default) { vault.changePins(currentSecret, newSecret, newDuress) }

    fun protectionLevel(): ProtectionLevel = vault.protectionLevel()

    /** « Tout effacer maintenant » (Réglages) : même destruction que le code d'effacement, puis configuration. */
    suspend fun wipeNow() = mutex.withLock {
        withContext(Dispatchers.IO) { wipeEverything() }
        _state.value = LockState.NeedsSetup
    }

    /** Verrouille immédiatement (bouton, retour arrière, mise en arrière-plan). */
    fun lock() {
        val current = _state.value
        if (current is LockState.Unlocked) {
            _state.value = LockState.Locked
            current.session.close()
        }
    }

    /** Appelé par le cycle de vie du processus (ON_STOP). */
    fun onAppBackgrounded() {
        if (externalActivityInFlight) return
        lock()
    }

    /** À encadrer autour d'un écran externe lancé par nous (scanner QR Google). */
    fun expectExternalActivity(inFlight: Boolean) {
        externalActivityInFlight = inFlight
    }

    /** Après effacement, l'app redevient vierge : au prochain démarrage → configuration. */
    fun isConfigured() = vault.isConfigured()

    private fun wipeEverything() {
        lock()
        ResultService.stop(context)
        BackgroundPrefs.setEnabled(context, false)
        dropBox.wipe()
        vault.wipe()
        wipeFiles()
    }

    private fun wipeFiles() {
        context.deleteDatabase(AppDatabase.FILE_NAME)
        listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir, context.codeCacheDir)
            .forEach { dir -> dir.listFiles()?.forEach(File::deleteRecursively) }
    }
}
