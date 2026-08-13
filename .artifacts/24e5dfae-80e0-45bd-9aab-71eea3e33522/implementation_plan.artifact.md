# Ajout de l'alerte sonore de dépassement de vitesse

Ce plan décrit l'implémentation d'une alerte sonore automatique lorsque la vitesse du véhicule dépasse la limite définie. L'alerte sera routée intelligemment vers le Bluetooth s'il est connecté, ou vers les haut-parleurs du Pixel 7 sinon.

## Proposed Changes

### [Component Name]

#### [MODIFY] [LocationForegroundService.kt](file:///H:/GitHub/GPS-APP/app/src/main/java/com/example/gpsapp/LocationForegroundService.kt)
- Ajout des imports `MediaPlayer`, `AudioAttributes` et `RingtoneManager`.
- Ajout d'une variable `lastAlertTime` pour limiter la fréquence des bips.
- Implémentation de la fonction `jouerAlerteSonore()` utilisant `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` pour le routage audio automatique.
- Déclenchement de l'alerte dans `onLocationResult` si `overLimit` est vrai.

## Verification Plan

### Manual Verification
- Déployer l'application sur le Pixel 7.
- Simuler ou effectuer un trajet dépassant la limite fixée (ex: 50 km/h).
- Vérifier que le son sort du téléphone par défaut.
- Connecter un appareil Bluetooth et vérifier que le son bascule automatiquement vers celui-ci.
- Vérifier que la musique en cours (si présente) baisse de volume pendant l'alerte (ducking).
