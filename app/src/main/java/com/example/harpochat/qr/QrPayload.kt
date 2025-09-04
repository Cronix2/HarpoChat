package com.example.harpochat.qr

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class QrInvite(
    val version: Int = 1,
    val type: String = "chat_invite",
    val fromUserId: String,
    val fromUserName: String,
    val threadId: String,
    val saltB64: String,
    val code: String,       // 10 chars
    val expEpochSec: Long   // now + 60s
)

private val RNG = SecureRandom()
private val CODE_ALPHABET = "abcdefghijklmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789" // pas de 0/O/1/I/l

private fun randomCode(len: Int = 10): String =
    buildString(len) { repeat(len) { append(CODE_ALPHABET[RNG.nextInt(CODE_ALPHABET.length)]) } }

private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { RNG.nextBytes(it) }

fun encodeInvite(inv: QrInvite): String {
    val json = JSONObject().apply {
        put("v", inv.version)
        put("type", inv.type)
        put("fromUser", JSONObject().apply {
            put("id", inv.fromUserId)
            put("name", inv.fromUserName)
        })
        put("threadId", inv.threadId)
        put("salt", inv.saltB64)
        put("code", inv.code)
        put("exp", inv.expEpochSec)
        // pas de sig en MVP
    }
    val raw = json.toString().toByteArray(Charsets.UTF_8)
    return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

fun decodeInvite(str: String): Result<QrInvite> = runCatching {
    val raw = Base64.decode(str, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val o = JSONObject(String(raw, Charsets.UTF_8))
    require(o.optInt("v", 0) == 1) { "Version incompatible" }
    require(o.optString("type") == "chat_invite") { "Type invalide" }
    val from = o.getJSONObject("fromUser")
    QrInvite(
        version = 1,
        type = "chat_invite",
        fromUserId = from.getString("id"),
        fromUserName = from.getString("name"),
        threadId = o.getString("threadId"),
        saltB64 = o.getString("salt"),
        code = o.getString("code"),
        expEpochSec = o.getLong("exp")
    )
}

fun makeInviteNow(fromUserId: String, fromUserName: String, threadId: String, ttlSec: Long = 60): QrInvite {
    val nowSec = System.currentTimeMillis() / 1000
    return QrInvite(
        fromUserId = fromUserId,
        fromUserName = fromUserName,
        threadId = threadId,
        saltB64 = Base64.encodeToString(randomBytes(16), Base64.NO_WRAP),
        code = randomCode(10),
        expEpochSec = nowSec + ttlSec
    )
}

fun isInviteExpired(inv: QrInvite, nowMs: Long = System.currentTimeMillis()): Boolean {
    val nowSec = TimeUnit.MILLISECONDS.toSeconds(nowMs)
    return nowSec > inv.expEpochSec
}

