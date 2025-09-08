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

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.*
import org.whispersystems.libsignal.state.impl.*
import org.whispersystems.libsignal.util.KeyHelper

class MemorySignalStore(
    identityKeyPair: IdentityKeyPair,
    registrationId: Int
) {
    val sessionStore: SessionStore = InMemorySessionStore()
    val preKeyStore: PreKeyStore = InMemoryPreKeyStore()
    val signedPreKeyStore: SignedPreKeyStore = InMemorySignedPreKeyStore()
    val identityStore: IdentityKeyStore = object : InMemoryIdentityKeyStore(identityKeyPair, registrationId) {}

    /** Fournit un SignalProtocolStore unique en déléguant aux 4 stores ci-dessus */
    fun toSignalStore(): SignalProtocolStore =
        DelegatingSignalStore(sessionStore, preKeyStore, signedPreKeyStore, identityStore)
}

/** Store composite qui implémente SignalProtocolStore par délégation */
class DelegatingSignalStore(
    private val sessionStore: SessionStore,
    private val preKeyStore: PreKeyStore,
    private val signedPreKeyStore: SignedPreKeyStore,
    private val identityKeyStore: IdentityKeyStore
) : SignalProtocolStore,
    SessionStore by sessionStore,
    PreKeyStore by preKeyStore,
    SignedPreKeyStore by signedPreKeyStore,
    IdentityKeyStore by identityKeyStore

data class GeneratedKeys(
    val identityKeyPair: IdentityKeyPair,
    val registrationId: Int,
    val preKeys: List<PreKeyRecord>,
    val signedPreKey: SignedPreKeyRecord
)

fun generateKeys(startPreKeyId: Int = 1, count: Int = 100): GeneratedKeys {
    val identity = KeyHelper.generateIdentityKeyPair()
    val regId = KeyHelper.generateRegistrationId(false)
    val preKeys = KeyHelper.generatePreKeys(startPreKeyId, count)
    val signedPre = KeyHelper.generateSignedPreKey(identity, 1)
    return GeneratedKeys(identity, regId, preKeys, signedPre)
}

fun buildPreKeyBundle(
    store: MemorySignalStore,
    deviceId: Int,
    signedPreKeyId: Int = 1,
    onePreKey: PreKeyRecord
): PreKeyBundle {
    val identity = store.identityStore.identityKeyPair.publicKey
    val registrationId = store.identityStore.localRegistrationId
    val signedPre = store.signedPreKeyStore.loadSignedPreKey(signedPreKeyId)
    return PreKeyBundle(
        registrationId,
        deviceId,
        onePreKey.id,
        onePreKey.keyPair.publicKey,
        signedPreKeyId,
        signedPre.keyPair.publicKey,
        signedPre.signature,
        identity
    )
}
