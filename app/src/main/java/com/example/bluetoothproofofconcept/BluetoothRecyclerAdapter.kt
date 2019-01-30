package com.example.bluetoothproofofconcept

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.item_bluetooth_list.view.*

class BluetoothRecyclerAdapter(private val context: Context, private val devices: List<Device>) : RecyclerView.Adapter<BluetoothRecyclerAdapter.ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, p1: Int): ViewHolder {

        val itemView = layoutInflater.inflate(R.layout.item_bluetooth_list, parent, false)

        return ViewHolder(itemView)
    }

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.name.text = devices[position].name
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val name: TextView = itemView.deviceName
    }
}