package com.example.cassette.data.databases

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.cassette.data.Songs // <--- This is the CORRECTED IMPORT for Songs
import com.example.cassette.data.Playlist // <--- This is the CORRECTED IMPORT for Playlist
import java.util.ArrayList

class EchoDatabase : SQLiteOpenHelper {


    var _songList = ArrayList<Songs>()

    object Staticated {
        var DB_VERSION = 2 // INCREMENTED DATABASE VERSION FOR SCHEMA CHANGES
        val DB_NAME = "FavouriteDatabase" // This name won't change
        val TABLE_NAME = "FavouriteTable" // This table won't change its name
        val COLUMN_ID = "SongID"
        val COLUMN_SONG_TITLE = "SongTitle"
        val COLUMN_SONG_ARTIST = "SongArtist"
        val COLUMN_SONG_PATH = "SongPath"
        // Note: FavouriteTable does not store dateAdded or albumID, these are placeholders when queried.

        // NEW: Constants for Playlists Table
        val PLAYLIST_TABLE_NAME = "Playlists"
        val PLAYLIST_ID = "PlaylistID"
        val PLAYLIST_NAME = "PlaylistName"
        val PLAYLIST_DATE_CREATED = "DateCreated"

        // NEW: Constants for Playlist Songs Table (Join Table)
        val PLAYLIST_SONGS_TABLE_NAME = "PlaylistSongs"
        val PLAYLIST_SONG_TABLE_PRIMARY_ID = "_id" // Unique ID for each entry in this table
        val PS_PLAYLIST_ID = "PlaylistID" // Foreign key to Playlists table
        val PS_SONG_ID = "SongID" // Song ID from MediaStore
        val PS_SONG_PATH = "SongPath" // Song path for quick retrieval (optional but useful)
        val PS_DATE_ADDED = "DateAddedToPlaylist"
    }

    override fun onCreate(db: SQLiteDatabase?) { // Renamed sqliteDatabase to db for brevity
        // Create FavouriteTable (existing one)
        db?.execSQL("CREATE TABLE " + Staticated.TABLE_NAME + " ( " + Staticated.COLUMN_ID + " INTEGER, " +
                Staticated.COLUMN_SONG_ARTIST + " TEXT, " + Staticated.COLUMN_SONG_TITLE + " TEXT, " + // Use TEXT for strings
                Staticated.COLUMN_SONG_PATH + " TEXT );")

        // NEW: Create Playlists Table
        db?.execSQL("CREATE TABLE " + Staticated.PLAYLIST_TABLE_NAME + " ( " + Staticated.PLAYLIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                Staticated.PLAYLIST_NAME + " TEXT, " +
                Staticated.PLAYLIST_DATE_CREATED + " INTEGER );") // INTEGER for timestamp

        // NEW: Create Playlist Songs Table (Join Table)
        db?.execSQL("CREATE TABLE " + Staticated.PLAYLIST_SONGS_TABLE_NAME + " ( " + Staticated.PLAYLIST_SONG_TABLE_PRIMARY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                Staticated.PS_PLAYLIST_ID + " INTEGER, " +
                Staticated.PS_SONG_ID + " INTEGER, " + // This is the MediaStore song ID
                Staticated.PS_SONG_PATH + " TEXT, " +
                Staticated.PS_DATE_ADDED + " INTEGER, " +
                "FOREIGN KEY(" + Staticated.PS_PLAYLIST_ID + ") REFERENCES " + Staticated.PLAYLIST_TABLE_NAME + "(" + Staticated.PLAYLIST_ID + ") ON DELETE CASCADE );")
        // ON DELETE CASCADE means if a playlist is deleted, its songs from this table are also deleted.
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) { // Renamed p0, p1, p2
        // FOR DEVELOPMENT: Drop existing tables and recreate on upgrade.
        // In a real app, you would write ALTER TABLE statements to preserve data.
        if (oldVersion < 2) { // Upgrade from version 1 to 2
            db?.execSQL("DROP TABLE IF EXISTS " + Staticated.PLAYLIST_TABLE_NAME)
            db?.execSQL("DROP TABLE IF EXISTS " + Staticated.PLAYLIST_SONGS_TABLE_NAME)
            // Note: if you have older versions than 1, you might need more specific upgrade logic here
        }
        // This line ensures the FavouriteTable also gets dropped and recreated if DB_VERSION is bumped.
        // It's generally better to handle each table individually if you want to preserve data.
        db?.execSQL("DROP TABLE IF EXISTS " + Staticated.TABLE_NAME)
        onCreate(db) // Recreate all tables (both old and new)
    }

