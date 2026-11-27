# `harpochat-core` v0.1 — guide d'intégration côté Android

> **Pour :** l'équipe qui développe l'app Android HarpoChat.
> **De :** l'équipe serveur. **Date :** 2026-11-26. **Version du noyau :** celle de la release (page Releases : `latest`, ou `vX.Y.Z`).
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
| `CoverRate` (0x09) | téléphone → nœud | `cover_rate_body(hz)` : débit descendant voulu (§8.1). |

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

## 8. Temps réel et trafic de couverture (phase P6, implémentée)

### 8.1 Ce que la gateway vous envoie : un débit constant

La gateway ne vous envoie plus une trame dès qu'elle en a une. Elle émet à des instants tirés
d'un **processus de Poisson** de débit λ. À chaque instant, elle envoie la prochaine trame réelle
en attente (`Deliver`, `Directory`) ou, à défaut, une trame `Padding`. Un observateur du réseau
voit donc le même flux de trames, que vous receviez du courrier ou non.

- **Ignorez les trames `Padding`**, dans tous vos `recv`.
- **Choisissez λ** avec la trame `CoverRate` : `encodeFrame(COVER_RATE, coverRateBody(hz), SMALL)`.
  Envoyez-la **à chaque connexion**, juste après `RegisterInbox` (le réglage n'est pas mémorisé).
  La gateway borne la valeur à sa configuration (par défaut entre 0,05 et 10 trames/s).
  Une valeur malformée ferme le lien. Sans `CoverRate`, la gateway applique son défaut (1/s).
- **Compromis à régler côté app** (spec §6.3). Une trame fait 2 Ko et une trame réelle attend en
  moyenne 1/λ :

| λ | Données descendantes | Latence ajoutée (moyenne) | Usage conseillé |
|---|---|---|---|
| 2 /s | ≈ 15 Mo/h | 0,5 s | conversation ouverte |
| 1 /s | ≈ 7 Mo/h | 1 s | app au premier plan |
| 0,1 /s | ≈ 0,7 Mo/h | 10 s | arrière-plan |
| 0,05 /s | ≈ 0,35 Mo/h | 20 s | économie maximale |

### 8.2 Ce que vous envoyez : votre propre couverture (spec §6.3)

Le serveur ne peut pas masquer votre trafic montant : c'est à l'app de le faire.

1. Émettez un paquet Sphinx à chaque instant d'un processus de Poisson de débit λ_up (spec §13 :
   environ 1 toutes les 2 s app ouverte, moins en arrière-plan) : intervalle `-ln(1-u)/λ_up`.
2. À chaque instant : si un vrai paquet attend (PUT, FETCH, ACK, SUBSCRIBE), envoyez-le, sinon un
   leurre, moitié *drop*, moitié *loop* :
   - **Drop** : `buildDrop()` le long de `forwardRoute(nodes, gw, replique, 100)` vers une réplique
     prise au hasard (pas votre gateway). La mailbox le jette.
   - **Loop** : `phoneLoopRoute(nodes, gw, 100)` puis
     `buildLoopPacket(route.hops, inbox, loopId, route.delaysMs)`, avec `loopId` = 16 octets
     aléatoires. Il revient en trame `Deliver` dont les 16 premiers octets valent `loopId`.
3. **Pertes :** un loop non revenu après Σ`delaysMs` + 10 s est perdu. Comptez-le contre chaque
   nœud de sa route (hors gateway). Un nœud qui perd plus de 75 % de vos loops, sur au moins
   10 essais, est évité dans vos routes. Les nœuds font la même chose de leur côté. Ne publiez
   rien : ces mesures restent dans l'app.
4. Ne faites jamais partir un vrai paquet « tout de suite » en dehors du rythme : c'est ce rythme
   qui rend vos envois indiscernables.

### 8.3 Recevoir en temps réel : SUBSCRIBE → PUSH / NOTIFY (spec §7.5)

1. **À chaque connexion** (votre `inbox` change, donc les anciens SURB ne mènent plus nulle part),
   et pour chaque `mailbox_id` surveillé (époque courante, et la précédente pendant le
   recouvrement), envoyez un `SUBSCRIBE` à **chaque réplique qui n'est pas votre gateway** :
   `buildSubscribe(keys, surbs, until, now)`, avec 3 SURB par paquet `Small`
   (`sizes(SMALL).maxSurbsPerSubscribe`). Chaque SURB est créé avec
   `replyRoute(nodes, gw, replique, 100)`.
2. **Le serveur garde au plus 32 SURB par boîte** (et 100 000 par nœud). `until` est ramené à
   24 h au plus. Un `until <= now` **désabonne**. Les rejets sont silencieux.
3. **À chaque PUT accepté**, la réplique consomme **un** SURB et vous envoie :
   - `Push { mailboxId, blobs: [{ id, blob }] }` si le blob tient dans une réponse `Small` ;
   - sinon `Notify { mailboxId, pending }` : faites un `FETCH`.
4. **Réapprovisionnez** : après chaque push reçu, renvoyez un `SUBSCRIBE`. Il passe dans votre
   rythme de couverture (§8.2).
5. **Doublons :** chaque réplique pousse sa copie, avec **son propre** `id`. Dédupliquez sur le
   contenu (le message chiffré de bout en bout), puis envoyez un `ACK` à **chaque** réplique avec
   l'`id` qu'elle vous a donné. Le blob reste stocké jusqu'à l'ACK : un push perdu ne coûte que
   de la latence.
6. **Repli :** si aucun push n'arrive (stock épuisé, nœud redémarré), le `FETCH` périodique
   (§7) prend le relais.

Latence mesurée en test (5 nœuds, liens à 50 /s, téléphone à 20 /s) : **moins de 2 s** entre
la PUT et le PUSH. Avec λ = 1 /s, comptez environ 1 s de plus.

## 5. Ce qui arrive ensuite

| Phase | Pour vous |
|---|---|
| P2 | **Fait** : voir [`gradle-integration.md`](gradle-integration.md). |
| P4 | **Fait** : voir §6. Premier serveur joignable (`harpochat-server run`). |
| P5 | **Fait** : voir §7. |
| P6 | **Fait** : voir §8. Trames `Padding` à ignorer, `CoverRate`, SUBSCRIBE / PUSH, loops. |
| P7 | **Fait**, rien à changer côté app : les annuaires listent aussi les nœuds parrainés, avec leur chaîne de `Vouch`. `verifyDirectory` vérifie les chaînes jusqu'aux racines embarquées, les révocations et la limite de 3 parrainages par 30 jours. Le champ `sponsor` de `FfiNode` sert déjà à la diversité des chemins. |
| P8 | **Fait**. Un point à respecter : la **clé Sphinx de chaque nœud change tous les 7 jours** (l'ancienne reste acceptée 7 jours de plus) et un descripteur expire après 24 h. Rafraîchissez l'annuaire au moins une fois par jour, et à chaque reconnexion, avant de construire routes et SURB. Un paquet construit pour une clé vieille de plus de deux semaines est jeté sans réponse. |
