# Réseau, étape N2 : contacts et messages par le mesh

**Date :** 2026-11-28. **Références :** `docs/SERVER_SPEC.md` §7 à §9, `docs/server/core-integration.md` §7 et §8.

## Objectif

Deux téléphones s'ajoutent comme contacts avec un consentement explicite, puis échangent des messages chiffrés de bout en bout à travers le mesh. Cela marche même quand ils ne sont pas connectés au même moment.

## Ajout d'un contact (`e2e/Pairing.kt`)

Le protocole suit la spec §9 : B ne reçoit rien tant que A n'a pas accepté.

1. **A crée une invitation** : un secret S de 32 octets et une date d'expiration. Rien d'autre : ni nom, ni adresse, ni nom d'application.
   - Le texte fait 50 caractères base64url.
   - Durée : 15 minutes pour un QR montré en personne, 48 heures pour une invitation envoyée par une autre messagerie.
2. **B importe l'invitation**, en la scannant ou en la collant. Il dépose une demande dans la boîte « demande », dérivée de S :
   - la demande contient sa clé X25519 éphémère, un aléa kB et son nom ;
   - elle est chiffrée avec une clé dérivée de S ;
   - B voit un **code de contrôle** à 6 chiffres, dérivé de S, de sa clé et de kB.
3. **A reçoit la demande** et voit le même code. Il le compare avec celui de B, puis accepte ou refuse. L'invitation est à usage unique : les autres demandes reçues pour elle sont supprimées.
4. **A répond** dans la boîte « réponse », dérivée de S et de kB : sa clé X25519 éphémère et son nom. Les deux côtés dérivent alors du même secret X25519 :
   - deux boîtes permanentes, une par sens ;
   - deux clés de chiffrement, une par sens ;
   - l'identifiant du fil.

**Sécurité de l'appairage :**
- Quelqu'un qui aurait photographié le QR peut lire la demande, mais il ne peut pas calculer le secret X25519.
- S'il envoie sa propre demande, son code de contrôle diffère de celui de B : la comparaison à voix haute le démasque.
- Un contact ajouté par QR est marqué vérifié. Un contact ajouté à distance reste « à vérifier ».

## Messages (`e2e/AppMessage.kt`, `messaging/PostOffice.kt`)

- **Format** : texte ou accusé de réception, dans un format binaire compact.
- **Bourrage** : chaque blob a l'une de quatre tailles fixes (256, 512, 1 024 ou 1 400 o). Le nœud qui stocke le blob ne voit jamais la longueur réelle du message.
- **Chiffrement** : AES-256-GCM, avec une clé par sens issue de l'appairage, derrière l'interface `PairCipher`.
- **Statuts** :
  - « envoyé » : le message a quitté le téléphone ;
  - « remis » : l'accusé de réception du contact est arrivé.
- **Messages longs** : un texte trop long pour un paquet part en plusieurs messages.
- **Messages éphémères** : la durée de vie part avec le message, et le destinataire l'applique.
- **File d'envoi persistante** (table `outbox`) : un message écrit hors ligne, ou juste avant un verrouillage, part à la prochaine ouverture.

## Acheminement (`net/Mailboxes.kt`)

- **Abonnement temps réel** : un SUBSCRIBE de 3 SURB par boîte surveillée, par époque (courante et précédente) et par réplique.
  - Chaque PUSH reçu est suivi d'un réabonnement.
  - Le moteur se réabonne aussi toutes les 15 minutes et au changement de jour UTC.
- **Relève** à la connexion : FETCH des époques depuis la création de la boîte, 7 jours au plus.
- **Dépôt** : une copie par réplique, jamais sur sa propre gateway, sauf dans un mesh de test.
- **Réception** :
  - déduplication sur le contenu, puisque chaque réplique pousse sa propre copie ;
  - traitement, puis ACK à chaque réplique ;
  - un blob dont le traitement échoue n'est pas acquitté : il revient à la relève suivante.
- **Rythme** : tout paquet part dans le rythme de Poisson du trafic leurre, par ordre de priorité. Un dépôt prend la place d'un leurre ; le flux observable ne change pas.

## Limites assumées, et suite

- **Pas encore de confidentialité persistante.** Une clé de contact compromise permettrait de lire les messages passés que quelqu'un aurait enregistrés. Il faut donc remplacer `PairCipher` par le double cliquet Olm (vodozemac) : c'est la demande ci-dessous.
- **Réception seulement quand l'app est ouverte.** Le courrier attend jusqu'à 7 jours dans le mesh. La réception en arrière-plan relève de l'étape N3 (service de premier plan déguisé).
- **Accusé « lu »** : pas encore envoyé, pour ne rien révéler sans un réglage explicite.

## Vérification

- **Tests JVM** (`PairingTest`, 10 tests) :
  - appairage complet, avec secrets identiques et croisés ;
  - message lisible seulement dans le bon sens ;
  - blob modifié rejeté ;
  - code de contrôle différent pour un usurpateur ;
  - invitation courte et neutre ;
  - noms tronqués proprement ;
  - quatre tailles de blob seulement ;
  - encodages ;
  - refus d'une clé X25519 nulle.
- **Essai réel sur émulateur**, avec deux apps (debug et release) à travers le nœud racine `harpo.cronix.fr` :
  1. invitation à distance de A, collée sur B ;
  2. demande reçue sur A, avec un code identique des deux côtés, puis acceptée ;
  3. message de A reçu par B, réponse de B reçue par A, puis accusé « remis » chez A.

  Chaque étape a abouti alors que l'autre app était fermée : tout est passé par les boîtes aux lettres. La release a été installée par-dessus la version précédente (migration de base 2 vers 3).

## Demande à l'équipe serveur : double cliquet Olm dans le noyau

Décision du porteur du projet (2026-11-28) : le chiffrement de bout en bout repose sur **vodozemac**, l'implémentation Rust auditée d'Olm (licence Apache-2.0), exposée par `corekit`. libsignal est écarté pour trois raisons :
- sa licence AGPL-3.0 ;
- son poids dans l'APK ;
- ses premiers messages post-quantiques, plus gros qu'un paquet.

API souhaitée (noms indicatifs), en restant pur, sans E/S :

```text
OlmAccount.new() -> OlmAccount
OlmAccount.identityKey() -> [u8; 32]                      // curve25519
OlmAccount.generateOneTimeKeys(n) / oneTimeKeys() -> [[u8; 32]] / markKeysAsPublished()
OlmAccount.createOutboundSession(theirIdentityKey, theirOneTimeKey) -> OlmSession
OlmAccount.createInboundSession(theirIdentityKey, preKeyMessage) -> (OlmSession, plaintext)
OlmAccount.pickle(key: [u8; 32]) -> bytes / OlmAccount.fromPickle(bytes, key)
OlmSession.encrypt(plaintext) -> { type: u8, body: bytes }
OlmSession.decrypt(type, body) -> plaintext
OlmSession.pickle(key) / OlmSession.fromPickle(bytes, key)
```

Contraintes côté app :
- Les états (*pickles*) sont chiffrés par l'app avec une clé de 32 octets, puis stockés dans SQLCipher.
- Le premier message (pré-clé) et les suivants doivent tenir dans un blob de 1 400 o, avec un texte d'environ 1 000 o.
- Les clés publiques s'ajouteront à la demande et à l'acceptation de l'appairage, qui gardent leurs clés X25519 éphémères pour dériver les boîtes.
