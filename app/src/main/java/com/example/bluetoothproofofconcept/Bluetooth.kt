package com.example.bluetoothproofofconcept

object Bluetooth {

   internal fun getDiscoverableDevices(): List<BluetoothDevice> {
       return listOf<BluetoothDevice>(BluetoothDevice(0,"Test1"),
           BluetoothDevice(1,"Test2"),
           BluetoothDevice(2,"Test3"),
           BluetoothDevice(3,"Test4"),
           BluetoothDevice(4,"Test5")
           )
   }
}