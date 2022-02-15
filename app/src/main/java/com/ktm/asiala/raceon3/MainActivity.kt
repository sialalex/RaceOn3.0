package com.ktm.asiala.raceon3

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import org.altbeacon.beaconreference.R


class MainActivity : AppCompatActivity() {
    //Views
    lateinit var beaconStateTextView: TextView
    lateinit var beaconCountTextView: TextView
    lateinit var updateButton: Button
    lateinit var disconnectButton: Button
    var alertDialog: AlertDialog? = null

    //BLE Variables
    lateinit var mBluetoothAdapter: BluetoothAdapter
    lateinit var mBluetoothLeScanner: BluetoothLeScanner
    lateinit var mBluetoothManager: BluetoothManager
    lateinit var mBluetoothSocket: BluetoothSocket

    var alreadyFound: Boolean = false

    private val mLeScanCallback: ScanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result);
            Log.i("BLE", result.getDevice().address)

            if(result.device.address.equals("B8:27:EB:B0:A7:9F")){
                mBluetoothLeScanner.stopScan(this)
                connectWithMotorcycle(result.device.address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.i("BLE", "error")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();


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

    fun startScanning(){
        mBluetoothLeScanner.startScan(mLeScanCallback);
    }

    fun stopScanning(){

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectWithMotorcycle(macAdress: String) {
        mBluetoothLeScanner.stopScan(mLeScanCallback)
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;
        val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAdress)
        device.connectGatt(this, false, mGattCallback)
    }

    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothAdapter.cancelDiscovery()
                mBluetoothSocket = gatt.device.createInsecureL2capChannel(0x0080)
                mBluetoothSocket.connect()
                if(mBluetoothSocket.isConnected){
                    beaconStateTextView.setText("Connected!")

                }

            }
        }

    }


    fun updateButtonClicked(view: View) {
        startScanning()
    }

    fun disconnectButtonClicked(view: View) {
        //beaconReferenceApplication.disconnectGatt()
    }

    fun getKeyButtonClicked(view: View){
        beaconCountTextView.setText("Sending Data!")
        mBluetoothSocket.outputStream.write("187 Strassenbande".toByteArray())
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