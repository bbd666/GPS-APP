package com.example.gpsapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var tvCoordinates: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCurrentLimit: TextView

    private var speedLimit: Double = 50.0
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission GPS refusée.", Toast.LENGTH_LONG).show()
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
        val btnLimit80: Button = findViewById(R.id.btnLimit80)
        val btnLimit90: Button = findViewById(R.id.btnLimit90)
        val btnLimit110: Button = findViewById(R.id.btnLimit110)
        val btnLimit130: Button = findViewById(R.id.btnLimit130)

// Configuration des actions au clic
        btnLimit30.setOnClickListener {
            speedLimit = 30.0
            tvCurrentLimit.text = "Limite : 30 km/h"
        }
        btnLimit50.setOnClickListener {
            speedLimit = 50.0
            tvCurrentLimit.text = "Limite : 50 km/h"
        }
        btnLimit80.setOnClickListener {
            speedLimit = 80.0
            tvCurrentLimit.text = "Limite : 80 km/h"
        }
        btnLimit90.setOnClickListener {
            speedLimit = 90.0
            tvCurrentLimit.text = "Limite : 90 km/h"
        }
        btnLimit110.setOnClickListener {
            speedLimit = 110.0
            tvCurrentLimit.text = "Limite : 110 km/h"
        }
        btnLimit130.setOnClickListener {
            speedLimit = 130.0
            tvCurrentLimit.text = "Limite : 130 km/h"
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateUI(location)
                }
            }
        }

        checkPermissionsAndStart()

    }

    private fun checkPermissionsAndStart() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }

    }

    private fun updateUI(location: Location) {
        val latLongText = "Latitude : " + location.latitude + "\nLongitude : " + location.longitude
        tvCoordinates.text = latLongText
        val speedInKmh = location.speed * 3.6
        tvSpeed.text = String.format("%.1f km/h", speedInKmh)

        if (speedInKmh > speedLimit) {
            tvSpeed.setTextColor(android.graphics.Color.RED)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } else {
            tvSpeed.setTextColor(android.graphics.Color.parseColor("#007ACC"))
        }

    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.release()
    }

    override fun onResume() {
        super.onResume()
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocation == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

}