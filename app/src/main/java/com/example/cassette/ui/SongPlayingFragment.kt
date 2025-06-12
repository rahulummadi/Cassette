package com.example.cassette.ui

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cleveroad.audiovisualization.GLAudioVisualizationView
import com.example.cassette.R
import com.example.cassette.databinding.FragmentSongPlayingBinding
import com.example.cassette.data.Songs
import com.example.cassette.data.CurrentSongHelper
import com.example.cassette.data.databases.EchoDatabase
import java.util.ArrayList
import java.util.Random
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat

class SongPlayingFragment : Fragment() {

    // View Binding property (local to fragment instance)
    private var _binding: FragmentSongPlayingBinding? = null
    private val binding get() = _binding!!

    // UI elements from fragment_song_playing.xml (local to fragment instance)
    var visualizerView: GLAudioVisualizationView? = null
    var favouriteIcon: ImageButton? = null
    var songTitle: TextView? = null
    var songArtist: TextView? = null
    var startTime: TextView? = null
    var endTime: TextView? = null
    var seekBar: SeekBar? = null
    var playPauseButton: ImageButton? = null
    var previousButton: ImageButton? = null
    var nextButton: ImageButton? = null
    var loopButton: ImageButton? = null
    var shuffleButton: ImageButton? = null
    var bigCoverArtImageView: ImageView? = null

