package com.example.cassette.ui.home

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.*
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cassette.R
import com.example.cassette.data.Songs
import com.example.cassette.data.adapters.MainScreenAdapter
import com.example.cassette.databinding.FragmentHomeBinding
import com.example.cassette.services.MediaPlaybackService
import java.util.*
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.M)
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var myActivity: Activity
    private var mainScreenAdapter: MainScreenAdapter? = null
    private var getSongsList: ArrayList<Songs> = ArrayList()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // NEW: MediaBrowser and MediaController for service communication
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    // NEW: Handler for updating seek bar
    private val seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private var seekBarUpdateRunnable: Runnable? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        myActivity = context as Activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // Enable options menu in fragment

        //Registering permission launcher before view is created
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                loadSongsIntoUI()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission denied. Cannot load songs.",
                    Toast.LENGTH_LONG
                ).show()
                binding.visibleLayout.visibility = View.INVISIBLE
                binding.noSongs.visibility = View.VISIBLE
            }
        }

        // NEW: Initialize the MediaBrowser
        mediaBrowser = MediaBrowserCompat(
            requireContext(),
            ComponentName(
                requireContext(), MediaPlaybackService::
                class.java
            ),
            connectionCallbacks,
            null
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestPermissions()
        setupClickListeners()

        binding.nowPlayingSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopSeekBarUpdate() // pause updates while user is dragging
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newPosition = it.progress.toLong()
                    mediaController?.transportControls?.seekTo(newPosition)
                    startSeekBarUpdate() // resume updates
                }
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Optional: update start time as user drags
                    binding.nowPlayingStartTime.text =
                        android.text.format.DateUtils.formatElapsedTime((progress / 1000).toLong())
                }
            }
        })

    }

    private fun checkAndRequestPermissions() {
        val permission = getMediaPermission()

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Already granted
                loadSongsIntoUI()
            }

            shouldShowRequestPermissionRationale(permission) -> {
                // Optional: Explain why permission is needed
                Toast.makeText(
                    requireContext(),
                    "We need access to your media to load songs.",
                    Toast.LENGTH_SHORT
                ).show()
                requestPermissionLauncher.launch(permission)
            }

            else -> {
                //  Request directly
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun getMediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        }
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()

        seekBarUpdateRunnable?.let {
            seekBarUpdateHandler.removeCallbacks(it)
            seekBarUpdateRunnable = null
        }
    }

    // NEW: Callbacks for connecting to the MediaPlaybackService
    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaController = MediaControllerCompat(requireContext(), mediaBrowser.sessionToken)
            mediaController?.registerCallback(controllerCallback)

            // Sync UI with current state of the service
            val metadata = mediaController?.metadata
            val playbackState = mediaController?.playbackState
            updateUiWithMetadata(metadata)
            updateUiWithPlaybackState(playbackState)
        }

        override fun onConnectionSuspended() {
            mediaController = null
        }

        override fun onConnectionFailed() {
            mediaController = null
        }
    }

    // NEW: Callback to receive updates from the MediaPlaybackService
    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateUiWithMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updateUiWithPlaybackState(state)
        }
    }
    /*
        private fun checkAndRequestPermissions() {
            when {
                ContextCompat.checkSelfPermission(
                    myActivity, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    loadSongsIntoUI()
                }

                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }
            }
        }*/

    private fun loadSongsIntoUI() {
        getSongsList = getSongsFromPhone()
        if (getSongsList.isEmpty()) {
            binding.visibleLayout.visibility = View.INVISIBLE
            binding.noSongs.visibility = View.VISIBLE
        } else {
            binding.visibleLayout.visibility = View.VISIBLE
            binding.noSongs.visibility = View.INVISIBLE

            mainScreenAdapter = MainScreenAdapter(getSongsList, myActivity) { song, position ->
                if (mediaBrowser.isConnected) {
                    val extras = Bundle().apply {
                        putParcelableArrayList("song_list", getSongsList)
                        putInt("start_index", position)
                    }
                    mediaController?.transportControls?.sendCustomAction(
                        "com.example.cassette.services.ACTION_PLAY_FROM_LIST", extras
                    )
                }
            }
            binding.contentMain.layoutManager = LinearLayoutManager(myActivity)
            binding.contentMain.itemAnimator = DefaultItemAnimator()
            binding.contentMain.adapter = mainScreenAdapter
            applySorting()
        }
    }

    // REFACTORED: All click listeners now use the mediaController
    private fun setupClickListeners() {
        binding.hiddenBarMainScreen.setOnClickListener {
            // Navigate to the full player fragment
            findNavController().navigate(R.id.navigation_song_playing)
        }

        binding.nowPlayingPlayPauseButton.setOnClickListener {
            val state = mediaController?.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }
        binding.nowPlayingNextButton.setOnClickListener { mediaController?.transportControls?.skipToNext() }
        binding.nowPlayingPreviousButton.setOnClickListener { mediaController?.transportControls?.skipToPrevious() }
    }

    fun uriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // NEW: Function to update the mini-player based on service metadata
    private fun updateUiWithMetadata(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            binding.hiddenBarMainScreen.visibility = View.GONE
            return
        }
        binding.hiddenBarMainScreen.visibility = View.VISIBLE
        binding.nowPlayingSongTitleBar.text =
            metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        binding.nowPlayingSongArtistBar.text =
            metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        binding.nowPlayingEndTime.text =
            android.text.format.DateUtils.formatElapsedTime(metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000)

        // NEW: Get the album art URI from the metadata and load it with Glide
        val albumArtUriString = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        val albumArtUri = Uri.parse(albumArtUriString)
        if (uriExists(requireContext(), albumArtUri)) {
            Glide.with(this@HomeFragment)
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image)
                .error(R.drawable.now_playing_bar_eq_image)
                .into(binding.nowPlayingCoverArt)
        } else {
            // Load fallback if Uri is broken
            binding.nowPlayingCoverArt.setImageResource(R.drawable.now_playing_bar_eq_image)
        }
    }

    // NEW: Function to update the mini-player based on service playback state
    private fun updateUiWithPlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            binding.hiddenBarMainScreen.visibility = View.GONE
            return
        }

        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                binding.nowPlayingPlayPauseButton.setImageResource(R.drawable.pause_icon)
                startSeekBarUpdate()
            }

            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED,
            PlaybackStateCompat.STATE_NONE -> {
                binding.nowPlayingPlayPauseButton.setImageResource(R.drawable.play_icon)
                stopSeekBarUpdate()
            }
        }

        // Optional: move this into startSeekBarUpdate()
        binding.nowPlayingSeekBar.progress = getCurrentPlaybackPosition(state).toInt()
    }

    private fun getCurrentPlaybackPosition(state: PlaybackStateCompat): Long {
        return if (state.state == PlaybackStateCompat.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            state.position + timeDelta
        } else {
            state.position
        }
    }

    private fun startSeekBarUpdate() {
        stopSeekBarUpdate() // Prevent duplication

        seekBarUpdateRunnable = object : Runnable {
            override fun run() {
                val playbackState = mediaController?.playbackState
                val metadata = mediaController?.metadata

                if (playbackState != null && metadata != null) {
                    val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                    val currentPosition = getCurrentPlaybackPosition(playbackState)

                    binding.nowPlayingSeekBar.max = duration.toInt()
                    binding.nowPlayingSeekBar.progress = currentPosition.toInt()

                    binding.nowPlayingStartTime.text =
                        android.text.format.DateUtils.formatElapsedTime(currentPosition / 1000)
                    binding.nowPlayingEndTime.text =
                        android.text.format.DateUtils.formatElapsedTime(duration / 1000)
                }

                seekBarUpdateHandler.postDelayed(this, 1000)
            }
        }

        seekBarUpdateHandler.post(seekBarUpdateRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        seekBarUpdateRunnable?.let {
            seekBarUpdateHandler.removeCallbacks(it)
            seekBarUpdateRunnable = null
        }
    }


    private fun getSongsFromPhone(): ArrayList<Songs> {
        val arrayList = ArrayList<Songs>()
        try {
            val contentResolver = myActivity.contentResolver
            val songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = contentResolver.query(songUri, projection, selection, null, null)

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    arrayList.add(
                        Songs(
                            it.getLong(idColumn),
                            it.getString(titleColumn),
                            it.getString(artistColumn),
                            it.getString(dataColumn),
                            it.getLong(dateColumn),
                            it.getLong(albumIdColumn)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., SecurityException if permissions are revoked
        }
        return arrayList
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val editor = myActivity.getSharedPreferences("action_sort", Context.MODE_PRIVATE).edit()
        when (item.itemId) {
            R.id.action_sort_ascending -> {
                editor.putString("action_sort_ascending", "true")
                editor.putString("action_sort_recent", "false")
            }

            R.id.action_sort_recent -> {
                editor.putString("action_sort_ascending", "false")
                editor.putString("action_sort_recent", "true")
            }

            else -> return super.onOptionsItemSelected(item)
        }
        editor.apply()
        applySorting()
        return true
    }

    private fun applySorting() {
        val prefs = myActivity.getSharedPreferences("action_sort", Context.MODE_PRIVATE)
        val sortAsc = prefs.getString("action_sort_ascending", "true").toBoolean()
        if (sortAsc) {
            Collections.sort(getSongsList, Songs.nameComparator)
        } else {
            Collections.sort(getSongsList, Songs.dateComparator)
        }
        mainScreenAdapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}