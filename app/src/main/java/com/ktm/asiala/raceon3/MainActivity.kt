package com.ktm.asiala.raceon3

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beaconreference.R
import java.util.*


class MainActivity : AppCompatActivity() {
    //Views
    lateinit var beaconStateTextView: TextView
    lateinit var beaconCountTextView: TextView
    lateinit var readButton: Button
    lateinit var writeButton: Button
    var alertDialog: AlertDialog? = null

    //BLE variables
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    lateinit var mBluetoothManager: BluetoothManager
    lateinit var mBluetoothGatt: BluetoothGatt

    var RACEON3_SERVICE_UUID: UUID = UUID.fromString("71ced1ac-0000-44f5-9454-806ff70b3e02")
    var RACEON3_CHARAC_UUID: UUID = UUID.fromString("4116f8d2-9f66-4f58-a53d-fc7440e7c14e")
    var RACEON3_DESC_UUID: UUID = convertFromInteger(0x2902)

    lateinit var raceon3_characteristic: BluetoothGattCharacteristic
    lateinit var raceon3_descriptor: BluetoothGattDescriptor


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
        readButton = findViewById<Button>(R.id.readButton)
        writeButton = findViewById<Button>(R.id.writeButton)
        beaconStateTextView = findViewById<TextView>(R.id.beaconState)
        beaconCountTextView = findViewById<TextView>(R.id.beaconCount)
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

        writeButton.performClick()
        mBluetoothGatt.disconnect()
        mBluetoothGatt.close()
    }

    //Not used right now
    val monitoringObserver = Observer<Int> { state ->
        /*var dialogTitle = "Beacons detected"
        var dialogMessage = "didEnterRegionEvent has fired"
        var stateString = "inside"
        if (state == MonitorNotifier.OUTSIDE) {
            dialogTitle = "No beacons detected"
            dialogMessage = "didExitRegionEvent has fired"
            stateString == "outside"
            beaconCountTextView.text = "Outside of the beacon region -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        }
        else {
            beaconCountTextView.text = "Inside the beacon region."
        }
        Log.d(TAG, "monitoring state changed to : $stateString")
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(android.R.string.ok, null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()*/
    }

    //Check if the right beacon is found
    val rangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.count()} beacons")

        //Check if the required beacon is found
        if (beacons.size != 0) {
            beacons.forEach { beacon ->
                if (beacon.bluetoothName.equals("RaceOn")) {
                    Log.d(TAG, "Found the right RaceOn Beacon")

                    beaconCountTextView.setText("Found the motorcycle! " + beacon.bluetoothAddress)
                    beaconStateTextView.setText("Connecting...")

                    //Stop searching for more beacons
                    val beaconManager = BeaconManager.getInstanceForApplication(this)
                    beaconManager.stopMonitoring(beaconReferenceApplication.region);
                    beaconManager.stopRangingBeacons(beaconReferenceApplication.region);

                    //Connect to the device
                    connectAndBondWithMotorcycle(beacon.bluetoothAddress);
                }
            }
        }
    }

    fun readButtonClicked(view: View) {
        raceon3_descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        mBluetoothGatt.writeDescriptor(raceon3_descriptor)
    }

    fun writeButtonClicked(view: View) {
        raceon3_descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        mBluetoothGatt.writeDescriptor(raceon3_descriptor)
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

    //Connect / Bond the smartphone with the motorcycle (over the mac address)
    fun connectAndBondWithMotorcycle(macAdress: String) {
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;
        val mBluetoothAdapter = mBluetoothManager.getAdapter();
        val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAdress)
        device.connectGatt(this, false, bluetoothGattCallback)
        //device.createBond();
    }

    //Create GATT Callback
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected")
                beaconStateTextView.setText("Connected! Bonded!")

                //Save the bluetooth gatt
                if (gatt != null) {
                    mBluetoothGatt = gatt
                    mBluetoothGatt.discoverServices()
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnect")
                beaconStateTextView.setText("Disconnected!")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "Found services")

            raceon3_characteristic =
                mBluetoothGatt.getService(RACEON3_SERVICE_UUID)
                    .getCharacteristic(RACEON3_CHARAC_UUID)

            raceon3_descriptor = raceon3_characteristic.getDescriptor(RACEON3_DESC_UUID)
            if(raceon3_descriptor.value == null){
                raceon3_descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }else{
                raceon3_descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            mBluetoothGatt.writeDescriptor(raceon3_descriptor)

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        //Enabled Notifications
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            if (mBluetoothGatt != null) {
                mBluetoothGatt.setCharacteristicNotification(raceon3_characteristic, true)
                raceon3_characteristic.setValue("187 Straßenbande")
                mBluetoothGatt.writeCharacteristic(raceon3_characteristic)
            }
        }
    }

    //Convert Integer to UUID
    fun convertFromInteger(i: Int): UUID {
        val MSB = 0x0000000000001000L
        val LSB = -0x7fffff7fa064cb05L
        val value = (i and (-0x1.toLong()).toInt()).toLong()
        return UUID(MSB or (value shl 32), LSB)
    }

    companion object {
        val TAG = "MainActivity"
        val PERMISSION_REQUEST_BACKGROUND_LOCATION = 0
        val PERMISSION_REQUEST_FINE_LOCATION = 1
    }

}