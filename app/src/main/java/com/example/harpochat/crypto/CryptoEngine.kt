package com.example.harpochat.crypto

interface CryptoEngine {
    suspend fun generateIdentity()
    suspend fun encrypt(plaintext: ByteArray, peerId: String): ByteArray
    suspend fun decrypt(ciphertext: ByteArray, peerId: String): ByteArray
}

class FakeCryptoEngine : CryptoEngine {
    override suspend fun generateIdentity() {}
    override suspend fun encrypt(plaintext: ByteArray, peerId: String) = plaintext
    override suspend fun decrypt(ciphertext: ByteArray, peerId: String) = ciphertext
}
