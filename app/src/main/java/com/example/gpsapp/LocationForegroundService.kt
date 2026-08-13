package com.example.gpsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

/**
 * Service qui garde le suivi GPS actif même quand l'application
 * n'est plus au premier plan. Affiche une notification permanente
 * (obligatoire pour un foreground service) et déclenche le bip
 * d'alerte quand la vitesse dépasse la limite.
 */
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private var speedLimit: Double = 50.0

    private val updateLimitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            speedLimit = intent?.getDoubleExtra(EXTRA_SPEED_LIMIT, speedLimit) ?: speedLimit
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        speedLimit = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getFloat(PREF_SPEED_LIMIT, 50f).toDouble()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    handleLocation(location)
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            updateLimitReceiver,
            IntentFilter(ACTION_UPDATE_LIMIT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("En attente du signal GPS…"))
        startLocationUpdates()
        // START_STICKY : si le système tue le service faute de ressources,
        // il tente de le relancer automatiquement.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun handleLocation(location: Location) {
        val speedInKmh = location.speed * 3.6
        val overLimit = speedInKmh > speedLimit

        if (overLimit) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }

        val notifText = String.format("%.1f km/h (limite : %.0f km/h)", speedInKmh, speedLimit)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(notifText))

        // Informe l'Activity si elle est visible (sinon l'intent est simplement ignoré)
        val update = Intent(ACTION_LOCATION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_SPEED, speedInKmh)
            putExtra(EXTRA_OVER_LIMIT, overLimit)
        }
        sendBroadcast(update)
    }

    private fun buildNotification(contentText: String): Notification {
        createChannelIfNeeded()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Suivi GPS actif")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Suivi de vitesse GPS",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        toneGenerator.release()
        unregisterReceiver(updateLimitReceiver)
    }

    companion object {
        const val CHANNEL_ID = "gps_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_LOCATION_UPDATE = "com.example.gpsapp.ACTION_LOCATION_UPDATE"
        const val ACTION_UPDATE_LIMIT = "com.example.gpsapp.ACTION_UPDATE_LIMIT"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_OVER_LIMIT = "extra_over_limit"
        const val EXTRA_SPEED_LIMIT = "extra_speed_limit"

        const val PREFS_NAME = "gps_app_prefs"
        const val PREF_SPEED_LIMIT = "pref_speed_limit"

        /** Met à jour la limite de vitesse à chaud (et la persiste pour le prochain démarrage du service). */
        fun updateSpeedLimit(context: Context, newLimit: Double) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putFloat(PREF_SPEED_LIMIT, newLimit.toFloat()).apply()
            val intent = Intent(ACTION_UPDATE_LIMIT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_SPEED_LIMIT, newLimit)
            }
            context.sendBroadcast(intent)
        }
    }
}