package com.example.cassette.data

import android.os.Parcel
import android.os.Parcelable

data class Playlist(
    val playlistID: Long,
    val playlistName: String,
    val dateCreated: Long,
    var songsCount: Int = 0 // Optional: to easily display number of songs in UI
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "", // Handle potential nullability for String
        parcel.readLong(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(playlistID)
        parcel.writeString(playlistName)
        parcel.writeLong(dateCreated)
        parcel.writeInt(songsCount)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Playlist> {
        override fun createFromParcel(parcel: Parcel): Playlist {
            return Playlist(parcel)
        }

        override fun newArray(size: Int): Array<Playlist?> {
            return arrayOfNulls(size)
        }
    }
}