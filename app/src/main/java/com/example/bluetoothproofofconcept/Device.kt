package com.example.bluetoothproofofconcept

interface Device {
    val name: String
    val address: String
    val paired: Boolean
}