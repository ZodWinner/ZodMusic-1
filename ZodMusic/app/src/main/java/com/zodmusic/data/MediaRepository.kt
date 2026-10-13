package com.zodmusic.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val albumArt: Uri,
)

/** Capa que deve aparecer para a música: a escolhida pelo usuário ou a do álbum. */
fun coverOf(track: Track, custom: Map<Long, String>): Uri =
    custom[track.id]?.let { Uri.fromFile(File(it)) } ?: track.albumArt

object MediaRepository {
    private val albumArtBase = Uri.parse("content://media/external/audio/albumart")

    /** Lê as músicas do aparelho pelo MediaStore. */
    suspend fun load(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += Track(
                    id = id,
                    title = c.getString(iTitle) ?: "Sem título",
                    artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Artista desconhecido",
                    album = c.getString(iAlbum) ?: "",
                    durationMs = c.getLong(iDur),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    albumArt = ContentUris.withAppendedId(albumArtBase, c.getLong(iAlbumId)),
                )
            }
        }
        out
    }
}

/** Guarda as imagens escolhidas na pasta do app, para não depender da galeria. */
object CoverStore {
    private const val MAX_SIDE = 1024

    suspend fun save(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2

            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }
}
