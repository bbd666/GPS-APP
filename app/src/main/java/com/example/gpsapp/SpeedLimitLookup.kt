package com.example.gpsapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Recherche la limite de vitesse la plus proche dans la base OSM locale.
 * Table attendue : segments(lat1, lon1, lat2, lon2, maxspeed_kmh, cell)
 * où "cell" est une grille de 0.01° (~1 km) utilisée comme index spatial grossier.
 *
 * La base est copiée depuis les assets vers le stockage interne au premier lancement :
 * SQLite doit ouvrir un vrai fichier sur le disque, pas une entrée compressée dans l'APK.
 */
class SpeedLimitLookup(context: Context) {

    private val db: SQLiteDatabase

    init {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
        }
        db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * Retourne la limite de vitesse (km/h) du segment routier le plus proche,
     * dans un rayon de [maxDistanceMeters], ou null si rien n'a été trouvé
     * (hors zone couverte par la base, ou trop loin de toute route connue).
     */
    fun findSpeedLimitNear(lat: Double, lon: Double, maxDistanceMeters: Double = 40.0): Int? {
        val cellKeys = neighboringCells(lat, lon)
        val placeholders = cellKeys.joinToString(",") { "?" }
        val cursor = db.rawQuery(
            "SELECT lat1, lon1, lat2, lon2, maxspeed_kmh FROM segments WHERE cell IN ($placeholders)",
            cellKeys.toTypedArray()
        )

        var bestDistance = Double.MAX_VALUE
        var bestSpeed: Int? = null

        // Projection locale plane (petites distances) : suffisant à l'échelle d'une ville/région
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(lat))

        cursor.use {
            while (it.moveToNext()) {
                val lat1 = it.getDouble(0)
                val lon1 = it.getDouble(1)
                val lat2 = it.getDouble(2)
                val lon2 = it.getDouble(3)
                val maxspeed = it.getInt(4)

                val ax = (lon1 - lon) * metersPerDegLon
                val ay = (lat1 - lat) * metersPerDegLat
                val bx = (lon2 - lon) * metersPerDegLon
                val by = (lat2 - lat) * metersPerDegLat

                val dist = distanceToSegmentMeters(0.0, 0.0, ax, ay, bx, by)
                if (dist < bestDistance) {
                    bestDistance = dist
                    bestSpeed = maxspeed
                }
            }
        }

        return if (bestDistance <= maxDistanceMeters) bestSpeed else null
    }

    /**
     * Les 9 cellules de la grille (0.01°) autour du point, pour ne pas rater
     * un segment proche mais situé juste de l'autre côté d'une bordure de cellule.
     */
    private fun neighboringCells(lat: Double, lon: Double): List<String> {
        val cells = mutableListOf<String>()
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                val cLat = roundTo2(lat + dLat * 0.01)
                val cLon = roundTo2(lon + dLon * 0.01)
                cells.add("${cLat}_${cLon}")
            }
        }
        return cells.distinct()
    }

    private fun roundTo2(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun distanceToSegmentMeters(
        px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) {
            return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        }
        var t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        t = t.coerceIn(0.0, 1.0)
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY))
    }

    fun close() {
        db.close()
    }

    companion object {
        private const val DB_NAME = "speed_limits.db"
    }
}