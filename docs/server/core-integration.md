# `harpochat-core` v0.1 — guide d'intégration côté Android

> **Pour :** l'équipe qui développe l'app Android HarpoChat.
> **De :** l'équipe serveur. **Date :** 2026-11-26. **Version du noyau :** `0.1.0` (tag `core-v0.1.0` après fusion).
> **Contrat de référence :** `docs/SERVER_SPEC.md` v0.2. Les écarts proposés sont dans [`spec-deltas.md`](spec-deltas.md).

## 1. Ce qui change pour vous, en bref

1. **L'ancien protocole JSON est supprimé** côté serveur : `RelayWsTransport`, les trames `hello` /
   `challenge` / `send` / `deliver`, et le format d'enveloppe signé `from`/`to` n'existent plus. Ne
   pas investir dessus.
2. **Tous les formats du mesh viennent d'un seul crate Rust, `harpochat-core`.** Ne réimplémentez ni
   CBOR, ni Sphinx, ni la dérivation des boîtes, ni les jetons en Kotlin : appelez le noyau.
3. **Les bindings Kotlin (uniffi) sont livrés en phase P2** : voir [`gradle-integration.md`](gradle-integration.md).
   Les noms Kotlin suivent ceux de l'API Rust ci-dessous (`build_put` → `buildPut`, etc.).
4. Le noyau est **pur** : aucun réseau, aucun fichier, aucune horloge. L'heure est toujours passée en
   paramètre (`now_unix_secs`). Le réseau (WSS + Noise) reste dans l'app.

## 2. Tailles figées

Valeurs mesurées, aussi présentes dans `crates/harpochat-core/test-vectors/v1.json` (clé `sizes`) :

| | Petit paquet (`Small`) | Grand paquet (`Large`) |
|---|---|---|
| Message Noise sur le fil | 2 048 o | 32 768 o |
| Trame de lien (avant Noise) | 2 032 o | 32 752 o |
| Paquet Sphinx | 2 029 o | 32 749 o |
| Charge utile Sphinx | 1 681 o | 32 401 o |
| **Enveloppe max** (`max_message_len`) | **1 664 o** | **32 384 o** |
| **Blob max dans une PUT** | **1 400 o** | ≈ 32 100 o |
| SURB par SUBSCRIBE | **3** | 16 (plafond `MAX_SURBS`) |

En-tête Sphinx : 348 o. Un SURB sérialisé : 396 o. Chemins de 1 à 5 sauts (`MAX_HOPS = 5`).

**Conséquence importante pour le temps réel (§7.5 de la spec) :** un SUBSCRIBE en petit paquet ne
porte que **3 SURB**, pas ~10. Pour constituer un stock de 10 SURB, l'app envoie **plusieurs
SUBSCRIBE** sur la même boîte (le serveur cumulera les stocks), ou un SUBSCRIBE en grand paquet.

## 3. Modules et usage attendu

### 3.1 Boîtes aux lettres — `mailbox`

- `MailboxKeys::derive(pair_secret: &[u8; 32], epoch: u32)` → clés d'écriture et de lecture de
  l'époque, `mailbox_id()`.
- **Le secret de paire est directionnel :** une boîte A→B et une boîte B→A, chacune avec son propre
  secret. Seul l'expéditeur garde la clé d'écriture, seul le destinataire la clé de lecture.
- Époque = jour UTC : `epoch_of(now)`. **À la reconnexion, relever les 7 époques** renvoyées par
  `epochs_to_poll(now)` (TTL 7 jours).
- PUT : `sign_put(&keys, blob, nonce)` → `WriteAuth`. FETCH / ACK / SUBSCRIBE : construire le
  `Message`, calculer `envelope::read_bound(&msg)`, puis `sign_read(&keys, op, &bound, now, nonce)`.
  Les nonces viennent de `keys::random_bytes::<16>()`. L'horloge du téléphone doit être à ±10 min.

### 3.2 Enveloppes — `envelope`

`Message::{Put, Fetch, Ack, Drop, Loop, Subscribe, FetchResponse, Push, Notify}`, `encode` / `decode`.
Codes de type identiques au §8 de la spec. `DIRECTORY_REQUEST`/`DIRECTORY` n'en font plus partie (écart E1).

