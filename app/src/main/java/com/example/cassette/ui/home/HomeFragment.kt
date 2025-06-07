package com.example.cassette.ui.home

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView // Added for ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar // Added for SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout // Added for ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cassette.R
import com.example.cassette.databinding.FragmentHomeBinding
import com.example.cassette.data.Songs
import com.internshala.echo.adapters.MainScreenAdapter
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit // Added for time formatting
import com.bumptech.glide.Glide
import android.net.Uri
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    // UI elements linked via binding.
    // Ensure these types match the elements in your detailed XML layout
    var nowPlayingButtonBar: ConstraintLayout? = null // Corrected type to ConstraintLayout
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

    // Activity context
    lateinit var myActivity: Activity

    // Adapter and song list
    var _mainScreenAdapter: MainScreenAdapter? = null
    var getSongsList: ArrayList<Songs>? = null

    // View Binding property
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // MediaPlayer instance
    private var mPlayer: MediaPlayer? = null
    private var currentPlayingSong: Songs? = null
    private var currentSongPosition: Int = 0

    // Add a flag to indicate if a song is currently being played, so the bar only shows if relevant.
    private var isPlayingAnything: Boolean = false // ADD THIS LINE


    // Handler to update seek bar and time (for basic implementation)
    private var updateSeekBar = Runnable {
        // This will be implemented later when adding seek bar logic
    }


    // ActivityResultLauncher for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermissionGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

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
        songTitle = binding.nowPlayingSongTitleBar // Corrected ID
        playPauseButton = binding.nowPlayingPlayPauseButton // Corrected ID

        // Link the NEW UI ELEMENTS from the detailed player bar layout
        nowPlayingSeekBar = binding.nowPlayingSeekBar
        nowPlayingStartTime = binding.nowPlayingStartTime
        nowPlayingEndTime = binding.nowPlayingEndTime
        nowPlayingCoverArt = binding.nowPlayingCoverArt
        nowPlayingArtistBar = binding.nowPlayingSongArtistBar // Link song artist
        nowPlayingShuffleButton = binding.nowPlayingShuffleButton
        nowPlayingPreviousButton = binding.nowPlayingPreviousButton
        nowPlayingNextButton = binding.nowPlayingNextButton
        nowPlayingFavoriteButton = binding.nowPlayingFavoriteButton

        recyclerView = binding.contentMain1 // Corrected ID

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Request permissions when the view is created
        checkAndRequestPermissions()

        // Set up click listener for play/pause button
        playPauseButton?.setOnClickListener {
            if (mPlayer?.isPlaying == true) {
                pauseSong()
            } else {
                resumeSong()
            }
        }

        // --- MODIFY THIS CLICK LISTENER FOR THE NOW PLAYING BAR ---
        nowPlayingButtonBar?.setOnClickListener {
            // Only navigate if a song is actually playing or paused
            if (currentPlayingSong != null) {
                val bundle = Bundle().apply {
                    putParcelable("songData", currentPlayingSong)
                    putInt("songPosition", currentSongPosition)

                    // --- CHANGE THIS LINE TO PASS THE SONG LIST AS AN ARRAY ---
                    // Ensure getSongsList is not null before converting to array
                    getSongsList?.let { list ->
                        putParcelableArray("songList", list.toTypedArray()) // Convert ArrayList to Array
                    }
                    // --- END CHANGE ---
                }
                findNavController().navigate(R.id.navigation_song_playing, bundle)
            } else {
                Toast.makeText(myActivity, "No song currently playing.", Toast.LENGTH_SHORT).show()
            }
        }
        // --- END NEW CLICK LISTENER ---


        // You'll need to implement click listeners for shuffle, previous, next, favorite buttons here later.
        // Also, SeekBar change listener for user seeking.
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(
                myActivity,
                Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
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
                playSong(song, position)
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
                ) // Corrected access path
            } else if (action_sort_recent!!.equals("true", true)) {
                Collections.sort(
                    getSongsList,
                    Songs.CREATOR.Statified.dateComparator
                ) // Corrected access path
            }
        }
    }


    // --- Media Playback Functions ---
    private fun playSong(song: Songs, position: Int) {
        // Release any existing media player
        mPlayer?.release()
        mPlayer = null

        try {
            // Create a new MediaPlayer instance
            mPlayer = MediaPlayer().apply {
                setDataSource(myActivity, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.songID))
                prepareAsync() // Prepare asynchronously to avoid blocking UI
                setOnPreparedListener { player ->
                    player.start() // Start playback when prepared
                    playPauseButton?.setBackgroundResource(R.drawable.pause_icon) // Change button to pause

                    // Update seek bar and times (initial setup)
                    nowPlayingSeekBar?.max = player.duration
                    nowPlayingEndTime?.text = formatDuration(player.duration.toLong())
                    nowPlayingStartTime?.text = "00:00"

                    // Set up seek bar updater (will be implemented in detail later)
                    // You'd typically use a Handler to update the seek bar every second
                    // myActivity.runOnUiThread(updateSeekBar) // Example if updateSeekBar is a runnable
                    // visualizationView.setAudioSessionId(player.audioSessionId) // For visualization if added directly here
                }
                setOnCompletionListener {
                    // Handle song completion (e.g., play next song)
                    Toast.makeText(myActivity, "Song finished", Toast.LENGTH_SHORT).show()
                    // You would implement logic to play the next song here (e.g., call playSong with next song)
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(myActivity, "Playback error: $what, $extra", Toast.LENGTH_LONG).show()
                    false // Return false to indicate the error was not handled
                }
            }
            currentPlayingSong = song
            currentSongPosition = position
            songTitle?.text = song.songTitle // Update UI with current song title
            nowPlayingArtistBar?.text = song.artist // Update artist text

            // --- ADD THIS CODE TO LOAD COVER ART ---
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumID
            )
            Glide.with(this@HomeFragment) // Use 'this@HomeFragment' as Fragment context
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image) // Placeholder if no art
                .error(R.drawable.now_playing_bar_eq_image)      // Image to show on error
                .into(nowPlayingCoverArt!!) // Load into the ImageView. !! because we know it's not null here.
            // --- END ADDITION ---

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(myActivity, "Error playing song: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pauseSong() {
        mPlayer?.pause()
        playPauseButton?.setBackgroundResource(R.drawable.play_icon) // Change button to play
    }

    private fun resumeSong() {
        mPlayer?.start()
        playPauseButton?.setBackgroundResource(R.drawable.pause_icon) // Change button to pause
    }

    // Helper function to format duration (added for clarity)
    private fun formatDuration(duration: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
        )
    }

    // --- Fragment Lifecycle for MediaPlayer Release ---
    override fun onStop() {
        super.onStop()
        mPlayer?.release() // Release MediaPlayer resources when fragment is stopped
        mPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release ViewBinding
        _binding = null
        // Media player is handled in onStop, but good to double check
        mPlayer?.release()
        mPlayer = null
    }

    // --- Existing Methods (from your original code) ---
    // Note: onAttach(Activity) is deprecated, but including defensive check for backward compatibility.
    // minSdk 30 means onAttach(Context) is preferred.
    override fun onAttach(context: Context) {
        super.onAttach(context)
        myActivity = context as Activity
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (!this::myActivity.isInitialized) { // Defensive check
            myActivity = activity
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // You'll need to call setHasOptionsMenu(true) in onCreateView or onViewCreated for this to work
        inflater.inflate(R.menu.main, menu)
        super.onCreateOptionsMenu(menu, inflater) // Call super last
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
                Collections.sort(getSongsList, Songs.CREATOR.Statified.nameComparator) // CORRECTED
            }
            _mainScreenAdapter?.notifyDataSetChanged()
            return false
        } else if (switcher == R.id.action_sort_recent) {
            val editorTwo =
                myActivity?.getSharedPreferences("action_sort", Context.MODE_PRIVATE)?.edit()
            editorTwo?.putString("action_sort_recent", "true")
            editorTwo?.putString("action_sort_ascending", "false")
            editorTwo?.apply()
            if (getSongsList != null) {
                Collections.sort(getSongsList, Songs.CREATOR.Statified.dateComparator) // CORRECTED
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
            MediaStore.Audio.Media.ALBUM_ID // ADD THIS LINE TO THE PROJECTION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = contentResolver.query(songUri, projection, selection, null, null)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn =
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) // GET ALBUM ID COLUMN

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val albumId = it.getLong(albumIdColumn) // RETRIEVE ALBUM ID
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                // Pass the new albumId to the Songs constructor
                arrayList.add(
                    Songs(
                        id,
                        title,
                        artist,
                        contentUri.toString(),
                        dateAdded,
                        albumId
                    )
                ) // CORRECTED
            }
        }
        return arrayList
    }
}