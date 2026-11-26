# Écarts proposés à `SERVER_SPEC.md` v0.2

> **Pour :** le porteur du projet, qui tient la spec, et l'équipe Android.
> **De :** l'équipe serveur, phase P1 (2026-11-26). La spec n'a **pas** été modifiée : chaque ligne
> ci-dessous est une proposition de modification, avec sa raison. Tout est déjà implémenté ainsi
> dans `harpochat-core` v0.1.

| # | Section de la spec | Proposition | Raison |
|---|---|---|---|
| E1 | §4.2, §8 (types 0x20 / 0x21) | `DIRECTORY_REQUEST` / `DIRECTORY` deviennent des **trames de lien** (sur la session Noise), pas des charges Sphinx. | À l'amorçage, le client ne connaît pas encore la topologie : il ne peut pas construire de chemin Sphinx. |
| E2 | §5 | Transport nœud ↔ nœud : **WSS + Noise uniquement**, port 443 par défaut. Pas de `tcp://…:7600`. | Un seul port à ouvrir pour les bénévoles derrière une box. |
| E3 | §7.1, §7.2 | Contenu exact de `write_auth` / `read_auth` et dérivation de `mailbox_id` = `BLAKE2b-256(len‖"harpo-mbx-id" ‖ write_pub ‖ read_pub)`, clés par époque dérivées par `HKDF-SHA256(pair_secret, "harpo-mbx-keys" ‖ epoch_be)`. | La spec laissait le format ouvert. La boîte se vérifie **sans état** : aucun enregistrement préalable, aucune confiance au premier usage. |
| E4 | §4.4 (règle 1), §5 | Noise ne manipule que des clés X25519. Chaque nœud a une **clé statique Noise X25519 long terme**, et le handshake porte une signature de cette clé par `id_pub` (Ed25519). | Sans ça, « authentifier la racine par sa clé `id_pub` via Noise » est impossible. Le bloc `Bootstrap` reste inchangé. |
| E5 | §4.1 | Ajout du champ `noise_pub` (X25519) dans `NodeDescriptor`. | Conséquence d'E4. |
| E6 | §4.1, §4.3 | Les objets signés (descripteur, `Vouch`, `Revoke`, `Directory`, `NodeInvite`) sont encodés `{ body: bstr(CBOR du contenu), sig }`, la signature portant sur les octets exacts de `body`. | Évite toute règle de CBOR canonique et toute malléabilité à la vérification. |
| E7 | §7.5 | Un SUBSCRIBE en petit paquet porte **3 SURB** au plus (16 en grand paquet). Le stock d'environ 10 SURB se constitue en **plusieurs** SUBSCRIBE. | Mesuré : un SURB fait 396 o et l'enveloppe d'un petit paquet 1 664 o. |
| E8 | §4.3 (jeton) | Le jeton `hcn1-` mesure environ **370 caractères** (avec un seul endpoint), pas ≈ 200. | 32 o d'identité + 32 o de secret + 64 o de signature + endpoint, en Base32. Le décodage tolère espaces, retours à la ligne, tirets, minuscules et confusions O/0, I/L/1. |
| E9 | §4.3 (révocation) | Une révocation n'est prise en compte que si elle est signée par le **parrain direct** du nœud révoqué ou par une **racine**. | Sinon n'importe qui pourrait révoquer n'importe quel nœud. |
| E10 | §13 | Blob maximal d'une PUT en petit paquet : **1 400 o**. Au-delà, grand paquet ou découpage en morceaux (§8, médias). | Mesuré. |
