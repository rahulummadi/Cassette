package com.example.cassette.ui.search

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cassette.data.Songs
import com.example.cassette.data.adapters.MainScreenAdapter
import com.example.cassette.databinding.FragmentSearchBinding
import com.example.cassette.services.MediaPlaybackService
import java.util.ArrayList

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private var searchResultsAdapter: MainScreenAdapter? = null
    private var allSongs: ArrayList<Songs> = ArrayList()

    // NEW: MediaBrowser for communicating with the MediaPlaybackService
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NEW: Initialize the MediaBrowser
        mediaBrowser = MediaBrowserCompat(
            requireContext(),
            ComponentName(requireContext(), MediaPlaybackService::class.java),
            connectionCallbacks,
            null // optional Bundle
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // NEW: Fetch the master song list directly from storage
        allSongs = getSongsFromStorage(requireContext())

        if (allSongs.isEmpty()) {
            binding.noSearchResultsText.text = "No songs available to search."
            binding.noSearchResultsText.visibility = View.VISIBLE
            binding.searchResultsRecyclerView.visibility = View.GONE
        } else {
            binding.noSearchResultsText.visibility = View.GONE
            binding.searchResultsRecyclerView.visibility = View.VISIBLE

            // REFACTORED: The click listener now sends a command to the service
            searchResultsAdapter = MainScreenAdapter(ArrayList(), requireContext()) { song, _ ->
                if (mediaBrowser.isConnected && mediaController != null) {
                    // Find the song's original position in the master list
                    val originalIndex = allSongs.indexOf(song)
                    if (originalIndex != -1) {
                        val extras = Bundle().apply {
                            putParcelableArrayList("song_list", allSongs)
                            putInt("start_index", originalIndex)
                        }
                        mediaController!!.transportControls.sendCustomAction(
                            "com.example.cassette.services.ACTION_PLAY_FROM_LIST",
                            extras
                        )
                    }
                } else {
                    Toast.makeText(requireContext(), "Cannot play song. Media service not ready.", Toast.LENGTH_SHORT).show()
                }
            }

            binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.searchResultsRecyclerView.adapter = searchResultsAdapter
            filterSongs(binding.searchEditText.text.toString()) // Initially populate with filter
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSongs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // NEW: Add onStart and onStop to connect/disconnect the MediaBrowser
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

    // NEW: Callbacks for the MediaBrowser connection
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaController = MediaControllerCompat(requireContext(), mediaBrowser.sessionToken)
        }
        override fun onConnectionSuspended() { mediaController = null }
        override fun onConnectionFailed() { mediaController = null }
    }

    // NEW: Function to fetch all audio files from the device's MediaStore
    private fun getSongsFromStorage(context: Context): ArrayList<Songs> {
        val songList = ArrayList<Songs>()
        val contentResolver = context.contentResolver
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val musicCursor = contentResolver.query(musicUri, null, null, null, null)

        if (musicCursor != null && musicCursor.moveToFirst()) {
            val titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val dataColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val dateColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

            do {
                val thisId = musicCursor.getLong(idColumn)
                val thisTitle = musicCursor.getString(titleColumn)
                val thisArtist = musicCursor.getString(artistColumn)
                val thisData = musicCursor.getString(dataColumn)
                val thisDate = musicCursor.getLong(dateColumn)
                val thisAlbumId = musicCursor.getLong(albumIdColumn)

                songList.add(Songs(thisId, thisTitle, thisArtist, thisData, thisDate, thisAlbumId))
            } while (musicCursor.moveToNext())
        }
        musicCursor?.close()
        return songList
    }

    private fun filterSongs(query: String) {
        if (allSongs.isEmpty()) return

        val filteredList = if (query.isBlank()) {
            allSongs // Show all songs if query is blank
        } else {
            allSongs.filter { song ->
                // Use ?. to safely call contains() on nullable strings.
                // Use ?: false to default to 'false' if the title or artist is null.
                val titleMatches = song.songTitle?.contains(query, true) ?: false
                val artistMatches = song.artist?.contains(query, true) ?: false
                titleMatches || artistMatches
            } as ArrayList<Songs>
        }

        searchResultsAdapter?.updateData(filteredList) // Assuming MainScreenAdapter has this method

        binding.noSearchResultsText.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}