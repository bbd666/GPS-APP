package com.example.gpsapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.gpsapp.R

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoordinates: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCurrentLimit: TextView
    private lateinit var tvStatusInfo: TextView
    private lateinit var tvDbName: TextView
    private lateinit var etDefaultSpeed: android.widget.EditText
    private lateinit var viewOsmIndicator: android.view.View
    private lateinit var btnActivateOsm: Button

    private var speedLimit: Double = 50.0

    // Récepteur de messages (Broadcast) pour intercepter la position envoyée par le Service
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val lat = intent.getDoubleExtra(LocationForegroundService.EXTRA_LATITUDE, 0.0)
            val lon = intent.getDoubleExtra(LocationForegroundService.EXTRA_LONGITUDE, 0.0)
            val speed = intent.getDoubleExtra(LocationForegroundService.EXTRA_SPEED, 0.0)
            val overLimit = intent.getBooleanExtra(LocationForegroundService.EXTRA_OVER_LIMIT, false)
            val limit = intent.getDoubleExtra(LocationForegroundService.EXTRA_LIMIT, speedLimit)
            val osmActive = intent.getBooleanExtra(LocationForegroundService.EXTRA_OSM_ACTIVE, true)
            val limitFound = intent.getBooleanExtra(LocationForegroundService.EXTRA_LIMIT_FOUND, true)

            // Mise à jour de l'interface utilisateur
            tvCoordinates.text = "Latitude : $lat\nLongitude : $lon"
            tvSpeed.text = String.format("%.1f km/h", speed)
            tvCurrentLimit.text = "Limite : ${limit.toInt()} km/h"

            // Affichage "limitation inconnue"
            tvStatusInfo.visibility = if (!limitFound && osmActive) android.view.View.VISIBLE else android.view.View.GONE

            // Mise à jour du voyant OSM
            if (osmActive) {
                viewOsmIndicator.setBackgroundColor(android.graphics.Color.RED)
                btnActivateOsm.visibility = android.view.View.GONE
            } else {
                viewOsmIndicator.setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))
                btnActivateOsm.visibility = android.view.View.VISIBLE
            }
            
            tvSpeed.setTextColor(
                if (overLimit) android.graphics.Color.RED
                else android.graphics.Color.parseColor("#007ACC")
            )
        }

    }

    // Gestionnaire des demandes de permissions de base (Localisation + Notifications)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            requestBackgroundLocationIfNeeded()
            checkDbAndStartService()
        } else {
            Toast.makeText(this, "Permission GPS refusée.", Toast.LENGTH_LONG).show()
        }
    }

    // Gestionnaire de la permission spécifique pour la localisation en arrière-plan
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

    // Lanceur système pour ouvrir l'explorateur de fichiers et sélectionner un fichier .db
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("FILE_URI", uri.toString())
                .apply()

            initialiserAvecFichier(uri)
        } else {
            Toast.makeText(this, "Sélection du fichier annulée.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
// Liaison avec le fichier activity_main.xml
        setContentView(R.layout.activity_main)
// Liaison des éléments graphiques (Views) avec forçage de la classe R du projet
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvCurrentLimit = findViewById(R.id.tvCurrentLimit)
        tvStatusInfo = findViewById(R.id.tvStatusInfo)
        tvDbName = findViewById(R.id.tvDbName)
        etDefaultSpeed = findViewById(R.id.etDefaultSpeed)
        viewOsmIndicator = findViewById(R.id.viewOsmIndicator)
        btnActivateOsm = findViewById(R.id.btnActivateOsm)

        val btnLimit30: Button = findViewById(R.id.btnLimit30)
        val btnLimit50: Button = findViewById(R.id.btnLimit50)
        val btnLimit70: Button = findViewById(R.id.btnLimit70)
        val btnLimit80: Button = findViewById(R.id.btnLimit80)
        val btnLimit90: Button = findViewById(R.id.btnLimit90)
        val btnLimit110: Button = findViewById(R.id.btnLimit110)
        val btnLimit130: Button = findViewById(R.id.btnLimit130)
        val btnChangeDatabase: Button = findViewById(R.id.btnChangeDatabase)
        val btnStopApp: Button = findViewById(R.id.btnStopApp)

// Assignation des boutons de limites de vitesse
        btnLimit30.setOnClickListener { setSpeedLimit(30.0) }
        btnLimit50.setOnClickListener { setSpeedLimit(50.0) }
        btnLimit70.setOnClickListener { setSpeedLimit(70.0) }
        btnLimit80.setOnClickListener { setSpeedLimit(80.0) }
        btnLimit90.setOnClickListener { setSpeedLimit(90.0) }
        btnLimit110.setOnClickListener { setSpeedLimit(110.0) }
        btnLimit130.setOnClickListener { setSpeedLimit(130.0) }

// Configuration de l'action de clic du bouton d'externalisation
        btnChangeDatabase.setOnClickListener { changerDeDossierBase() }

        btnStopApp.setOnClickListener {
            stopService(Intent(this, LocationForegroundService::class.java))
            finish()
        }

        btnActivateOsm.setOnClickListener {
            val defaultS = getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
                .getFloat("DEFAULT_SPEED", 50.0f).toDouble()
            speedLimit = defaultS
            tvCurrentLimit.text = "Limite : ${speedLimit.toInt()} km/h"

            LocationForegroundService.activateOsmMode(this)
            Toast.makeText(this, "Mode OSM réactivé", Toast.LENGTH_SHORT).show()
        }

        // Charger la vitesse par défaut sauvegardée et initialiser la limite
        val prefs = getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
        val savedDefaultSpeed = prefs.getFloat("DEFAULT_SPEED", 50.0f).toDouble()
        speedLimit = savedDefaultSpeed
        etDefaultSpeed.setText(savedDefaultSpeed.toInt().toString())
        tvCurrentLimit.text = "Limite : ${speedLimit.toInt()} km/h"

        // Mettre à jour la vitesse par défaut quand l'utilisateur change la valeur
        etDefaultSpeed.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val speed = s.toString().toDoubleOrNull() ?: 50.0
                getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putFloat("DEFAULT_SPEED", speed.toFloat())
                    .apply()
                // Notifier le service du changement
                LocationForegroundService.updateDefaultSpeed(this@MainActivity, speed)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        checkPermissionsAndStart()
    }

    private fun setSpeedLimit(newLimit: Double) {
        speedLimit = newLimit
        tvCurrentLimit.text = "Limite : " + newLimit.toInt() + " km/h"
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
            checkDbAndStartService()
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

    // Fonction pour oublier l'ancien fichier et réouvrir l'explorateur
    private fun changerDeDossierBase() {
        getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
            .edit()
            .remove("FILE_URI")
            .apply()
        Toast.makeText(this, "Sélectionnez votre fichier .db", Toast.LENGTH_LONG).show()
        openFileLauncher.launch(arrayOf("*/*"))
    }

    private fun checkDbAndStartService() {
        val prefs = getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
        val savedUriString = prefs.getString("FILE_URI", null)
        
        if (savedUriString != null) {
            val fileUri = Uri.parse(savedUriString)
            val hasPermission = contentResolver.persistedUriPermissions.any { it.uri == fileUri }
            if (hasPermission) {
                initialiserAvecFichier(fileUri)
                return
            }
        }
        Toast.makeText(this, "Veuillez sélectionner votre fichier .db", Toast.LENGTH_LONG).show()
        openFileLauncher.launch(arrayOf("*/*"))
    }

    private fun initialiserAvecFichier(fileUri: Uri) {
        val documentFile = DocumentFile.fromSingleUri(this, fileUri)
        if (documentFile != null && documentFile.exists()) {
            tvDbName.text = "Base : ${documentFile.name}"
            val defaultSpeed = getSharedPreferences("GPS_APP_PREFS", Context.MODE_PRIVATE)
                .getFloat("DEFAULT_SPEED", 50.0f).toDouble()
            startTrackingService(fileUri.toString(), defaultSpeed)
        } else {
            Toast.makeText(this, "Fichier introuvable.", Toast.LENGTH_LONG).show()
            openFileLauncher.launch(arrayOf("*/*"))
        }
    }

    private fun startTrackingService(fileUriString: String, defaultSpeed: Double) {
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            putExtra("EXTRA_DB_URI", fileUriString)
            putExtra("EXTRA_DEFAULT_SPEED", defaultSpeed)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        // Enregistrement du récepteur de données GPS
        ContextCompat.registerReceiver(
            this,
            locationUpdateReceiver,
            IntentFilter(LocationForegroundService.ACTION_LOCATION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        // Désactivation du récepteur pour éviter les fuites de mémoire
        unregisterReceiver(locationUpdateReceiver)
    }
}
