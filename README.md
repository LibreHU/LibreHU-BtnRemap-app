# LibreHU BtnRemap — commandes au volant

Choisissez vous-même l'action de chaque bouton du volant (ou du boîtier CAN), en **appui court** et **appui long** :
ouvrir une appli, « source suivante » dans **votre** liste d'applis, lecture / pause, piste suivante / précédente,
volume, muet, accueil, retour, navigation, assistant vocal, téléphone… Les boutons s'ajoutent tout seuls à la liste
quand on les appuie (apprentissage), et l'app suit le thème clair / sombre et la couleur d'accent de LibreHU Launcher.

## Comment fonctionnent les commandes au volant sur l'UJC201 (d'origine)

```
bouton → boîtier CAN Hiworld → trame 0x11 (octet 6 & 0x1F = touche) → USART1 de la MCU → trame MCU 0x10
→ ivi-services (passthrough) → app CAN com.can.activity (décode, CanKey.TranslateKeyMap)
→ ICar.sendKeyToMcu(code IVIKey) → IVICore.postKey → KeyCodeUtil.onKeyEvent :
   1. diffuse com.jancar.services.action.key.event (key_event_id, key_event_type : 1 appui, 0 relâché, 2 clic)
   2. exécute l'action par défaut (volume → puce audio, MODE → ModeUtil, HOME, NEXT/PREV → média…)
```
La touche **MODE / SRC** fait défiler la liste `[ModeControl]` de `/jancar/config/ivi-settings.ini`
(`COUNT=6` : radio, musique, vidéo, musique BT, entrée AV, navigation). Les boutons à résistance (sans boîtier CAN)
arrivent en trame MCU `20` et passent par l'apprentissage de `com.jancar.steeringwheelkeys`.

## Branches

Cette branche : **`ivi`** (Jancar ivi-services).


| Branche | Source des boutons | Les actions d'origine… |
|---|---|---|
| `main` | touches Android (service d'accessibilité) | sont remplacées pour les boutons réglés |
| `ivi` | diffusion `com.jancar.services.action.key.event` d'ivi-services | **s'exécutent aussi** (ivi-services n'offre aucun moyen de les bloquer) |
| `librehu-service` | trames du boîtier CAN Hiworld (`0x11`) et boutons à résistance (MCU `20`) via LibreHU-service | sont remplacées : l'app est seule à recevoir les boutons |

### Branche `librehu-service` : une seule appli pour les touches du volant
LibreHU-service sait aussi transformer les touches du volant (`0x11`) en actions (onglet CAN, « Touches du volant »,
actif par défaut sans ivi-services). Avec BtnRemap, désactiver cette option du service, sinon chaque appui
déclenche deux actions.

### Branche `ivi` : neutraliser MODE
ivi-services lance quand même une appli de sa liste pour MODE. Avec root, on remplace sa liste par une activité
vide de cette app (`ModeNoOpActivity`, qui se ferme aussitôt) : seule l'action choisie ici s'exécute.
```
adb root
adb shell sed -i 's/^COUNT=6/COUNT=1/; s#^APP0=.*#APP0=org.librehu.btnremap/org.librehu.btnremap.ModeNoOpActivity#' /jancar/config/ivi-settings.ini
```
(sauvegardez le fichier avant ; à refaire si le système le recopie depuis `/system/etc`).

## Installation
1. Installer l'APK (artefact de l'Action **Build**), l'ouvrir : le service démarre (notification discrète).
2. Optionnel : activer le **service d'accessibilité** (Retour, Applis récentes, Notifications ; et sur `main`,
   indispensable pour recevoir les touches).
3. Appuyer sur chaque bouton du volant, puis le toucher dans la liste pour choisir ses actions.

Non testé sur l'autoradio à ce stade.