    constructor(context: Context?, name: String?, factory: SQLiteDatabase.CursorFactory?, version: Int) :
            super(context, name, factory, version)

    // The preferred constructor, using static constants
    constructor(context: Context?) : super(context, Staticated.DB_NAME, null, Staticated.DB_VERSION)

    // --- Existing FavouriteTable Methods ---
    fun storeAsFavourite(id: Int?, artist: String?, songTitle: String?, path: String?) {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(Staticated.COLUMN_ID, id)
        contentValues.put(Staticated.COLUMN_SONG_ARTIST, artist)
        contentValues.put(Staticated.COLUMN_SONG_TITLE, songTitle)
        contentValues.put(Staticated.COLUMN_SONG_PATH, path)
        db.insert(Staticated.TABLE_NAME, null, contentValues)
        db.close()
    }

    fun queryDBList(): ArrayList<Songs>? {
        val songListFromDb = ArrayList<Songs>() // Use a local list to avoid re-adding
        try {
            val db = this.readableDatabase
            val query_params = "SELECT * FROM " + Staticated.TABLE_NAME
            val cSor = db.rawQuery(query_params, null)
            if (cSor.moveToFirst()) {
                do {
                    val _id = cSor.getInt(cSor.getColumnIndexOrThrow(Staticated.COLUMN_ID))
                    val _artist = cSor.getString(cSor.getColumnIndexOrThrow(Staticated.COLUMN_SONG_ARTIST))
                    val _title = cSor.getString(cSor.getColumnIndexOrThrow(Staticated.COLUMN_SONG_TITLE))
                    val _songPath = cSor.getString(cSor.getColumnIndexOrThrow(Staticated.COLUMN_SONG_PATH))
                    // Provide 0L placeholders for dateAdded and albumID as they are not in this table
                    songListFromDb.add(Songs(_id.toLong(), _title, _artist, _songPath, 0L, 0L))
                } while (cSor.moveToNext())
            }
            cSor.close() // Close cursor
            db.close() // Close db
        } catch (e: Exception) {
            e.printStackTrace()
            return null // Return null on error
        }
        return songListFromDb
    }

    fun checkifIDExists(_id: Int): Boolean {
        var storeID = -1090
        val db = this.readableDatabase
        val query_params = "SELECT * FROM " + Staticated.TABLE_NAME + " WHERE SongID = '$_id'"
        val cSor = db.rawQuery(query_params, null)
        val exists = cSor.moveToFirst()
        cSor.close() // Close cursor
        db.close() // Close db
        return exists // Directly return if move to first was successful
    }

    fun deleteFavourite(_id: Int) {
        val db = this.writableDatabase
        db.delete(Staticated.TABLE_NAME, Staticated.COLUMN_ID + "=" + _id, null)
        db.close()
    }

    fun checkSize(): Int {
        var counter = 0
        val db = this.readableDatabase
        val query_params = "SELECT * FROM " + Staticated.TABLE_NAME
        val cSor = db.rawQuery(query_params, null)
        counter = cSor.count // Get count directly
        cSor.close()
        db.close()
        return counter
    }

    // --- NEW: Playlist Management Methods ---

