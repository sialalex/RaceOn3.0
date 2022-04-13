package com.ktm.asiala.raceon3

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.*
import org.altbeacon.beaconreference.R
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.util.*


class BeaconReferenceApplication : Application() {
    lateinit var region: Region
    lateinit var mBluetoothManager: BluetoothManager
    lateinit var mBluetoothGatt: BluetoothGatt
    lateinit var mBluetoothAdapter: BluetoothAdapter
    lateinit var mBluetoothSocket : BluetoothSocket

    var beaconState = "Unknown"
    var beaconInformation = "Unknown"
    var key = "NO KEY"
    var data: String = "NO DATA"

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

    fun disconnectRaceOn3() {
        super.onTerminate()
        mBluetoothSocket.close()

        beaconState = "Disconnecting..."
        beaconInformation = "Closing Connection!"

        mBluetoothGatt.disconnect()
        mBluetoothGatt.close()

        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancelAll()
    }


    fun getKey(){
        mBluetoothSocket.outputStream.write("GetKey".toByteArray())

        val buffer = ByteArray(1024) //1 MB
        val a = mBluetoothSocket.inputStream
        val bytes = a.read(buffer)

        key = String(buffer, StandardCharsets.UTF_8)
    }

    /*fun getData(){
        mBluetoothSocket.outputStream.write("GetData".toByteArray())

        val buffer = ByteArray(70000)
        val buffer2 = ByteArray(70000)
        val a = mBluetoothSocket.inputStream
        val bytes = a.read(buffer)
        val bytes2 = a.read(buffer2)

        val data1 = String(buffer, StandardCharsets.UTF_8)
        Log.d(TAG, data1)
        val data2 = String(buffer2, StandardCharsets.UTF_8)
        Log.d(TAG, data2)

        val sb = StringBuilder()
        sb.append(data1.substring(0, 20) + "...").append("\r\n").append(data2.substring(0, 20) + "...")
        data = sb.toString()
    }*/

    fun getData(){
        mBluetoothSocket.outputStream.write("GetData".toByteArray())

        val inputStream = mBluetoothSocket.inputStream
        val reader = BufferedReader(inputStream.reader())
        val content = StringBuilder()
        try {
            var line = reader.readLine()
            while (line != null) {
                content.append(line)
                line = reader.readLine()
                Log.d(TAG, line)

                if(line == "----END----"){
                    Log.d(TAG, "Found end")
                    data = "Received Data!"
                    reader.close()
                    return
                }
            }
        } finally {
            reader.close()
        }
    }

    fun readData(){

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

    @RequiresApi(Build.VERSION_CODES.Q)
    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(MainActivity.TAG, "Ranged: ${beacons.count()} beacons")
        for (beacon: Beacon in beacons) {
            Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            //Check if the required beacon is found
            if (beacons.size != 0) {
                beacons.forEach { beacon ->
                    if (beacon.bluetoothName.equals("RaceOn3")) {
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
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectWithMotorcycle(macAdress: String) {
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAdress)
        device.connectGatt(this, false, mBluetoothGattCallback)
    }

    private val mBluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothAdapter.cancelDiscovery()
                if (gatt != null) {
                    mBluetoothSocket = gatt.device.createInsecureL2capChannel(0x0080)
                }
                mBluetoothSocket.connect()
                if(mBluetoothSocket.isConnected == true){
                    Log.d(TAG, mBluetoothSocket.isConnected.toString())
                    beaconState = "Connected!"
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                beaconState = "Disconnected!"
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