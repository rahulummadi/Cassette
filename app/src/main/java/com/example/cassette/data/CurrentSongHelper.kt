package com.example.cassette.data

class CurrentSongHelper {
    var artist: String? = null      // Keep this one
    var songTitle: String? = null
    var songPath: String? = null
    var songId: Long = 0L           // Note: Changed from 0 to 0L for consistency with Long type
    var currentPosition: Int = 0
    var isPlaying: Boolean = false
    var isLoop: Boolean = false
    var isShuffle: Boolean = false
    var trackPosition: Int = 0
}