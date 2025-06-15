package com.example.cassette.ui.home

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.databinding.FragmentHomeBinding
import com.example.cassette.data.Songs
import com.example.cassette.data.adapters.MainScreenAdapter
import com.bumptech.glide.Glide // Single import for Glide
import com.example.cassette.ui.SongPlayingFragment // Single import for SongPlayingFragment
import com.example.cassette.ui.dialogs.AddSongToPlaylistDialog // Single import for AddSongToPlaylistDialog
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    // UI elements linked via binding.
    var nowPlayingButtonBar: ConstraintLayout? = null
    var playPauseButton: ImageButton? = null
    var songTitle: TextView? = null
    var noSongs: RelativeLayout? = null
    var visibleLayout: RelativeLayout? = null
    var recyclerView: RecyclerView? = null

    // Variables for the NEW DETAILED PLAYER BAR ELEMENTS
    var nowPlayingSeekBar: SeekBar? = null
    var nowPlayingStartTime: TextView? = null
    var nowPlayingEndTime: TextView? = null
    var nowPlayingCoverArt: ImageView? = null
    var nowPlayingArtistBar: TextView? = null
    var nowPlayingShuffleButton: ImageButton? = null
    var nowPlayingPreviousButton: ImageButton? = null
    var nowPlayingNextButton: ImageButton? = null
    var nowPlayingFavoriteButton: ImageButton? = null
    var nowPlayingLoopButton: ImageButton? = null

    // Activity context
    lateinit var myActivity: Activity

    // Adapter and song list
    var _mainScreenAdapter: MainScreenAdapter? = null
    var getSongsList: ArrayList<Songs>? = null

    // View Binding property
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Add a flag to indicate if a song is currently being played, so the bar only shows if relevant.
    private var isPlayingAnything: Boolean = false

    // ActivityResultLauncher for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val audioPermissionGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (audioPermissionGranted) {
            loadSongsIntoUI()
        } else {
            Toast.makeText(
                myActivity,
                "Permission to read audio files denied. Cannot load songs.",
                Toast.LENGTH_LONG
            ).show()
            visibleLayout?.visibility = View.INVISIBLE
            noSongs?.visibility = View.VISIBLE
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        // Link all UI elements using binding
        visibleLayout = binding.visibleLayout
        noSongs = binding.noSongs
        nowPlayingButtonBar = binding.hiddenBarMainScreen

        // Corrected assignments for song title and play/pause button to match new XML IDs
        songTitle = binding.nowPlayingSongTitleBar
        playPauseButton = binding.nowPlayingPlayPauseButton

        // Link the NEW UI ELEMENTS from the detailed player bar layout
        nowPlayingSeekBar = binding.nowPlayingSeekBar
        nowPlayingStartTime = binding.nowPlayingStartTime
        nowPlayingEndTime = binding.nowPlayingEndTime
        nowPlayingCoverArt = binding.nowPlayingCoverArt
        nowPlayingArtistBar = binding.nowPlayingSongArtistBar
        nowPlayingShuffleButton = binding.nowPlayingShuffleButton
        nowPlayingPreviousButton = binding.nowPlayingPreviousButton
        nowPlayingNextButton = binding.nowPlayingNextButton
        nowPlayingFavoriteButton = binding.nowPlayingFavoriteButton

        recyclerView = binding.contentMain // Correctly using contentMain

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Request permissions when the view is created
        checkAndRequestPermissions()

        // Set up click listener for play/pause button on mini-player
        playPauseButton?.setOnClickListener {
            // Check if a player instance exists before trying to control it
            if (SongPlayingFragment.Statified.mPlayer != null) {
                if (SongPlayingFragment.Statified.mPlayer?.isPlaying == true) {
                    SongPlayingFragment.Statified.pauseSong()
                } else {
                    SongPlayingFragment.Statified.resumeSong()
                }
                updatePlayPauseButtonIcon() // Update mini-player's icon based on global state
            } else {
                Toast.makeText(myActivity, "No song selected to play.", Toast.LENGTH_SHORT).show()
            }
        }

        // Click listener for the Now Playing bar to open full player
        nowPlayingButtonBar?.setOnClickListener {
            // Check global current song
            if (SongPlayingFragment.Statified.currentSong != null) {
                val bundle = Bundle().apply {
                    putParcelable("songData", SongPlayingFragment.Statified.currentSong)
                    putInt("songPosition", SongPlayingFragment.Statified.currentPosition)
                    SongPlayingFragment.Statified.songList?.let { list ->
                        putParcelableArray("songList", list.toTypedArray())
                    }
                }
                findNavController().navigate(R.id.navigation_song_playing, bundle)
            } else {
                Toast.makeText(myActivity, "No song currently playing.", Toast.LENGTH_SHORT).show()
            }
        }

        // Add click listeners for Next, Previous, Shuffle buttons in mini-player
        nowPlayingNextButton?.setOnClickListener {
            if (SongPlayingFragment.Statified.songList != null && SongPlayingFragment.Statified.songList!!.isNotEmpty()) {
                SongPlayingFragment.Statified.playNextSong()
                updateMiniPlayerUI() // Update mini-player after playing next
            } else {
                Toast.makeText(myActivity, "No songs in list.", Toast.LENGTH_SHORT).show()
            }
        }

        nowPlayingPreviousButton?.setOnClickListener {
            if (SongPlayingFragment.Statified.songList != null && SongPlayingFragment.Statified.songList!!.isNotEmpty()) {
                SongPlayingFragment.Statified.playPreviousSong()
                updateMiniPlayerUI() // Update mini-player after playing previous
            } else {
                Toast.makeText(myActivity, "No songs in list.", Toast.LENGTH_SHORT).show()
            }
        }

        nowPlayingShuffleButton?.setOnClickListener {
            SongPlayingFragment.Statified.toggleShuffle()
            updateMiniPlayerUI() // Update mini-player for shuffle icon
        }

        nowPlayingFavoriteButton?.setOnClickListener {
            // Launch the AddSongToPlaylistDialog (already implemented this)
            SongPlayingFragment.Statified.currentSong?.let { song ->
                val dialog = AddSongToPlaylistDialog.newInstance(song)
                dialog.show(childFragmentManager, "AddSongToPlaylistDialog")
            } ?: run {
                Toast.makeText(myActivity, "No song currently playing to add.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    myActivity,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    myActivity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(
                myActivity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            loadSongsIntoUI()
        }
    }



    private fun loadSongsIntoUI() {
        getSongsList = getSongsFromPhone()
        val prefs = activity?.getSharedPreferences("action_sort", Context.MODE_PRIVATE)
        val action_sort_ascending = prefs?.getString("action_sort_ascending", "true")
        val action_sort_recent = prefs?.getString("action_sort_recent", "false")

        if (getSongsList == null || getSongsList!!.isEmpty()) {
            visibleLayout?.visibility = View.INVISIBLE
            noSongs?.visibility = View.VISIBLE
        } else {
            visibleLayout?.visibility = View.VISIBLE
            noSongs?.visibility = View.INVISIBLE

            // Initialize adapter with a click listener
            _mainScreenAdapter = MainScreenAdapter(
                getSongsList as ArrayList<Songs>,
                myActivity as Context
            ) { song, position ->
                // When a song is clicked in the list, set global song data and start playing it
                SongPlayingFragment.Statified.songList = getSongsList // Set global song list
                SongPlayingFragment.Statified.currentPosition = position // Set global position
                SongPlayingFragment.Statified.currentSong = song // Set global current song
                SongPlayingFragment.Statified.myContext = myActivity // Ensure myContext is set on Statified from here
                SongPlayingFragment.Statified.playSong(myActivity, song) // Play song via global player

                // Update mini-player UI immediately after starting playback
                updateMiniPlayerUI()
            }
            val mLayoutManager = LinearLayoutManager(myActivity)
            recyclerView?.layoutManager = mLayoutManager
            recyclerView?.itemAnimator = DefaultItemAnimator()
            recyclerView?.adapter = _mainScreenAdapter

            // Apply sorting if preferences exist
            if (action_sort_ascending!!.equals("true", true)) {
                Collections.sort(
                    getSongsList,
                    Songs.CREATOR.Statified.nameComparator
                )
            } else if (action_sort_recent!!.equals("true", true)) {
                Collections.sort(
                    getSongsList,
                    Songs.CREATOR.Statified.dateComparator
                )
            }
        }
    }

    // --- Helper function to update mini-player UI state (reads from global Statified) ---
    private fun updateMiniPlayerUI() {
        val currentSong = SongPlayingFragment.Statified.currentSong
        val mPlayer = SongPlayingFragment.Statified.mPlayer
        val currentSongHelper = SongPlayingFragment.Statified.currentSongHelper

        if (currentSong != null && mPlayer != null) {
            nowPlayingButtonBar?.visibility = View.VISIBLE
            songTitle?.text = currentSong.songTitle
            nowPlayingArtistBar?.text = currentSong.artist

            // Update play/pause icon
            updatePlayPauseButtonIcon()

            // Load mini-player cover art
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                currentSong.albumID
            )
            Glide.with(this@HomeFragment)
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image)
                .error(R.drawable.now_playing_bar_eq_image)
                .into(nowPlayingCoverArt!!)

            // Update shuffle/loop icons
            updateShuffleLoopButtonIcons()

            // Update seek bar and times (no continuous seek bar movement on mini-player here, just initial text)
            nowPlayingStartTime?.text = SongPlayingFragment.Statified.formatDuration(mPlayer.currentPosition.toLong())
            nowPlayingEndTime?.text = SongPlayingFragment.Statified.formatDuration(mPlayer.duration.toLong())

        } else {
            nowPlayingButtonBar?.visibility = View.INVISIBLE
        }
    }

    // --- Helper function to update mini-player's play/pause icon ---
    private fun updatePlayPauseButtonIcon() {
        if (SongPlayingFragment.Statified.mPlayer?.isPlaying == true) {
            playPauseButton?.setBackgroundResource(R.drawable.pause_icon)
        } else {
            playPauseButton?.setBackgroundResource(R.drawable.play_icon)
        }
    }

    // --- Helper function to update mini-player's shuffle/loop icons ---
    private fun updateShuffleLoopButtonIcons() {
        if (SongPlayingFragment.Statified.currentSongHelper?.isShuffle == true) {
            nowPlayingShuffleButton?.setBackgroundResource(R.drawable.shuffle_icon_pressed)
        } else {
            nowPlayingShuffleButton?.setBackgroundResource(R.drawable.shuffle_white_icon)
        }

        if (SongPlayingFragment.Statified.currentSongHelper?.isLoop == true) {
            nowPlayingLoopButton?.setBackgroundResource(R.drawable.loop_icon_pressed)
        } else {
            nowPlayingLoopButton?.setBackgroundResource(R.drawable.loop_white_icon)
        }
    }


    // Helper function to format duration (can be accessed via SongPlayingFragment.Statified.formatDuration)
    private fun formatDuration(duration: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
        )
    }

    // --- Fragment Lifecycle ---
    override fun onStop() {
        super.onStop()
        // No local MediaPlayer to release here anymore
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // No local MediaPlayer to release here anymore
    }

    override fun onResume() {
        super.onResume()
        // Update mini-player UI to reflect current global playback state
        updateMiniPlayerUI()
        // Start mini-player's seek bar updates if global player is active and playing
        if (SongPlayingFragment.Statified.mPlayer != null && SongPlayingFragment.Statified.mPlayer?.isPlaying == true) {
            // Note: This Handler/Runnable is local to SongPlayingFragment, so we need to access its Statified version
            SongPlayingFragment.Statified.updateSeekBarHandler.postDelayed(SongPlayingFragment.Statified.updateSeekBarRunnable!!, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop mini-player's seek bar updates
       // SongPlayingFragment.Statified.updateSeekBarHandler.removeCallbacks(SongPlayingFragment.Statified.updateSeekBarRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        // No local MediaPlayer to release here anymore
    }

    // --- Existing Methods (from your original code) ---
    override fun onAttach(context: Context) {
        super.onAttach(context)
        myActivity = context as Activity
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (!this::myActivity.isInitialized) {
            myActivity = activity
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val switcher = item?.itemId
        if (switcher == R.id.action_sort_ascending) {
            val editorOne =
                myActivity?.getSharedPreferences("action_sort", Context.MODE_PRIVATE)?.edit()
            editorOne?.putString("action_sort_recent", "false")
            editorOne?.putString("action_sort_ascending", "true")
            editorOne?.apply()
            if (getSongsList != null) {
                Collections.sort(
                    getSongsList,
                    Songs.CREATOR.Statified.nameComparator
                )
            }
            _mainScreenAdapter?.notifyDataSetChanged()
            return false
        } else if (switcher == R.id.action_sort_recent) {
            val editorTwo = myActivity?.getSharedPreferences("action_sort", Context.MODE_PRIVATE)?.edit()
            editorTwo?.let {
                it.putString("action_sort_recent", "true")
                it.putString("action_sort_ascending", "false")
                it.apply()
            }
            if (getSongsList != null) {
                Collections.sort(
                    getSongsList,
                    Songs.CREATOR.Statified.dateComparator
                )
            }
            _mainScreenAdapter?.notifyDataSetChanged()
            return false
        }
        return super.onOptionsItemSelected(item)
    }

    fun getSongsFromPhone(): ArrayList<Songs> {
        val arrayList = ArrayList<Songs>()
        val contentResolver = myActivity.contentResolver
        val songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = contentResolver.query(songUri, projection, selection, null, null)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val albumId = it.getLong(albumIdColumn)
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                arrayList.add(
                    Songs(
                        id,
                        title,
                        artist,
                        contentUri.toString(),
                        dateAdded,
                        albumId
                    )
                )
            }
        }
        return arrayList
    }
}