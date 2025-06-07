package com.example.cassette.ui // MAKE SURE THIS PACKAGE IS CORRECT FOR YOUR FILE LOCATION

import android.content.Context
import android.media.MediaPlayer
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
import com.example.cassette.R // Ensure your R file is imported
import com.example.cassette.databinding.FragmentSongPlayingBinding // This will be generated after building
import com.example.cassette.data.Songs // Import your Songs data class
import java.util.concurrent.TimeUnit
import android.content.ContentUris // This is needed for ContentUris
import android.net.Uri // This is needed for Uri.parse

class SongPlayingFragment : Fragment() {

    // View Binding property
    private var _binding: FragmentSongPlayingBinding? = null
    private val binding get() = _binding!!

    // UI elements from fragment_song_playing.xml
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
    var bigCoverArtImageView: ImageView? = null // For the new big cover art ImageView

    // MediaPlayer and song data
    var myActivity: Context? = null // Use Context as Fragment's context is more reliable
    var mPlayer: MediaPlayer? = null
    var currentSong: Songs? = null // To hold the currently playing song's data
    var currentPosition: Int = 0 // To hold the position in the original song list
    var songList: Array<Songs>? = null // To hold the full song list passed from HomeFragment

    // Handler for seek bar updates
    private var updateSeekBarHandler: Handler = Handler(Looper.getMainLooper())


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSongPlayingBinding.inflate(inflater, container, false)
        val view = binding.root

        // --- LINK ALL UI ELEMENTS BEFORE THE RETURN STATEMENT ---
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
        bigCoverArtImageView = binding.bigCoverArtImageView // LINK THE NEW IMAGEVIEW HERE

        return view // Now the view is returned AFTER linking
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myActivity = context

        // --- UPDATED: Receive song data and songList from arguments ---
        arguments?.let {
            currentSong = it.getParcelable("songData") // Get the current song
            currentPosition = it.getInt("songPosition", 0) // Get its position

            // Get the entire song list as an Array<Songs>
            // Use getParcelableArray and cast, as we sent it as Array<Songs> from HomeFragment
            songList = it.getParcelableArray("songList") as? Array<Songs>
        }