### 3.3 Sphinx — `sphinx`

- Envoi : `build_forward(env_bytes, &route, LOCAL_DELIVERY, &delays_ms, class, None)`. La route est
  `gateway → mix(es) → mailbox` (au moins 3 nœuds distincts). `LOCAL_DELIVERY` signifie « remets à
  la boîte aux lettres du dernier nœud ». Les délais (loi exponentielle, moyenne 100 ms) sont tirés
  par l'app.
- Réponses : `create_surb(&[mix…, gateway], inbox_token, surb_id, &delays)` → `(surb, recovery)`.
  Le SURB part dans un FETCH ou un SUBSCRIBE ; **`recovery` reste en RAM dans l'app** (jamais sur
  disque). Quand un paquet revient, la gateway vous le remet avec `surb_id` : retrouvez la
  `SurbRecovery` correspondante et appelez `recover_reply(&recovery, payload)`.
- `inbox_token` : 32 octets aléatoires, enregistrés auprès de la gateway par la trame de lien
  `RegisterInbox` (le format exact de cette trame arrive en P4). Changez-le à chaque connexion.
- SURB « à graine unique » : ni la boîte aux lettres qui répond, ni la gateway ne peuvent lire la
  réponse. Seul l'app peut.

### 3.4 Annuaire et amorçage — `bootstrap`, `directory`, `descriptor`

- Le bloc embarqué dans l'APK est un `Bootstrap { v, roots: [RootEntry { id_pub, endpoints }] }`
  (CBOR, `Bootstrap::decode`). L'obscurcissement reste à votre charge.
- Après réception d'annuaires : `verify_directory(&signed_dir, &bootstrap.root_ids(), now)` pour
  chaque nœud interrogé, puis `intersect(&[vue1, vue2, …])` (§4.2 : un seul nœud ne peut pas vous
  imposer une fausse topologie).
- `Hop::from(&descriptor)` donne un saut de route.

### 3.5 Trames de lien — `link`

`LinkFrame { kind, body }.encode(class)` produit exactement la taille de la classe ; à chiffrer
ensuite avec Noise. `LinkFrame::padding(class)` sert au trafic de couverture de lien.

## 4. Vecteurs de test

`crates/harpochat-core/test-vectors/v1.json` : 14 vecteurs (`inputs` → `output` en hexadécimal) et
la table `sizes`. Les vecteurs Sphinx figent un paquet et les clés des nœuds ; le résultat attendu
est le texte clair après traitement de tous les sauts. Votre CI devra les rejouer via les bindings
de P2. Côté serveur, `cargo test -p harpochat-core --test vectors` les rejoue.

## 6. Connexion à un nœud (phase P4, implémentée côté serveur)

Pile, de bas en haut : **TCP → TLS 1.3 → WebSocket (messages binaires uniquement) → Noise XX**.

1. **TLS :** le nœud présente un certificat auto-signé, régénéré en RAM à chaque démarrage.
   **Acceptez n'importe quel certificat** (mais laissez la vérification des signatures de la
   poignée de main TLS active). TLS ne sert qu'à ressembler à du HTTPS ordinaire. URL de la requête
   WebSocket : `wss://harpochat/` (le chemin est ignoré).
2. **Noise :** `Noise_XX_25519_ChaChaPoly_BLAKE2s`, le téléphone est l'**initiateur**. Utilisez
   `harpochat_core::noise::Handshake::initiator(&X25519Secret::generate(), None)` : une clé statique
   **neuve à chaque connexion**, sans identité. Trois messages binaires : vous écrivez le 1, lisez le
   2, écrivez le 3. Le message 2 porte la preuve `NodeAuth` du nœud. Après `into_transport()`,
   **vérifiez `peer.node_id == Some(id_pub attendu)`**, la clé de la racine ou du descripteur :
   c'est ce qui authentifie le nœud.
3. **Ensuite**, chaque message WebSocket est **un** message Noise de 2 048 o (`Small`) ou de
   32 768 o (`Large`). Le clair est une `LinkFrame` de 2 032 / 32 752 o. Toute autre taille, tout
   message texte ou tout déchiffrement raté **ferme le lien**.
