/*
——————What are you doing here ?——————
⠀⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝
⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇
⠀⠀⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀
⠀⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀
⠀⠀⠀⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀
⠀⠀⠀⠀⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
————————————————————————————————————
*/

package com.example.harpochat.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle

/**
 * Moteur E2EE basé sur signal-protocol-java.
 * Démo "loopback" : on simule deux appareils locaux (me -> peer).
 */
class SignalCryptoEngine : CryptoEngine {

    // Deux identités/stores (appareils simulés)
    private lateinit var alice: MemorySignalStore
    private lateinit var bob: MemorySignalStore

    // Adresses Signal (userId, deviceId)
    private val aliceAddr = SignalProtocolAddress("me", 1)
    private val bobAddr   = SignalProtocolAddress("peer", 2)

    private var isInit = false

    override suspend fun generateIdentity() = withContext(Dispatchers.Default) {
        if (isInit) return@withContext

        // Génère identité + prekeys pour les deux côtés
        val a = generateKeys()
        val b = generateKeys()

        alice = MemorySignalStore(a.identityKeyPair, a.registrationId)
        bob   = MemorySignalStore(b.identityKeyPair, b.registrationId)

        // Charge les prekeys/signé dans les stores
        a.preKeys.forEach { alice.preKeyStore.storePreKey(it.id, it) }
        b.preKeys.forEach { bob.preKeyStore.storePreKey(it.id, it) }
        alice.signedPreKeyStore.storeSignedPreKey(1, a.signedPreKey)
        bob.signedPreKeyStore.storeSignedPreKey(1, b.signedPreKey)

        // Prépare les bundles à "publier"
        val aBundle = buildPreKeyBundle(alice, aliceAddr.deviceId, 1, a.preKeys.first())
        val bBundle = buildPreKeyBundle(bob,   bobAddr.deviceId,   1, b.preKeys.first())

        // Initialise les sessions dans les deux sens
        setupSession(alice, bobAddr, bBundle)
        setupSession(bob,   aliceAddr, aBundle)

        isInit = true
    }

    /** Crée la session avec un pair à partir de son PreKeyBundle. */
    private fun setupSession(
        localStore: MemorySignalStore,
        remote: SignalProtocolAddress,
        remoteBundle: PreKeyBundle
    ) {
        val builder = SessionBuilder(localStore.toSignalStore(), remote)
        builder.process(remoteBundle)
    }

    /** Chiffre un message envoyé de "me" vers "peer". */
    override suspend fun encrypt(plaintext: ByteArray, peerId: String): ByteArray =
        withContext(Dispatchers.Default) {
            if (!isInit) generateIdentity()
            val cipher = SessionCipher(alice.toSignalStore(), bobAddr)
            val msg: CiphertextMessage = cipher.encrypt(plaintext)
            msg.serialize()
        }

    /** Déchiffre un message reçu par "peer" depuis "me". */
    override suspend fun decrypt(ciphertext: ByteArray, peerId: String): ByteArray =
        withContext(Dispatchers.Default) {
            if (!isInit) generateIdentity()
            val cipher = SessionCipher(bob.toSignalStore(), aliceAddr)

            // Choisir explicitement la surcharge correcte
            try {
                val preKeyMsg = PreKeySignalMessage(ciphertext)
                cipher.decrypt(preKeyMsg)
            } catch (_: Exception) {
                val signalMsg = SignalMessage(ciphertext)
                cipher.decrypt(signalMsg)
            }
        }
}
