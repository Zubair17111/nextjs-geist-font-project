package com.circletracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import io.github.controlwear.virtual.joystick.android.JoystickView

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var joystickView: JoystickView
    private lateinit var btnLockLocation: Button
    private lateinit var btnCircleRoute: Button
    private lateinit var btnSquareRoute: Button
    private lateinit var btnSetGeofence: Button
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var locationSimulator: LocationSimulator

    private var currentLocation: LatLng? = null
    private var locationLocked = false
    private var geofenceEnabled = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            setupMap()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupLocationSimulator()
        setupListeners()
        
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        checkPermissions()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        joystickView = findViewById(R.id.joystickView)
        btnLockLocation = findViewById(R.id.btnLockLocation)
        btnCircleRoute = findViewById(R.id.btnCircleRoute)
        btnSquareRoute = findViewById(R.id.btnSquareRoute)
        btnSetGeofence = findViewById(R.id.btnSetGeofence)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedText = findViewById(R.id.speedText)
    }

    private fun setupLocationSimulator() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationSimulator = LocationSimulator(this, fusedLocationClient)
    }

    private fun setupListeners() {
        btnLockLocation.setOnClickListener {
            locationLocked = !locationLocked
            btnLockLocation.text = if (locationLocked) "Unlock Location" else "Lock Location"
            Toast.makeText(this, if (locationLocked) "Location Locked" else "Location Unlocked", Toast.LENGTH_SHORT).show()
        }

        btnCircleRoute.setOnClickListener {
            currentLocation?.let { location ->
                locationSimulator.startCircularRoute(location, 50.0) // 50 meters radius
                Toast.makeText(this, "Starting circular route", Toast.LENGTH_SHORT).show()
            }
        }

        btnSquareRoute.setOnClickListener {
            currentLocation?.let { location ->
                locationSimulator.startSquareRoute(location, 100.0) // 100 meters size
                Toast.makeText(this, "Starting square route", Toast.LENGTH_SHORT).show()
            }
        }

        btnSetGeofence.setOnClickListener {
            currentLocation?.let { location ->
                geofenceEnabled = !geofenceEnabled
                if (geofenceEnabled) {
                    locationSimulator.setGeofence(location, 1000.0) // 1km radius
                    btnSetGeofence.text = "Remove Geofence"
                    Toast.makeText(this, "Geofence enabled", Toast.LENGTH_SHORT).show()
                } else {
                    locationSimulator.disableGeofence()
                    btnSetGeofence.text = "Set Geofence"
                    Toast.makeText(this, "Geofence disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = (progress / 10.0)
                speedText.text = "Speed: $speed m/s"
                locationSimulator.setWalkingSpeed(speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        joystickView.setOnMoveListener { angle, strength ->
            if (!locationLocked && googleMap != null && currentLocation != null) {
                val newLocation = calculateNewLocation(currentLocation!!, angle, strength)
                currentLocation = newLocation
                locationSimulator.updateLocation(newLocation)
                updateMapLocation(newLocation)
            }
        }
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            setupMap()
        }
    }

    private fun setupMap() {
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.apply {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = false
            uiSettings.isMapToolbarEnabled = false
            setMapStyle(MapStyleOptions.loadRawResourceStyle(this@MainActivity, R.raw.map_style_dark))
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    updateMapLocation(currentLocation!!)
                    locationSimulator.updateLocation(currentLocation!!)
                }
            }
        }
    }

    private fun calculateNewLocation(current: LatLng, angle: Float, strength: Int): LatLng {
        val speed = speedSeekBar.progress / 10.0
        val distance = (strength / 1000.0) * speed
        val radian = Math.toRadians(angle.toDouble())
        val deltaLat = distance * Math.cos(radian)
        val deltaLng = distance * Math.sin(radian)
        return LatLng(current.latitude + deltaLat, current.longitude + deltaLng)
    }

    private fun updateMapLocation(location: LatLng) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18f))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        locationSimulator.startSensorSimulation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationSimulator.stopSensorSimulation()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
