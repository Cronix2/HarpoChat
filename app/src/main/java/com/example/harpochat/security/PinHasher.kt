package com.example.harpochat.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.max

object PinHasher {
    private const val ITER = 150_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_LEN = 16

    private val rng = SecureRandom()

    /** Retourne "v1:<iter>:<saltB64>:<hashB64>" */
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITER, KEY_LEN_BITS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "v1:$ITER:$saltB64:$hashB64"
    }

    /** Vérifie un PIN sur une empreinte stockée */
    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != "v1") return false
        val iter = max(parts[1].toIntOrNull() ?: return false, 50_000)
        val salt = Base64.decode(parts[2], Base64.NO_WRAP)
        val expected = Base64.decode(parts[3], Base64.NO_WRAP)
        val got = pbkdf2(pin, salt, iter, expected.size * 8)
        return constantTimeEquals(expected, got)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iter: Int, keyLenBits: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, keyLenBits)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
