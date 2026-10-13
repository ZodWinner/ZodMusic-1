package com.zodmusic

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zodmusic.data.CoverStore
import com.zodmusic.data.MediaRepository
import com.zodmusic.data.PlaylistEntity
import com.zodmusic.data.PlaylistTrack
import com.zodmusic.data.Track
import com.zodmusic.data.TrackCover
import com.zodmusic.data.ZodDb
import com.zodmusic.data.coverOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ZodDb.get(app).dao()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    /** trackId -> caminho da capa escolhida pelo usuário. */
    val covers: StateFlow<Map<Long, String>> = dao.covers()
        .map { list -> list.associate { it.trackId to it.coverPath } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val playlists: StateFlow<List<PlaylistEntity>> = dao.playlists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load() {
        viewModelScope.launch { _tracks.value = MediaRepository.load(getApplication()) }
    }

    fun playlistTracks(id: Long): Flow<List<Track>> =
        combine(dao.playlistTracks(id), tracks) { rows, all ->
            val byId = all.associateBy { it.id }
            rows.mapNotNull { byId[it.trackId] }
        }

    fun mediaItems(list: List<Track>): List<MediaItem> {
        val custom = covers.value
        return list.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(t.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .setArtworkUri(coverOf(t, custom))
                        .build(),
                )
                .build()
        }
    }

    // ---- Playlists ----

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { dao.insertPlaylist(PlaylistEntity(name = clean)) }
    }

    fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { dao.renamePlaylist(id, clean) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { dao.deletePlaylist(id) }
    }

    fun addTracks(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch {
            val current = dao.playlistTracksOnce(playlistId)
            val existing = current.map { it.trackId }.toSet()
            val start = (current.maxOfOrNull { it.position } ?: -1) + 1
            val rows = trackIds
                .filter { it !in existing }
                .mapIndexed { i, id -> PlaylistTrack(playlistId, id, start + i) }
            dao.insertTracks(rows)
        }
    }

    fun removeTrack(playlistId: Long, trackId: Long) {
        viewModelScope.launch { dao.removeTrack(playlistId, trackId) }
    }

    /** Move uma música de uma posição para outra dentro da playlist. */
    fun moveTrack(playlistId: Long, current: List<Track>, from: Int, to: Int) {
        if (from !in current.indices || to !in current.indices) return
        val ids = current.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { dao.saveOrder(playlistId, ids) }
    }

    // ---- Capas ----

    fun setTrackCover(trackId: Long, source: Uri) {
        viewModelScope.launch {
            val path = CoverStore.save(getApplication(), source) ?: return@launch
            dao.setCover(TrackCover(trackId, path))
        }
    }

    fun clearTrackCover(trackId: Long) {
        viewModelScope.launch { dao.clearCover(trackId) }
    }

    fun setPlaylistCover(playlistId: Long, source: Uri) {
        viewModelScope.launch {
            val path = CoverStore.save(getApplication(), source) ?: return@launch
            dao.setPlaylistCover(playlistId, path)
        }
    }
}
