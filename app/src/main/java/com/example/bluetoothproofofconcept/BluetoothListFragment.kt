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


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val enableBluetoothRequestCode = 123

    private var socket: BluetoothSocket? = null

    private var drone: BluetoothDevice? = null

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_UUID)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter
    }

    private val debugTag = "BluetoothLog"

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = getBluetoothDeviceFromIntent(intent)
                    Log.d(debugTag,"Connected to ${device.name}")

                    Toast.makeText(requireContext(), "Connected to ${device?.name}", Toast.LENGTH_LONG).show()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getBluetoothDeviceFromIntent(intent)

                    Log.d(debugTag,"Disconnected from ${device.name}")

                    Toast.makeText(requireContext(), "Disconnected from ${device?.name}", Toast.LENGTH_LONG).show()
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(debugTag,"DISCOVERY_STARTED")

                    disableAutoConnect()
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(debugTag,"DISCOVERY_FINISHED")

                    if(bluetoothDevices.size == 0){
                        showMessageInDialog("No bluetooth device were found, make sure your drone is powered on")
                    }

                    enableAutoConnect()
                }

                BluetoothDevice.ACTION_FOUND ->{

                    val device = getBluetoothDeviceFromIntent(intent)

                    if(isValidDevice(device)){
                        addDeviceToList(device)
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED ->{
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val device = getBluetoothDeviceFromIntent(intent)

                    Log.d(debugTag, "ACTION_BOND_STATE_CHANGED")
                    Log.d(debugTag, "State of ${device.name} : $state Previous state : $previousState")

                    if(state == BluetoothDevice.BOND_BONDED){
                        tryToConnectToDevice(device)
                    }
                }
            }
        }
    }

    private fun showMessageInDialog(message: String){
        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setMessage(message)
        dialogBuilder.setCancelable(true)

        dialogBuilder.setPositiveButton(
            "OK"
        ) { dialog, _ -> dialog.cancel() }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun getBluetoothDeviceFromIntent(intent: Intent) =
        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

    private fun isValidDevice(device: BluetoothDevice) = device.name != null && device.address != null

    private fun addDeviceToList(device: BluetoothDevice) {
        if(!DataManager.devices.contains(BluetoothDeviceModel(device.name,device.address)))
        {
            DataManager.devices.add(BluetoothDeviceModel(device.name, device.address))

            bluetoothDevices.add(device)

            (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
        }
    }

    private fun connectedToDrone(): Boolean = drone != null

    override fun onClick(deviceAddress: String) {

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        tryToPairConnectDevice(device)
    }

    private fun tryToPairConnectDevice(device: BluetoothDevice?){
        cancelDiscoveryIfAlreadyInProgress()

        if(device != null){
            if(deviceIsNotPaired(device)) {
                if (pairDevice(device)) {
                    Log.d(debugTag,"${device.name} paired successfully!")
                } else {
                    Log.d(debugTag,"${device.name} failed pairing!")
                }
            }else{
                tryToConnectToDevice(device)
            }
        }
    }

    private fun deviceIsNotPaired(device: BluetoothDevice?) =
        device?.bondState == BluetoothDevice.BOND_NONE

    private fun pairDevice(device: BluetoothDevice) = device.createBond()

    private fun tryToConnectToDevice(device: BluetoothDevice){
        try {
            closeSocketIfConnected()

            createSocket(device)

            Log.d(debugTag,"Trying to connect to ${device.name}...")

            socket?.connect()

            drone = device

            enableAutoConnect()
        } catch (exception: IOException) {
            Log.d(debugTag,"Connection to ${device.name} failed, ${exception.message}")
        }
    }

    private fun createSocket(device: BluetoothDevice) {
        Log.d(debugTag, "Creating socket...")

        val method = device::class.java.getMethod("createRfcommSocket", Int::class.java)
        socket = method.invoke(device, 1) as BluetoothSocket?
    }

    private fun closeSocketIfConnected() {
        if (socket?.isConnected == true) {
            socket?.close()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        requireContext().registerReceiver(bluetoothReceiver, filter)

        search.setOnClickListener {

            tryToAutoConnect()

            onButtonPressed()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter

        tryToAutoConnect()
    }

    private fun tryToAutoConnect() {
        drone = null

        checkIfAccessCoarseLocationIsGranted()

        checkIfBluetoothIsOn()

        checkIfDroneInPairedDevices()

        if(!connectedToDrone())
            launchDiscovery()
    }


    private fun checkIfDroneInPairedDevices(){
        Log.d(debugTag, "Checking if drone is in paired devices")

        bluetoothAdapter?.bondedDevices?.forEach { device ->
            Log.d(debugTag, "${device.name} is a paired device, checking if it's a drone...")
            if(!connectedToDrone()){
                tryToConnectToDevice(device)
            }
        }
    }

    private fun launchDiscovery() {
        Log.d(debugTag, "Couldn't find drone in paired devices, starting discovery...")

        cancelDiscoveryIfAlreadyInProgress()

        showMessageInDialog("No paired drone found, your drone needs to be paired in order to be connected automatically when the app starts. Please select your drone to pair it.")

        Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_LONG).show()

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
        search.isEnabled = true

        loadingSpinner.visibility = View.GONE
    }

    private fun disableAutoConnect() {
        DataManager.devices.clear()

        bluetoothDevices.clear()

        loadingSpinner.visibility = View.VISIBLE

        search.isEnabled = false
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