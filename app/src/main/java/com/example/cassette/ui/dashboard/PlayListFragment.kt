package com.example.cassette.ui.dashboard

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController // ADD THIS IMPORT
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.cassette.R
import com.example.cassette.databinding.FragmentPlayListBinding
import com.example.cassette.data.Playlist
import com.example.cassette.data.adapters.PlaylistAdapter
import com.example.cassette.data.databases.EchoDatabase
import java.util.ArrayList

class PlayListFragment : Fragment() {

    // View Binding property
    private var _binding: FragmentPlayListBinding? = null
    private val binding get() = _binding!!

    // UI elements
    var playlistRecyclerView: RecyclerView? = null
    var createPlaylistFab: FloatingActionButton? = null

    // Data and Adapter
    var playlists: ArrayList<Playlist>? = null
    var playlistAdapter: PlaylistAdapter? = null
    var echoDatabase: EchoDatabase? = null
    var myContext: Context? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        myContext = context
        echoDatabase = EchoDatabase(context) // Initialize database
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPlayListBinding.inflate(inflater, container, false)
        val view = binding.root

        // Link UI elements from fragment_play_list.xml
        playlistRecyclerView = binding.playlistRecyclerView
        createPlaylistFab = binding.createPlaylistFab

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load and display playlists
        loadPlaylists()

        // Set up FAB click listener for creating new playlists
        createPlaylistFab?.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    // --- Playlist Management Logic ---

    private fun loadPlaylists() {
        playlists = echoDatabase?.getPlaylists() // Get playlists from DB
        playlists?.let {
            if (it.isEmpty()) {
                // Optionally show a message if no playlists exist
                Toast.makeText(myContext, "No playlists found. Create one!", Toast.LENGTH_SHORT).show()
            }
            // Initialize adapter with listeners for item click and rename button click
            playlistAdapter = PlaylistAdapter(
                it,
                myContext!!,
                { playlist, position ->
                    // --- HANDLE PLAYLIST ITEM CLICK: NAVIGATE TO PLAYLIST SONGS ---
                    val bundle = Bundle().apply {
                        putLong("playlistID", playlist.playlistID)
                        putString("playlistName", playlist.playlistName)
                    }
                    findNavController().navigate(R.id.navigation_playlist, bundle)
                    // --- END NAVIGATION LOGIC ---
                }
            ) { playlist, position ->
                // Handle rename button click for a playlist
                showRenamePlaylistDialog(playlist)
            }
            playlistRecyclerView?.layoutManager = LinearLayoutManager(myContext)
            playlistRecyclerView?.adapter = playlistAdapter
        }
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
                    loadPlaylists() // Refresh list
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

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val builder = AlertDialog.Builder(myContext)
        builder.setTitle("Rename Playlist")
        val input = EditText(myContext)
        input.setText(playlist.playlistName) // Pre-fill with current name
        builder.setView(input)

        builder.setPositiveButton("Rename") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != playlist.playlistName) {
                val rowsAffected = echoDatabase?.renamePlaylist(playlist.playlistID, newName)
                if (rowsAffected ?: 0 > 0) {
                    Toast.makeText(myContext, "Playlist renamed to '$newName'", Toast.LENGTH_SHORT).show()
                    loadPlaylists() // Refresh list
                } else {
                    Toast.makeText(myContext, "Failed to rename playlist.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(myContext, "New name cannot be empty or same as old name.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    // --- Lifecycle methods for cleanup ---
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear binding reference
        echoDatabase?.close() // Close database when fragment view is destroyed
    }

    override fun onResume() {
        super.onResume()
        // Refresh playlists when returning to this fragment (e.g., after creating one)
        loadPlaylists()
    }
}