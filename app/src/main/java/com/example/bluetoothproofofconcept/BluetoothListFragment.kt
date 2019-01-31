package com.example.bluetoothproofofconcept

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
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
import io.palaima.smoothbluetooth.Device
import io.palaima.smoothbluetooth.SmoothBluetooth
import kotlinx.android.synthetic.main.content_bluetooth_list.*
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.io.IOException
import java.util.*

class BluetoothListFragment : Fragment(), onBluetoothItemInteraction, SmoothBluetooth.Listener {

    private var listener: OnFragmentInteractionListener? = null

    private val ENABLE_BT_REQUEST = 1

    private val smoothBluetooth: SmoothBluetooth by lazy {
        val smoothBluetooth = SmoothBluetooth(requireContext())
        smoothBluetooth.setListener(this)
        smoothBluetooth
    }

    private var socket: BluetoothSocket? = null

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter
    }

    override fun onClick(deviceAddress: String) {

    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> //Do something if connected
                    Log.d("test","ACTION_ACL_CONNECTED")
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> //Do something if connected
                    Log.d("test","ACTION_ACL_DISCONNECTED")
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("test","ACTION_DISCOVERY_STARTED")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("test","ACTION_DISCOVERY_FINISHED")
                }
                BluetoothDevice.ACTION_FOUND ->{

                    Log.d("test","ACTION_FOUND")

                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    if(device.name != null && device.address != null){
                        if(device.name.toLowerCase().contains("xps")){
                            pairDevice(device)
                        }
                    }
                }
            }
        }
    }

    private fun pairDevice(device: BluetoothDevice){
        if(device.bondState == BluetoothDevice.BOND_NONE) {
            if (device.createBond()) {
                Log.d("test","Successfully paired")
                //connectDevice(device)
            }else{
                Log.d("test","Failed pairing")
            }
        }else{
            Log.d("test","${device.name} was already paired")
            //connectDevice(device)
        }
    }

    private fun connectDevice(device: BluetoothDevice){
        try {
            Log.d("test","Connecting to ${device.name}")

//            socket = selectedDevice?.javaClass?.getMethod("createRfcommSocket",
//                Int::class.javaPrimitiveType
//            )?.invoke(selectedDevice, 1) as BluetoothSocket


            socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))

        } catch (e1: IOException) {
            Log.d("test", "socket not created")
            e1.printStackTrace()
        }

        try {
            socket?.connect()
        } catch (e: IOException) {
            try {
                socket?.close()
            } catch (e1: IOException) {
                e1.printStackTrace()
            }
        }
    }

    private fun launchDiscovery(){
        DataManager.devices.clear()
        loadingSpinner.visibility = View.VISIBLE
        refresh.isEnabled = false

        smoothBluetooth.doDiscovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().registerReceiver(bluetoothReceiver, filter)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refresh.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), ENABLE_BT_REQUEST)
            }else {
                launchDiscovery()
            }

            onRefresh()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE_BT_REQUEST) {
            if (resultCode == RESULT_OK) {
                launchDiscovery()
            }else{
                loadingSpinner.visibility = View.GONE
                refresh.isEnabled = true
            }
        }
    }

    override fun onDevicesFound(
        deviceList: MutableList<Device>?,
        connectionCallback: SmoothBluetooth.ConnectionCallback?
    ) {
        Log.d("test","onDevicesFound")

        deviceList?.forEach {
            if(it.name != null && it.address !== null)
            {
                DataManager.devices.add(BluetoothDeviceModel(if(it.isPaired) "(PAIRED) ${it.name}" else it.name , it.address))
//                if(it.name.toLowerCase().contains(("XPS").toLowerCase())){
//                    connectionCallback?.connectTo(it)
//                }
            }
        }

        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
    }

    override fun onDiscoveryFinished() {
        Log.d("test","onDiscoveryFinished")
        loadingSpinner.visibility = View.GONE
        refresh.isEnabled = true
    }

    override fun onConnecting(device: Device?) {
        Log.d("test","onConnecting")
        Toast.makeText(requireContext(), "Connecting to ${device?.name}", Toast.LENGTH_SHORT).show()

    }

    override fun onDataReceived(data: Int) {
        Log.d("test","onDataReceived")
        Toast.makeText(requireContext(), "Data received!", Toast.LENGTH_SHORT).show()

    }

    override fun onBluetoothNotSupported() {
        Log.d("test","onBluetoothNotSupported")
        Toast.makeText(requireContext(), "Bluetooth not found", Toast.LENGTH_SHORT).show();
        requireActivity().finish()
    }

    override fun onBluetoothNotEnabled() {
        Log.d("test","onBluetoothNotEnabled")
        val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBluetooth, ENABLE_BT_REQUEST)
    }

    override fun onConnected(device: Device?) {
        Log.d("test","onConnected")
        Toast.makeText(requireContext(), "Connected to ${device?.name}", Toast.LENGTH_SHORT).show()

    }

    override fun onDiscoveryStarted() {
        Log.d("test","onDiscoveryStarted")
        Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_SHORT).show()

    }

    override fun onConnectionFailed(device: Device?) {
        Log.d("test","onConnectionFailed")
        Toast.makeText(requireContext(), "Failed to connect to ${device?.name}, device pairing : ${device?.isPaired}", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected() {
        Log.d("test","onDisconnected")
        Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show()

    }

    override fun onNoDevicesFound() {
        Log.d("test","onNoDevicesFound")
        Toast.makeText(requireContext(), "No device found!", Toast.LENGTH_SHORT).show()

    }

    override fun onResume() {
        super.onResume()
        items.adapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        smoothBluetooth.stop()
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.content_bluetooth_list, container, false)
    }

    private fun onRefresh() {
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
