package com.example.bluetoothproofofconcept

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity(), BluetoothListFragment.OnFragmentInteractionListener {
    override fun onUpdateBluetoothDevices() {
        Log.d("test","activity")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothListFragment = BluetoothListFragment()
        val transaction = supportFragmentManager.beginTransaction()

        transaction.replace(R.id.fragmentContainer, bluetoothListFragment)
        transaction.addToBackStack(null)
        transaction.commit()

    }


}
