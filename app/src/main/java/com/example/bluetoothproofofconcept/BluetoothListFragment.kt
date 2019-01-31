package com.example.bluetoothproofofconcept

import android.Manifest
import android.app.Activity.RESULT_OK
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
import java.util.*


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private val enableBtRequest = 123

    private var selectedDevice: BluetoothDevice? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private val bluetoothUuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var socket: BluetoothSocket? = null

    private val autoConnectBluetoothDeviceName = "mdBT_3_0290"

    private val autoPairing = true

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter
    }

    private val debugTag = "BluetoothLog"

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(debugTag,"Connected")

                    Toast.makeText(requireContext(), "Connected to ${selectedDevice?.name}", Toast.LENGTH_SHORT).show()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(debugTag,"Disconnected")

                    Toast.makeText(requireContext(), "Disconnected from ${selectedDevice?.name}", Toast.LENGTH_SHORT).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(debugTag,"DISCOVERY_STARTED")

                    Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_SHORT).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(debugTag,"DISCOVERY_FINISHED")

                    showDiscoveryNotInProgress()
                }

                BluetoothDevice.ACTION_FOUND ->{

                    val device = getBluetoothDeviceFromIntent(intent)

                    if(device.name != null && device.address != null){

                        addDeviceToList(device)

                        if(autoPairing){
                            if(bluetoothDeviceMatchesCriterias(device))
                            {
                                Log.d(debugTag,"Found the device you were looking, device name : ${device.name}")

                                selectedDevice = device

                                pairAndConnectSelectedDevice()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bluetoothDeviceMatchesCriterias(device: BluetoothDevice) =
            device.name.toLowerCase().contains(autoConnectBluetoothDeviceName.toLowerCase())

    private fun addDeviceToList(device: BluetoothDevice) {
        DataManager.devices.add(BluetoothDeviceModel(device.name, device.address))

        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
    }

    private fun getBluetoothDeviceFromIntent(intent: Intent) =
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

    private fun showDiscoveryNotInProgress() {
        refresh.isEnabled = true

        loadingSpinner.visibility = View.GONE
    }

    override fun onClick(deviceAddress: String) {
        selectedDevice = bluetoothDevices.first { it.address == deviceAddress }

        pairAndConnectSelectedDevice()
    }

    private fun pairAndConnectSelectedDevice(){
        Log.d(debugTag,"Attempting communication with ${selectedDevice?.name}")

        if(bluetoothAdapter?.isDiscovering == true){
            Log.d(debugTag,"Cancelling discovery...")
            bluetoothAdapter?.cancelDiscovery()
        }

        if(selectedDeviceIsNotPaired()) {
            Log.d(debugTag,"${selectedDevice?.name} was not already paired")

            tryToPairSelectedDevice()
        }else{
            Log.d(debugTag,"${selectedDevice?.name} was already paired")

            connectSelectedPairedDevice()
        }
    }

    private fun selectedDeviceIsNotPaired() = selectedDevice?.bondState == BluetoothDevice.BOND_NONE

    private fun tryToPairSelectedDevice(){
        if (pairSelectedDevice()) {
            Log.d(debugTag,"${selectedDevice?.name} paired successfully!")

            connectSelectedPairedDevice()
        } else {
            Log.d(debugTag,"${selectedDevice?.name} failed pairing!")
        }
    }

    private fun pairSelectedDevice() = selectedDevice?.createBond() == true

    private fun connectSelectedPairedDevice(){
        try {
            Log.d(debugTag,"creating a socket with uuid : $bluetoothUuid")

            socket = selectedDevice?.createRfcommSocketToServiceRecord(bluetoothUuid)

            Log.d(debugTag,"Attempting connection to socket")

            socket?.connect()
        } catch (exception: IOException) {
            Log.d(debugTag,"Connection to socket failed, ${exception.message}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        requireContext().registerReceiver(bluetoothReceiver, filter)

        refresh.setOnClickListener {

            launchDiscovery()

            onButtonPressed()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter
    }

    private fun launchDiscovery() {
        checkIfAccessCoarseLocationIsGranted()

        checkIfBluetoothIsOn()

        showDiscoveryInProgress()

        cancelDiscoveryIfAlreadyInProgress()

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

    private fun showDiscoveryInProgress() {
        DataManager.devices.clear()

        loadingSpinner.visibility = View.VISIBLE

        refresh.isEnabled = false
    }

    private fun checkIfBluetoothIsOn() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, enableBtRequest)
        }
    }

    private fun checkIfAccessCoarseLocationIsGranted() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 123)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == enableBtRequest) {
            if (resultCode == RESULT_OK) {
                smoothBluetooth.tryConnection()
            }else{
                loadingSpinner.visibility = View.GONE
                refresh.isEnabled = true
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