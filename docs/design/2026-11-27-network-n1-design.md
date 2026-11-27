# Réseau, étape N1 : connexion réelle au mesh

**Date :** 2026-11-27. **Références :** `docs/SERVER_SPEC.md`, `docs/server/core-integration.md` §6 à §8, `docs/server/gradle-integration.md`.

## Objectif

Remplacer l'état réseau simulé par un vrai lien au mesh, ouvert pendant la session déverrouillée :
- nœud authentifié ;
- annuaire vérifié ;
- trafic leurre réel ;
- mesures affichées dans le panneau « Chiffrement et réseau ».

L'envoi et la réception de messages par le mesh constituent l'étape N2.

## Ce qui est en place

1. **Noyau `harpochat-core` embarqué**, avec les bindings uniffi et JNA :
   - bibliothèques natives `arm64-v8a`, `armeabi-v7a` et `x86_64` ; l'APK est limité à ces architectures ;
   - mise à jour par `scripts/update-core.sh [tag]`, qui vérifie l'empreinte SHA-256 de l'archive ; la version embarquée est notée dans `app/core-version.txt`.
2. **Racines de confiance** (`app/build.gradle.kts`) : identité publique et adresses des nœuds racines, encodées en CBOR à la compilation.
   - Le bloc est obscurci par un XOR avec un flux SHA-256 : aucune adresse ne se lit en clair dans l'APK. C'est de la discrétion, pas un secret.
   - Une build de debug peut viser un nœud local avec `mesh.roots.debug` dans `local.properties`.
3. **Lien** (`net/NodeLink.kt`) : TCP, puis TLS, puis WebSocket binaire, puis Noise XX.
   - TLS ne sert qu'à ressembler à du HTTPS : aucun certificat n'est vérifié.
   - Le nœud est authentifié par Noise : il doit prouver l'identité de la racine attendue, sinon le lien est refusé.
   - Le téléphone utilise une clé statique neuve à chaque connexion.
   - Tout message de taille inattendue, tout message texte et tout déchiffrement raté ferment le lien.
4. **Session réseau** (`net/MeshClient.kt`) :
   - à la connexion :
     - boîte de réception neuve (`RegisterInbox`) ;
     - débit descendant constant de 1 trame/s (`CoverRate`) ;
     - annuaire vérifié jusqu'aux racines, rafraîchi toutes les heures ;
   - trafic montant au rythme d'un processus de Poisson de 0,5 paquet/s : un vrai paquet s'il y en a, sinon un leurre (moitié drop, moitié loop) ;
   - **loops** : délai aller-retour médian et pertes, avec exclusion des nœuds qui perdent plus de 75 % des loops sur au moins 10 essais ;
   - **vivacité** : 20 s sans trame, c'est un lien mort. Reconnexion avec un délai croissant de 2 s à 60 s, avec une part aléatoire ;
   - le lien est coupé au verrouillage, en même temps que la base.
5. **Interface** : l'indicateur des conversations et le panneau affichent l'état réel :
   - relais actifs, aller-retour mesuré, nœud d'entrée, leurres revenus ;
   - la cause d'une erreur de connexion, quand il y en a une ;
   - un avertissement « réseau de test » sous 3 nœuds : sans relais intermédiaire, le nœud d'entrée voit la boîte visée.

## Choix et limites assumées

- **Connexion seulement pendant la session.** L'app se verrouille dès qu'elle quitte l'écran, et le lien suit. Recevoir en arrière-plan relève de l'étape N3 (service de premier plan et notification déguisée).
- **Une seule vue de l'annuaire.** La gateway ne peut pas ajouter de faux nœuds : tout est vérifié jusqu'aux racines. Elle pourrait en revanche en omettre. Croiser les annuaires de plusieurs nœuds (`intersectDirectories`) viendra avec plusieurs gateways.
- **Moins de 3 nœuds** : les leurres prennent le trajet direct par la gateway, en mode développement prévu par l'équipe serveur. L'écran le signale.
- **Identifiants dans l'APK.** R8 renomme les classes des bindings. En revanche, le nom de la bibliothèque native et les noms de ses fonctions contiennent « harpochat » : il faut un renommage du namespace uniffi côté serveur.

## Vérification

- **Tests JVM** (`NetLogicTest`) :
  - bloc d'amorçage révélé, sans adresse en clair ;
  - moyenne des intervalles de Poisson ;
  - loops revenus, perdus et suspects.
- **Tests sur appareil** (`CoreOnDeviceTest`) :
  - chargement du noyau et tailles figées ;
  - vecteurs `bootstrap`, `mailbox_keys` et `link_frame_small` ;
  - décodage du bloc embarqué ;
  - lien réel vers une racine avec `-e mesh 1` : poignée de main, identité vérifiée, annuaire vérifié.
- **Émulateur, contre un nœud local** (binaire Windows de la release serveur) :
  - connexion et loops revenus (10 sur 10, aller-retour de 1,0 s) ;
  - passage hors ligne à l'arrêt du nœud, reconnexion automatique à son retour ;
  - même résultat avec une build de release minifiée.
