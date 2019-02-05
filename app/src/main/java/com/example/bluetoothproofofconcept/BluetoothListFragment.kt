package com.example.bluetoothproofofconcept

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.support.design.widget.Snackbar
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.*


class BluetoothListFragment : Fragment(), OnBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var discoveredBluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val enableBluetoothRequestCode = 123

    private var socket: BluetoothSocket? = null

    private var connectedDevice: BluetoothDevice? = null

    private var numberOfCheckedDevices = 0

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_UUID)
        filter
    }

    private val debugTag = "BluetoothLog"

    private val droneUUID = "00001101-0000-1000-8000-00805F9B34FB"

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(debugTag,"DISCOVERY_STARTED")

                    Toast.makeText(requireContext(),"Searching nearby drones...", Toast.LENGTH_LONG).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(debugTag,"DISCOVERY_FINISHED")

                    discoveredBluetoothDevices.forEach { device ->
                        Log.d(debugTag, "${device.name} is an UNPAIRED device, checking if it's a drone...")
                        device.fetchUuidsWithSdp()
                    }

                    fetchDronesFromPairedDevices()
                }
                BluetoothDevice.ACTION_UUID->{
                    Log.d(debugTag,"ACTION UUID")

                    val device = getBluetoothDeviceFromIntent(intent)

                    val UUIDs =intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)

                    UUIDs?.forEach {
                        Log.d(debugTag, "${device.name} : UUID : $it")
                        if(deviceHasValidAttributes(device) && UUIDMatchesDroneUUID(it) && !connectedToDrone()){
                            Log.d(debugTag,"${device?.name} is a drone adding it to the list of drones")

                            addDeviceToDrones(device)
                        }
                    }

                    numberOfCheckedDevices++

                    if(numberOfCheckedDevices == ((discoveredBluetoothDevices).size) + (bluetoothAdapter?.bondedDevices)?.size!!){
                            enableFindMyDrone()
                    }
                }
                BluetoothDevice.ACTION_FOUND ->{
                    Log.d(debugTag, "ACTION_FOUND")

                    val device = getBluetoothDeviceFromIntent(intent)

                    if(deviceHasValidAttributes(device) && !discoveredBluetoothDevices.contains(device))
                        discoveredBluetoothDevices.add(device)

                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED ->{
                    Log.d(debugTag, "ACTION_BOND_STATE_CHANGED")

                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val device = getBluetoothDeviceFromIntent(intent)

                    if(state == BluetoothDevice.BOND_BONDED)
                        doWithDevice(ConnectivityAction.CONNECT, device)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED ->{
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    when(state){
                        BluetoothAdapter.STATE_OFF ->{
                            Log.d(debugTag, "BLUETOOTH STATE_OFF")
                            doWithDevice(ConnectivityAction.DISCONNECT)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED ->{
                    Log.d(debugTag, "ACTION_ACL_DISCONNECTED")

                    val device = getBluetoothDeviceFromIntent(intent)

                    if(alreadyConnectedTo(device)){
                        doWithDevice(ConnectivityAction.DISCONNECT)
                    }
                }
            }
        }
    }

    private fun UUIDMatchesDroneUUID(it: Parcelable) = it.toString().toUpperCase() == droneUUID

    private fun doWithDevice(action: ConnectivityAction, device: BluetoothDevice? = null){
        try {
                when (action){
                    ConnectivityAction.PAIR -> {
                        Log.d(debugTag,"Trying to pair ${device?.name}...")

                        tryToPairDevice(device)
                    }
                    ConnectivityAction.CONNECT ->{

                        if(alreadyConnectedTo(device))
                            throw Exception("${device?.name} is already connected")

                        tryToConnectToDevice(device)

                        requireActivity().runOnUiThread {
                            Snackbar.make(requireActivity().findViewById(android.R.id.content), "Connected to ${connectedDevice?.name}", Snackbar.LENGTH_SHORT).show()
                        }

                        disableFindMyDrone()
                        enableDisconnectFromDrone()
                    }
                    ConnectivityAction.DISCONNECT ->{
                        val connectedDeviceName = connectedDevice?.name
                        Log.d(debugTag,"Trying to disconnect from $connectedDeviceName...")

                        tryToCloseSocket()

                        Log.d(debugTag,"Disconnected from $connectedDeviceName...")

                        requireActivity().runOnUiThread {
                            Snackbar.make(requireActivity().findViewById(android.R.id.content), "Disconnected from $connectedDeviceName", Snackbar.LENGTH_SHORT).show()
                        }

                        connectedDevice = null

                        disableDisconnectFromDrone()
                        enableFindMyDrone()
                    }
                }
            }catch(e: Exception){
                Log.d(debugTag,e.message)
            }
    }

    private fun alreadyConnectedTo(device: BluetoothDevice?) =
            socket != null && socket?.isConnected == true && connectedDevice?.address == device?.address

    private fun getBluetoothDeviceFromIntent(intent: Intent) =
        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

    private fun deviceHasValidAttributes(device: BluetoothDevice) = device.name != null && device.address != null

    private fun addDeviceToDrones(device: BluetoothDevice) {
        if(!DataManager.drones.contains(BluetoothDeviceModel(device.name,device.address, deviceIsPaired(device))))
        {
            DataManager.drones.add(BluetoothDeviceModel(device.name, device.address, deviceIsPaired(device)))

            (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
        }
    }

    private fun connectedToDrone(): Boolean = connectedDevice != null

    override fun onClick(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        doWithDevice(ConnectivityAction.CONNECT,device)
    }

    private fun pairDevice(device: BluetoothDevice) = device.createBond()

    private fun tryToPairDevice(device: BluetoothDevice?){
        cancelDiscoveryIfAlreadyInProgress()

        if(deviceIsPaired(device))
        {
            Log.d(debugTag,"${device?.name} is already paired")
            doWithDevice(ConnectivityAction.CONNECT,device)
        }else{
            if(device == null)
                throw Exception("Can't pair a null device")

            if (!pairDevice(device))
                throw Exception("Failed pairing with ${device.name}")
        }

    }

    private fun deviceIsPaired(device: BluetoothDevice?) =
        device?.bondState == BluetoothDevice.BOND_BONDED

    private fun tryToConnectToDevice(device: BluetoothDevice?){
        Log.d(debugTag,"Trying to connect to ${device?.name}...")

        tryToCloseSocketIfConnected()

        if(device == null)
            throw Exception("Can't connect to a null device")

        tryToCreateSocket(device)

        socket?.connect()

        Log.d(debugTag, "Connected to ${socket?.remoteDevice?.name}")

        connectedDevice = socket?.remoteDevice
    }

    private fun tryToCloseSocketIfConnected() {
        if (socket?.isConnected == true) {
            tryToCloseSocket()
        }
    }

    private fun tryToCreateSocket(device: BluetoothDevice) {
        Log.d(debugTag, "Creating socket...")

        socket = device.createRfcommSocketToServiceRecord(UUID.fromString(droneUUID))

        Log.d(debugTag, "Socket created!")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        requireContext().registerReceiver(bluetoothReceiver, filter)

        search.setOnClickListener {
            fetchDrones()

            onButtonPressed()
        }

        disconnectDevice.setOnClickListener {
            doWithDevice(ConnectivityAction.DISCONNECT)
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.drones)
        adapter.setListener(this)

        items.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        fetchDrones()
    }

    private fun fetchDrones() {
        GlobalScope.launch {
            disableFindMyDrone()

            numberOfCheckedDevices = 0

            connectedDevice = null

            checkIfAccessCoarseLocationIsGranted()

            checkIfBluetoothIsOn()

            launchBluetoothDiscovery()
        }
    }


    private fun fetchDronesFromPairedDevices(){
        Log.d(debugTag, "Checking for paired drones")

        cancelDiscoveryIfAlreadyInProgress()

        bluetoothAdapter?.bondedDevices?.forEach { device ->
            Log.d(debugTag, "${device.name} is a paired device, checking if it's a drone...")
            device.fetchUuidsWithSdp()
        }
    }

    private fun launchBluetoothDiscovery() {
        cancelDiscoveryIfAlreadyInProgress()

        discoveredBluetoothDevices.clear()

        items.adapter?.notifyDataSetChanged()

        startDiscovery()
    }

    private fun cancelDiscoveryIfAlreadyInProgress() {
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(debugTag, "Cancelling discovery...")
            bluetoothAdapter?.cancelDiscovery()
            enableFindMyDrone()
        }
    }

    private fun startDiscovery() {
        bluetoothAdapter?.startDiscovery()
    }

    private fun enableFindMyDrone() {
        requireActivity().runOnUiThread {
            loadingSpinner.visibility = View.INVISIBLE
            search.visibility = View.VISIBLE
        }
    }

    private fun disableFindMyDrone() {
        DataManager.drones.clear()
        discoveredBluetoothDevices.clear()

        requireActivity().runOnUiThread {
            loadingSpinner.visibility = View.VISIBLE
            search.visibility = View.INVISIBLE
        }
    }

    private fun enableDisconnectFromDrone(){
        if(connectedDevice != null)
        {
            requireActivity().runOnUiThread {
                disconnectDevice.visibility = View.VISIBLE
            }
        }
    }

    private fun disableDisconnectFromDrone(){
        requireActivity().runOnUiThread {
            disconnectDevice.visibility = View.INVISIBLE
        }
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
                launchBluetoothDiscovery()
            }else{
                enableFindMyDrone()
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
        cancelDiscoveryIfAlreadyInProgress()
        requireContext().unregisterReceiver(bluetoothReceiver)
    }

    override fun onStop() {
        super.onStop()
        doWithDevice(ConnectivityAction.DISCONNECT)
    }

    private fun tryToCloseSocket() {
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
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
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