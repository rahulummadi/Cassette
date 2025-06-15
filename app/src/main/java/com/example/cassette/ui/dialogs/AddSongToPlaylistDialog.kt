package com.example.cassette.ui.dialogs // Adjust package if you didn't create 'dialogs' folder

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.databinding.DialogAddSongToPlaylistBinding // This will be generated
import com.example.cassette.data.Playlist
import com.example.cassette.data.Songs
import com.example.cassette.data.adapters.PlaylistAdapter
import com.example.cassette.data.databases.EchoDatabase
import java.util.ArrayList

class AddSongToPlaylistDialog : DialogFragment() {

    private var _binding: DialogAddSongToPlaylistBinding? = null
    private val binding get() = _binding!!

    private var songToAdd: Songs? = null // The song passed to the dialog
    private var playlists: ArrayList<Playlist>? = null
    private var playlistAdapter: PlaylistAdapter? = null
    private var echoDatabase: EchoDatabase? = null
    private lateinit var myContext: Context

    override fun onAttach(context: Context) {
        super.onAttach(context)
        myContext = context
        echoDatabase = EchoDatabase(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddSongToPlaylistBinding.inflate(LayoutInflater.from(myContext))
        val view = binding.root

        // Retrieve the song data passed to this dialog
        arguments?.let {
            songToAdd = it.getParcelable("songToAdd")
        }

        // Setup RecyclerView for playlists
        loadPlaylists()

        // Setup click listener for "Create New Playlist"
        binding.dialogCreateNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        val builder = AlertDialog.Builder(myContext)
        builder.setView(view)
        return builder.create()
    }

    private fun loadPlaylists() {
        playlists = echoDatabase?.getPlaylists()
        playlists?.let {
            if (it.isEmpty()) {
                Toast.makeText(myContext, "No playlists found. Create one!", Toast.LENGTH_SHORT).show()
            }
            playlistAdapter = PlaylistAdapter(
                it,
                myContext,
                { playlist, position ->
                    // Handle playlist item click: Add song to this playlist
                    addSongToSelectedPlaylist(playlist)
                    dismiss() // Close dialog after selection
                }
            ) { playlist, position ->
                // Handle rename button click (optional, could be in PlayListFragment only)
                Toast.makeText(myContext, "Rename clicked for ${playlist.playlistName}", Toast.LENGTH_SHORT).show()
                // You could open a rename dialog here too if desired
            }
            binding.dialogPlaylistsRecyclerView.layoutManager = LinearLayoutManager(myContext)
            binding.dialogPlaylistsRecyclerView.adapter = playlistAdapter
        }
    }

    private fun addSongToSelectedPlaylist(playlist: Playlist) {
        songToAdd?.let { song ->
            val rowsAffected = echoDatabase?.addSongToPlaylist(playlist.playlistID, song)
            if (rowsAffected != -1L) {
                // CORRECTED STRING FORMATTING HERE
                Toast.makeText(myContext, "${song.songTitle} added to ${playlist.playlistName}", Toast.LENGTH_SHORT).show()
                // Optionally update playlist song count in UI if you display it
            } else {
                Toast.makeText(myContext, "Failed to add song to playlist.", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(myContext, "No song to add.", Toast.LENGTH_SHORT).show()
    }

    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(myContext)
        builder.setTitle("Create New Playlist")
        val input = EditText(myContext)
        input.hint = "Playlist Name"
        builder.setView(input)

        builder.setPositiveButton("Create") { dialog, _ ->
            val playlistName = input.text.toString().trim()
            if (playlistName.isNotEmpty()) {
                val newPlaylistId = echoDatabase?.createPlaylist(playlistName)
                if (newPlaylistId != -1L) {
                    Toast.makeText(myContext, "Playlist '$playlistName' created!", Toast.LENGTH_SHORT).show()
                    loadPlaylists() // Refresh the list in the dialog
                } else {
                    Toast.makeText(myContext, "Failed to create playlist.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(myContext, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        echoDatabase?.close() // Close database when dialog view is destroyed
    }

    companion object {
        private const val ARG_SONG_TO_ADD = "songToAdd"

        // THIS IS THE newInstance METHOD THAT WAS MISSING
        fun newInstance(song: Songs): AddSongToPlaylistDialog {
            val fragment = AddSongToPlaylistDialog()
            val args = Bundle()
            args.putParcelable(ARG_SONG_TO_ADD, song)
            fragment.arguments = args
            return fragment
        }
    }
}