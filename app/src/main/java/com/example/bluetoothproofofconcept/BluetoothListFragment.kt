package com.example.bluetoothproofofconcept

import android.Manifest
import android.bluetooth.*
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
import java.lang.Exception
import android.bluetooth.BluetoothAdapter
import android.os.ParcelUuid
import android.os.Parcelable


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var selectedDevice: BluetoothDevice? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private val bluetoothUuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var socket: BluetoothSocket? = null

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
    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    //Do something if connected
                    Log.d("test","Connected")

                    val device = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_DEVICE)


                    val uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.ACTION_UUID)

                    Log.d("test","uuid null ? : ${uuidExtra == null}")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> //Do something if connected
                    Log.d("test","Disconnected")
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("test","DISCOVERY_STARTED")
                    Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_SHORT).show()
                    refresh.isEnabled = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("test","DISCOVERY_FINISHED")
                    refresh.isEnabled = true
                    loadingSpinner.visibility = View.GONE
                }
                BluetoothDevice.ACTION_UUID ->{
                    Log.d("test","ACTION_UUID")
                }

                BluetoothDevice.ACTION_FOUND ->{
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    if(device.name != null && device.address != null){

                        DataManager.devices.add(BluetoothDeviceModel(device.name, device.address))

                        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()

                        if(device.name.toLowerCase().contains("mdbt_03_7398"))
                        {
                            Log.d("test","Found the device you were looking for : ${device.name}")

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
        Log.d("test","Attempting communication with ${selectedDevice?.name}")

        if(selectedDevice?.bondState == BluetoothDevice.BOND_NONE) {
            Log.d("test","${selectedDevice?.name} was not already paired")

            tryToPairSelectedDevice()
        }else{
            Log.d("test","${selectedDevice?.name} was already paired")

            connectSelectedPairedDevice()
        }
    }

    private fun tryToPairSelectedDevice(){
        if (selectedDevice?.createBond() == true) {
            Log.d("test","${selectedDevice?.name} paired successfully!")

            connectSelectedPairedDevice()
        } else {
            Log.d("test","${selectedDevice?.name} failed pairing!")
        }
    }

    private fun connectSelectedPairedDevice(){
        try {
            val method = selectedDevice?.javaClass?.getMethod("getUuids")
            val parcelUuids: Array<ParcelUuid>  = method?.invoke(selectedDevice) as Array<ParcelUuid>
            val uuid = parcelUuids[0]
            Log.d("test","creating a socket with uuid : ${uuid.uuid}")

            socket = selectedDevice?.createRfcommSocketToServiceRecord(uuid.uuid)

            Log.d("test","Attempting connection to socket")

            socket?.connect()
        } catch (exception: IOException) {
            Log.d("test","Connection to socket failed, ${exception.message}")
//
//            if(selectedDevice != null){
//                try {
//                    Log.d("test","Attempting connection to socket using reflection")
//                    socket?.connect()
//                }catch (e: Exception){
//                    Log.d("test","Connection to socket failed using reflection, ${e.message}")
//                }
//            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        requireContext().registerReceiver(bluetoothReceiver, filter)

        refresh.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 123)
            }

            if (bluetoothAdapter?.isEnabled == false)
                bluetoothAdapter?.enable()

            DataManager.devices.clear()

            loadingSpinner.visibility = View.VISIBLE

            if(bluetoothAdapter?.isDiscovering == true)
                bluetoothAdapter?.cancelDiscovery()

            bluetoothAdapter?.startDiscovery()
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
        if(bluetoothAdapter?.isDiscovering == true)
            bluetoothAdapter?.cancelDiscovery()
        requireContext().unregisterReceiver(bluetoothReceiver)
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