package com.ktm.asiala.raceon3

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_main.*
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beaconreference.R


class MainActivity : AppCompatActivity() {
    //Views
    lateinit var beaconStateTextView: TextView
    lateinit var beaconCountTextView: TextView
    lateinit var updateButton: Button
    lateinit var disconnectButton: Button

    //BLE variables
    lateinit var beaconReferenceApplication: BeaconReferenceApplication



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        beaconReferenceApplication = application as BeaconReferenceApplication

        // Set up a Live Data observer for beacon data
        val regionViewModel = BeaconManager.getInstanceForApplication(this)
            .getRegionViewModel(beaconReferenceApplication.region)
        // observer will be called each time the monitored regionState changes (inside vs. outside region)
        regionViewModel.regionState.observe(this, monitoringObserver)
        // observer will be called each time a new list of beacons is ranged (typically ~1 second in the foreground)
        regionViewModel.rangedBeacons.observe(this, rangingObserver)

        //Link views with variables
        updateButton = findViewById<Button>(R.id.updateButton)
        disconnectButton = findViewById<Button>(R.id.disconnectButton)
        beaconStateTextView = findViewById<TextView>(R.id.beaconState)
        beaconCountTextView = findViewById<TextView>(R.id.beaconInformation)
        beaconCountTextView.text = "Searching..."
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    //Not used right now
    val monitoringObserver = Observer<Int> { state ->
    }

    //Check if the right beacon is found
    val rangingObserver = Observer<Collection<Beacon>> { beacons ->

    }

    fun updateButtonClicked(view: View) {
        beaconStateTextView.setText(beaconReferenceApplication.beaconState)
        beaconInformation.setText(beaconReferenceApplication.beaconInformation)
        keyTextView.setText(beaconReferenceApplication.key)
        dataTextView.setText(beaconReferenceApplication.data)

    }

    fun disconnectButtonClicked(view: View) {
        beaconReferenceApplication.disconnectRaceOn3()
    }

    fun getKeyButtonClicked(view: View){
        beaconReferenceApplication.getKey()
        updateButton.performClick()
    }

    fun getDataButtonClicked(view: View){
        beaconReferenceApplication.getData()
        updateButton.performClick()
    }


    //Permissions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in 1..permissions.size - 1) {
            Log.d(TAG, "onRequestPermissionResult for " + permissions[i] + ":" + grantResults[i])
        }
    }

    //Permissions
    fun checkPermissions() {
        // basepermissions are for M and higher
        var permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        var permissionRationale =
            "This app needs both fine location permission and background location permission to detect beacons in the background.  Please grant both now."
        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionRationale =
                "This app needs fine location permission and nearby devices permission to detect beacons.  Please grant this now."
        }
        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            // Uncomment line below if targeting SDK 31
            // permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN)
            permissionRationale =
                "This app needs both fine location permission and nearby devices permission to detect beacons.  Please grant both now."
        }
        var allGranted = true
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) allGranted =
                false;
        }
        if (!allGranted) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("This app needs permissions to detect beacons")
                builder.setMessage(permissionRationale)
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    requestPermissions(
                        permissions,
                        PERMISSION_REQUEST_FINE_LOCATION
                    )
                }
                builder.show()
            } else {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since location and device permissions have not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location and device discovery permissions to this app.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener { }
                builder.show()
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("This app needs background location access")
                        builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener {
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                PERMISSION_REQUEST_BACKGROUND_LOCATION
                            )
                        }
                        builder.show()
                    } else {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("Functionality limited")
                        builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener { }
                        builder.show()
                    }
                }
            }
        }
    }


    companion object {
        val TAG = "MainActivity"
        val PERMISSION_REQUEST_BACKGROUND_LOCATION = 0
        val PERMISSION_REQUEST_FINE_LOCATION = 1
    }

}