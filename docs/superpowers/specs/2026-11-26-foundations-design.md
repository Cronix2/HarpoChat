# Fondations Android : conception (approche B)

**Date :** 2026-11-26. **Branche :** `feat/foundations`, partie de `phase-a-refactor`.

## Intention

Corriger les failles de discrétion et de sécurité relevées par l'audit, et donner à l'app une structure saine **avant** la refonte du design. Le design se construira sur ce socle sans rien refaire.

Public final : une démonstration devant un jury. Il faut qu'on voie que l'auteur a pensé à tout : sécurité, UX, utilisateurs.

## Approches envisagées

| | Approche | Verdict |
|---|---|---|
| A | Patchs ciblés dans les 7 activités existantes | Écartée : le design réécrira ces écrans, et une protection oubliée sur un nouvel écran reste possible. |
| **B** | **Une seule activité + navigation Compose, puis corrections dans ce cadre** | **Retenue** par le porteur de projet. |
| C | B + protocole réel (libsignal, invitations avec clés, réseau) | Écartée : l'instance serveur fournira tout ça via `harpochat-core` (Rust/uniffi). Ne pas réimplémenter CBOR, Sphinx, les boîtes aux lettres ni les jetons côté Android. |

## Objectifs

1. **Une seule activité**, avec un état de verrouillage central :
   - `NeedsSetup` : premier lancement, on choisit les PIN ;
   - `Locked` : façade calculatrice ;
   - `Unlocked` : messagerie.
2. **Verrouillage automatique** dès que l'app passe en arrière-plan (`ProcessLifecycleOwner` ON_STOP). La base est fermée et la clé effacée de la mémoire. Exception : une activité externe lancée par nous (scanner QR), signalée explicitement.
3. **Protection de l'écran** :
   - `FLAG_SECURE` actif tant que la messagerie est déverrouillée (captures et enregistrements bloqués) ;
   - `setRecentsScreenshotEnabled(false)` (API 33+) en permanence, donc aucune miniature dans les apps récentes.
4. **Coffre de clés (`Vault`)** :
   - `étiré = PBKDF2-HMAC-SHA256(PIN, sel, 210 000 itérations)` ;
   - `kek = HMAC-SHA256 Keystore(étiré)`, avec une clé HMAC non exportable, StrongBox si disponible ;
   - `clé_db` (32 octets aléatoires) chiffrée en AES-256-GCM par `kek`.
   - Conséquence : **sans le PIN *et* sans le matériel du téléphone, la base est illisible**, et chaque essai de PIN passe par le Keystore. Aucune empreinte du PIN secret n'est stockée : la vérification se fait en déchiffrant.
   - Le PIN de détresse est vérifié par une empreinte PBKDF2. Sa divulgation est sans danger, puisqu'il ne fait qu'effacer. Les deux vérifications sont toujours calculées, pour ne rien révéler par le temps de réponse.
   - Le stockage se fait dans des `SharedPreferences` ordinaires au nom neutre, qui ne contiennent aucun secret en clair. `EncryptedSharedPreferences` (déprécié) disparaît.
5. **Effacement de détresse par crypto-shredding** :
   - suppression de l'alias Keystore ;
   - suppression des fichiers de base, des préférences et des répertoires `files`, `cache` et `no_backup` ;
   - retour silencieux à la calculatrice ;
   - **confirmation discrète** : double vibration brève et écran remis à « 0 », sans texte.
   - Au lancement suivant, l'app est vierge et redemande la configuration. Il n'y a jamais de PIN par défaut.
6. **Premier lancement** : écran de configuration obligatoire.
   - PIN secret de 6 à 12 chiffres, PIN de détresse de 6 à 12 chiffres, les deux différents ;
   - confirmation de chaque PIN ;
   - explication du geste (appui long sur « = »).
7. **Base de données** :
   - instance unique ouverte au déverrouillage ;
   - `net.zetetic:sqlcipher-android` (pages de 16 Ko) ;
   - `PRAGMA secure_delete = ON` ;
   - nom de fichier neutre.
8. **Discrétion du paquet** :
   - libellé « Calculatrice » ;
   - `applicationId` neutre (`app.calc.scientific`) ;
   - icône adaptative de calculatrice (fini le robot Android) ;
   - **aucune permission caméra** : le scan QR passe par Google Code Scanner (Play Services), et la génération de QR par ZXing *core* seul ;
   - plus d'ASCII art ;
   - règles de sauvegarde et de transfert qui excluent tout.
9. **Corrections** :
   - bug « Alice » (renommage des conversations) ;
   - notation scientifique de la calculatrice ;
   - `x²` sur un écran vide ;
   - vérification du PIN hors du fil d'interface ;
   - liste des conversations triée par activité, avec dernier message et non-lus ;
   - clavier sans apprentissage (`IME_FLAG_NO_PERSONALIZED_LEARNING`) sur la saisie des messages.
10. **Ménage** :
    - suppression de `server/` et `server.yml` (le serveur a son propre dépôt) ;
    - suppression de `RelayWsTransport` + OkHttp, du moteur Signal de démonstration + `signal-protocol-java`, de `DbCrypto`/`SecureStore` ;
    - suppression des anciennes activités, de `data/Model.kt` et de la boîte de dialogue XML « PIN par défaut » ;
    - suppression de `.gradle/`, `.idea/` et `local.properties` du dépôt.
11. **Qualité** :
    - le moteur de calcul est extrait en Kotlin pur et testé ;
    - tests du `Vault` sur la JVM via une abstraction du Keystore ;
    - CI GitHub : build, tests et lint sur chaque PR ; APK de release publié automatiquement sur tag `v*`.

## Non-objectifs

- Réseau réel, libsignal, nouveau format d'invitation : ils viendront avec `harpochat-core`. Le format d'invitation JSON actuel est conservé comme **transitoire**.
- Refonte visuelle : projet suivant. Ici, on se contente d'unifier le thème existant.
- Médias, messages éphémères, détection de capture : fonctionnalités ultérieures.

## Point d'attention pour plus tard

La réception en temps réel (service de premier plan) devra stocker des messages **pendant que l'app est verrouillée**, alors que la base est fermée. Piste prévue : une boîte de réception chiffrée vers une clé publique de l'appareil, déchiffrée et versée dans la base au déverrouillage.
