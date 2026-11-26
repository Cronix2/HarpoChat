# HarpoChat Server : spécification et prérequis

> **Statut :** v0.2, 2026-11-26. Décisions du porteur de projet intégrées : temps réel, TTL 7 jours, parrainage par jeton, noyau Rust partagé, mesh d'environ 5 nœuds au lancement.
> **Public :** l'équipe (ou l'agent) qui développe `harpochat-server`, en parallèle du client Android.
> **Rôle de ce document :** c'est le **contrat** entre le client et le serveur. Toute divergence se règle ici d'abord, dans le code ensuite.

Les mots **DOIT / NE DOIT PAS / DEVRAIT / PEUT** ont le sens de la RFC 2119.

---

## 1. Principes non négociables

| # | Principe | Conséquence concrète |
|---|----------|----------------------|
| P1 | **Zéro connaissance** : un opérateur de nœud ne peut lire ni le contenu ni savoir qui parle à qui. | Tout contenu est chiffré de bout en bout par le client *avant* d'entrer dans le réseau. Le serveur ne manipule que des paquets opaques de taille fixe. |
| P2 | **Zéro historique** : rien ne survit sur un serveur. | Aucune écriture disque de données utilisateur. Tout l'état est en RAM, a une durée de vie bornée et disparaît au redémarrage. |
| P3 | **Zéro compte** : ni identifiant, ni numéro, ni e-mail, ni mot de passe. | Le serveur ne connaît que des identifiants de boîtes aux lettres aléatoires et tournants. |
| P4 | **Opérateurs non fiables** : n'importe qui peut faire tourner un nœud, y compris un adversaire. | La sécurité ne doit jamais reposer sur l'honnêteté d'un nœud isolé. Un nœud malveillant ne doit apprendre que ce qu'il voit déjà au niveau réseau. |
| P5 | **Brouillage des pistes** : un message traverse plusieurs nœuds, mêlé à du trafic leurre. | Routage multi-sauts en oignon, délais aléatoires, trafic de couverture (§6). |
| P6 | **Le serveur ne connaît pas les groupes.** | Les groupes sont gérés côté client par diffusion (*fan-out*). Le serveur ne voit que des paquets individuels. |
| P7 | **Code ouvert, builds vérifiables.** | Binaire unique Windows/Linux, publié par GitHub Actions avec sommes de contrôle signées. Builds reproductibles visés. |

**Pas de crypto maison.** On utilise uniquement des constructions publiées et analysées : Noise, Sphinx, X25519, Ed25519, ChaCha20-Poly1305, BLAKE2/HKDF. On les assemble, on ne les invente pas.

---

## 2. Modèle de menace

