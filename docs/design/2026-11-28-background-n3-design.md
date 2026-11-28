# Étape N3 : réception quand l'app est verrouillée

**Date :** 2026-11-28. **Décision du porteur du projet :** modèle « boîte de dépôt scellée ».

## Problème

Verrouiller l'app ferme la base chiffrée, efface sa clé de la mémoire et coupe le réseau. Pour recevoir des messages pendant ce temps, il faut garder un lien au mesh et pouvoir relever les boîtes aux lettres sans le code secret. Et cela sans affaiblir le verrouillage.

## Modèles considérés

| Modèle | Verdict |
|---|---|
| **Boîte de dépôt scellée** : seuls les secrets de relève sont lisibles sans le code ; le courrier est rangé tel quel, chiffré | **Retenu** |
| Session gardée ouverte : la clé de la base reste en mémoire tant que le service tourne | Écarté : le verrouillage ne protège plus rien quand le téléphone est dans la poche |
| Pas d'arrière-plan : réception seulement app ouverte | Écarté : pas de temps réel |

## Fonctionnement

1. **Réglage explicite** : Réglages, « Réception en arrière-plan ». Le réglage est désactivé par défaut. Sur Android 13 et plus, l'activer demande la permission de notification, présentée au nom de la façade (« Calculator »).
2. **À chaque changement**, la session déverrouillée écrit la liste des boîtes à relever dans la **boîte de dépôt** (`security/DropBox.kt`). C'est un fichier chiffré avec une clé Android Keystore propre, utilisable sans le code.
   - Il ne contient que les **secrets de lecture des boîtes**. Ils permettent de relever et d'acquitter le courrier, pas de le lire : les clés de chiffrement des conversations restent dans la base SQLCipher.
3. **App verrouillée**, un **service de premier plan** (`bg/ResultService.kt`, type `remoteMessaging`) :
   - ouvre un lien au mesh, à débit réduit : CoverRate de 0,1 trame/s, trafic montant de 0,1 paquet/s ;
   - s'abonne aux boîtes et les relève ;
   - range chaque blob reçu **tel quel**, toujours chiffré de bout en bout, dans la boîte de dépôt ;
   - affiche une notification neutre : « Calculator — Nouveau résultat disponible », sans aucun contenu, en visibilité privée.
4. **Au déverrouillage** :
   - le service coupe son lien et la session prend le relais ;
   - le courrier rangé est traité comme s'il venait d'arriver : déchiffrement, insertion, accusés de réception ;
   - puis il est supprimé, et la notification aussi.
5. **Le code d'effacement**, et « Tout effacer », arrêtent le service, désactivent le réglage et détruisent la boîte de dépôt ainsi que sa clé Keystore.

Notification permanente du service : canal « Calculs en cours », importance minimale et silencieuse. La notification de résultat utilise le canal « Résultats ».

## Ce que gagne un attaquant qui a le téléphone, app verrouillée

- **Sans extraire la clé Keystore** : rien. Les fichiers sont chiffrés.
- **En extrayant la clé**, ce qui demande un téléphone compromis :
  - il obtient les secrets de relève, donc peut relever ou supprimer des messages en attente ;
  - il ne peut pas les lire, ni écrire au nom du contact ;
  - il ne voit ni les conversations, ni les contacts, ni les noms.

## Limites connues

- **Redémarrage du téléphone** : le service ne revient qu'à la prochaine ouverture de l'app, pour ne pas demander la permission de démarrage automatique.
- **Mode Doze** : sur certains téléphones, la réception peut être retardée en veille profonde. Une exclusion de l'optimisation de batterie pourra être proposée.
- **Temps réel** : les PUSH du mesh multi-sauts arrivent avec un identifiant de SURB inattendu. Le problème est signalé à l'équipe serveur et en cours d'analyse. En attendant, le courrier arrive par la relève : à la connexion du lien, puis toutes les 15 minutes.

## Vérification (émulateur, mesh de 3 nœuds harpo, harpo2 et harpo3)

1. **Activation** : demande de permission « Allow Calculator to send you notifications? », puis service actif en premier plan, type `remoteMessaging`.
2. **App A verrouillée, lien d'arrière-plan** : 3 nœuds vus, SUBSCRIBE et FETCH sur les 2 répliques hors gateway. Le lien tient plusieurs minutes à 0,1 trame/s.
3. **Message de B à A pendant le verrouillage** :
   - relevé par le lien d'arrière-plan et rangé chiffré dans le dépôt (2 fichiers) ;
   - notification « Nouveau résultat disponible » au bout de 30 s.
4. **Déverrouillage de A** : message affiché (« Push test 4 »), dépôt vidé, notification retirée.
