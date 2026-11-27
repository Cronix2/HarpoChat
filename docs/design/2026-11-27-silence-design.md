# Refonte visuelle « Silence » : conception

**Date :** 2026-11-27. **Maquettes validées :** canevas « HarpoChat — directions de design », page « Silence — tous les écrans ».

## Intention

Que le jury voie une application moderne, calme et sûre d'elle, dont chaque détail montre que la sécurité, l'ergonomie et les usages ont été pensés. La façade reste une calculatrice banale ; l'identité visuelle ne se révèle qu'une fois l'espace privé ouvert.

## Approches considérées

| Direction | Verdict |
|---|---|
| **Silence** : encre, un seul accent vert d'eau, beaucoup d'air | **Retenue** |
| Coffre : noir chaud, laiton, titres à empattements | Écartée : plus luxueuse que crédible pour un outil sensible |
| Poste de commande : bleu électrique, chiffres en monospace partout | Écartée comme style de base. Son panneau réseau est **repris**, mais affiché seulement à la demande |

## Système visuel

- **Couleurs** :
  - fond encre `#0B0D10`, surfaces `#15181D` / `#1E242B` ;
  - un seul accent, vert d'eau `#8FD6B8` ;
  - ambre `#F0C27A`, réservé aux alertes ;
  - rouge doux `#FF9B8A`, réservé aux actions destructrices.
- **Typographie** : Manrope pour les titres, Inter pour le texte, une police monospace pour les empreintes de clé.
  - Les polices sont **embarquées dans l'APK**. Pas de Google Fonts téléchargeables : une requête réseau vers Google trahirait l'app.
- **Icônes** : uniquement des vecteurs au trait, dessinés à partir des tracés SVG des maquettes. **Jamais d'emoji.**
- Cibles tactiles de 44 dp minimum. L'état de sécurité se lit d'un coup d'œil ; le détail s'obtient d'un geste.

## Ce qui est implémenté dans ce lot

1. **Thème, polices et jeu d'icônes** partagés par tous les écrans.
2. **Façade calculatrice** restylée : touches rondes, opérateurs orange, comme l'icône. L'effacement d'un chiffre se fait en balayant l'affichage. La disposition scientifique en paysage est conservée.
3. **Déverrouillage** : un cercle s'ouvre depuis la touche « = » (environ 450 ms), avec une vibration brève.
   - Le verrouillage reste **instantané**, sans animation inverse : la base est fermée immédiatement et rien ne doit rester affiché.
4. **Premier lancement en trois étapes** : présentation, code secret (avec confirmation), code d'effacement (avec confirmation). La saisie se fait dans des cases.
5. **Conversations** :
   - badge « vérifié » et minuteur éphémère par fil ;
   - indicateur « Chiffré de bout en bout » ouvrant le **panneau réseau** (trajet, relais, délai, trafic leurre, empreinte de clé). Les valeurs y sont **explicitement signalées comme simulées** tant que le réseau n'est pas branché.
6. **Conversation** :
   - en-tête avec statut de vérification et sélecteur de durée éphémère (désactivé, 1 h, 24 h, 7 jours) ;
   - bulles Silence ;
   - messages système.
   - Le bouton « + » liste les pièces jointes à venir, marquées comme telles.
7. **Messages éphémères effectifs** : chaque message reçoit une date d'expiration selon la durée du fil, et les messages expirés sont supprimés (avec `secure_delete`) à l'ouverture de la session, puis chaque minute.
8. **Ajouter un contact** : onglets « Mon code » et « Scanner ».
   - Le code de contrôle est à lire à voix haute.
   - Un contact ajouté après comparaison du code est marqué vérifié.
   - L'onglet « Fichier » est annoncé, mais pas encore actif.
   - L'ancien écran « code d'appairage », de démonstration, est supprimé.
9. **Réglages** :
   - bilan de protection calculé à partir de l'état réel (niveau matériel de la clé) ;
   - changement des codes sur un écran dédié ;
   - durée éphémère par défaut, nom affiché ;
   - « Tout effacer maintenant », avec confirmation dans la page.

## Hors de ce lot (maquettés, à venir)

- **Notifications déguisées** : elles dépendent du service temps réel et du réseau.
- **Masquage des visages et voix transformée** : lot « médias », avec détection sur l'appareil.
- **Alerte de capture d'écran envoyée au contact** : elle dépend du réseau.
- **Demandes d'ajout reçues** : elles dépendent du réseau.
