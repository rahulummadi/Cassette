package com.example.cassette.services

import android.content.ContentUris
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.cassette.data.Songs

private const val ACTION_PLAY_FROM_LIST = "com.example.cassette.services.ACTION_PLAY_FROM_LIST"

class MediaPlaybackService : MediaBrowserServiceCompat(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private var mediaSession: MediaSessionCompat? = null
    private var mediaPlayer: MediaPlayer? = null

    private var songList: ArrayList<Songs> = ArrayList()
    private var currentSongIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, "MediaPlaybackService").apply {
            setCallback(MediaSessionCallback())
            setSessionToken(sessionToken)
        }
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener(this@MediaPlaybackService)
            setOnCompletionListener(this@MediaPlaybackService)
            setOnErrorListener(this@MediaPlaybackService)
        }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            mediaPlayer?.takeIf { !it.isPlaying }?.start()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
        override fun onPause() {
            mediaPlayer?.takeIf { it.isPlaying }?.pause()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }
        override fun onSkipToNext() {
            if (songList.isNotEmpty()) {
                currentSongIndex = (currentSongIndex + 1) % songList.size
                playSongAtIndex(currentSongIndex)
            }
        }
        override fun onSkipToPrevious() {
            if (songList.isNotEmpty()) {
                currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else songList.size - 1
                playSongAtIndex(currentSongIndex)
            }
        }
        override fun onSeekTo(pos: Long) {
            mediaPlayer?.seekTo(pos.toInt())
            updatePlaybackState(if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        }
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_PLAY_FROM_LIST) {
                val receivedList = extras?.getParcelableArrayList<Songs>("song_list")
                val startIndex = extras?.getInt("start_index", 0) ?: 0
                if (receivedList != null) {
                    songList = receivedList
                    currentSongIndex = startIndex
                    playSongAtIndex(currentSongIndex)
                }
            }
        }
    }

    private fun playSongAtIndex(index: Int) {
        if (index < 0 || index >= songList.size) return
        val song = songList[index]
        try {
            updateMetadata(song)
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(applicationContext, Uri.parse(song.songData))
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun updateMetadata(song: Songs) {
        // Construct the album art URI from the album ID
        val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumID)

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.songTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.songID.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.songData)

            // NEW: Add albumID and dateAdded to the metadata bundle
            .putLong("album_id", song.albumID)
            .putLong("date_added", song.dateAdded)

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)
        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.let {
            val metadataBuilder = MediaMetadataCompat.Builder(mediaSession?.controller?.metadata)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration.toLong())
            mediaSession?.setMetadata(metadataBuilder.build())
            it.start()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        mediaSession?.controller?.transportControls?.skipToNext()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        return true
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot("root_id", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession?.release()
    }
}