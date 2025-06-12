package com.example.cassette.data.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.data.Songs
import java.util.ArrayList

class MainScreenAdapter(
    // MODIFIED: Added the itemClickListener lambda to the constructor
    private val songDetails: ArrayList<Songs>, // Changed to direct property
    private val mContext: Context,            // Changed to direct property
    private val itemClickListener: (song: Songs, position: Int) -> Unit // THIS IS THE NEW PARAMETER
) : RecyclerView.Adapter<MainScreenAdapter.MyViewHolder>() {


    override fun onBindViewHolder(holder: MyViewHolder, position: Int) { // Renamed p0, p1 for clarity
        val songObject = songDetails[position] // Access directly, no need for ?.get(p1)
        holder.trackTitle?.text = songObject.songTitle
        holder.trackArtist?.text = songObject.artist

        // NEW: Attach the click listener to the content holder
        holder.contentHolder?.setOnClickListener {
            itemClickListener.invoke(songObject, position) // Invoke the lambda with song data and position
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder { // Renamed p0, p1 for clarity
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_custom_mainscreen_adapter, parent, false)

        return MyViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        // Access directly, no need for null checks or cast
        return songDetails.size
    }

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var trackTitle: TextView? = null
        var trackArtist: TextView? = null
        var contentHolder: RelativeLayout? = null

        init {
            trackTitle = view.findViewById<TextView>(R.id.trackTitle)
            trackArtist = view.findViewById<TextView>(R.id.trackArtist)
            contentHolder = view.findViewById<RelativeLayout>(R.id.contentRow)
        }
    }
}