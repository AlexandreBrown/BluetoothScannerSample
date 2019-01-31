package com.example.bluetoothproofofconcept

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
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
import android.system.Os.socket




class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var selectedDevice: BluetoothDevice? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private val BluetoothUuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private var socket: BluetoothSocket? = null

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter
    }
    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> //Do something if connected
                    Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show()
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_SHORT).show()
                    refresh.isEnabled = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    refresh.isEnabled = true
                    loadingSpinner.visibility = View.GONE
                }
                BluetoothDevice.ACTION_FOUND ->{
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    if(device.name != null && device.address != null){
                        val deviceWithSameAddress = DataManager.devices.find { it.address == device.address }
                        val deviceName: String = if(device.bondState == BluetoothDevice.BOND_BONDED)
                            "(PAIRED) ${device.name}"
                        else
                            device.name

                        if(deviceWithSameAddress == null){
                            DataManager.devices.add(BluetoothDeviceModel(deviceName, device.address))
                            bluetoothDevices.add(device) // very inefficient, only for proof of concept
                        }
                        else{
                            DataManager.devices[DataManager.devices.indexOf(deviceWithSameAddress)].name = deviceName
                        }

                        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()

                        if(device.name.toLowerCase().contains("s8"))
                        {
                            selectedDevice = device
                            pairAndConnectSelectedDevice()
                        }

                    }
                }
            }
        }
    }

    override fun onClick(deviceAddress: String) {
        selectedDevice = bluetoothDevices.first { it.address == deviceAddress }

        pairAndConnectSelectedDevice()
    }

    private fun pairAndConnectSelectedDevice(){
        Toast.makeText(requireContext(), "Attempting communication with ${selectedDevice?.name}", Toast.LENGTH_SHORT).show()

        if(selectedDevice?.bondState == BluetoothDevice.BOND_NONE) {
            tryToPairSelectedDevice()
        }else{
            connectSelectedPairedDevice()
        }
    }

    private fun tryToPairSelectedDevice(){
        if (selectedDevice?.createBond() == true) {
            Toast.makeText(requireContext(), "Devices successfully paired", Toast.LENGTH_SHORT).show()
            connectSelectedPairedDevice()
        } else {
            Toast.makeText(requireContext(), "Devices failed pairing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectSelectedPairedDevice(){
        try {
            Toast.makeText(requireContext(), "Connecting to ${selectedDevice?.name}", Toast.LENGTH_SHORT).show()

//            socket = selectedDevice?.javaClass?.getMethod("createRfcommSocket",
//                Int::class.javaPrimitiveType
//            )?.invoke(selectedDevice, 1) as BluetoothSocket


            socket = selectedDevice?.createInsecureRfcommSocketToServiceRecord(BluetoothUuid)
        } catch (e1: IOException) {
            Log.d(TAG, "socket not created")
            Toast.makeText(requireContext(), "socket not created", Toast.LENGTH_SHORT).show()
            e1.printStackTrace()
        }

        try {
            if(socket?.isConnected == true)
                socket?.close()

            socket?.connect()

        } catch (e: IOException) {
            try {
                socket?.close()
            } catch (e1: IOException) {
                e1.printStackTrace()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireContext().registerReceiver(bluetoothReceiver, filter)

        refresh.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 123)
            }
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            if (!bluetoothAdapter.isEnabled)
                bluetoothAdapter.enable()

            DataManager.devices.clear()

            loadingSpinner.visibility = View.VISIBLE

            bluetoothAdapter.startDiscovery()
            onButtonPressed()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(bluetoothReceiver, filter)
        items.adapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        socket?.close()
        requireContext().unregisterReceiver(bluetoothReceiver)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.content_bluetooth_list, container, false)
    }

    fun onButtonPressed() {
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
