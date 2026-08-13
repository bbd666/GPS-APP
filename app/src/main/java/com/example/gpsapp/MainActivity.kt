package com.example.gpsapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoordinates: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCurrentLimit: TextView

    private var speedLimit: Double = 50.0

    // Reçoit les mises à jour envoyées par LocationForegroundService pendant que l'Activity est visible
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val lat = intent.getDoubleExtra(LocationForegroundService.EXTRA_LATITUDE, 0.0)
            val lon = intent.getDoubleExtra(LocationForegroundService.EXTRA_LONGITUDE, 0.0)
            val speed = intent.getDoubleExtra(LocationForegroundService.EXTRA_SPEED, 0.0)
            val overLimit = intent.getBooleanExtra(LocationForegroundService.EXTRA_OVER_LIMIT, false)

            tvCoordinates.text = "Latitude : $lat\nLongitude : $lon"
            tvSpeed.text = String.format("%.1f km/h", speed)
            tvSpeed.setTextColor(
                if (overLimit) android.graphics.Color.RED
                else android.graphics.Color.parseColor("#007ACC")
            )
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            requestBackgroundLocationIfNeeded()
            startTrackingService()
        } else {
            Toast.makeText(this, "Permission GPS refusée.", Toast.LENGTH_LONG).show()
        }
    }

    // La permission "arrière-plan" doit être demandée séparément (exigence du système)
    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Sans localisation en arrière-plan, le suivi s'arrêtera si vous quittez l'appli.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvCurrentLimit = findViewById(R.id.tvCurrentLimit)

        // Initialisation de tous les boutons
        val btnLimit30: Button = findViewById(R.id.btnLimit30)
        val btnLimit50: Button = findViewById(R.id.btnLimit50)
        val btnLimit70: Button = findViewById(R.id.btnLimit70)
        val btnLimit80: Button = findViewById(R.id.btnLimit80)
        val btnLimit90: Button = findViewById(R.id.btnLimit90)
        val btnLimit110: Button = findViewById(R.id.btnLimit110)
        val btnLimit130: Button = findViewById(R.id.btnLimit130)

        // Configuration des actions au clic
        btnLimit30.setOnClickListener { setSpeedLimit(30.0) }
        btnLimit50.setOnClickListener { setSpeedLimit(50.0) }
        btnLimit70.setOnClickListener { setSpeedLimit(70.0) }
        btnLimit80.setOnClickListener { setSpeedLimit(80.0) }
        btnLimit90.setOnClickListener { setSpeedLimit(90.0) }
        btnLimit110.setOnClickListener { setSpeedLimit(110.0) }
        btnLimit130.setOnClickListener { setSpeedLimit(130.0) }

        tvCurrentLimit.text = "Limite : ${speedLimit.toInt()} km/h"

        checkPermissionsAndStart()
    }

    private fun setSpeedLimit(newLimit: Double) {
        speedLimit = newLimit
        tvCurrentLimit.text = "Limite : ${newLimit.toInt()} km/h"
        // Le service applique la nouvelle limite immédiatement, même s'il tourne déjà en fond
        LocationForegroundService.updateSpeedLimit(this, newLimit)
    }

    private fun checkPermissionsAndStart() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        val permissionsToRequest = mutableListOf<String>()
        if (fineLocation != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (coarseLocation != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isEmpty()) {
            requestBackgroundLocationIfNeeded()
            startTrackingService()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, LocationForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            locationUpdateReceiver,
            IntentFilter(LocationForegroundService.ACTION_LOCATION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationUpdateReceiver)
        // Le service continue de tourner en fond : on ne l'arrête pas ici.
    }
}