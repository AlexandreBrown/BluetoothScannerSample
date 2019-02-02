package com.example.bluetoothproofofconcept

import android.Manifest
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.content_bluetooth_list.*
import java.io.IOException
import java.util.*


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val enableBluetoothRequestCode = 123

    private var socket: BluetoothSocket? = null

    private var drone: BluetoothDevice? = null

    private val droneBluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

   

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_UUID)
        filter
    }

    private val debugTag = "BluetoothLog"

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = getBluetoothDeviceFromIntent(intent)
                    Log.d(debugTag,"Connected to ${device.name}, Drone is null ? : ${drone == null}")

                    Toast.makeText(requireContext(), "Connected to ${device?.name}", Toast.LENGTH_SHORT).show()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getBluetoothDeviceFromIntent(intent)

                    Log.d(debugTag,"Disconnected from ${device.name}, Drone is null ? : ${drone == null}")

                    Toast.makeText(requireContext(), "Disconnected from ${device?.name}", Toast.LENGTH_SHORT).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(debugTag,"DISCOVERY_STARTED")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(debugTag,"DISCOVERY_FINISHED")

                    enableAutoConnect()
                }

                BluetoothDevice.ACTION_FOUND ->{

                    val device = getBluetoothDeviceFromIntent(intent)

                    if(isValidDevice(device)){
                        addDeviceToList(device)
                    }
                }
            }
        }
    }

    private fun isValidDevice(device: BluetoothDevice) = device.name != null && device.address != null

    private fun uuidsContainDroneUUID(uuids: Array<ParcelUuid>?): Boolean {
        if(uuids != null){
            for (uuid in uuids) {
                if (isDroneUUID(uuid)) {
                    Log.d(debugTag,"Drone UUID Found ($uuid)")
                    return true
                }
            }
        }

        return false
    }

    private fun droneWasFound(): Boolean{
        val droneWasFound = drone != null
        if(droneWasFound){
            Log.d(debugTag,"Drone was already found, Drone is null ? : ${drone == null}, Drone name : ${drone?.name}")
        }
        return droneWasFound
    }

    private fun isDroneUUID(uuid: ParcelUuid) =
        uuid.toString().toLowerCase() == droneBluetoothUUID.toString().toLowerCase()

    private fun addDeviceToList(device: BluetoothDevice) {
        if(!DataManager.devices.contains(BluetoothDeviceModel(device.name,device.address)))
        {
            DataManager.devices.add(BluetoothDeviceModel(device.name, device.address))

            bluetoothDevices.add(device)

            (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
        }
    }

    private fun getBluetoothDeviceFromIntent(intent: Intent) =
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

    override fun onClick(deviceAddress: String) {
        drone = bluetoothDevices.first { it.address == deviceAddress }

        interactWithDrone()
    }

    private fun interactWithDrone(){
        cancelDiscoveryIfAlreadyInProgress()

        if(droneIsNotPaired()) {
            Log.d(debugTag,"${drone?.name} was not already paired")

            tryToPairAndConnectDrone()
        }else{
            Log.d(debugTag,"${drone?.name} was already paired")

            connectToDrone()
        }
    }

    private fun droneIsNotPaired() = drone?.bondState == BluetoothDevice.BOND_NONE

    private fun tryToPairAndConnectDrone(){
        kotlin.run {
            if (pairDrone()) {
                Log.d(debugTag,"${drone?.name} paired successfully!")
                Toast.makeText(requireContext(), "Drone ${drone?.name} paired successfully!", Toast.LENGTH_SHORT).show()

                while(bluetoothAdapter?.bondedDevices?.any { it.address == drone?.address } == false)
                {
                    Thread.sleep(200)
                }

                connectToDrone()
            } else {
                Log.d(debugTag,"${drone?.name} failed pairing!")
            }
        }
    }

    private fun pairDrone() = drone?.createBond() == true

    private fun connectToDrone(){
        try {
            Log.d(debugTag,"Creating socket...")

            if(socket?.isConnected == true){
                socket?.close()
            }

            val method = drone!!::class.java.getMethod("createRfcommSocket", Int::class.java)
            socket = method.invoke(drone, 1) as BluetoothSocket?

            Log.d(debugTag,"Connecting...")
            Toast.makeText(requireContext(), "Connecting to ${drone?.name}...", Toast.LENGTH_SHORT).show()

            socket?.connect()
        } catch (exception: IOException) {
            Toast.makeText(requireContext(), "Connection failed", Toast.LENGTH_SHORT).show()
            Log.d(debugTag,"Connection failed, ${exception.message}")
        }
        finally {
            enableAutoConnect()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        requireContext().registerReceiver(bluetoothReceiver, filter)

        searchAndConnect.setOnClickListener {

            drone = null

            checkIfAccessCoarseLocationIsGranted()

            checkIfBluetoothIsOn()

            disableAutoConnect()

            checkIfDroneInPairedDevices()

            if(!droneWasFound())
                launchDiscovery()

            onButtonPressed()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter
    }


    // scan for devices
    scanner.startScan(new ScanCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids()
        }
    }

    private fun checkIfDroneInPairedDevices(){
        Log.d(debugTag, "Checking if drone is already paired")
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            Log.d(debugTag, "${device.name} is a paired device, checking if it's a drone...")
            val uuids = device.uuids
            Log.d(debugTag,"uuids of ${device.name} : null ? ${uuids == null}")
            if(!droneWasFound() && uuidsContainDroneUUID(uuids)){
                Log.d(debugTag, "Drone ${device.name} found in paired devices!")

                drone = device

                connectToDrone()
            }
        }
    }

    private fun launchDiscovery() {
        cancelDiscoveryIfAlreadyInProgress()
        Log.d(debugTag, "Couldn't find drone in paired devices, starting discovery...")

        Toast.makeText(requireContext(), "Couldn't find drone in paired devices, searching for devices...", Toast.LENGTH_SHORT).show()

        startDiscovery()
    }

    private fun startDiscovery() {
        bluetoothAdapter?.startDiscovery()
    }

    private fun cancelDiscoveryIfAlreadyInProgress() {
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(debugTag, "Cancelling discovery...")
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    private fun enableAutoConnect() {
        searchAndConnect.isEnabled = true

        loadingSpinner.visibility = View.GONE
    }

    private fun disableAutoConnect() {
        DataManager.devices.clear()

        bluetoothDevices.clear()

        loadingSpinner.visibility = View.VISIBLE

        searchAndConnect.isEnabled = false
    }

    private fun checkIfBluetoothIsOn() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, enableBluetoothRequestCode)
        }
    }

    private fun checkIfAccessCoarseLocationIsGranted() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 123)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == enableBluetoothRequestCode) {
            if (resultCode == RESULT_OK) {
                launchDiscovery()
            }else{
                enableAutoConnect()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(bluetoothReceiver, filter)
        items.adapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        closeSocket()
        cancelDiscoveryIfAlreadyInProgress()
        requireContext().unregisterReceiver(bluetoothReceiver)
    }

    private fun closeSocket() {
        socket?.close()
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.content_bluetooth_list, container, false)
    }

    private fun onButtonPressed() {
        listener?.onUpdateBluetoothDevices()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    interface OnFragmentInteractionListener {
        fun onUpdateBluetoothDevices()
    }


}