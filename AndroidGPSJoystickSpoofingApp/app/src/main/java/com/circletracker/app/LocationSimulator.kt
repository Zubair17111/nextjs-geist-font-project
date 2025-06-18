package com.circletracker.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin

class LocationSimulator(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : SensorEventListener {

    private var currentLocation: Location? = null
    private var isAutoWalking = false
    private var autoWalkPath: List<LatLng> = emptyList()
    private var autoWalkIndex = 0
    private var walkingSpeed = 5.0 // meters per second
    private var isAutoWalkingEnabled = false
    private val routeParser = RouteParser()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoWalkRunnable = object : Runnable {
        override fun run() {
            if (isAutoWalkingEnabled && autoWalkPath.isNotEmpty()) {
                if (autoWalkIndex >= autoWalkPath.size) {
                    autoWalkIndex = 0 // Loop the route
                }
                updateLocation(autoWalkPath[autoWalkIndex])
                autoWalkIndex++
                
                // Calculate delay based on speed and distance to next point
                val delay = if (autoWalkIndex < autoWalkPath.size) {
                    val current = autoWalkPath[autoWalkIndex - 1]
                    val next = autoWalkPath[autoWalkIndex]
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        current.latitude, current.longitude,
                        next.latitude, next.longitude,
                        results
                    )
                    (results[0] / walkingSpeed * 1000).toLong() // Convert to milliseconds
                } else {
                    (1000 / walkingSpeed).toLong() // Default delay
                }
                
                handler.postDelayed(this, delay)
            }
        }
    }
    private var geofenceCenter: LatLng? = null
    private var geofenceRadius = 1000.0 // meters
    private var isGeofenceEnabled = false

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    init {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun startSensorSimulation() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopSensorSimulation() {
        sensorManager.unregisterListener(this)
    }

    fun updateLocation(newLatLng: LatLng) {
        if (isGeofenceEnabled && !isWithinGeofence(newLatLng)) {
            return
        }

        val location = Location("GPS").apply {
            latitude = newLatLng.latitude
            longitude = newLatLng.longitude
            accuracy = 3.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            altitude = 0.0
            speed = if (currentLocation != null) {
                currentLocation!!.distanceTo(this) / 
                ((time - currentLocation!!.time) / 1000.0f)
            } else 0.0f
            bearing = if (currentLocation != null) {
                currentLocation!!.bearingTo(this)
            } else 0.0f
        }

        currentLocation = location
        simulateSensorData(location)
        
        // Create a LocationResult with our simulated location
        val locationResult = LocationResult.create(listOf(location))
        
        // Update location listeners
        locationCallback?.onLocationResult(locationResult)
    }

    private var locationCallback: LocationCallback? = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Forward to any registered callbacks
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            super.onLocationAvailability(availability)
        }
    }

    fun setAutoWalkPath(path: List<LatLng>, interpolatePoints: Boolean = true) {
        autoWalkPath = if (interpolatePoints) {
            routeParser.interpolateRoute(path, walkingSpeed) // Interpolate points based on walking speed
        } else {
            path
        }
        autoWalkIndex = 0
        startAutoWalk()
    }

    fun setGeofence(center: LatLng, radius: Double) {
        geofenceCenter = center
        geofenceRadius = radius
        isGeofenceEnabled = true
    }

    fun disableGeofence() {
        isGeofenceEnabled = false
    }

    private fun isWithinGeofence(location: LatLng): Boolean {
        if (!isGeofenceEnabled || geofenceCenter == null) return true
        
        val results = FloatArray(1)
        Location.distanceBetween(
            geofenceCenter!!.latitude,
            geofenceCenter!!.longitude,
            location.latitude,
            location.longitude,
            results
        )
        return results[0] <= geofenceRadius
    }

    private fun simulateSensorData(location: Location) {
        // Simulate accelerometer data based on movement
        if (currentLocation != null) {
            val deltaTime = (location.time - currentLocation!!.time) / 1000.0f // seconds
            val deltaLat = location.latitude - currentLocation!!.latitude
            val deltaLng = location.longitude - currentLocation!!.longitude
            
            // Simulate acceleration based on position change
            val acceleration = floatArrayOf(
                (deltaLat / (deltaTime * deltaTime)).toFloat() * 9.81f,
                (deltaLng / (deltaTime * deltaTime)).toFloat() * 9.81f,
                9.81f // Gravity
            )
            
            onSensorChanged(SensorEvent(accelerometer, acceleration))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Handle sensor updates if needed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    fun setWalkingSpeed(speed: Double) {
        walkingSpeed = speed
        if (isAutoWalkingEnabled) {
            stopAutoWalk()
            startAutoWalk() // Restart with new speed
        }
    }

    fun startCircularRoute(center: LatLng, radius: Double) {
        val points = mutableListOf<LatLng>()
        val steps = 360
        for (i in 0..steps) {
            val angle = Math.toRadians(i * (360.0 / steps))
            val lat = center.latitude + (radius / 111111.0) * cos(angle)
            val lng = center.longitude + (radius / (111111.0 * cos(Math.toRadians(center.latitude)))) * sin(angle)
            points.add(LatLng(lat, lng))
        }
        setAutoWalkPath(points, true)
    }

    fun startSquareRoute(center: LatLng, size: Double) {
        val halfSize = size / 2
        val points = listOf(
            LatLng(center.latitude + halfSize, center.longitude + halfSize),
            LatLng(center.latitude + halfSize, center.longitude - halfSize),
            LatLng(center.latitude - halfSize, center.longitude - halfSize),
            LatLng(center.latitude - halfSize, center.longitude + halfSize),
            LatLng(center.latitude + halfSize, center.longitude + halfSize)
        )
        setAutoWalkPath(points, true)
    }
}