    // Companion object to hold global playback state and methods
    companion object Statified {
        // Global MediaPlayer instance
        var mPlayer: MediaPlayer? = null

        // Global song data
        var currentSong: Songs? = null
        var currentPosition: Int = 0 // Position in the songList
        var songList: ArrayList<Songs>? = null // The full list of songs

        // Global helper for tracking song state
        var currentSongHelper: CurrentSongHelper? = null

        // Global database for favorites
        var favouriteContent: EchoDatabase? = null
        lateinit var myActivity: Context

        // Global handler for seek bar updates
        var updateSeekBarHandler: Handler = Handler(Looper.getMainLooper())

        // Global runnable for seek bar updates
        var updateSeekBarRunnable: Runnable? = null // Will be initialized later in fragment instance

        // Global context (initialized in onAttach)
        var myContext: Context? = null


        // --- Playback Control Functions (now static/global via Statified) ---
        fun playSong(context: Context, song: Songs) { // Context is passed directly to avoid NullPointer
            mPlayer?.release()
            mPlayer = null

            try {
                mPlayer = MediaPlayer().apply {
                    setDataSource(context, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.songID))
                    prepareAsync()
                    setOnPreparedListener { player ->
                        player.start()
                        // UI updates handled by fragment instance
                    }
                    setOnCompletionListener {
                        onSongComplete()
                    }
                    setOnErrorListener { _, what, extra ->
                        Toast.makeText(context, "Playback error: $what, $extra", Toast.LENGTH_LONG).show()
                        false
                    }
                }
                Statified.currentSong = song
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error playing song: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        fun pauseSong() {
            mPlayer?.pause()
            updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable!!)
            currentSongHelper?.isPlaying = false
        }

        fun resumeSong() {
            mPlayer?.start()
            updateSeekBarHandler.postDelayed(updateSeekBarRunnable!!, 0)
            currentSongHelper?.isPlaying = true
        }

        fun onSongComplete() {
            currentSongHelper?.let { helper ->
                if (helper.isShuffle == true) {
                    playNextSong(true)
                    helper.isPlaying = true
                } else {
                    if (helper.isLoop == true) {
                        playSong(Statified.myContext!!, currentSong!!) // Use Statified.myContext here
                        helper.isPlaying = true
                    }
                    else {
                        playNextSong(false)
                        helper.isPlaying = true
                    }
                }
            }
        }

        fun playNextSong(isShuffle: Boolean = false) {
            songList?.let { list ->
                if (list.isNotEmpty()) {
                    val nextPosition = if (isShuffle) {
                        Random().nextInt(list.size)
                    } else {
                        (currentPosition + 1) % list.size
                    }
                    if (!isShuffle && nextPosition == list.size) {
                        Statified.currentPosition = 0
                    } else {
                        Statified.currentPosition = nextPosition
                    }
                    Statified.currentSong = list[Statified.currentPosition]

                    currentSongHelper?.songTitle = Statified.currentSong?.songTitle
                    currentSongHelper?.songPath = Statified.currentSong?.songData
                    currentSongHelper?.songId = Statified.currentSong?.songID ?: 0L
                    currentSongHelper?.currentPosition = Statified.currentPosition
                    currentSongHelper?.artist = Statified.currentSong?.artist

                    playSong(Statified.myContext!!, Statified.currentSong!!) // Use Statified.myContext here
                } else {
                    Toast.makeText(Statified.myContext, "No more songs in list!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun playPreviousSong() {
            songList?.let { list ->
                if (list.isNotEmpty()) {
                    val prevPosition = if (Statified.currentPosition - 1 < 0) list.size - 1 else Statified.currentPosition - 1
                    Statified.currentPosition = prevPosition
                    Statified.currentSong = list[prevPosition]

                    currentSongHelper?.songTitle = Statified.currentSong?.songTitle
                    currentSongHelper?.songPath = Statified.currentSong?.songData
                    currentSongHelper?.songId = Statified.currentSong?.songID ?: 0L
                    currentSongHelper?.currentPosition = Statified.currentPosition
                    currentSongHelper?.artist = Statified.currentSong?.artist

                    playSong(Statified.myContext!!, Statified.currentSong!!) // Use Statified.myContext here
                } else {
                    Toast.makeText(Statified.myContext, "No more songs in list!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun toggleLoop() {
            currentSongHelper?.isLoop = !(currentSongHelper?.isLoop ?: false)
            if (currentSongHelper?.isLoop == true) {
                Toast.makeText(Statified.myContext, "Loop Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(Statified.myContext, "Loop Disabled", Toast.LENGTH_SHORT).show()
            }
            currentSongHelper?.isShuffle = false
        }

        fun toggleShuffle() {
            currentSongHelper?.isShuffle = !(currentSongHelper?.isShuffle ?: false)
            if (currentSongHelper?.isShuffle == true) {
                Toast.makeText(Statified.myContext, "Shuffle Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(Statified.myContext, "Shuffle Disabled", Toast.LENGTH_SHORT).show()
            }
            currentSongHelper?.isLoop = false
        }

        fun toggleFavorite() {
            currentSong?.let { song ->
                val songIdInt = song.songID.toInt()

                if (favouriteContent?.checkifIDExists(songIdInt) == true) {
                    favouriteContent?.deleteFavourite(songIdInt)
                    Toast.makeText(Statified.myContext, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    favouriteContent?.storeAsFavourite(
                        songIdInt,
                        song.artist,
                        song.songTitle,
                        song.songData
                    )
                    Toast.makeText(Statified.myContext, "Added to Favorites", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun formatDuration(duration: Long): String {
            return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))
        }
    } // End of companion object Statified

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize global Statified properties once per app lifecycle
        if (Statified.currentSongHelper == null) {
            Statified.currentSongHelper = CurrentSongHelper()
        }
        if (Statified.favouriteContent == null) {
            Statified.favouriteContent = EchoDatabase(context)
        }
       // Statified.myContext = context // Set global context
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSongPlayingBinding.inflate(inflater, container, false)
        val view = binding.root

        // Link local UI elements
        visualizerView = binding.visualizerView
        favouriteIcon = binding.favouriteIcon
        songTitle = binding.songTitle
        songArtist = binding.songArtist
        startTime = binding.startTime
        endTime = binding.endTime
        seekBar = binding.seekBar
        playPauseButton = binding.playPauseButton
        previousButton = binding.previousButton
        nextButton = binding.nextButton
        loopButton = binding.loopButton
        shuffleButton = binding.shuffleButton
        bigCoverArtImageView = binding.bigCoverArtImageView

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myActivity = requireContext() // Initialize myActivity here

        // Receive song data and songList from arguments
        arguments?.let {
            currentSong = it.getParcelable("songData")
            currentPosition = it.getInt("songPosition", 0)
            val receivedSongArray = it.getParcelableArray("songList") as? Array<Songs>
            songList = receivedSongArray?.toCollection(ArrayList())
        }

        // Display initial song details and cover art (call updateUI from here)
        Statified.currentSong?.let { song ->
            updateUI(song) // Call local fragment updateUI
            Statified.playSong(myActivity, song) // CORRECTED: Pass myActivity (Context) here

            // Update all local UI based on newly played song
            updatePlayPauseButtonIcon()
            updateFavoriteButtonState(song.songID.toInt())
            updateShuffleLoopButtonIcons()

            // Initial seek bar setup
            seekBar?.max = Statified.mPlayer?.duration ?: 0
            endTime?.text = Statified.formatDuration(Statified.mPlayer?.duration?.toLong() ?: 0L)
            Statified.updateSeekBarHandler.postDelayed(Statified.updateSeekBarRunnable!!, 0) // Start updates
        } ?: run {
            Toast.makeText(myActivity, "Error: No song data received. Navigating back.", Toast.LENGTH_LONG).show()
        }

        // Set up click listeners for playback controls
        playPauseButton?.setOnClickListener {
            if (Statified.mPlayer?.isPlaying == true) {
                Statified.pauseSong()
            } else {
                Statified.resumeSong()
            }
            updatePlayPauseButtonIcon() // Update local icon after action
        }

        previousButton?.setOnClickListener { Statified.playPreviousSong() }
        nextButton?.setOnClickListener { Statified.playNextSong() }
        shuffleButton?.setOnClickListener { Statified.toggleShuffle() }
        loopButton?.setOnClickListener { Statified.toggleLoop() }
        favouriteIcon?.setOnClickListener { Statified.toggleFavorite() }

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Statified.mPlayer?.seekTo(progress)
                }
                startTime?.text = Statified.formatDuration(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Statified.updateSeekBarHandler.removeCallbacks(Statified.updateSeekBarRunnable!!)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Statified.updateSeekBarHandler.postDelayed(Statified.updateSeekBarRunnable!!, 1000)
            }
        })
    }

    // --- Helper function to update UI elements (title, artist, cover art) ---
    private fun updateUI(song: Songs) {
        songTitle?.text = song.songTitle ?: "Unknown Title"
        songArtist?.text = song.artist ?: "Unknown Artist"

        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumID
        )
        Glide.with(this)
            .load(albumArtUri)
            .placeholder(R.drawable.now_playing_bar_eq_image)
            .error(R.drawable.now_playing_bar_eq_image)
            .into(bigCoverArtImageView!!)
    }

    // --- Helper function to update play/pause button icon (local to fragment) ---
    private fun updatePlayPauseButtonIcon() {
        if (Statified.mPlayer?.isPlaying == true) {
            playPauseButton?.setBackgroundResource(R.drawable.pause_icon)
        } else {
            playPauseButton?.setBackgroundResource(R.drawable.play_icon)
        }
    }

    // --- Helper function to update favorite button icon (local to fragment) ---
    private fun updateFavoriteButtonState(songId: Int) {
        if (Statified.favouriteContent?.checkifIDExists(songId) == true) {
            favouriteIcon?.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.favorite_on))
        } else {
            favouriteIcon?.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.favorite_off))
        }
    }

    // --- Helper function to update shuffle/loop icons (local to fragment) ---
    private fun updateShuffleLoopButtonIcons() {
        if (Statified.currentSongHelper?.isShuffle == true) {
            shuffleButton?.setBackgroundResource(R.drawable.shuffle_icon_pressed)
        } else {
            shuffleButton?.setBackgroundResource(R.drawable.shuffle_white_icon)
        }

        if (Statified.currentSongHelper?.isLoop == true) {
            loopButton?.setBackgroundResource(R.drawable.loop_icon_pressed)
        } else {
            loopButton?.setBackgroundResource(R.drawable.loop_white_icon)
        }
    }


    // Helper function to start continuous seek bar updates (local to fragment)
    private fun updateSeekBarAndTimer() {
        Statified.updateSeekBarHandler.postDelayed(Statified.updateSeekBarRunnable!!, 0)
    }

    // Helper function to format duration (local to fragment)
    private fun formatDuration(duration: Long): String {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))
    }

    // --- Fragment Lifecycle ---
    override fun onStop() {
        super.onStop()
        // No release here, as mPlayer is global now
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Stop handler callbacks if fragment view is destroyed
        Statified.updateSeekBarHandler.removeCallbacks(Statified.updateSeekBarRunnable!!)
    }

    override fun onResume() {
        super.onResume()
        // visualizerView?.onResume()
        // Update local UI to reflect current global playback state
        Statified.currentSong?.let { song ->
            updateUI(song)
            updatePlayPauseButtonIcon()
            updateFavoriteButtonState(song.songID.toInt())
            updateShuffleLoopButtonIcons()
        }
        // Start seek bar updates if global player is active and playing
        if (Statified.mPlayer != null && Statified.mPlayer?.isPlaying == true) {
            updateSeekBarAndTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        // visualizerView?.onPause()
        Statified.updateSeekBarHandler.removeCallbacks(Statified.updateSeekBarRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        // visualizerView?.release()
        // Only release global player when application is completely shut down (e.g., in a Service)
        // For now, it will remain active until app process dies or explicitly killed.
    }
}