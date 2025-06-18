package com.circletracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
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
import io.github.controlwear.virtual.joystick.android.JoystickView

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var joystickView: JoystickView
    private lateinit var btnLockLocation: Button

    private var spoofedLocation: LatLng? = null
    private var locationLocked = false

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

        mapView = findViewById(R.id.mapView)
        joystickView = findViewById(R.id.joystickView)
        btnLockLocation = findViewById(R.id.btnLockLocation)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        btnLockLocation.setOnClickListener {
            locationLocked = !locationLocked
            btnLockLocation.text = if (locationLocked) "Unlock Location" else "Lock Location"
            Toast.makeText(this, if (locationLocked) "Location Locked" else "Location Unlocked", Toast.LENGTH_SHORT).show()
        }

        joystickView.setOnMoveListener { angle, strength ->
            if (!locationLocked && googleMap != null && spoofedLocation != null) {
                val newLocation = calculateNewLocation(spoofedLocation!!, angle, strength)
                spoofedLocation = newLocation
                updateMapLocation(newLocation)
                sendMockLocation(newLocation)
            }
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            setupMap()
        }
    }

    private fun setupMap() {
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isMyLocationButtonEnabled = false
        googleMap?.uiSettings?.isCompassEnabled = false
        googleMap?.uiSettings?.isMapToolbarEnabled = false
        googleMap?.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
        )

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    spoofedLocation = LatLng(location.latitude, location.longitude)
                    updateMapLocation(spoofedLocation!!)
                    sendMockLocation(spoofedLocation!!)
                }
            }
        }
    }

    private fun calculateNewLocation(current: LatLng, angle: Float, strength: Int): LatLng {
        // Calculate new location based on joystick angle and strength
        val distance = strength / 1000.0 // Adjust speed factor
        val radian = Math.toRadians(angle.toDouble())
        val deltaLat = distance * Math.cos(radian)
        val deltaLng = distance * Math.sin(radian)
        return LatLng(current.latitude + deltaLat, current.longitude + deltaLng)
    }

    private fun updateMapLocation(location: LatLng) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18f))
    }

    private fun sendMockLocation(location: LatLng) {
        // TODO: Implement mock location update using Android mock location APIs
        // This requires setting up a mock location provider and pushing location updates
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
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