| Adversaire | Capacité | Objectif de protection |
|------------|----------|------------------------|
| Opérateur d'**un** nœud | Voit les IP qui se connectent à lui et les paquets qui transitent. | Ne connaît ni le contenu, ni l'expéditeur réel, ni le destinataire final (sauf s'il est le premier ou le dernier saut, et alors seulement une moitié). |
| **Plusieurs** nœuds en collusion (attaque Sybil) | Un adversaire lance beaucoup de nœuds. | Rendre la prise de contrôle d'un chemin complet coûteuse : diversité des chemins, admission des nœuds (§4.3). |
| Observateur réseau local / FAI | Voit qu'un téléphone parle à un nœud HarpoChat. | Le trafic est chiffré et ressemble à du TLS/WebSocket sur le port 443. Il ne voit ni le contenu ni les correspondants. |
| Saisie physique d'un serveur | Récupère la machine, son disque, éventuellement sa RAM. | Rien sur le disque. En RAM : uniquement des blobs chiffrés de bout en bout sous des identifiants opaques, avec durée de vie courte. |
| Adversaire passif **global** (voit tout Internet) | Corrèle les temps d'entrée et de sortie. | **Protection partielle seulement** : délais aléatoires + trafic de couverture compliquent la corrélation, sans la rendre impossible. Avec un petit mesh et peu d'utilisateurs, l'ensemble d'anonymat est petit. C'est une limite à documenter honnêtement. |

**Hors périmètre du serveur :** téléphone compromis (malware), capture d'écran par un autre appareil, destinataire malveillant qui divulgue ce qu'il reçoit.

---

## 3. Architecture générale

Le modèle de référence est **Loopix** (Piotrowska et al., USENIX Security 2017), qui sert aussi de base au réseau Nym. Un **seul binaire** `harpochat-server` peut jouer tous les rôles, selon sa configuration :

- **Gateway (entrée)** : point de connexion des téléphones. Connaît l'IP du téléphone, **pas** ce qu'il envoie ni à qui.
- **Mix** : nœud intermédiaire. Déchiffre une couche de l'oignon, attend un délai aléatoire, transmet au suivant. Ne connaît que le saut précédent et le suivant.
- **Mailbox (boîte aux lettres)** : stocke **temporairement, en RAM** les blobs adressés à une boîte aux lettres, jusqu'à leur récupération ou leur expiration. Connaît l'identifiant opaque de la boîte, **pas** l'IP ni l'identité de son propriétaire.

```text
 Téléphone A                                                        Téléphone B
     │  WSS/Noise                                                         ▲
     ▼                                                                    │ (FETCH via SURB)
 ┌─────────┐   Sphinx   ┌──────┐  Sphinx  ┌──────┐  Sphinx  ┌─────────┐   │
 │ Gateway │──────────▶ │ Mix1 │────────▶ │ Mix2 │────────▶ │ Mailbox │───┘
 └─────────┘  (délai)   └──────┘ (délai)  └──────┘ (délai)  └─────────┘
      ▲                                                          │
      └──── trafic leurre (drop / loop) mêlé au trafic réel ─────┘
```

Un téléphone n'est **jamais** joignable directement : il initie toujours la connexion (NAT, mobilité, discrétion).

---

## 4. Identité des nœuds et annuaire

### 4.1 Identité d'un nœud

- Chaque nœud génère au premier démarrage une paire **Ed25519** (identité long terme) et une paire **X25519** (clé Sphinx, tournante, par exemple hebdomadaire).
- **Seul fichier persistant autorisé** : la clé d'identité du nœud et sa configuration. C'est l'unique exception à P2, car elles ne contiennent aucune donnée utilisateur.
- Un nœud publie un **descripteur signé** :

```cbor
NodeDescriptor {
  v: 1,
  id_pub: Ed25519PublicKey,
  sphinx_pub: X25519PublicKey,
  sphinx_key_epoch: u32,
  roles: [ "gateway" | "mix" | "mailbox" ],
  endpoints: [ "wss://host:443", "tcp://host:7600" ],
  layer: u8,                 // couche de mix (0..2), assignée pour la topologie stratifiée
  valid_until: u64,          // epoch secondes
  sig: Ed25519Signature
}
```

### 4.2 Découverte

- **Amorçage (décidé)** : l'APK embarque, **obscurcis**, les endpoints et les **clés publiques Ed25519 des 2 nœuds racines** (§4.4). Au premier lancement, ou si plus aucun nœud connu ne répond, le client les contacte pour obtenir l'annuaire signé. Ensuite, il vit sur les nœuds appris (gossip + invitations de contact, de façon automatique et invisible pour l'utilisateur). Il n'y a pas d'annuaire central au-delà de cet amorçage.
- **Gossip** : les nœuds s'échangent les descripteurs qu'ils connaissent. Les descripteurs expirés ou mal signés sont rejetés.
- Un client obtient la vue du réseau auprès de **plusieurs** nœuds et en fait l'intersection, pour qu'un seul nœud ne puisse pas lui présenter une fausse topologie.
- **Endpoint d'annuaire** : tout nœud **DOIT** répondre à `DIRECTORY_REQUEST` (type `0x20`, §8) par `DIRECTORY { descriptors: [NodeDescriptor + chaîne de Vouch], issued, sig }`. C'est la liste des nœuds valides qu'il connaît, signée par lui. Le client n'accepte un descripteur que si sa chaîne de parrainage remonte à une clé racine embarquée.

### 4.3 Admission : parrainage par jeton (**décidé**)

Un nœud n'est accepté dans le mesh que s'il est **parrainé** par un nœud déjà membre. Le parcours doit être aussi simple qu'un **copier-coller d'un long code**.

#### Parcours opérateur

```text
# Sur un nœud existant (le parrain)
$ harpochat-server invite-node
hcn1-7K3QX9…(≈200 caractères)…M2ZP
  ↳ valable 48 h, utilisable une seule fois

# Sur la nouvelle machine
$ harpochat-server join hcn1-7K3QX9…M2ZP
✔ clés générées · ✔ parrain joint · ✔ certificat de parrainage reçu · ✔ annoncé au mesh
```

#### Contenu du jeton

CBOR, encodé en Base32 Crockford, préfixe `hcn1-` pour la version :

```cbor
NodeInvite {
  v: 1,
  sponsor_id: Ed25519PublicKey,      // identité du parrain
  sponsor_endpoints: [ "wss://…" ],  // où le joindre
  secret: [32],                      // secret à usage unique
  expires: u64,
  sig: Ed25519Signature              // signé par le parrain
}
```

#### Protocole `join`

1. Le nouveau nœud génère ses clés, ouvre une session Noise `XX` vers le parrain, puis envoie `JoinRequest { descriptor, proof = HMAC(secret, descriptor) }`.
2. Le parrain vérifie la preuve, l'expiration et l'usage unique, puis **brûle le secret**.
3. Le parrain renvoie un **certificat de parrainage** : `Vouch { node_id, sponsor_id, issued, sig_sponsor }`.
4. Le nouveau nœud joint ce certificat à son descripteur et l'annonce par gossip. Les autres nœuds rejettent tout descripteur sans chaîne de parrainage valide jusqu'à un **nœud racine**.

#### Règles

- **Nœuds racines** : le ou les premiers nœuds du mesh, dont les clés publiques sont listées dans la configuration d'amorçage. Leur chaîne s'arrête à eux-mêmes.
- **Limite de parrainage** : un nœud parraine au plus **3 nœuds par période de 30 jours** (paramétrable). Ça borne l'inondation même si un parrain est compromis.
- **Révocation** : un parrain peut publier `Revoke { node_id, sig }`. Le nœud révoqué et tous ceux qu'il a parrainés **après** la date de révocation sont rejetés.
- **Le jeton est un secret** : il ne doit circuler que par un canal de confiance, et ne pas être journalisé.

#### Sélection de chemins

- Le client **DOIT** éviter de mettre dans un même chemin deux nœuds de la même branche de parrainage directe (même parrain), ou partageant un préfixe IP /16, **quand le mesh est assez grand pour le permettre**.
- En petit mesh (§6.1), ces contraintes sont relâchées dans l'ordre suivant : d'abord la branche, puis le /16, jamais « deux fois le même nœud ».

---

### 4.4 Amorçage embarqué dans l'app (**décidé**)

L'utilisateur ne saisit rien : l'app sait d'emblée où trouver le mesh.

#### Ce que l'APK embarque

Un bloc chiffré et découpé, reconstitué en mémoire seulement au moment de l'usage.

```cbor
Bootstrap {
  v: 1,
  roots: [
    { id_pub: Ed25519PublicKey, endpoints: ["wss://…", "wss://<IP>:443"] },
    { id_pub: Ed25519PublicKey, endpoints: [ … ] }
  ]
}
```

#### Règles côté serveur, pour que ce soit robuste

1. **La clé fait foi, pas le nom de domaine.** Le client authentifie un nœud racine par sa clé Noise (`id_pub`), jamais par le certificat TLS ni par le DNS. Si le domaine expire et qu'un tiers le rachète, il ne peut ni se faire passer pour le nœud, ni lire quoi que ce soit.
2. **Plusieurs endpoints par racine** : un nom de domaine **et** une IP directe, pour survivre à une panne DNS ou à un domaine non renouvelé. Un nœud racine **DOIT** accepter des connexions WSS sur IP, avec un certificat auto-signé, puisque l'authentification réelle passe par Noise.
3. **L'amorçage sert une seule fois.** Une racine contactée en amorçage renvoie l'annuaire signé (`DIRECTORY`, §4.2), et le client bascule aussitôt sur le mesh. Le client ne recontacte les racines que si **aucun** nœud connu ne répond. Conséquence : les racines voient passer peu de connexions et ne deviennent pas un point de surveillance permanent.
4. **Changement d'adresse** : une nouvelle version de l'app (GitHub Release) suffit. Les installations existantes ne sont pas affectées, puisqu'elles connaissent déjà d'autres nœuds par gossip.

**Limite assumée** : l'obscurcissement empêche une recherche simple dans l'APK, mais pas un analyste qui lance l'app et observe le réseau. L'adresse des racines doit donc être considérée comme **semi-publique**. La sécurité repose sur les clés, pas sur le secret de l'adresse.

## 5. Transport

| Lien | Protocole | Remarques |
|------|-----------|-----------|
| Téléphone → Gateway | **WebSocket sur TLS 1.3, port 443**, avec à l'intérieur une session **Noise `XX`** (ou `IK` si la clé de la gateway est connue). | Ressemble à du trafic web ordinaire et traverse les pare-feu mobiles. Noise authentifie la gateway par sa clé, indépendamment du certificat TLS, ce qui permet des certificats auto-signés. |
| Nœud ↔ Nœud | **TCP + Noise `XX`** (port configurable, par défaut 7600), ou WSS. | Authentification mutuelle par les clés d'identité des descripteurs. |

- Les connexions sont **longues** et multiplexées. Un nœud **DOIT** émettre du trafic de couverture sur chaque lien, pour qu'un lien inactif ne se distingue pas d'un lien actif (§6.3).
- Pas de HTTP/REST en clair, pas de métadonnées dans les en-têtes (User-Agent neutre, pas de cookies).

---

## 6. Format des paquets et mélange

### 6.1 Sphinx

- Tous les paquets suivent le format **Sphinx** (Danezis & Goldberg, 2009) : en-tête en oignon, charge utile chiffrée par couche, **taille strictement fixe** et indistinguabilité d'un saut à l'autre.
- Implémentation recommandée : le crate Rust `sphinx-packet` (Nym), audité et utilisé en production.
- **La longueur du chemin s'adapte à la taille du mesh** (§13) :
  - **petit mesh, environ 5 nœuds au lancement** : chaque nœud cumule les trois rôles (gateway, mix, mailbox). Chemin = gateway → 1 mix → mailbox, soit **3 nœuds distincts**. L'anonymat repose alors surtout sur le trafic de couverture et les délais. **À documenter honnêtement auprès des utilisateurs.**
  - **à partir d'environ 12 nœuds** : topologie stratifiée sur 3 couches, avec un chemin gateway → mix L0 → mix L1 → mix L2 → mailbox.
  - La règle est calculée côté client à partir de la vue du réseau, et bornée par un minimum absolu de 3 nœuds distincts.
- Prise en charge des **SURB** (*Single-Use Reply Blocks*) **obligatoire** : c'est ce qui permet au propriétaire d'une boîte de récupérer ses messages sans révéler son IP au nœud mailbox.

### 6.2 Délais

- Chaque mix retarde chaque paquet d'un délai tiré d'une **loi exponentielle** (moyenne paramétrable, par exemple 50 à 200 ms). Le délai est choisi par l'expéditeur et encodé dans la couche Sphinx du saut, comme dans Loopix.
- Un mix **NE DOIT PAS** traiter les paquets dans l'ordre d'arrivée sans délai (pas de FIFO).

### 6.3 Trafic de couverture (les « leurres »)

C'est la traduction technique de l'idée « un message part à deux endroits, l'un est un leurre ». Trois types, indistinguables des vrais paquets pour tout nœud intermédiaire :

| Type | Émis par | Trajet | But |
|------|----------|--------|-----|
| **Drop** | Client et nœuds | Chemin complet vers une mailbox aléatoire, puis jeté. | Masquer quand et combien un utilisateur envoie vraiment. |
| **Loop** | Client et nœuds | Chemin qui revient à l'émetteur. | Masquer le trafic et détecter les nœuds qui suppriment des paquets (mesure de fiabilité). |
| **Duplication** | Client (optionnel) | Le vrai message est envoyé à **deux mailboxes** (redondance réelle), en plus de drops vers d'autres. | Résister à la panne ou à la mauvaise foi d'une mailbox. |

- Le client émet en continu selon un **processus de Poisson** (débit λ paramétrable). Quand il n'a rien à envoyer, il envoie des drops ou des loops. Ce compromis entre batterie et anonymat est réglé côté client.
- Chaque nœud émet aussi ses propres loops.

---

## 7. Boîtes aux lettres

### 7.1 Identifiants

- Une boîte est désignée par un **`mailbox_id` de 32 octets**, pseudo-aléatoire, dérivé **côté client** d'un secret partagé entre deux contacts. Exemple : `HKDF(secret_paire, "harpo-mbx" || epoch)`.
- Les identifiants **tournent** à chaque époque (par exemple toutes les 24 h). Le serveur ne peut pas relier deux époques entre elles.
- Le serveur ne sait **pas** à qui appartient une boîte, ni combien de boîtes appartiennent à la même personne.

### 7.2 Droits d'écriture et de lecture

- **Écriture (PUT)** : le paquet doit porter `write_tag = HMAC(write_key, mailbox_id || nonce)`. La `write_key` n'est connue que des contacts autorisés (échangée à l'appairage). Le serveur vérifie le tag **sans** connaître la clé : la boîte est créée à son premier PUT avec `H(write_key)` comme vérificateur, ou le tag est vérifié contre une clé publique Ed25519 propre à la boîte (**variante recommandée** : chaque écriture est signée par une clé dérivée).
- **Lecture (FETCH) et suppression (ACK)** : authentifiées par une signature avec la clé de lecture de la boîte (`read_key`, Ed25519), connue du seul destinataire.
- Conséquence : un inconnu ne peut ni déposer de messages (anti-spam, consentement) ni lire une boîte.

### 7.3 Stockage

- **RAM uniquement** (`HashMap<mailbox_id, Queue<Blob>>`). Aucun fichier, aucun swap volontaire (verrouiller la mémoire avec `mlock` quand c'est possible), aucune base de données.
- Chaque blob a une durée de vie **TTL de 7 jours** (**décidé**). Il est supprimé à expiration ou dès le premier ACK. Au-delà de 7 jours sans relève, on considère que le destinataire n'utilise plus l'app (ou que son téléphone est compromis) : le message est détruit.
- Comme les `mailbox_id` tournent toutes les 24 h, un client qui se reconnecte **DOIT** relever les boîtes des **7 dernières époques**.
- Quotas par boîte : nombre de blobs et octets maximum. Au-delà, rejet (pas d'éviction silencieuse des anciens).
- Au redémarrage du nœud, tout est perdu. C'est **voulu**. La duplication côté client (§6.3) compense.

### 7.4 Récupération par le destinataire

1. Le téléphone B envoie, à travers le mix, un `FETCH(mailbox_id, signature, [SURB…])`.
2. La mailbox répond **via les SURB**. Elle ne connaît jamais l'IP de B.
3. B envoie `ACK(ids)`, qui déclenche la suppression immédiate.

### 7.5 Temps réel : abonnement par SURB (**décidé**)

La réception doit être **en temps réel**. Sans révéler l'IP du destinataire, ça se fait ainsi :
1. Le téléphone garde une connexion WSS + Noise **permanente** avec sa gateway. Côté Android, elle est portée par un service de premier plan déguisé (ce n'est pas l'affaire du serveur).
2. Le téléphone dépose auprès de chaque mailbox qu'il surveille un **stock de SURB** : `SUBSCRIBE { mailbox_id, read_auth, surbs: [SURB; ~10], until }`.
3. Quand un PUT arrive, la mailbox **consomme un SURB** pour pousser immédiatement le blob (ou une notification `NOTIFY` si le blob dépasse un paquet) au téléphone, à travers le mix.
4. Le téléphone réapprovisionne son stock au fil de l'eau. Ces envois de réapprovisionnement se fondent dans son trafic de couverture.
5. Les SURB sont conservés **en RAM**, expirent à `until` ou à la rotation de la clé Sphinx des nœuds concernés, et sont supprimés au redémarrage.
6. **Repli** : si le stock est épuisé ou périmé, la relève classique par `FETCH` (§7.4) prend le relais au prochain cycle.

Latence visée : délais de mix cumulés + délai de la gateway, soit **moins de 2 s** en petit mesh.

---

## 8. Messages applicatifs (charge utile Sphinx finale)

Encodage **CBOR** (RFC 8949), champ de version obligatoire. La charge utile est **bourrée** (*padding*) jusqu'à la taille fixe du paquet.

```cbor
Envelope { v: 1, type: u8, body: bytes }

type 0x01 PUT    { mailbox_id: [32], write_auth: bytes, blob: bytes }           // blob = message E2EE (opaque)
type 0x02 FETCH  { mailbox_id: [32], read_auth: bytes, max: u16, surbs: [SURB] }
type 0x03 ACK    { mailbox_id: [32], read_auth: bytes, blob_ids: [[16]] }
type 0x04 DROP   { }                                                            // leurre, jeté à l'arrivée
type 0x05 LOOP   { }                                                            // leurre, revient à l'émetteur
type 0x06 SUBSCRIBE { mailbox_id: [32], read_auth: bytes, surbs: [SURB], until: u64 }
type 0x20 DIRECTORY_REQUEST { }                                                 // annuaire (§4.2), aussi utilisé à l'amorçage
type 0x21 DIRECTORY { descriptors: [bytes], issued: u64, sig: bytes }
type 0x10 FETCH_RESPONSE { blobs: [{ id: [16], blob: bytes }], more: bool }     // renvoyé via SURB
type 0x11 PUSH   { mailbox_id: [32], blobs: [{ id: [16], blob: bytes }] }       // poussé via SURB d'abonnement
type 0x12 NOTIFY { mailbox_id: [32], pending: u16 }                             // « il y a du courrier », si trop gros pour un PUSH
```

- `blob` est **toujours** un message déjà chiffré de bout en bout par le client (libsignal, mode *sealed sender* : même l'identité de l'expéditeur est dans le chiffré). Le serveur **NE DOIT PAS** essayer de l'interpréter.
- **Gros contenus (médias)** : le client découpe le média en morceaux de taille fixe, chiffrés avec une clé aléatoire, et les dépose en PUT dans une boîte éphémère dédiée. Seule la clé et la référence passent dans le message E2EE. Pour un groupe, le média est donc déposé **une seule fois**, et seule la clé est diffusée à chaque membre.
- Le serveur **DOIT** rejeter les types inconnus sans effet de bord, et répondre de façon uniforme (pas d'oracle d'erreur exploitable).

---

## 9. Interface avec le client Android (pour info)

Ce que le client fournit ou attend, afin que les deux implémentations restent alignées :

- **Invitation de contact** (QR ou fichier) : clé d'identité libsignal + pré-clés + secret d'appairage à usage unique + `write_key`/`read_key` initiales + **3 à 5 descripteurs de nœuds** pour l'amorçage + date d'expiration. Le serveur n'intervient pas dans l'appairage.
- **Consentement** : A affiche l'invitation, B la scanne et envoie une demande dans une boîte temporaire, puis **A accepte explicitement**. Sans acceptation, B ne reçoit jamais les clés d'écriture des boîtes permanentes.
- **Noyau protocole partagé (décidé)** : le format Sphinx, l'encodage CBOR, la dérivation des boîtes, le client Noise et le format des jetons vivent dans un **crate Rust commun** (nom de travail : `harpochat-core`), compilé à la fois dans le serveur et dans l'app Android (via `uniffi`, avec `cargo-ndk` pour les ABI `arm64-v8a`, `armeabi-v7a` et `x86_64`). Une seule implémentation, donc zéro divergence de format. Le serveur ayant déjà démarré en Rust, ce crate **DEVRAIT** en être extrait le plus tôt possible.
  - Le crate ne doit contenir **aucun I/O spécifique à une plateforme**, uniquement des fonctions pures et des machines à états. Le réseau reste dans le serveur d'un côté et dans l'app de l'autre.
  - Les vecteurs de test (paquets Sphinx, CBOR, jetons) sont versionnés dans le crate et exécutés par les deux CI.
- **Notifications** : pas de FCM (Google verrait le rythme des messages). **Temps réel décidé** : connexion permanente + abonnement par SURB (§7.5). Côté Android, un service de premier plan affiche une notification déguisée en calculatrice ; c'est hors du périmètre serveur.
- **Amorçage embarqué (décidé, §4.4)** : l'APK contient, obscurcis, les endpoints et les clés publiques des 2 nœuds racines. Aucun jeton à saisir pour l'utilisateur. Les invitations de contact transmettent en plus, automatiquement, quelques nœuds supplémentaires.

---

## 10. Ce que le serveur NE DOIT JAMAIS faire

- ❌ Journaliser des IP, des `mailbox_id`, des tailles ou des horodatages par paquet. Les logs se limitent au démarrage, à l'arrêt, aux erreurs de configuration et à des **compteurs agrégés anonymes** (paquets/s au total).
- ❌ Écrire sur disque une donnée liée à un paquet ou à une boîte (ni base, ni fichier temporaire, ni cache).
- ❌ Exposer des métriques par utilisateur, par boîte ou par IP.
- ❌ Proposer un mode « debug » qui désactive le chiffrement, les délais ou le bourrage dans un build de release.
- ❌ Contacter un service tiers (télémétrie, mise à jour automatique, analytics, DNS-over-HTTPS d'un fournisseur, etc.).
- ❌ Conserver un blob au-delà de son TTL ou après son ACK.

---

## 11. Anti-abus

- Droits d'écriture par boîte (§7.2) : pas de spam possible sans avoir été accepté comme contact.
- Limitation de débit **par lien Noise** (pas par IP stockée) et quotas par boîte.
- **Preuve de travail** légère et optionnelle sur les PUT vers une boîte d'invitation (seules boîtes « ouvertes »), pour freiner l'inondation.
- Un nœud qui détecte des pertes répétées sur un voisin (via les loops) le note localement et l'évite, sans rien publier qui puisse servir à dénoncer des utilisateurs.

---

## 12. Exploitation, build et distribution

### 12.1 Cibles

- **Un seul exécutable autonome**, sans dépendance à installer :
  - `harpochat-server-windows-x86_64.exe`
  - `harpochat-server-linux-x86_64`
  - `harpochat-server-linux-aarch64` (Raspberry Pi, petits VPS ARM : utile pour des bénévoles qui hébergent chez eux)
- Langage recommandé : **Rust** (sûreté mémoire, crates Sphinx et Noise existants, noyau partageable avec Android, binaires statiques avec la cible `musl` sous Linux). Une alternative en Go est acceptable si le noyau partagé est abandonné.
- Crates indicatifs : `tokio`, `tokio-tungstenite`, `rustls`, `snow` (Noise), `sphinx-packet`, `x25519-dalek`, `ed25519-dalek`, `chacha20poly1305`, `blake2`/`hkdf`, `ciborium` (CBOR), `zeroize`.

### 12.2 Configuration

- Un fichier `harpochat-server.toml` : rôles, ports, endpoints publics, nœuds d'amorçage, quotas, TTL, débits de couverture.
- Commandes :
  - `init --root` : crée un nœud racine (premier nœud du mesh) ;
  - `invite-node [--ttl 48h]` : génère un jeton de parrainage (§4.3) ;
  - `join <jeton>` : rejoint le mesh avec un jeton ;
  - `run` : démarre le nœud ;
  - `status` : compteurs agrégés uniquement ;
  - `revoke <node_id>` : révoque un nœud parrainé.
- Exécution en service : unité systemd (Linux) et service Windows. Pas besoin des droits root/administrateur, sauf pour écouter sur le port 443. Sous Linux, utiliser de préférence `CAP_NET_BIND_SERVICE`.
- Nœud derrière une box : redirection de port requise, documentée. UPnP en option.

### 12.3 Durcissement

- Désactiver les core dumps, utiliser `mlock` pour les clés, `zeroize` pour tous les secrets en mémoire.
- Aucune dépendance réseau au démarrage en dehors des pairs configurés.
- Build `--release` avec `panic = "abort"`, sans symboles de débogage dans les artefacts publiés.

### 12.4 CI/CD (GitHub Actions)

1. Sur chaque PR : `cargo fmt --check`, `cargo clippy -D warnings`, tests unitaires, **tests d'intégration multi-nœuds** (lancer 5 nœuds en local et vérifier un aller-retour PUT/FETCH/ACK via SURB), `cargo audit` / `cargo deny`.
2. Sur tag `v*` : build en matrice (Windows x86_64, Linux x86_64 et aarch64 en musl), génération de `SHA256SUMS`, **signature** (minisign ou cosign), puis publication d'une GitHub Release.
3. Objectif : **builds reproductibles** (chemins normalisés, `SOURCE_DATE_EPOCH`), pour que n'importe qui puisse vérifier qu'un binaire correspond bien au code.

---

## 13. Paramètres réseau (valeurs initiales, à ajuster par mesure)

| Paramètre | Valeur initiale | Commentaire |
|-----------|-----------------|-------------|
| Taille de paquet Sphinx | 2 048 o (messages) / 32 768 o (morceaux de médias) | Deux classes de taille fixes. Tous les paquets d'une même classe sont identiques en taille. |
| Nombre de couches de mix | 1 (moins de 12 nœuds) → 3 (12 nœuds ou plus) | Chemin d'au moins 3 nœuds distincts (§6.1). |
| Taille du mesh au lancement | ~5 nœuds, tous rôles cumulés | Décidé. |
| Parrainages par nœud | 3 / 30 jours | Jeton à usage unique, valable 48 h. |
| Stock de SURB par abonnement | ~10 | Réapprovisionné en continu. |
| Délai moyen par mix | 100 ms | Loi exponentielle. |
| Débit de couverture client (λ) | ~1 paquet / 2 s app ouverte ; réduit en arrière-plan | Compromis entre batterie et anonymat, réglé côté client. |
| TTL des blobs | **7 jours** | Décidé. |
| Rotation des `mailbox_id` | 24 h | Le client interroge aussi l'époque précédente pendant une période de recouvrement. |
| Rotation de la clé Sphinx d'un nœud | 7 jours | Avec une période de recouvrement de l'ancienne clé. |
| Quota par boîte | 1 000 blobs / 50 Mo | Configurable par l'opérateur. |

---

## 14. Décisions

### Prises (2026-11-26)

| Sujet | Décision |
|-------|----------|
| Admission des nœuds | **Parrainage par jeton** à copier-coller (§4.3) |
| TTL des messages en attente | **7 jours** |
| Réception | **Temps réel** : connexion permanente + abonnement par SURB (§7.5) |
| Noyau protocole | **Crate Rust partagé** entre le serveur et l'app (§9) |
| Taille du mesh au lancement | **~5 nœuds** : chemins courts, rôles cumulés (§6.1) |
| Groupes | **Côté client uniquement** (diffusion), invisibles pour le serveur (P6) |

| Emplacement de `harpochat-core` | Crate du **dépôt `harpochat-server`** (workspace Cargo : `harpochat-core` + `harpochat-server`), consommé par l'app Android **par tag Git** |
| Nœuds racines | **2**, opérés par le porteur du projet, sur deux réseaux différents (domicile + VPS) |
| Amorçage du client | **Endpoints et clés racines embarqués, obscurcis, dans l'APK**, sans jeton utilisateur (§4.4) |
| Effacement auto côté téléphone après inactivité | **Non** pour l'instant (piste éventuelle à long terme) |

### Encore ouvertes

Aucune côté serveur à ce stade.

---

## 15. Jalons proposés

| Jalon | Contenu | Critère de fin |
|-------|---------|----------------|
| **M0** | Squelette Rust, `init`/`run`, config TOML, clés de nœud, CI avec binaires Windows et Linux. | Une Release GitHub contient les trois binaires signés. |
| **M1** | Mailbox en RAM (PUT/FETCH/ACK, TTL, quotas, authentification), transport WSS + Noise, **un seul saut**. | Deux clients de test échangent un blob ; redémarrage = tout est effacé. |
| **M2** | Paquets Sphinx, 3 couches de mix, délais exponentiels, réponses par SURB. | Test d'intégration avec 5 nœuds au vert ; la mailbox ne voit jamais l'IP du destinataire. |
| **M3** | Trafic de couverture (drop, loop) côté nœuds, duplication de PUT, détection de pertes. | Le trafic d'un lien inactif est statistiquement indistinguable d'un lien actif. |
| **M3b** | Abonnement par SURB (SUBSCRIBE / PUSH / NOTIFY) pour le temps réel. | Un message poussé arrive en moins de 2 s sur un mesh de 5 nœuds. |
| **M4** | Gossip des descripteurs, jetons `invite-node` / `join`, chaînes de parrainage, révocation, sélection de chemins adaptative. | Un nouveau nœud rejoint le mesh avec un seul copier-coller de jeton. |
| **M5** | Audit interne : revue des logs (aucune fuite), fuzzing du parseur CBOR/Sphinx, builds reproductibles. | `cargo fuzz` sans crash pendant 24 h ; build reproduit bit à bit. |

---

### Références

- Danezis & Goldberg, *Sphinx: A Compact and Provably Secure Mix Format*, IEEE S&P 2009.
- Piotrowska et al., *The Loopix Anonymity System*, USENIX Security 2017.
- Perrin, *The Noise Protocol Framework*.
- Signal, *Sealed Sender* et spécifications X3DH / PQXDH / Double Ratchet.
