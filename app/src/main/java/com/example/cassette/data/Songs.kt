package com.example.cassette.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Comparator

@Parcelize
data class Songs(
    val songID: Long,
    val songTitle: String?,
    val artist: String?,
    val songData: String?,
    val dateAdded: Long,
    val albumID: Long
) : Parcelable {

    // The comparators are now in the standard companion object,
    // which is where HomeFragment is looking for them.
    companion object {
        var nameComparator: Comparator<Songs> = Comparator { song1, song2 ->
            val songOne = song1.songTitle.orEmpty().uppercase()
            val songTwo = song2.songTitle.orEmpty().uppercase()
            songOne.compareTo(songTwo)
        }

        var dateComparator: Comparator<Songs> = Comparator { song1, song2 ->
            val dateOne = song1.dateAdded.toString()
            val dateTwo = song2.dateAdded.toString()
            dateTwo.compareTo(dateOne) // Sort descending for most recent
        }
    }
}