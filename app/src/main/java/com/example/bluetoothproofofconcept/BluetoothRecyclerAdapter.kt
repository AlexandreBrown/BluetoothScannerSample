package com.example.bluetoothproofofconcept

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.item_bluetooth_list.view.*
import java.util.*

interface onBluetoothItemInteraction{
    fun onClick(deviceAddress: String)
}

class BluetoothRecyclerAdapter(private val context: Context, private val devices: List<Device>) : RecyclerView.Adapter<BluetoothRecyclerAdapter.ViewHolder>() {

    private lateinit var listener: onBluetoothItemInteraction
    private val layoutInflater = LayoutInflater.from(context)

    fun setListener(listener: onBluetoothItemInteraction){
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, p1: Int): ViewHolder {

        val itemView = layoutInflater.inflate(R.layout.item_bluetooth_list, parent, false)

        val holder = ViewHolder(itemView)

        holder.itemView.setOnClickListener {
            listener.onClick(itemView.deviceAddress.text.toString())
        }

        return holder
    }

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.name.text = devices[position].name
        holder.address.text = devices[position].address
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val name: TextView = itemView.deviceName
        val address: TextView = itemView.deviceAddress
    }
}