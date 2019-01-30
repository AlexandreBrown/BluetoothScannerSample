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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.content_bluetooth_list.*


class BluetoothListFragment : Fragment() {
    private var listener: OnFragmentInteractionListener? = null

    private val filter by lazy {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED == action) {
                Log.i("bluetooth", "discovery started")
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                Log.i("bluetooth", "discovery finished")

            }

            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                DataManager.devices.add(BluetoothDeviceModel(device.name ?: device.address))

                (items.adapter as BluetoothRecyclerAdapter).notifyDataSetChanged()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireContext().registerReceiver(bluetoothReceiver, filter)

        refresh.setOnClickListener {
            Log.d("bluetooth","reached fragment")
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

        items.adapter = BluetoothRecyclerAdapter(this.requireContext(), DataManager.devices)
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
