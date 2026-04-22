// SPDX-License-Identifier: MIT
package app.harpochat.link

import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Canonical invite payload exchanged out-of-band (QR code, link, NFC…) to
 * bootstrap a secure conversation between two devices.
 *
 * Wire format: base64url(JSON). The JSON is signed detached with the
 * inviter's Ed25519 identity key. A peer scanning the QR can:
 *   1. Verify the signature with [identityPub].
 *   2. Verify the code was not altered (the code is part of the signed JSON).
 *   3. Connect to [relayUrl] and authenticate with [identityPub].
 *
 * Replaces the earlier `QrPayload`/`InvitePayload` prototypes: they had no
 * signature and the two formats disagreed on field names.
 */
data class Invite(
    val version: Int,
    val identityPub: ByteArray,    // 32 bytes Ed25519 public key
    val displayName: String,
    val threadId: String,
    val relayUrl: String,          // e.g. wss://relay.example.com/v1/ws
    val nonce: ByteArray,          // 16 random bytes
    val code: String,              // short human-readable, part of signed payload
    val expEpochSec: Long,
    val signature: ByteArray       // 64 bytes Ed25519 signature over canonical(payload)
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        TimeUnit.MILLISECONDS.toSeconds(nowMs) > expEpochSec

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Invite) return false
        return version == other.version
            && identityPub.contentEquals(other.identityPub)
            && displayName == other.displayName
            && threadId == other.threadId
            && relayUrl == other.relayUrl
            && nonce.contentEquals(other.nonce)
            && code == other.code
            && expEpochSec == other.expEpochSec
            && signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var r = version
        r = 31 * r + identityPub.contentHashCode()
        r = 31 * r + displayName.hashCode()
        r = 31 * r + threadId.hashCode()
        r = 31 * r + relayUrl.hashCode()
        r = 31 * r + nonce.contentHashCode()
        r = 31 * r + code.hashCode()
        r = 31 * r + expEpochSec.hashCode()
        r = 31 * r + signature.contentHashCode()
        return r
    }
}

object InviteCodec {
    const val VERSION = 1
    private const val TYPE = "harpo_invite"
    private const val DOMAIN = "harpochat/v1/invite"

    private val RNG = SecureRandom()
    private val CODE_ALPHABET =
        "abcdefghijklmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789" // avoid 0/O/1/I/l

    /** Build + sign a fresh invite using the caller's Ed25519 identity key. */
    fun create(
        identityPub: ByteArray,
        identityPrivSeed: ByteArray,
        displayName: String,
        threadId: String,
        relayUrl: String,
        ttlSec: Long = 60,
        now: Long = System.currentTimeMillis()
    ): Invite {
        require(identityPub.size == 32) { "Ed25519 public key must be 32 bytes" }
        require(identityPrivSeed.size == 32) { "Ed25519 private key seed must be 32 bytes" }
        require(displayName.length in 1..64) { "displayName length must be 1..64" }
        require(threadId.isNotBlank()) { "threadId required" }
        require(relayUrl.startsWith("ws://") || relayUrl.startsWith("wss://")) {
            "relayUrl must be a WebSocket URL"
        }

        val nowSec = TimeUnit.MILLISECONDS.toSeconds(now)
        val nonce = ByteArray(16).also { RNG.nextBytes(it) }
        val code = buildString(10) {
            repeat(10) { append(CODE_ALPHABET[RNG.nextInt(CODE_ALPHABET.length)]) }
        }
        val expEpochSec = nowSec + ttlSec

        val msg = canonicalDigest(
            identityPub = identityPub,
            displayName = displayName,
            threadId = threadId,
            relayUrl = relayUrl,
            nonce = nonce,
            code = code,
            expEpochSec = expEpochSec
        )
        val signature = sign(identityPrivSeed, msg)
        return Invite(
            version = VERSION,
            identityPub = identityPub,
            displayName = displayName,
            threadId = threadId,
            relayUrl = relayUrl,
            nonce = nonce,
            code = code,
            expEpochSec = expEpochSec,
            signature = signature
        )
    }

    fun encode(invite: Invite): String {
        val json = JSONObject().apply {
            put("v", invite.version)
            put("type", TYPE)
            put("identity", b64(invite.identityPub))
            put("name", invite.displayName)
            put("thread", invite.threadId)
            put("relay", invite.relayUrl)
            put("nonce", b64(invite.nonce))
            put("code", invite.code)
            put("exp", invite.expEpochSec)
            put("sig", b64(invite.signature))
        }
        return b64(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(wire: String): Result<Invite> = runCatching {
        val raw = Base64.decode(wire, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val o = JSONObject(String(raw, Charsets.UTF_8))
        require(o.optInt("v", 0) == VERSION) { "unsupported invite version" }
        require(o.optString("type") == TYPE) { "wrong invite type" }

        val invite = Invite(
            version = VERSION,
            identityPub = b64d(o.getString("identity")),
            displayName = o.getString("name"),
            threadId = o.getString("thread"),
            relayUrl = o.getString("relay"),
            nonce = b64d(o.getString("nonce")),
            code = o.getString("code"),
            expEpochSec = o.getLong("exp"),
            signature = b64d(o.getString("sig"))
        )
        require(invite.identityPub.size == 32) { "identity key must be 32 bytes" }
        require(invite.signature.size == 64) { "signature must be 64 bytes" }
        invite
    }

    /** Verify signature against the invite's own public key. */
    fun verify(invite: Invite): Boolean {
        val msg = canonicalDigest(
            identityPub = invite.identityPub,
            displayName = invite.displayName,
            threadId = invite.threadId,
            relayUrl = invite.relayUrl,
            nonce = invite.nonce,
            code = invite.code,
            expEpochSec = invite.expEpochSec
        )
        return verifySignature(invite.identityPub, msg, invite.signature)
    }

    // ------------------------------------------------------------------------
    // Crypto
    // ------------------------------------------------------------------------

    private fun sign(privSeed: ByteArray, message: ByteArray): ByteArray {
        val spec = EdDSANamedCurveTable.getByName("Ed25519")
        val privKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(privSeed, spec))
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(privKey)
        engine.update(message)
        return engine.sign()
    }

    private fun verifySignature(pub: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return runCatching {
            val spec = EdDSANamedCurveTable.getByName("Ed25519")
            val pubKey = EdDSAPublicKey(EdDSAPublicKeySpec(pub, spec))
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(pubKey)
            engine.update(message)
            engine.verify(signature)
        }.getOrDefault(false)
    }

    private fun canonicalDigest(
        identityPub: ByteArray,
        displayName: String,
        threadId: String,
        relayUrl: String,
        nonce: ByteArray,
        code: String,
        expEpochSec: Long
    ): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(DOMAIN.toByteArray(Charsets.UTF_8))
        md.update(identityPub)
        md.update(byteOf(displayName))
        md.update(byteOf(threadId))
        md.update(byteOf(relayUrl))
        md.update(nonce)
        md.update(byteOf(code))
        md.update(longLe(expEpochSec))
        return md.digest()
    }

    private fun byteOf(s: String) = s.toByteArray(Charsets.UTF_8)

    private fun longLe(v: Long): ByteArray {
        val out = ByteArray(8)
        var x = v
        for (i in 0..7) {
            out[i] = (x and 0xFF).toByte()
            x = x shr 8
        }
        return out
    }

    private fun b64(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun b64d(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