4. **Trames utiles côté téléphone :**

| Trame | Sens | Corps |
|---|---|---|
| `RegisterInbox` (0x04) | téléphone → nœud | 32 octets aléatoires, **nouveaux à chaque connexion**. C'est la destination de vos SURB. |
| `Sphinx` (0x01) | téléphone → nœud | un paquet Sphinx (`sphinx::build_forward` ou `use_surb`). |
| `Deliver` (0x08) | nœud → téléphone | `surb_id[16] ‖ charge utile finale` → `recover_reply(&recovery, payload)`. |
| `DirectoryReq` (0x02) | téléphone → nœud | vide. |
| `Directory` (0x03) | nœud → téléphone | CBOR `Signed<Directory>` → `directory::verify_directory`. |
| `Padding` (0x00) | les deux | trafic de couverture du lien, à ignorer. |

5. **Limite de débit :** 50 trames/s par lien, rafales de 200. Au-delà, le lien est fermé.
6. **Rejets silencieux :** une PUT ou un FETCH mal signé, rejoué ou expiré ne reçoit **aucune**
   réponse. Prévoyez un délai d'attente, puis une nouvelle tentative avec un nouveau nonce.
7. **Réponses de FETCH :** une réponse par SURB fourni, au plus ~1 400 o de blobs chacune, avec
   `more = true` s'il reste du courrier. Fournissez plusieurs SURB, ou refaites un FETCH.

**En M1 (P4), le chemin ne compte qu'un saut** : `[gateway]` pour l'aller comme pour les SURB.
Les chemins multi-nœuds arrivent en P5, sans changement de format.

## 7. Chemins multi-nœuds (phase P5, implémentée)

1. **Vue du réseau :** `verifyDirectory` sur les annuaires de plusieurs nœuds, puis
   `intersectDirectories`. Le résultat (`List<FfiNode>`) sert à toutes les fonctions ci-dessous.
2. **Où est une boîte ?** `mailboxNodes(mailboxId, nodes)` renvoie **3 répliques**, choisies par
   hachage de rendez-vous. Expéditeur et destinataire les calculent chacun de leur côté, sans se
   concerter.
   - **PUT** : envoyez une copie à **chaque réplique qui n'est pas votre gateway**.
   - **FETCH / SUBSCRIBE** : interrogez **les répliques qui ne sont pas votre gateway**.

   Une route ne peut jamais finir sur sa propre gateway (`forwardRoute` renvoie `Malformed`) : la
   gateway verrait à la fois votre IP et l'identifiant de la boîte. Avec 3 répliques, l'ensemble de
   l'expéditeur et celui du destinataire ont toujours au moins une réplique en commun.
3. **Aller :** `forwardRoute(nodes, gatewayId, replicaId, 100uL)` renvoie `FfiRoute { hops, delaysMs }`,
   à passer tel quel à `buildForward(env, route.hops, localDelivery(), route.delaysMs, SMALL)`.
4. **Retour (SURB) :** `replyRoute(nodes, gatewayId, replicaId, 100uL)` renvoie `mix… → gateway`, à
   passer à `createSurb(route.hops, inbox, surbId, route.delaysMs)`.
5. **Taille du mesh :** en dessous de 12 nœuds, un mix ; à partir de 12, trois mix (un par couche).
   Les délais suivent une loi exponentielle de moyenne 100 ms, uniquement sur les mix.
   Diversité : le noyau évite deux nœuds du même parrain, puis du même /16 IPv4, et relâche ces
   contraintes dans cet ordre si le mesh est trop petit.
6. **Nœud injoignable :** retirez-le de votre vue et recalculez routes et répliques. Les autres
   répliques gardent le courrier.

## 5. Ce qui arrive ensuite

| Phase | Pour vous |
|---|---|
| P2 — `core-v0.2.0` | **Fait** : voir [`gradle-integration.md`](gradle-integration.md). |
| P4 | **Fait** : voir §6. Premier serveur joignable (`harpochat-server run`). |
| P5 | **Fait** : voir §7. |
| P6 | SUBSCRIBE / PUSH en temps réel, trafic de couverture. |
