package com.ktm.asiala.raceon3

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.*
import org.altbeacon.beaconreference.R
import java.nio.charset.StandardCharsets
import java.util.*


class BeaconReferenceApplication : Application() {
    lateinit var region: Region
    lateinit var mBluetoothManager: BluetoothManager
    lateinit var mBluetoothGatt: BluetoothGatt

    var RACEON3_SERVICE_UUID: UUID = UUID.fromString("71ced1ac-0000-44f5-9454-806ff70b3e02")
    var RACEON3_CHARAC_UUID: UUID = UUID.fromString("4116f8d2-9f66-4f58-a53d-fc7440e7c14e")
    var RACEON3_DESC_UUID: UUID = convertFromInteger(0x2902)

    var beaconState = "Unknown"
    var beaconInformation = "Unknown"
    var key = "NO KEY"

    lateinit var raceon3_characteristic: BluetoothGattCharacteristic
    lateinit var raceon3_descriptor: BluetoothGattDescriptor

    override fun onCreate() {
        super.onCreate()

        val beaconManager = BeaconManager.getInstanceForApplication(this)
        BeaconManager.setDebug(true)

        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon, you must specify the byte layout for that beacon's
        // advertisement with a line like below.  The example shows how to find a beacon with the
        // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
        // layout expression for other beacon types, do a web search for "setBeaconLayout"
        // including the quotes.
        //
        //beaconManager.getBeaconParsers().clear();
        //beaconManager.getBeaconParsers().add(new BeaconParser().
        //        setBeaconLayout("m:0-1=4c00,i:2-24v,p:24-24"));


        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon like Eddystone or iBeacon, you must specify the byte layout
        // for that beacon's advertisement with a line like below.
        //
        // If you don't care about AltBeacon, you can clear it from the defaults:
        beaconManager.getBeaconParsers().clear()

        // The example shows how to find iBeacon.
        beaconManager.getBeaconParsers().add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        // enabling debugging will send lots of verbose debug information from the library to Logcat
        // this is useful for troubleshooting problmes
        // BeaconManager.setDebug(true)


        // The BluetoothMedic code here, if included, will watch for problems with the bluetooth
        // stack and optionally:
        // - power cycle bluetooth to recover on bluetooth problems
        // - periodically do a proactive scan or transmission to verify the bluetooth stack is OK
        // BluetoothMedic.getInstance().enablePowerCycleOnFailures(this)
        // BluetoothMedic.getInstance().enablePeriodicTests(this, BluetoothMedic.SCAN_TEST + BluetoothMedic.TRANSMIT_TEST)

        // By default, the library will scan in the background every 5 minutes on Android 4-7,
        // which will be limited to scan jobs scheduled every ~15 minutes on Android 8+
        // If you want more frequent scanning (requires a foreground service on Android 8+),
        // configure that here.
        // If you want to continuously range beacons in the background more often than every 15 mintues,
        // you can use the library's built-in foreground service to unlock this behavior on Android
        // 8+.   the method below shows how you set that up.
        setupForegroundService()
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(0);
        beaconManager.setBackgroundScanPeriod(1100); //1100

        // Ranging callbacks will drop out if no beacons are detected
        // Monitoring callbacks will be delayed by up to 25 minutes on region exit
        // beaconManager.setIntentScanningStrategyEnabled(true)

        // The code below will start "monitoring" for beacons matching the region definition below
        // the region definition is a wildcard that matches all beacons regardless of identifiers.
        // if you only want to detect beacons with a specific UUID, change the id1 paremeter to
        // a UUID like Identifier.parse("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6")
        region = Region("radius-uuid", null, null, null)
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)
        // These two lines set up a Live Data observer so this Activity can get beacon data from the Application class
        val regionViewModel =
            BeaconManager.getInstanceForApplication(this).getRegionViewModel(region)
        // observer will be called each time the monitored regionState changes (inside vs. outside region)
        regionViewModel.regionState.observeForever(centralMonitoringObserver)
        // observer will be called each time a new list of beacons is ranged (typically ~1 second in the foreground)
        regionViewModel.rangedBeacons.observeForever(centralRangingObserver)
    }

    fun disconnectGatt() {
        super.onTerminate()
        raceon3_descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        mBluetoothGatt.writeDescriptor(raceon3_descriptor)

        beaconState = "Disconnecting..."
        beaconInformation = "Closing Connection!"

        mBluetoothGatt.disconnect()
        mBluetoothGatt.close()

        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancelAll()
    }

    fun readCharacteristic(){
        if(mBluetoothGatt != null){
            mBluetoothGatt.readCharacteristic(raceon3_characteristic)
        }
    }

    fun setupForegroundService() {
        val builder = Notification.Builder(this, "BeaconReferenceApp")
        builder.setSmallIcon(R.drawable.ic_launcher_background)
        builder.setContentTitle("Scanning for Beacons")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent);
        val channel = NotificationChannel(
            "beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.setDescription("My Notification Channel Description")
        val notificationManager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        BeaconManager.getInstanceForApplication(this)
            .enableForegroundServiceScanning(builder.build(), 456);
    }

    val centralMonitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.OUTSIDE) {
            Log.d(TAG, "outside beacon region: " + region)
        } else {
            Log.d(TAG, "inside beacon region: " + region)
            sendNotification("Beacon Reference Application", "A beacon is nearby!")
        }
    }

    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(MainActivity.TAG, "Ranged: ${beacons.count()} beacons")
        for (beacon: Beacon in beacons) {
            Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            //Check if the required beacon is found
            if (beacons.size != 0) {
                beacons.forEach { beacon ->
                    if (beacon.bluetoothName.equals("RaceOn")) {
                        Log.d(MainActivity.TAG, "Found the right RaceOn Beacon")

                        beaconInformation = "Found the motorcycle! " + beacon.bluetoothAddress
                        beaconState = "Connecting..."

                        //Stop searching for more beacons
                        val beaconManager = BeaconManager.getInstanceForApplication(this)
                        beaconManager.stopMonitoring(region);
                        beaconManager.stopRangingBeacons(region);

                        //Connect to the device
                        connectWithMotorcycle(beacon.bluetoothAddress);
                    }
                }
            }
        }


    }

    //Connect / Bond the smartphone with the motorcycle (over the mac address)
    fun connectWithMotorcycle(macAdress: String) {
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
                Log.d(MainActivity.TAG, "Connected")
                beaconState = "Connected! Bonded!"
                sendNotification("Connected!", "Successfully connected with RaceOn!")

                //Save the bluetooth gatt
                if (gatt != null) {
                    mBluetoothGatt = gatt
                    mBluetoothGatt.discoverServices()
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(MainActivity.TAG, "Disconnect")
                beaconState = "Disconnected!"
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(MainActivity.TAG, "Found services")

            raceon3_characteristic =
                mBluetoothGatt.getService(RACEON3_SERVICE_UUID)
                    .getCharacteristic(RACEON3_CHARAC_UUID)

            raceon3_descriptor = raceon3_characteristic.getDescriptor(RACEON3_DESC_UUID)
            if (raceon3_descriptor.value == null) {
                raceon3_descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                raceon3_descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            mBluetoothGatt.writeDescriptor(raceon3_descriptor)

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
                raceon3_characteristic.setValue(
                    "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDM6puC5oNcmnA+\n" +
                            "rxI5hjifCQzTjy3NVvLeNJyaAxNl3kaHQndzANwVcELdz20tTJYt7RRAcFu6jVrr\n" +
                            "FDiNtHJP24mAaAZDg4bWhtlRuscQNDzwUbnlocHVmV87GNRq+jb77SGwT32Vzo3I\n" +
                            "g1GeQZh339kZKlaUUoAV/xi65v93uHDmmqCsDSVJ6QF7uYf+viL1T92KU/l3bTlV\n" +
                            "iyOVLeynQOqE0HkRwenjuQcDBjp8r3uWHdznMqdf5Z/9q2zErx3CirmIV5m8vny3\n" +
                            "JbW+SrkD7lh7A4zCRwqbbRbYF82K1uj2cQhWUz11dzr2EfbJ52KWSqb7hem3zFDm\n" +
                            "++RXHdfNAgMBAAECggEAEnHPm6G6EzzHe6zwfAML16zN3cEWg1QfOkcMDYTXWyT9\n" +
                            "vjEKZWyfYsKfEi4YiqpJHksntoEmkI0msOA6Eu86FtwQ7WDvp2YQXgD3ULb6Mggx\n" +
                            "sAP7MqMzulE61Cvw+swY5OY8UQ1mpXRZKMJBN3h6C9g3R1+bOXCPnOtAQ5qFRjZH\n" +
                            "cm4d9ehvoVzZCPxfcgvjSv6UuqEXN0URjqcUWEe7L7+wiEoTpBVSqNYziclBmPw7\n" +
                            "VemIZ/K05YlZ9soBEq6yoThHuneKhkCRIEb5kHzqG/CS3ocE3rmkreR0Qis637fL\n" +
                            "1qyOl5/WbUz6GG/HOHxt14jvRcAlDq1ebi8uZ+1/QQKBgQD7SqNpVWMyg1oqVoLV\n" +
                            "pb1sP39+3BMjHPdh8sOnZf3/3QdNnC04K5zLIBpUqcby6Kfi7fw7pEpsaRyUffy/\n" +
                            "1GqiGbxs30D+I47GMF94Cdfe8jN+zng1fjen8KJ91YN3VViBDH8ehH5M0OSByYXj\n" +
                            "jOZwt7wfOqXW3+DnEunwsylkkQKBgQDQwYXuu+RspMYyrWBW2SPk49IH66sJdahz\n" +
                            "MaBBD87IJFKvLgv5VIFv6PKRMZdQ2tFhGiQ4TlvvGF0uYk3GjnXP5Zv2Ad4ErARM\n" +
                            "pOM8uuTfrH99wR6R/kxtfkKVmRpjiL5vnj48yYY3HSgJLjOlhMw/ExO5gBCv0ZvG\n" +
                            "Xt8FbbZtfQKBgQCGp5I1KWpEcRppwX3OWkfMr6H0Kp2enTD6rYmmNAMNjjURo3Sf\n" +
                            "us8EEanKYEeZdo4wDfKxSvIOcay87V34tSyGvF+5v1AmXottLBKcUjn437Q0aRMF\n" +
                            "JyNPvKR15WnTEkqgrD+Z7Ml5BB7OZVx3eNMq52nJGjYvlDwB1qLBNmAh4QKBgQCR\n" +
                            "cQM7HTU8bGCaFik02PwlEITYd90mLg86kqywJ69NyeDBpDc7cyDrM0Us23wtHQcb\n" +
                            "u/bYM9/haPwiwOKnH8H9Il/SueJRJven3oljWmLzY18/4jjGRoJBFuKVD6JPDop/\n" +
                            "gaSi/VTBOVMXclURUMBsgYIQj6UQmd0KDDcdtR6QkQKBgBae8QKaFG6aY2uLSVBj\n" +
                            "oKcGHy27P4Y6F4JPUDNvwqhefpp6RTYCEsQvbvKFWU6pLF7SWMXc7OTAcFQzYBnS\n" +
                            "LGOVlt757SYtpioVFUsfjf6DwBn8ZktVatZ0S+z3Qf4QEZ1eLo1avsbxzD/DeBsw\n" +
                            "XRbwOLi+mCgsjRC0+RFBjmcV"
                )
                mBluetoothGatt.writeCharacteristic(raceon3_characteristic)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "Writing characteristic!")
        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(TAG, "Reading characteristic!")
            if (characteristic != null) {
                key = ""
                for(value in characteristic.value){
                    key += " "+ value
                }
            }
        }
    }

    private fun sendNotification(title: String, text: String) {
        val builder = NotificationCompat.Builder(this, "beacon-ref-notification-id")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_background)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(resultPendingIntent)
        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }

    //Convert Integer to UUID
    fun convertFromInteger(i: Int): UUID {
        val MSB = 0x0000000000001000L
        val LSB = -0x7fffff7fa064cb05L
        val value = (i and (-0x1.toLong()).toInt()).toLong()
        return UUID(MSB or (value shl 32), LSB)
    }

    companion object {
        val TAG = "BeaconReference"
    }

}