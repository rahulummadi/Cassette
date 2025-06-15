package com.example.cassette.ui

import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.cassette.R
import com.example.cassette.data.Songs
import com.example.cassette.databinding.FragmentSongPlayingBinding
import com.example.cassette.services.MediaPlaybackService
import java.util.concurrent.TimeUnit

class SongPlayingFragment : Fragment() {

    private var _binding: FragmentSongPlayingBinding? = null
    private val binding get() = _binding!!

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    // NEW: Handler for updating seek bar
    private val seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var seekBarUpdateRunnable: Runnable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            requireContext(),
            ComponentName(requireContext(), MediaPlaybackService::class.java),
            connectionCallbacks,
            null // optional Bundle
        )
        // FIXED: Call setupClickListeners so the buttons work
        setupClickListeners()
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
        // NEW: Stop seek bar updates when the screen is not visible
        stopSeekBarUpdate()
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaController = MediaControllerCompat(requireContext(), mediaBrowser.sessionToken).apply {
                registerCallback(controllerCallback)
            }

            // --- THIS IS THE MAIN FIX ---
            // Check if arguments were passed. If not, we came from the mini-player.
            if (arguments?.getParcelable<Songs>("songData") != null) {
                // Arguments exist, so play the new song from the list.
                getSongDataAndPlay()
            } else {
                // No arguments, so just sync the UI with the service's current state.
                updateUiWithMetadata(mediaController?.metadata)
                updateUiWithPlaybackState(mediaController?.playbackState)
            }
        }

        override fun onConnectionSuspended() {}

        override fun onConnectionFailed() {
            Toast.makeText(requireContext(), "Failed to connect to media service", Toast.LENGTH_SHORT).show()
        }
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateUiWithMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updateUiWithPlaybackState(state)
        }
    }

    private fun getSongDataAndPlay() {
        // These are now local variables, not properties of the Fragment
        val currentSong: Songs? = arguments?.getParcelable("songData")
        val songList: ArrayList<Songs>? = arguments?.getParcelableArrayList("songList")
        val currentPosition: Int = arguments?.getInt("songPosition", 0) ?: 0

        if (currentSong != null && songList != null) {
            val extras = Bundle().apply {
                putParcelableArrayList("song_list", songList)
                putInt("start_index", currentPosition)
            }
            mediaController?.transportControls?.sendCustomAction("com.example.cassette.services.ACTION_PLAY_FROM_LIST", extras)
        }
    }

    private fun updateUiWithMetadata(metadata: MediaMetadataCompat?) {
        metadata?.let {
            binding.songTitle.text = it.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            binding.songArtist.text = it.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            val duration = it.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            binding.endTime.text = formatDuration(duration)
            binding.seekBar.max = duration.toInt()

            // FIXED: Load album art from the URI provided in metadata
            val albumArtUriString = it.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            albumArtUriString?.let {
                Glide.with(this@SongPlayingFragment)
                    .load(Uri.parse(it))
                    .placeholder(R.drawable.default_album_art)
                    .error(R.drawable.default_album_art)
                    .into(binding.bigCoverArtImageView)
            }
        }
    }

    private fun updateUiWithPlaybackState(state: PlaybackStateCompat?) {
        binding.seekBar.progress = state?.position?.toInt() ?: 0

        when (state?.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                binding.playPauseButton.setImageResource(R.drawable.pause_icon)
                startSeekBarUpdate() // NEW: Start updating seek bar
            }
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED,
            PlaybackStateCompat.STATE_NONE -> {
                binding.playPauseButton.setImageResource(R.drawable.play_icon)
                stopSeekBarUpdate() // NEW: Stop updating seek bar
            }
        }
    }

    // NEW: Function to start updating the seek bar every second
    private fun startSeekBarUpdate() {
        seekBarUpdateRunnable = Runnable {
            mediaController?.playbackState?.let {
                binding.seekBar.progress = it.position.toInt()
                binding.startTime.text = formatDuration(it.position)
            }
            seekBarUpdateHandler.postDelayed(seekBarUpdateRunnable, 1000)
        }
        seekBarUpdateHandler.post(seekBarUpdateRunnable)
    }

    // NEW: Function to stop the seek bar updates
    private fun stopSeekBarUpdate() {
        if (::seekBarUpdateRunnable.isInitialized) {
            seekBarUpdateHandler.removeCallbacks(seekBarUpdateRunnable)
        }
    }

    private fun setupClickListeners() {
        binding.playPauseButton.setOnClickListener {
            val playbackState = mediaController?.playbackState?.state
            if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }
        binding.nextButton.setOnClickListener { mediaController?.transportControls?.skipToNext() }
        binding.previousButton.setOnClickListener { mediaController?.transportControls?.skipToPrevious() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.startTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let { mediaController?.transportControls?.seekTo(it.toLong()) }
            }
        })
    }

    private fun formatDuration(duration: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}