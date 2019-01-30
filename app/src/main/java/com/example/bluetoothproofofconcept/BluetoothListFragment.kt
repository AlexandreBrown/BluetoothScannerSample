package com.example.bluetoothproofofconcept

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.content_bluetooth_list.*


class BluetoothListFragment : Fragment(), onBluetoothItemInteraction {

    private var listener: OnFragmentInteractionListener? = null

    private var selectedDevice: BluetoothDevice? = null

    private var bluetoothDevices: MutableList<BluetoothDevice> = mutableListOf()

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
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
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> //Do something if disconnected
                    Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Toast.makeText(requireContext(), "Searching for devices...", Toast.LENGTH_SHORT).show()
                    refresh.isEnabled = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> refresh.isEnabled = true
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
                            bluetoothDevices.add(device)
                        }
                        else{
                            DataManager.devices[DataManager.devices.indexOf(deviceWithSameAddress)].name = deviceName
                        }

                        (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onClick(deviceAddress: String) {

        selectedDevice = bluetoothDevices.first { it.address == deviceAddress }

        Toast.makeText(requireContext(), "Attempting to pair with ${selectedDevice?.name}", Toast.LENGTH_SHORT).show()

        if(selectedDevice?.bondState == BluetoothDevice.BOND_BONDED)
            Toast.makeText(requireContext(), "Devices already paired!", Toast.LENGTH_SHORT).show()
        else{
            if(selectedDevice?.createBond() == true){
                Toast.makeText(requireContext(), "Devices paired", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(requireContext(), "Failed pairing", Toast.LENGTH_SHORT).show()
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
