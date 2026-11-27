// SPDX-License-Identifier: MIT
package app.calc.scientific.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Transformation à clé, liée au matériel du téléphone.
 *
 * Le [Vault] fait passer chaque essai de PIN par [bind] : sans le téléphone
 * (sa puce sécurisée), un PIN volé ou une copie des fichiers ne suffit pas,
 * et la force brute ne peut pas se faire hors de l'appareil.
 */
interface KeyBinder {
    fun exists(): Boolean
    /** Crée (ou recrée) la clé matérielle. */
    fun create()
    /** HMAC-SHA256(clé matérielle, input). */
    fun bind(input: ByteArray): ByteArray
    /** Détruit la clé : tout ce qu'elle protégeait devient définitivement illisible. */
    fun destroy()
    /** Niveau de protection effectif, pour l'écran Sécurité. */
    fun protectionLevel(): ProtectionLevel
}

enum class ProtectionLevel(val label: String, val short: String, val hardware: Boolean) {
    STRONGBOX("Puce sécurisée dédiée (StrongBox)", "Puce dédiée", true),
    TEE("Zone d'exécution sécurisée du processeur (TEE)", "Zone sécurisée", true),
    SOFTWARE("Logicielle (appareil sans puce sécurisée)", "Logiciel", false),
    UNKNOWN("Inconnue", "Inconnue", false)
}

/** Implémentation Android Keystore : clé HMAC non exportable, StrongBox si disponible. */
class KeystoreKeyBinder(private val alias: String = DEFAULT_ALIAS) : KeyBinder {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun exists(): Boolean = keyStore.containsAlias(alias)

    override fun create() {
        destroy()
        val strongBoxOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { generate(strongBox = true) }.isSuccess
        if (!strongBoxOk) generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).apply {
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
        }.build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    override fun bind(input: ByteArray): ByteArray {
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("clé matérielle absente")
        return Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(input)
        }
    }

    override fun destroy() {
        runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    override fun protectionLevel(): ProtectionLevel = runCatching {
        val key = keyStore.getKey(alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> ProtectionLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> ProtectionLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> ProtectionLevel.SOFTWARE
                else -> ProtectionLevel.UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) ProtectionLevel.TEE else ProtectionLevel.SOFTWARE
        }
    }.getOrDefault(ProtectionLevel.UNKNOWN)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        /** Alias neutre : rien qui évoque une messagerie. */
        const val DEFAULT_ALIAS = "calc_cfg_k1"
    }
}
