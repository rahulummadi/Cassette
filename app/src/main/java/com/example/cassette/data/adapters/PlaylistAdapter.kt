package com.example.cassette.data.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.data.Playlist
import java.util.ArrayList

class PlaylistAdapter(
    private var playlists: ArrayList<Playlist>, // Use var if list can change
    private val context: Context,
    private val itemClickListener: (playlist: Playlist, position: Int) -> Unit, // For clicking to view playlist songs
    private val renameClickListener: (playlist: Playlist, position: Int) -> Unit // For clicking rename button
) : RecyclerView.Adapter<PlaylistAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_playlist_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.playlistNameTextView?.text = playlist.playlistName
        holder.playlistSongsCountTextView?.text = "${playlist.songsCount} songs" // Display song count

        holder.playlistItemHolder?.setOnClickListener {
            itemClickListener.invoke(playlist, position)
        }

        holder.renamePlaylistButton?.setOnClickListener {
            renameClickListener.invoke(playlist, position)
        }
    }

    override fun getItemCount(): Int {
        return playlists.size
    }

    // Method to update the list of playlists (useful after creating/renaming)
    fun updatePlaylists(newPlaylists: ArrayList<Playlist>) {
        this.playlists = newPlaylists
        notifyDataSetChanged()
    }

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var playlistIcon: ImageView? = null
        var playlistNameTextView: TextView? = null
        var playlistSongsCountTextView: TextView? = null
        var renamePlaylistButton: ImageButton? = null
        var playlistItemHolder: RelativeLayout? = null

        init {
            playlistIcon = view.findViewById(R.id.playlistIcon)
            playlistNameTextView = view.findViewById(R.id.playlistNameTextView)
            playlistSongsCountTextView = view.findViewById(R.id.playlistSongsCountTextView)
            renamePlaylistButton = view.findViewById(R.id.renamePlaylistButton)
            playlistItemHolder = view.findViewById(R.id.playlist_item_holder)
        }
    }
}