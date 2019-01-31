package com.example.bluetoothproofofconcept

import android.Manifest
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
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
import me.aflak.bluetooth.Bluetooth
import me.aflak.bluetooth.BluetoothCallback
import me.aflak.bluetooth.DeviceCallback
import me.aflak.bluetooth.DiscoveryCallback
import java.util.*


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var bluetooth: Bluetooth? = null

    private val enableBtRequest = 1

    private val bluetoothCallback = object : BluetoothCallback {
        override fun onBluetoothTurningOn() {
            Log.d("test","onBluetoothTurningOn")
        }

        override fun onBluetoothOn() {
            Log.d("test","onBluetoothOn")
        }

        override fun onBluetoothTurningOff() {
            Log.d("test","onBluetoothTurningOff")
        }

        override fun onBluetoothOff() {
            Log.d("test","onBluetoothOff")
        }

        override fun onUserDeniedActivation() {
            Log.d("test","onUserDeniedActivation")
        }
    }

    private val discoveryCallback = object : DiscoveryCallback {
        override fun onDiscoveryStarted() {
            Log.d("test","onDiscoveryStarted")
            Toast.makeText(requireContext(),"Searching devices...", Toast.LENGTH_SHORT).show()

            DataManager.devices.clear()
            loadingSpinner.visibility = View.VISIBLE
            refresh.isEnabled = false
        }

        override fun onDiscoveryFinished() {
            Log.d("test","onDiscoveryFinished")

            loadingSpinner.visibility = View.GONE
            refresh.isEnabled = true
        }

        override fun onDeviceFound(device: BluetoothDevice) {
            Log.d("test","onDeviceFound : ${device.name} ${device.type}")

            if(device.name != null && device.address != null)
            {
                DataManager.devices.add(BluetoothDeviceModel(device.name,device.address))

                (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()

                if(device.name.toLowerCase().contains("Xps".toLowerCase())){
                    bluetooth?.pair(device)
                    //bluetooth?.connectToDevice(device)
                }

            }
        }

        override fun onDevicePaired(device: BluetoothDevice) {
            Log.d("test","onDevicePaired")
            Toast.makeText(requireContext(),"${device.name} paired", Toast.LENGTH_SHORT).show()
        }

        override fun onDeviceUnpaired(device: BluetoothDevice) {
            Log.d("test","onDeviceUnpaired")
            Toast.makeText(requireContext(),"${device.name} unpaired", Toast.LENGTH_SHORT).show()
        }

        override fun onError(message: String?) {
            Log.d("test","onError $message")
        }
    }

    private val deviceCallback = object : DeviceCallback {
        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d("test","onDeviceConnected ${device.name}")
            Toast.makeText(requireContext(),"${device.name} connected", Toast.LENGTH_SHORT).show()
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, message: String) {
            Log.d("test","onDeviceDisconnected ${device.name}")
            Toast.makeText(requireContext(),"${device.name} disconnected", Toast.LENGTH_SHORT).show()
        }

        override fun onMessage(message: String) {
            Log.d("test","onMessage $message")
        }

        override fun onError(message: String) {
            Log.d("test","onError $message")
        }

        override fun onConnectError(device: BluetoothDevice, message: String) {
            Log.d("test","onConnectError ${device.name} $message")
        }
    }
    override fun onClick(deviceAddress: String) {

    }

    override fun onStart() {
        super.onStart()
        bluetooth = Bluetooth(requireContext())
        bluetooth?.setBluetoothCallback(bluetoothCallback)
        bluetooth?.setDiscoveryCallback(discoveryCallback)
        bluetooth?.setDeviceCallback(deviceCallback)
        bluetooth?.onStart()
        bluetooth?.enable()
    }

    override fun onPause() {
        super.onPause()
        bluetooth?.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refresh.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), enableBtRequest)
            }else {
                bluetooth?.startScanning()
            }

            onRefreshActivityCallback()
        }

        items.layoutManager = LinearLayoutManager(context)

        val adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
        adapter.setListener(this)

        items.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == enableBtRequest) {
            if (resultCode == RESULT_OK) {
                bluetooth?.startScanning()
            }else{
                loadingSpinner.visibility = View.GONE
                refresh.isEnabled = true
            }
        }
    }

//
//    override fun onDevicesFound(
//        deviceList: MutableList<Device>?,
//        connectionCallback: SmoothBluetooth.ConnectionCallback?
//    ) {
//        Log.d("test","onDevicesFound")
//
//        deviceList?.forEach {
//            if(it.name != null && it.address !== null)
//            {
//                DataManager.devices.add(BluetoothDeviceModel(if(it.isPaired) "(PAIRED) ${it.name}" else it.name , it.address))
////                if(it.name.toLowerCase().contains(("XPS").toLowerCase())){
////                    connectionCallback?.connectTo(it)
////                }
//            }
//        }
//
//        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
//    }
//
//    override fun onDiscoveryFinished() {
//        Log.d("test","onDiscoveryFinished")
//        loadingSpinner.visibility = View.GONE
//        refresh.isEnabled = true
//    }

    override fun onResume() {
        super.onResume()
        items.adapter?.notifyDataSetChanged()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.content_bluetooth_list, container, false)
    }

    private fun onRefreshActivityCallback() {
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
