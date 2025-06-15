package com.example.cassette.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.data.Songs
import com.example.cassette.data.adapters.MainScreenAdapter
import com.example.cassette.data.databases.EchoDatabase
import com.example.cassette.databinding.FragmentPlaylistSongsBinding
import com.example.cassette.services.MediaPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import android.text.TextUtils
import java.util.Collections

class PlaylistSongsFragment : Fragment() {

    private var _binding: FragmentPlaylistSongsBinding? = null
    private val binding get() = _binding!!

    private var playlistSongsTitle: TextView? = null
    private var playlistSongsBackButton: ImageButton? = null
    private var playlistDetailRecyclerView: RecyclerView? = null
    private var noSongsInPlaylist: RelativeLayout? = null

    private var currentPlaylistID: Long = -1L
    private var currentPlaylistName: String? = null
    private var songsInPlaylist: ArrayList<Songs> = ArrayList() // Changed to non-nullable
    private var mainScreenAdapter: MainScreenAdapter? = null
    private var echoDatabase: EchoDatabase? = null
    private lateinit var myContext: Context

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        myContext = context
        echoDatabase = EchoDatabase(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaBrowser = MediaBrowserCompat(
            requireContext(),
            ComponentName(requireContext(), MediaPlaybackService::class.java),
            connectionCallbacks,
            null
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistSongsTitle = binding.playlistSongsTitle
        playlistSongsBackButton = binding.playlistSongsBackButton
        playlistDetailRecyclerView = binding.playlistDetailRecyclerView
        noSongsInPlaylist = binding.noSongsInPlaylist

        arguments?.let {
            currentPlaylistID = it.getLong("playlistID", -1L)
            currentPlaylistName = it.getString("playlistName", "Playlist")
        }

        playlistSongsTitle?.text = currentPlaylistName
        playlistSongsBackButton?.setOnClickListener { findNavController().popBackStack() }

        loadSongsForPlaylist()
    }

    override fun onStart() {
        super.onStart()
        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        }
    }

    override fun onStop() {
        super.onStop()
        mediaBrowser.disconnect()
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaController = MediaControllerCompat(requireContext(), mediaBrowser.sessionToken)
        }
        override fun onConnectionSuspended() { mediaController = null }
        override fun onConnectionFailed() { mediaController = null }
    }

    private fun loadSongsForPlaylist() {
        if (currentPlaylistID == -1L) {
            // Handle invalid playlist ID
            return
        }

        // Use a coroutine to fetch data in the background
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val partialSongs = echoDatabase?.getSongsFromPlaylist(currentPlaylistID)

            if (partialSongs.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    playlistDetailRecyclerView?.visibility = View.INVISIBLE
                    noSongsInPlaylist?.visibility = View.VISIBLE
                }
                return@launch
            }

            // Now, fetch full details for the partial songs
            val fullSongsList = fetchFullSongDetails(partialSongs)
            songsInPlaylist = fullSongsList // Store the full list

            // Update the UI on the main thread
            withContext(Dispatchers.Main) {
                setupRecyclerView()
            }
        }
    }

    // NEW: Function to fetch full details from MediaStore
    private fun fetchFullSongDetails(partialSongs: ArrayList<Songs>): ArrayList<Songs> {
        val fullSongList = ArrayList<Songs>()
        val songIds = partialSongs.map { it.songID.toString() }.toTypedArray()
        val selection = MediaStore.Audio.Media._ID + " IN (" + TextUtils.join(",", Collections.nCopies(songIds.size, "?")) + ")"

        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.ALBUM_ID
        )

        val cursor = myContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            songIds,
            null
        )

        cursor?.use {
            // Create a map for quick lookup
            val songMap = partialSongs.associateBy { it.songID }

            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                // Reconstruct the full Songs object
                val fullSong = Songs(
                    songID = id,
                    songTitle = it.getString(titleColumn),
                    artist = it.getString(artistColumn),
                    songData = it.getString(dataColumn),
                    dateAdded = it.getLong(dateColumn),
                    albumID = it.getLong(albumIdColumn)
                )
                fullSongList.add(fullSong)
            }
        }
        return fullSongList
    }

    private fun setupRecyclerView() {
        if (songsInPlaylist.isEmpty()) {
            playlistDetailRecyclerView?.visibility = View.INVISIBLE
            noSongsInPlaylist?.visibility = View.VISIBLE
            return
        }

        playlistDetailRecyclerView?.visibility = View.VISIBLE
        noSongsInPlaylist?.visibility = View.INVISIBLE

        mainScreenAdapter = MainScreenAdapter(songsInPlaylist, myContext) { song, position ->
            if (mediaBrowser.isConnected && mediaController != null) {
                val extras = Bundle().apply {
                    putParcelableArrayList("song_list", songsInPlaylist)
                    putInt("start_index", position)
                }
                mediaController!!.transportControls.sendCustomAction(
                    "com.example.cassette.services.ACTION_PLAY_FROM_LIST",
                    extras
                )
            } else {
                Toast.makeText(myContext, "Cannot play song. Media service not ready.", Toast.LENGTH_SHORT).show()
            }
        }
        playlistDetailRecyclerView?.layoutManager = LinearLayoutManager(myContext)
        playlistDetailRecyclerView?.itemAnimator = DefaultItemAnimator()
        playlistDetailRecyclerView?.adapter = mainScreenAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        echoDatabase?.close()
    }
}