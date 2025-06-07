package com.example.cassette.data

import android.os.Parcel
import android.os.Parcelable
import java.util.Comparator

class Songs(
    var songID: Long,
    var songTitle: String?,
    var artist: String?,
    var songData: String?,
    var dateAdded: Long,
    var albumID: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readLong(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(songID)
        parcel.writeString(songTitle)
        parcel.writeString(artist)
        parcel.writeString(songData)
        parcel.writeLong(dateAdded)
        parcel.writeLong(albumID)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Songs> {
        override fun createFromParcel(parcel: Parcel): Songs {
            return Songs(parcel)
        }

        override fun newArray(size: Int): Array<Songs?> {
            return arrayOfNulls(size)
        }

        object Statified {
            var nameComparator: Comparator<Songs> = Comparator { song1, song2 ->
                val songOne = song1.songTitle?.toUpperCase()
                val songTwo = song2.songTitle?.toUpperCase()
                songOne?.compareTo(songTwo!!, true) ?: 0
            }

            var dateComparator: Comparator<Songs> = Comparator { song1, song2 ->
                val songOne = song1.dateAdded
                val songTwo = song2.dateAdded
                songTwo?.compareTo(songOne!!) ?: 0
            }
        }
    }
}