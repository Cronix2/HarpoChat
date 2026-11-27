# Intégrer `harpochat-core` dans l'app Android (phase P2)

> **Pour :** l'équipe qui développe l'app Android HarpoChat.
> **De :** l'équipe serveur, 2026-11-27. À lire avec [`core-integration.md`](core-integration.md)
> (sémantique, tailles, protocole de connexion).

## 1. Ce qui est livré

`harpochat-core-android.zip` est publié sur la page
[Releases](https://github.com/Cronix2/HarpoChat_Serveur/releases), avec les binaires du serveur :
dans la pré-release **`latest`**, reconstruite à chaque modification de `main`, et dans chaque
version numérotée **`vX.Y.Z`**. Prenez une version numérotée pour une build de l'app, `latest` pour
suivre le développement. Sur une PR, le workflow **Android core** fournit aussi l'archive en
artefact. Contenu de l'archive :

```text
jniLibs/arm64-v8a/libharpochat_core_ffi.so
jniLibs/armeabi-v7a/libharpochat_core_ffi.so
jniLibs/x86_64/libharpochat_core_ffi.so
kotlin/app/harpochat/core/harpochat_core_ffi.kt     bindings générés (uniffi 0.33), package app.harpochat.core
test-vectors/v1.json                                 vecteurs à rejouer dans vos tests
```

Les `.so` sont compilés avec `minSdk` 26 (`--platform 26`), comme l'app.

## 2. Gradle

1. Copiez `jniLibs/` dans `app/src/main/jniLibs/`, et `kotlin/app/harpochat/core/` dans
   `app/src/main/java/app/harpochat/core/`. Ces fichiers sont générés : ne les modifiez pas, remplacez-les à
   chaque nouvelle version du noyau.
2. Ajoutez JNA, dont dépendent les bindings uniffi :

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("net.java.dev.jna:jna:5.17.0@aar")
}
```

3. R8 / ProGuard (activé dans l'app) : conservez JNA et les bindings, qui sont appelés par réflexion.

```proguard
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class app.harpochat.core.** { *; }
-dontwarn java.awt.**
```

## 3. Parcours d'un téléphone en Kotlin

Le noyau ne fait **aucune** I/O : vous gardez OkHttp pour le WebSocket. Le test serveur
`crates/harpochat-core-ffi/tests/phone_flow.rs` exécute exactement ce parcours contre un vrai nœud,
et sert de référence exécutable.

```kotlin
import app.harpochat.core.*

// 1. TLS (accepter tout certificat) + WebSocket binaire vers wss://<nœud>/  — OkHttp.
// 2. Noise XX, le téléphone est l'initiateur :
val hs = FfiHandshake.initiator()
ws.send(hs.writeMessage().toByteString())          // message 1
hs.readMessage(next())                              // message 2 (preuve d'identité du nœud)
ws.send(hs.writeMessage().toByteString())          // message 3
val session = hs.finish()
require(session.peerNodeId.contentEquals(expectedNodeId)) { "mauvais nœud" }
val t = session.transport

fun send(kind: FfiFrameKind, body: ByteArray) =
    ws.send(t.encrypt(encodeFrame(kind, body, FfiSizeClass.SMALL)).toByteString())
fun receive(bytes: ByteArray): FfiFrame? =
    decodeFrame(t.decrypt(bytes)).takeIf { it.kind != FfiFrameKind.PADDING }   // padding : ignorer

// 3. Boîte de réception de cette connexion (jeton neuf à chaque connexion),
//    puis débit descendant voulu (P6) : 1 trame/s au premier plan, 0,05 à 0,1 en arrière-plan.
val inbox = randomBytes(32u)
send(FfiFrameKind.REGISTER_INBOX, inbox)
send(FfiFrameKind.COVER_RATE, coverRateBody(1.0))

// 4. Annuaire : DIRECTORY_REQ → DIRECTORY, vérifié contre les racines du bloc d'amorçage.
val nodes = verifyDirectory(directoryFrame.body, roots, nowSecs)   // List<FfiNode>
val hop = FfiHop(nodes[0].idPub, nodes[0].sphinxPub)

// 5. Envoyer un message (blob déjà chiffré par libsignal).
val keys = FfiMailboxKeys.derive(pairSecretAtoB, epochOf(nowSecs))
val put = buildPut(keys, e2eeBlob)
send(FfiFrameKind.SPHINX, buildForward(put, listOf(hop), localDelivery(), listOf(0uL), FfiSizeClass.SMALL))

// 6. Relever sa boîte : un SURB vers son inbox, la réponse revient en trame DELIVER.
val surbId = randomBytes(16u)
val surb = createSurb(listOf(hop), inbox, surbId, listOf(0uL))   // garder surb.recovery EN RAM
val fetch = buildFetch(myKeys, 64u, listOf(surb.surb), nowSecs)
send(FfiFrameKind.SPHINX, buildForward(fetch, listOf(hop), localDelivery(), listOf(0uL), FfiSizeClass.SMALL))
// … à la réception d'une trame DELIVER : body = surbId(16) ‖ payload
val reply = decodeMessage(recoverReply(surb.recovery, frame.body.copyOfRange(16, frame.body.size)))
when (reply) {
    is FfiMessage.FetchResponse -> { /* reply.blobs → libsignal ; puis buildAck(...) */ }
    else -> {}
}
```

Ce parcours n'utilise qu'un nœud (`listOf(hop)`), comme le test de référence. Sur un vrai mesh,
les listes viennent de `forwardRoute` / `replyRoute` (voir `core-integration.md` §7). Le temps réel
(`buildSubscribe`, trames `Push` / `Notify`) et la couverture montante (`buildDrop`,
`phoneLoopRoute` + `buildLoopPacket`) sont décrits au §8. Le test `phone_is_pushed_through_ffi_only`
exécute SUBSCRIBE → PUT → PUSH avec la seule API FFI.

## 4. Erreurs

Toutes les fonctions qui peuvent échouer lèvent `FfiException`, avec pour sous-types `Malformed`,
`TooLarge`, `BadSignature`, `Expired`, `InvalidArgument`… `InvalidArgument` signale une mauvaise
longueur de tableau (clé de 31 octets, etc.) ou une poignée de main utilisée deux fois.

## 5. Bon à savoir

- **Panique = arrêt :** le noyau est compilé avec `panic = "abort"`. Aucune entrée hostile ne fait
  paniquer les décodeurs (testé par proptest), mais un bug ferait planter l'app plutôt que de
  laisser un état incohérent.
- **Rien n'est persisté par le noyau.** Les `recovery` des SURB doivent rester en mémoire.
  Le secret de paire et les clés de boîte relèvent du stockage chiffré de l'app (SQLCipher).
- **Versions :** `coreVersion()` renvoie la version du noyau embarqué. Le format des messages ne
  change pas sans changement de version majeure de `test-vectors`.