        // Display initial song details and cover art
        currentSong?.let { song ->
            songTitle?.text = song.songTitle
            songArtist?.text = song.artist

            // --- LOAD COVER ART USING GLIDE ---
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.albumID
            )
            Glide.with(this) // Use 'this' as Fragment context
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image) // Placeholder if no art
                .error(R.drawable.now_playing_bar_eq_image)      // Image to show on error
                .into(bigCoverArtImageView!!) // Load into the new big cover art ImageView
            // --- END LOAD COVER ART ---

            // Start playing the song
            playSong(song)

        } ?: run {
            Toast.makeText(myActivity, "Error: No song data received. Navigating back.", Toast.LENGTH_LONG).show()
            // Optionally, navigate back if no song data
            // findNavController().popBackStack() // Requires import androidx.navigation.fragment.findNavController
        }

        // Set up click listeners for playback controls
        playPauseButton?.setOnClickListener {
            if (mPlayer?.isPlaying == true) {
                pauseSong()
            } else {
                resumeSong()
            }
        }
        // You'll add listeners for previousButton, nextButton, loopButton, shuffleButton here.
        // You'll also set up the SeekBar.OnSeekBarChangeListener here.
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update start time as user drags or progress changes
                if (fromUser) { // Only update if user is dragging
                    mPlayer?.seekTo(progress)
                }
                startTime?.text = formatDuration(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Remove callbacks to prevent conflict while user is dragging
                updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Restart updates after user stops dragging
                mPlayer?.currentPosition?.let { mPlayer?.seekTo(it) }
                updateSeekBarHandler.postDelayed(updateSeekBarRunnable, 1000) // Start updating again
            }
        })
    }

    // --- Media Playback Functions (similar to HomeFragment, but for this full player) ---

    private fun playSong(song: Songs) {
        mPlayer?.release()
        mPlayer = null

        try {
            mPlayer = MediaPlayer().apply {
                myActivity?.let { setDataSource(it, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.songID)) }
                prepareAsync()
                setOnPreparedListener { player ->
                    player.start()
                    playPauseButton?.setBackgroundResource(R.drawable.pause_icon)
                    seekBar?.max = player.duration
                    endTime?.text = formatDuration(player.duration.toLong())
                    updateSeekBarAndTimer() // Start updating seek bar and times

                    // Integrate audio visualizer
                    //visualizerView?.setAudioSessionId(player.audioSessionId)
                }
                setOnCompletionListener {
                    // Implement logic for next song here
                    Toast.makeText(myActivity, "Song finished", Toast.LENGTH_SHORT).show()
                    // If songList is available, play the next song automatically
                    playNextSong()
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(myActivity, "Playback error: $what, $extra", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(myActivity, "Error playing song: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playNextSong() {
        if (songList != null && songList!!.isNotEmpty()) {
            val nextPosition = (currentPosition + 1) % songList!!.size
            val nextSong = songList!![nextPosition]
            currentPosition = nextPosition
            playSong(nextSong)

            // Update UI with new song details
            songTitle?.text = nextSong.songTitle
            songArtist?.text = nextSong.artist
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                nextSong.albumID
            )
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image)
                .error(R.drawable.now_playing_bar_eq_image)
                .into(bigCoverArtImageView!!)
        } else {
            Toast.makeText(myActivity, "No more songs in list!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPreviousSong() {
        if (songList != null && songList!!.isNotEmpty()) {
            val prevPosition = if (currentPosition - 1 < 0) songList!!.size - 1 else currentPosition - 1
            val prevSong = songList!![prevPosition]
            currentPosition = prevPosition
            playSong(prevSong)

            // Update UI with new song details
            songTitle?.text = prevSong.songTitle
            songArtist?.text = prevSong.artist
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                prevSong.albumID
            )
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.now_playing_bar_eq_image)
                .error(R.drawable.now_playing_bar_eq_image)
                .into(bigCoverArtImageView!!)
        } else {
            Toast.makeText(myActivity, "No more songs in list!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseSong() {
        mPlayer?.pause()
        playPauseButton?.setBackgroundResource(R.drawable.play_icon)
        updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable) // Stop updating
    }

    private fun resumeSong() {
        mPlayer?.start()
        playPauseButton?.setBackgroundResource(R.drawable.pause_icon)
        updateSeekBarAndTimer() // Resume updating
    }

    // Runnable to update seek bar and timers
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (mPlayer?.isPlaying == true) {
                val currentPosition = mPlayer?.currentPosition ?: 0
                seekBar?.progress = currentPosition
                startTime?.text = formatDuration(currentPosition.toLong())
                updateSeekBarHandler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    private fun updateSeekBarAndTimer() {
        updateSeekBarHandler.postDelayed(updateSeekBarRunnable, 0)
    }

    private fun formatDuration(duration: Long): String {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)))
    }

    override fun onStop() {
        super.onStop()
        // Release MediaPlayer when fragment is stopped
        mPlayer?.release()
        mPlayer = null
        updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable) // Stop handler
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release ViewBinding
        _binding = null
        // Ensure MediaPlayer and handler are stopped
        mPlayer?.release()
        mPlayer = null
        updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable)
    }

    override fun onResume() {
        super.onResume()
     //   visualizerView?.onResume() // Ensure visualizer resumes
        if (mPlayer != null && mPlayer?.isPlaying == true) {
            updateSeekBarAndTimer() // Resume seek bar updates if playing
        }
    }

    override fun onPause() {
        super.onPause()
      //  visualizerView?.onPause() // Ensure visualizer pauses
        updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable) // Stop updates when paused
    }

    override fun onDestroy() {
        super.onDestroy()
        // Destroy visualizer
      //  visualizerView?.release()
    }
}