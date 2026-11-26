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
3. **Les bindings Kotlin (uniffi) arrivent en phase P2** (`core-v0.2.0`). D'ici là, l'API Rust
   ci-dessous est la référence : les noms et la sémantique resteront ceux-là.
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

## 5. Ce qui arrive ensuite

| Phase | Pour vous |
|---|---|
| P2 — `core-v0.2.0` | Bindings uniffi + `.so` pour `arm64-v8a`, `armeabi-v7a`, `x86_64`, guide Gradle. |
| P4 | Format des trames `RegisterInbox` / `DirectoryReq`, handshake Noise (`Noise_XX_25519_ChaChaPoly_BLAKE2s` + signature de la clé statique, écart E4), premier serveur joignable. |
| P5–P6 | Mesh à 5 nœuds, SUBSCRIBE / PUSH en temps réel. |
