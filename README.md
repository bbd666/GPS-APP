# GPS-APP 🚗💨

**GPS-APP** est une application Android conçue pour les conducteurs, offrant un suivi de vitesse en temps réel et des alertes sonores intelligentes basées sur les données **OpenStreetMap (OSM)** hors-ligne.

## 🌟 Fonctionnalités principales

- **Suivi GPS en temps réel** : Affichage précis de la vitesse actuelle en km/h.
- **Base de données OSM Hors-ligne** : Recherche instantanée de la limite de vitesse locale à partir d'un fichier `.db` (OpenStreetMap).
- **Alertes Sonores Intelligentes** :
  - **Routage Automatique** : Le son bascule sur le **Bluetooth** de votre voiture s'il est connecté, ou sur les **haut-parleurs** du téléphone sinon.
  - **Audio Ducking** : Baisse automatiquement le volume de votre musique (Spotify, etc.) pendant l'alerte pour une meilleure audibilité.
  - **Fréquence optimisée** : Évite les bips répétitifs (intervalle de 5s).
- **Configuration Flexible** :
  - **Vitesse par défaut personnalisable** : Définissez la limite à appliquer (ex: 80 km/h) lorsque la route n'est pas identifiée.
  - **Sélection de base simplifiée** : Choisissez votre fichier de carte `.db` directement depuis l'application.
- **Mode Arrière-plan** : Le suivi continue même si l'écran est éteint ou si vous utilisez une autre application (Waze, Maps, etc.) grâce à un service de premier plan.

## 🛠️ Installation et Configuration

1. **Cloner le projet** et l'ouvrir dans Android Studio.
2. **Compiler et installer** l'APK sur votre appareil (optimisé pour Pixel 7).
3. **Préparer la base de données** :
   - Procurez-vous un fichier `openstreetmap.db` contenant les segments routiers (format attendu : `lat1, lon1, lat2, lon2, maxspeed_kmh, cell`).
   - Placez ce fichier sur votre téléphone.
4. **Lancer l'application** :
   - Accordez les permissions de localisation (incluant "Toujours autoriser" pour le mode arrière-plan).
   - Cliquez sur **"Changer de base"** pour sélectionner votre fichier `.db`.

## 📱 Utilisation

- **Mode OSM** : Activé par défaut si une base est chargée. L'indicateur (carré rouge) confirme que la base est lue.
- **Limites manuelles** : Vous pouvez forcer une limite (30, 50, 80, etc.) en cliquant sur les boutons dédiés. Le mode OSM peut être réactivé d'un clic.
- **Paramètres** : Ajustez la "Vitesse par défaut" directement dans le champ prévu en bas de l'écran.
- **Arrêt** : Utilisez le bouton rouge **"Arrêter l'application"** pour couper proprement le GPS et fermer le service.

## 🏗️ Architecture Technique

- **Langage** : Kotlin
- **Composants Android** :
  - `Foreground Service` pour la persistance du suivi.
  - `FusedLocationProviderClient` pour une géolocalisation haute précision.
  - `SQLite` pour la lecture performante des données OSM locales.
  - `MediaPlayer` avec `AudioAttributes` pour la gestion intelligente des flux sonores.
- **Permissions** : Localisation précise, Localisation en arrière-plan, Notifications, Bluetooth.

---
Développé avec ❤️ pour une conduite plus sûre.
