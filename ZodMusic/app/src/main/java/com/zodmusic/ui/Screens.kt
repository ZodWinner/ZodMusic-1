package com.zodmusic.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zodmusic.MainViewModel
import com.zodmusic.PlayerController
import com.zodmusic.data.PlaylistEntity
import com.zodmusic.data.Track
import com.zodmusic.data.coverOf
import java.io.File
import kotlin.math.max

@Composable
fun ZodTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private sealed interface CoverTarget {
    data class OfTrack(val id: Long) : CoverTarget
    data class OfPlaylist(val id: Long) : CoverTarget
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

// ---------------------------------------------------------------------------
// Raiz do app
// ---------------------------------------------------------------------------

@Composable
fun ZodApp(vm: MainViewModel, player: PlayerController) {
    val tracks by vm.tracks.collectAsState()
    val covers by vm.covers.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val current by player.current.collectAsState()
    val playing by player.isPlaying.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableLongStateOf(-1L) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<CoverTarget?>(null) }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        val t = target
        if (uri != null && t != null) {
            when (t) {
                is CoverTarget.OfTrack -> vm.setTrackCover(t.id, uri)
                is CoverTarget.OfPlaylist -> vm.setPlaylistCover(t.id, uri)
            }
        }
        target = null
    }
    val pickCover: (CoverTarget) -> Unit = {
        target = it
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    val playList: (List<Track>, Int) -> Unit = { list, index -> player.play(vm.mediaItems(list), index) }

    BackHandler(showPlayer) { showPlayer = false }
    BackHandler(!showPlayer && tab == 1 && openPlaylist != -1L) { openPlaylist = -1L }

    val item = current
    if (showPlayer && item != null) {
        val id = item.mediaId.toLongOrNull()
        val custom = id?.let { covers[it] }?.let { Uri.fromFile(File(it)) }
        PlayerScreen(
            player = player,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            cover = custom ?: item.mediaMetadata.artworkUri,
            playing = playing,
            onClose = { showPlayer = false },
            onEditCover = { id?.let { pickCover(CoverTarget.OfTrack(it)) } },
            onResetCover = { id?.let { vm.clearTrackCover(it) } },
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                if (item != null) {
                    val id = item.mediaId.toLongOrNull()
                    val cover = id?.let { covers[it] }?.let { Uri.fromFile(File(it)) }
                        ?: item.mediaMetadata.artworkUri
                    MiniPlayer(
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        artist = item.mediaMetadata.artist?.toString().orEmpty(),
                        cover = cover,
                        playing = playing,
                        onToggle = { player.toggle() },
                        onOpen = { showPlayer = true },
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.LibraryMusic, null) },
                        label = { Text("Músicas") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.QueueMusic, null) },
                        label = { Text("Playlists") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                tab == 0 -> LibraryScreen(
                    tracks = tracks,
                    covers = covers,
                    playlists = playlists,
                    playingId = item?.mediaId?.toLongOrNull(),
                    onPlay = playList,
                    onEditCover = { pickCover(CoverTarget.OfTrack(it)) },
                    onResetCover = { vm.clearTrackCover(it) },
                    onAddToPlaylist = { pid, tid -> vm.addTracks(pid, listOf(tid)) },
                )
                openPlaylist == -1L -> PlaylistsScreen(
                    playlists = playlists,
                    onOpen = { openPlaylist = it },
                    onCreate = { vm.createPlaylist(it) },
                )
                else -> {
                    val playlist = playlists.firstOrNull { it.id == openPlaylist }
                    if (playlist == null) {
                        openPlaylist = -1L
                    } else {
                        PlaylistDetail(
                            vm = vm,
                            playlist = playlist,
                            allTracks = tracks,
                            covers = covers,
                            playingId = item?.mediaId?.toLongOrNull(),
                            onBack = { openPlaylist = -1L },
                            onPlay = playList,
                            onEditPlaylistCover = { pickCover(CoverTarget.OfPlaylist(playlist.id)) },
                            onEditTrackCover = { pickCover(CoverTarget.OfTrack(it)) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------

@Composable
private fun Cover(model: Any?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    cover: Any?,
    playing: Boolean,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    leading: @Composable () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${track.artist} · ${formatTime(track.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        leading()
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    cover: Any?,
    playing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(cover, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pausar" else "Tocar")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Aba Músicas
// ---------------------------------------------------------------------------

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    covers: Map<Long, String>,
    playlists: List<PlaylistEntity>,
    playingId: Long?,
    onPlay: (List<Track>, Int) -> Unit,
    onEditCover: (Long) -> Unit,
    onResetCover: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
) {
    var pickPlaylistFor by remember { mutableStateOf<Track?>(null) }

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma música encontrada no aparelho.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            TrackRow(
                track = t,
                cover = coverOf(t, covers),
                playing = t.id == playingId,
                onClick = { onPlay(tracks, index) },
                actions = buildList {
                    add("Trocar capa" to { onEditCover(t.id) })
                    if (covers.containsKey(t.id)) add("Voltar à capa original" to { onResetCover(t.id) })
                    add("Adicionar à playlist" to { pickPlaylistFor = t })
                },
            )
        }
    }

    pickPlaylistFor?.let { t ->
        AlertDialog(
            onDismissRequest = { pickPlaylistFor = null },
            title = { Text("Adicionar à playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Você ainda não tem playlists. Crie uma na aba Playlists.")
                } else {
                    Column {
                        playlists.forEach { p ->
                            Text(
                                p.name,
                                Modifier.fillMaxWidth()
                                    .clickable { onAddToPlaylist(p.id, t.id); pickPlaylistFor = null }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPlaylistFor = null }) { Text("Fechar") } },
        )
    }
}

// ---------------------------------------------------------------------------
// Aba Playlists
// ---------------------------------------------------------------------------

@Composable
private fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Toque em + para criar sua primeira playlist.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(p.coverPath?.let { Uri.fromFile(File(it)) }, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, "Nova playlist") }
    }

    if (creating) {
        NameDialog(
            title = "Nova playlist",
            initial = "",
            confirmLabel = "Criar",
            onDismiss = { creating = false },
            onConfirm = { onCreate(it); creating = false },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nome") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PlaylistDetail(
    vm: MainViewModel,
    playlist: PlaylistEntity,
    allTracks: List<Track>,
    covers: Map<Long, String>,
    playingId: Long?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onEditPlaylistCover: () -> Unit,
    onEditTrackCover: (Long) -> Unit,
) {
    val tracksFlow = remember(playlist.id) { vm.playlistTracks(playlist.id) }
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(
                        playlist.coverPath?.let { Uri.fromFile(File(it)) },
                        112.dp,
                        Modifier.clickable(onClick = onEditPlaylistCover),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${tracks.size} músicas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Tocar")
                    }
                    OutlinedButton(onClick = { adding = true }) { Text("Adicionar") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEditPlaylistCover) { Text("Trocar capa") }
                    TextButton(onClick = { renaming = true }) { Text("Renomear") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Excluir") }
                }
            }
            HorizontalDivider()
        }

        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            TrackRow(
                track = t,
                cover = coverOf(t, covers),
                playing = t.id == playingId,
                onClick = { onPlay(tracks, index) },
                actions = listOf(
                    "Trocar capa" to { onEditTrackCover(t.id) },
                    "Remover da playlist" to { vm.removeTrack(playlist.id, t.id) },
                ),
                leading = {
                    IconButton(
                        onClick = { vm.moveTrack(playlist.id, tracks, index, index - 1) },
                        enabled = index > 0,
                    ) { Icon(Icons.Filled.ArrowUpward, "Subir") }
                    IconButton(
                        onClick = { vm.moveTrack(playlist.id, tracks, index, index + 1) },
                        enabled = index < tracks.lastIndex,
                    ) { Icon(Icons.Filled.ArrowDownward, "Descer") }
                },
            )
        }
    }

    if (adding) {
        PickTracksDialog(
            all = allTracks.filter { a -> tracks.none { it.id == a.id } },
            onDismiss = { adding = false },
            onConfirm = { ids -> vm.addTracks(playlist.id, ids); adding = false },
        )
    }
    if (renaming) {
        NameDialog(
            title = "Renomear playlist",
            initial = playlist.name,
            confirmLabel = "Salvar",
            onDismiss = { renaming = false },
            onConfirm = { vm.renamePlaylist(playlist.id, it); renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir playlist?") },
            text = { Text("A playlist \"${playlist.name}\" será removida. As músicas continuam no aparelho.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(playlist.id); confirmDelete = false; onBack() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PickTracksDialog(
    all: List<Track>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar músicas") },
        text = {
            if (all.isEmpty()) {
                Text("Todas as músicas do aparelho já estão nesta playlist.")
            } else {
                LazyColumn(Modifier.height(360.dp)) {
                    itemsIndexed(all, key = { _, t -> t.id }) { _, t ->
                        val checked = t.id in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (checked) selected.remove(t.id) else selected.add(t.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    t.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Adicionar (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------
// Player
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
    player: PlayerController,
    title: String,
    artist: String,
    cover: Any?,
    playing: Boolean,
    onClose: () -> Unit,
    onEditCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar player") }
                Row {
                    IconButton(onClick = onEditCover) { Icon(Icons.Filled.Image, "Trocar capa") }
                    IconButton(onClick = onResetCover) { Icon(Icons.Filled.Delete, "Voltar à capa original") }
                }
            }
            Spacer(Modifier.weight(1f))
            Cover(cover, 300.dp)
            Spacer(Modifier.height(32.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))
            Slider(
                value = dragging ?: position.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..max(duration, 1L).toFloat(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((dragging ?: position.toFloat()).toLong()), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Anterior", Modifier.size(36.dp))
                }
                FloatingActionButton(onClick = { player.toggle() }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pausar" else "Tocar")
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Próxima", Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
