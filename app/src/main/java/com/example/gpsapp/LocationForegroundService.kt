package com.example.gpsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.io.FileOutputStream

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var speedLimitLookup: SpeedLimitLookup? = null
    private var speedLimit: Double = 50.0
    private var defaultSpeed: Double = 50.0
    private var isOsmModeActive: Boolean = true
    private var lastAlertTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
// Initialisation du client de localisation Google Play Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

// Callback déclenché à chaque mise à jour GPS reçue du téléphone
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude

                    // Recherche de la limite de vitesse dans la base de données (seulement si mode OSM actif)
                    var limitFound = false
                    if (isOsmModeActive) {
                        val newLimit = speedLimitLookup?.findSpeedLimitNear(lat, lon)
                        if (newLimit != null) {
                            speedLimit = newLimit.toDouble()
                            limitFound = true
                            Log.d("LocationService", "Limite de vitesse trouvée (OSM) : $speedLimit km/h")
                        } else {
                            speedLimit = defaultSpeed
                            Log.d("LocationService", "Limite non trouvée (OSM), $speedLimit km/h par défaut")
                        }
                    }

                    // Conversion de la vitesse de m/s en km/h
                    val speedKmH = location.speed * 3.6
                    val overLimit = speedKmH > speedLimit

                    Log.d("LocationService", "GPS : Lat $lat, Lon $lon, Vitesse $speedKmH km/h")

                    // Envoyer instantanément les données actualisées à la MainActivity
                    envoyerMiseAJourCoordonnees(lat, lon, speedKmH, overLimit, speedLimit, isOsmModeActive, limitFound)

                    if (overLimit) {
                        jouerAlerteSonore()
                    }

                    // Mettre à jour le texte de la notification persistante
                    mettreAJourNotification(speedKmH)
                }
            }
        }

        creerCanalNotification()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
// Promouvoir immédiatement le service au premier plan (Foreground) pour Android
        startForeground(NOTIFICATION_ID, générerNotification(0.0))
        if (intent != null) {
            // Intercepter l'action de changement manuel de vitesse depuis l'UI
            if (intent.action == ACTION_UPDATE_LIMIT) {
                speedLimit = intent.getDoubleExtra(EXTRA_SPEED_LIMIT, 50.0)
                isOsmModeActive = false
                Log.d("LocationService", "Nouvelle limite manuelle appliquée : $speedLimit km/h (OSM désactivé)")
            }

            // Intercepter l'action pour réactiver OSM
            if (intent.action == ACTION_ACTIVATE_OSM) {
                isOsmModeActive = true
                speedLimit = defaultSpeed
                Log.d("LocationService", "Mode OSM réactivé, reset à la vitesse par défaut : $speedLimit km/h")
            }

            // Intercepter l'action de mise à jour de la vitesse par défaut
            if (intent.action == ACTION_UPDATE_DEFAULT_SPEED) {
                defaultSpeed = intent.getDoubleExtra(EXTRA_DEFAULT_SPEED, 50.0)
                if (isOsmModeActive) {
                    speedLimit = defaultSpeed
                }
                Log.d("LocationService", "Nouvelle vitesse par défaut appliquée : $defaultSpeed km/h")
            }

            // Récupérer la vitesse par défaut arbitrale au démarrage
            if (intent.hasExtra(EXTRA_DEFAULT_SPEED)) {
                defaultSpeed = intent.getDoubleExtra(EXTRA_DEFAULT_SPEED, 50.0)
                if (isOsmModeActive) speedLimit = defaultSpeed
            }

            // Récupérer l'URI de la base .db transmise par MainActivity
            val dbUriString = intent.getStringExtra("EXTRA_DB_URI")
            if (dbUriString != null) {
                try {
                    val fileUri = Uri.parse(dbUriString)
                    Log.d("LocationService", "Base OSM connectée avec succès !")
                    initialiserBaseOSM(fileUri)
                } catch (e: Exception) {
                    Log.e("LocationService", "Erreur lors de l'accès au fichier .db", e)
                }
            }
        }

// Lancer les requêtes GPS répétitives (toutes les 1 seconde)
        demarrerSuiviGPS()

        return START_STICKY

    }

    private fun demarrerSuiviGPS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Log.e("LocationService", "Perte de permission de localisation. Impossible de suivre.", unlikely)
        }

    }

    private fun envoyerMiseAJourCoordonnees(latitude: Double, longitude: Double, vitesse: Double, auDessusLimite: Boolean, limiteActuelle: Double, osmActif: Boolean, limitFound: Boolean) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
            putExtra(EXTRA_SPEED, vitesse)
            putExtra(EXTRA_OVER_LIMIT, auDessusLimite)
            putExtra(EXTRA_LIMIT, limiteActuelle)
            putExtra(EXTRA_OSM_ACTIVE, osmActif)
            putExtra(EXTRA_LIMIT_FOUND, limitFound)
            setPackage(packageName) // Sécurise l'envoi uniquement au package de l'application
        }
        sendBroadcast(intent)
    }

    private fun initialiserBaseOSM(uri: Uri) {
        try {
            val localFile = File(filesDir, "external_speed_limits.db")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            speedLimitLookup?.close()
            speedLimitLookup = SpeedLimitLookup(this, localFile.absolutePath)
            Log.d("LocationService", "SpeedLimitLookup initialisé avec ${localFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("LocationService", "Erreur lors de l'initialisation de SpeedLimitLookup", e)
        }
    }

    private fun creerCanalNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Suivi GPS Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun générerNotification(vitesse: Double): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS-APP en exécution")
            .setContentText(String.format("Vitesse actuelle : %.1f km/h", vitesse))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun mettreAJourNotification(vitesse: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, générerNotification(vitesse))
    }

    private fun jouerAlerteSonore() {
        val maintenant = System.currentTimeMillis()
        if (maintenant - lastAlertTime < 5000) return // Intervalle de 5 secondes
        lastAlertTime = maintenant

        try {
            val alerteUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            MediaPlayer().apply {
                setDataSource(this@LocationForegroundService, alerteUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Erreur lors de la lecture du son", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
// Arrêter proprement le capteur GPS pour économiser la batterie du Pixel 7
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Service arrêté et requêtes GPS coupées.")
    }

    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_LOCATION_UPDATE = "com.example.gpsapp.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_OVER_LIMIT = "extra_over_limit"
        const val EXTRA_LIMIT = "extra_limit"
        const val EXTRA_OSM_ACTIVE = "extra_osm_active"
        const val EXTRA_LIMIT_FOUND = "extra_limit_found"

        const val ACTION_UPDATE_LIMIT = "com.example.gpsapp.UPDATE_LIMIT"
        const val ACTION_ACTIVATE_OSM = "com.example.gpsapp.ACTIVATE_OSM"
        const val ACTION_UPDATE_DEFAULT_SPEED = "com.example.gpsapp.UPDATE_DEFAULT_SPEED"
        const val EXTRA_SPEED_LIMIT = "EXTRA_SPEED_LIMIT"
        const val EXTRA_DEFAULT_SPEED = "EXTRA_DEFAULT_SPEED"

        fun updateSpeedLimit(context: Context, newLimit: Double) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_LIMIT
                putExtra(EXTRA_SPEED_LIMIT, newLimit)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun activateOsmMode(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_ACTIVATE_OSM
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateDefaultSpeed(context: Context, newDefaultSpeed: Double) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_DEFAULT_SPEED
                putExtra(EXTRA_DEFAULT_SPEED, newDefaultSpeed)
            }
            ContextCompat.startForegroundService(context, intent)
        }

    }

}