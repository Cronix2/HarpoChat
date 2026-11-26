// SPDX-License-Identifier: MIT
package app.harpochat.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Stockage clé/valeur non secret (préférences au nom neutre en production, Map en test). */
interface VaultStore {
    fun get(key: String): String?
    fun putAll(values: Map<String, String>)
    fun clear()
}

/**
 * Coffre de clés de l'app.
 *
 * ```
 * étiré  = PBKDF2-HMAC-SHA256(PIN secret, sel, N itérations)
 * kek    = HMAC-SHA256(clé matérielle Keystore, étiré)       ← passage obligé par le téléphone
 * stocké = AES-256-GCM(kek, clé_db)                          ← clé_db : 32 octets aléatoires
 * ```
 *
 * - Aucune empreinte du PIN secret n'est conservée : la vérification consiste à déchiffrer.
 * - Le PIN de détresse est vérifié par une empreinte PBKDF2 (le connaître ne permet que d'effacer).
 * - Les deux vérifications sont **toujours** calculées : le temps de réponse ne révèle rien.
 * - [wipe] détruit la clé matérielle : la base devient illisible même si ses fichiers survivent.
 */
class Vault(
    private val store: VaultStore,
    private val binder: KeyBinder,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val rng: SecureRandom = SecureRandom()
) {
    sealed interface UnlockResult {
        /** PIN secret : clé de base de données déchiffrée (à effacer après usage). */
        class Secret(val dbKey: ByteArray) : UnlockResult
        data object Duress : UnlockResult
        data object NoMatch : UnlockResult
    }

    fun isConfigured(): Boolean = store.get(K_VERSION) == VERSION && binder.exists()

    /** Premier lancement : crée la clé matérielle et une clé de base neuve. Renvoie la clé de base. */
    fun setup(secretPin: String, duressPin: String): ByteArray {
        require(secretPin != duressPin) { "les PIN doivent différer" }
        binder.create()
        val dbKey = randomBytes(32)
        store.clear()
        store.putAll(wrapEntries(secretPin, dbKey) + duressEntries(duressPin) + (K_VERSION to VERSION))
        return dbKey
    }

    fun unlock(pin: String): UnlockResult {
        if (!isConfigured()) return UnlockResult.NoMatch
        // Les deux calculs sont faits quoi qu'il arrive (temps constant vis-à-vis du résultat).
        val duress = verifyDuress(pin)
        val dbKey = unwrap(pin)
        return when {
            dbKey != null -> UnlockResult.Secret(dbKey)
            duress -> UnlockResult.Duress
            else -> UnlockResult.NoMatch
        }
    }

    /** Change les PIN. Exige le PIN secret actuel ; la clé de base ne change pas (re-chiffrement). */
    fun changePins(currentSecret: String, newSecret: String, newDuress: String): Boolean {
        require(newSecret != newDuress) { "les PIN doivent différer" }
        val dbKey = unwrap(currentSecret) ?: return false
        try {
            store.putAll(wrapEntries(newSecret, dbKey) + duressEntries(newDuress))
        } finally {
            dbKey.fill(0)
        }
        return true
    }

    fun protectionLevel(): ProtectionLevel = binder.protectionLevel()

    /** Crypto-shredding : clé matérielle détruite, paramètres effacés. */
    fun wipe() {
        binder.destroy()
        store.clear()
    }

    // ------------------------------------------------------------------

    private fun wrapEntries(secretPin: String, dbKey: ByteArray): Map<String, String> {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val kek = deriveKek(secretPin, salt)
        try {
            val ct = Cipher.getInstance(AES_GCM).run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                updateAAD(AAD)
                doFinal(dbKey)
            }
            return mapOf(K_SALT to b64(salt), K_IV to b64(iv), K_WRAPPED to b64(ct), K_ITER to iterations.toString())
        } finally {
            kek.fill(0)
        }
    }

    private fun unwrap(pin: String): ByteArray? {
        val salt = store.get(K_SALT)?.let(::unb64) ?: return null
        val iv = store.get(K_IV)?.let(::unb64) ?: return null
        val ct = store.get(K_WRAPPED)?.let(::unb64) ?: return null
        val iter = store.get(K_ITER)?.toIntOrNull() ?: return null
        val kek = deriveKek(pin, salt, iter)
        return try {
            Cipher.getInstance(AES_GCM).run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                updateAAD(AAD)
                doFinal(ct)
            }
        } catch (_: AEADBadTagException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    private fun duressEntries(duressPin: String): Map<String, String> {
        val salt = randomBytes(16)
        return mapOf(K_D_SALT to b64(salt), K_D_HASH to b64(pbkdf2(duressPin, salt, iterations)))
    }

    private fun verifyDuress(pin: String): Boolean {
        val salt = store.get(K_D_SALT)?.let(::unb64) ?: return false
        val expected = store.get(K_D_HASH)?.let(::unb64) ?: return false
        val iter = store.get(K_ITER)?.toIntOrNull() ?: iterations
        return MessageDigest.isEqual(expected, pbkdf2(pin, salt, iter))
    }

    private fun deriveKek(pin: String, salt: ByteArray, iter: Int = iterations): ByteArray {
        val stretched = pbkdf2(pin, salt, iter)
        return try { binder.bind(stretched) } finally { stretched.fill(0) }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iter, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun randomBytes(n: Int) = ByteArray(n).also(rng::nextBytes)
    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    companion object {
        const val DEFAULT_ITERATIONS = 210_000
        private const val VERSION = "1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private val AAD = "calc-cfg-v1".toByteArray()
        // Noms de clés volontairement anodins.
        private const val K_VERSION = "v"
        private const val K_SALT = "s0"
        private const val K_IV = "s1"
        private const val K_WRAPPED = "s2"
        private const val K_ITER = "n"
        private const val K_D_SALT = "t0"
        private const val K_D_HASH = "t1"
    }
}