    // Method to create a new playlist
    fun createPlaylist(playlistName: String): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(Staticated.PLAYLIST_NAME, playlistName)
        contentValues.put(Staticated.PLAYLIST_DATE_CREATED, System.currentTimeMillis())
        val playlistID = db.insert(Staticated.PLAYLIST_TABLE_NAME, null, contentValues)
        db.close()
        return playlistID
    }

    // Method to get all playlists
    fun getPlaylists(): ArrayList<Playlist> {
        val playlists = ArrayList<Playlist>()
        val db = this.readableDatabase
        val query = "SELECT ${Staticated.PLAYLIST_ID}, ${Staticated.PLAYLIST_NAME}, ${Staticated.PLAYLIST_DATE_CREATED} FROM ${Staticated.PLAYLIST_TABLE_NAME} ORDER BY ${Staticated.PLAYLIST_DATE_CREATED} DESC"
        val cursor = db.rawQuery(query, null)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(Staticated.PLAYLIST_ID)
            val nameColumn = it.getColumnIndexOrThrow(Staticated.PLAYLIST_NAME)
            val dateColumn = it.getColumnIndexOrThrow(Staticated.PLAYLIST_DATE_CREATED)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val date = it.getLong(dateColumn)

                // Get songs count for this playlist (optional, but good for UI)
                val songsCount = getSongsCountInPlaylist(id)

                playlists.add(Playlist(id, name, date, songsCount))
            }
        }
        db.close()
        return playlists
    }

    // Helper method to get the number of songs in a specific playlist
    fun getSongsCountInPlaylist(playlistId: Long): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM ${Staticated.PLAYLIST_SONGS_TABLE_NAME} WHERE ${Staticated.PS_PLAYLIST_ID} = $playlistId"
        val cursor = db.rawQuery(query, null)
        var count = 0
        cursor?.use {
            if (it.moveToFirst()) {
                count = it.getInt(0) // Column 0 is the count
            }
        }
        cursor?.close()
        return count
    }


    // Method to add a song to a specific playlist
    // We store song details directly in PlaylistSongsTable to avoid repeated MediaStore queries
    fun addSongToPlaylist(playlistId: Long, song: Songs): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(Staticated.PS_PLAYLIST_ID, playlistId)
        contentValues.put(Staticated.PS_SONG_ID, song.songID)
        contentValues.put(Staticated.PS_SONG_PATH, song.songData) // Store path for quick retrieval
        contentValues.put(Staticated.PS_DATE_ADDED, System.currentTimeMillis())
        val id = db.insert(Staticated.PLAYLIST_SONGS_TABLE_NAME, null, contentValues)
        db.close()
        return id
    }

    // Method to get songs from a specific playlist
    fun getSongsFromPlaylist(playlistId: Long): ArrayList<Songs> {
        val songsFromPlaylist = ArrayList<Songs>()
        val db = this.readableDatabase
        val query = "SELECT ${Staticated.PS_SONG_ID}, ${Staticated.PS_SONG_PATH} FROM ${Staticated.PLAYLIST_SONGS_TABLE_NAME} WHERE ${Staticated.PS_PLAYLIST_ID} = $playlistId"
        val cursor = db.rawQuery(query, null)

        cursor?.use {
            val songIdColumn = it.getColumnIndexOrThrow(Staticated.PS_SONG_ID)
            val songPathColumn = it.getColumnIndexOrThrow(Staticated.PS_SONG_PATH)

            while (it.moveToNext()) {
                val songID = it.getLong(songIdColumn)
                val songPath = it.getString(songPathColumn)

                // Note: We only have ID and Path here. You might need to query MediaStore
                // or pass more details from HomeFragment to fully reconstruct a Songs object.
                // For simplicity, we create a partial Songs object.
                // You'll need to retrieve title, artist, dateAdded, albumID from MediaStore later if needed fully.
                songsFromPlaylist.add(Songs(songID, null, null, songPath, 0L, 0L))
            }
        }
        db.close()
        return songsFromPlaylist
    }

    // Method to rename a playlist
    fun renamePlaylist(playlistId: Long, newName: String): Int {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(Staticated.PLAYLIST_NAME, newName)
        val rowsAffected = db.update(Staticated.PLAYLIST_TABLE_NAME, contentValues, "${Staticated.PLAYLIST_ID} = ?", arrayOf(playlistId.toString()))
        db.close()
        return rowsAffected
    }

    // Method to delete a playlist (and its associated songs in PlaylistSongsTable due to ON DELETE CASCADE)
    fun deletePlaylist(playlistId: Long): Int {
        val db = this.writableDatabase
        val rowsAffected = db.delete(Staticated.PLAYLIST_TABLE_NAME, "${Staticated.PLAYLIST_ID} = ?", arrayOf(playlistId.toString()))
        db.close()
        return rowsAffected
    }

    // Method to delete a specific song from a specific playlist
    fun deleteSongFromPlaylist(playlistId: Long, songId: Long): Int {
        val db = this.writableDatabase
        val rowsAffected = db.delete(Staticated.PLAYLIST_SONGS_TABLE_NAME, "${Staticated.PS_PLAYLIST_ID} = ? AND ${Staticated.PS_SONG_ID} = ?", arrayOf(playlistId.toString(), songId.toString()))
        db.close()
        return rowsAffected
    }
}