package com.camlauncher.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.camlauncher.app.ui.theme.Background
import com.camlauncher.app.ui.theme.OnSurface
import com.camlauncher.app.ui.theme.OnSurfaceVariant
import com.camlauncher.app.ui.theme.Primary
import com.camlauncher.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import android.media.MediaMetadataRetriever
import android.media.ExifInterface

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val mimeType: String,
    val size: Long,
    val duration: Long = 0
)

data class BurstFolder(
    val name: String,
    val items: List<MediaItem>,
    val coverUri: Uri
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    previewUriStr: String? = null,
    previewType: String? = null,
    onPlayMedia: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var selectedTabIndex by remember(previewType) { 
        val initialTab = when (previewType) {
            "video" -> 0
            "burst" -> 1
            "audio" -> 2
            else -> 0
        }
        mutableStateOf(initialTab) 
    }
    val tabs = listOf("Videos", "Photo Bursts", "Audio")
    val scope = rememberCoroutineScope()

    var activePreviewUri by remember(previewUriStr) { mutableStateOf(previewUriStr) }
    var activePreviewType by remember(previewType) { mutableStateOf(previewType) }

    // State
    var videos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var burstFolders by remember { mutableStateOf<List<BurstFolder>>(emptyList()) }
    var audios by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<Any?>(null) } // MediaItem or BurstFolder

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    fun loadData(showRefreshIndicator: Boolean = false) {
        scope.launch {
            if (showRefreshIndicator) isRefreshing = true else isLoading = true
            val settings = com.camlauncher.app.data.SettingsStore(context)
            val customUriStr = settings.customStorageUri.first()
            val isCustom = settings.storageLocation.first() == com.camlauncher.app.data.StorageLocation.CUSTOM

            withContext(Dispatchers.IO) {
                // Load standard MediaStore items
                val msVideos = loadVideos(context)
                val msPhotos = loadPhotos(context)
                val msAudios = loadAudios(context)
                
                // Load custom SAF items if applicable
                if (isCustom && customUriStr != null) {
                    val safVideos = loadSafFiles(context, customUriStr, "video/")
                    val safPhotos = loadSafFiles(context, customUriStr, "image/")
                    val safAudios = loadSafFiles(context, customUriStr, "audio/")
                    
                    // Merge and sort
                    videos = (msVideos + safVideos).distinctBy { it.name }.sortedByDescending { it.dateAdded }
                    burstFolders = groupPhotosIntoBursts((msPhotos + safPhotos).distinctBy { it.name })
                    audios = (msAudios + safAudios).distinctBy { it.name }.sortedByDescending { it.dateAdded }
                } else {
                    videos = msVideos
                    burstFolders = groupPhotosIntoBursts(msPhotos)
                    audios = msAudios
                }
            }
            isLoading = false
            isRefreshing = false
        }
    }

    // ADDED: Permission launcher for multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Reload data regardless, but if granted, the unredacted URI will now work
        loadData()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_MEDIA_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            loadData()
        }
    }

    // Auto-refresh: reload gallery when a recording finishes saving
    val recordingState by com.camlauncher.app.service.RecordingService.stateFlow.collectAsState()
    LaunchedEffect(recordingState) {
        if (recordingState == com.camlauncher.app.data.RecordingState.IDLE && !isLoading) {
            // Small delay to let MediaStore index the newly saved file
            delay(1500L)
            loadData(showRefreshIndicator = true)
        }
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text("Confirm Delete", fontWeight = FontWeight.Bold)
            },
            text = {
                val msg = if (itemToDelete is BurstFolder) {
                    "Are you sure you want to delete this photo burst folder? (${(itemToDelete as BurstFolder).items.size} photos)"
                } else {
                    "Are you sure you want to delete this media?"
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = itemToDelete
                        itemToDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    if (toDelete is BurstFolder) {
                                        toDelete.items.forEach { com.camlauncher.app.data.StorageHelper.deleteMedia(context, it.uri) }
                                    } else if (toDelete is MediaItem) {
                                        com.camlauncher.app.data.StorageHelper.deleteMedia(context, toDelete.uri)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            loadData()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTabIndex == index) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { loadData(showRefreshIndicator = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (selectedTabIndex) {
                        0 -> VideosList(videos, imageLoader, onClick = { openMedia(context, it, onPlayMedia) }, onDelete = { itemToDelete = it })
                        1 -> BurstFoldersGrid(burstFolders, onClick = { openMedia(context, it.items.first(), onPlayMedia) }, onDelete = { itemToDelete = it })
                        2 -> AudioList(audios, onClick = { openMedia(context, it, onPlayMedia) }, onDelete = { itemToDelete = it })
                    }
                }
            }
        }
    }

    // Overlay for Preview
    if (activePreviewUri != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = { activePreviewUri = null }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks to avoid dismissing when clicking the card itself
                    )
            ) {
                val uri = try { Uri.parse(activePreviewUri) } catch(e: Exception) { null }
                if (uri != null) {
                    when (activePreviewType) {
                        "video" -> {
                            val video = videos.find { it.uri.lastPathSegment == uri.lastPathSegment } ?: MediaItem(
                                id = 0,
                                uri = uri,
                                name = "New Video",
                                dateAdded = System.currentTimeMillis() / 1000,
                                mimeType = "video/mp4",
                                size = 0,
                                duration = 0
                            )
                            VideoPreviewCard(video, imageLoader, onClose = { activePreviewUri = null }, onPlay = { openMedia(context, video, onPlayMedia) })
                        }
                        "burst" -> {
                            val folder = burstFolders.find { it.coverUri.lastPathSegment == uri.lastPathSegment } 
                                ?: burstFolders.find { it.items.any { item -> item.uri.lastPathSegment == uri.lastPathSegment } }
                                ?: BurstFolder(
                                    name = "New Burst",
                                    items = listOf(MediaItem(id = 0, uri = uri, name = "Burst Image", dateAdded = System.currentTimeMillis() / 1000, mimeType = "image/jpeg", size = 0)),
                                    coverUri = uri
                                )
                            BurstPreviewCard(folder, onClose = { activePreviewUri = null }, onPlay = { openMedia(context, it, onPlayMedia) })
                        }
                        "audio" -> {
                            val audio = audios.find { it.uri.lastPathSegment == uri.lastPathSegment } ?: MediaItem(
                                id = 0,
                                uri = uri,
                                name = "New Audio",
                                dateAdded = System.currentTimeMillis() / 1000,
                                mimeType = "audio/mp4",
                                size = 0,
                                duration = 0
                            )
                            AudioPreviewCard(audio, onClose = { activePreviewUri = null }, onPlay = { openMedia(context, audio, onPlayMedia) })
                        }
                        else -> activePreviewUri = null
                    }
                } else {
                    activePreviewUri = null
                }
            }
        }
    }
}

@Composable
fun VideosList(videos: List<MediaItem>, imageLoader: ImageLoader, onClick: (MediaItem) -> Unit, onDelete: (MediaItem) -> Unit) {
    if (videos.isEmpty()) {
        EmptyState("No videos recorded yet")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos) { video ->
                VideoItem(video, imageLoader, onClick, onDelete)
            }
        }
    }
}

@Composable
fun BurstFoldersGrid(folders: List<BurstFolder>, onClick: (BurstFolder) -> Unit, onDelete: (BurstFolder) -> Unit) {
    if (folders.isEmpty()) {
        EmptyState("No photo bursts taken yet")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(folders) { folder ->
                BurstFolderItem(folder, onClick, onDelete)
            }
        }
    }
}

@Composable
fun AudioList(audios: List<MediaItem>, onClick: (MediaItem) -> Unit, onDelete: (MediaItem) -> Unit) {
    if (audios.isEmpty()) {
        EmptyState("No audio recorded yet")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(audios) { audio ->
                AudioItem(audio, onClick, onDelete)
            }
        }
    }
}

@Composable
fun VideoItem(video: MediaItem, imageLoader: ImageLoader, onClick: (MediaItem) -> Unit, onDelete: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val hashStore = remember { com.camlauncher.app.data.VideoHashStore(context) }
    val existingHash = remember(video.uri) { hashStore.getHash(video.uri.toString()) }
    val model = remember(video.uri) {
        ImageRequest.Builder(context)
            .data(video.uri)
            .videoFrameMillis(1000)
            .build()
    }
    var showHashDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        MediaDetailsDialog(item = video, onDismiss = { showDetailsDialog = false })
    }

    if (showHashDialog && existingHash != null) {
        AlertDialog(
            onDismissRequest = { showHashDialog = false },
            title = { Text("SHA-256 Hash", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = existingHash,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHashDialog = false }) { Text("Close") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable { onClick(video) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            // Delete Button
            IconButton(
                onClick = { onDelete(video) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }

            // Overlay Actions (Top Left)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Hash and Share icons ONLY appear if a hash exists
                if (existingHash != null) {
                    IconButton(onClick = { showHashDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "View Hash",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, 
                                    "SHA-256: $existingHash\nFile: ${video.name}")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Hash"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Hash",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // 2. INFO ICON IS ALWAYS VISIBLE (Moved outside the hash check!)
                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "View Details",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = video.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDate(video.dateAdded),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = formatDuration(video.duration),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun BurstFolderItem(folder: BurstFolder, onClick: (BurstFolder) -> Unit, onDelete: (BurstFolder) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick(folder) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            // Delete Button
            IconButton(
                onClick = { onDelete(folder) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }

            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "${folder.items.size} photos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatDate(folder.items.first().dateAdded),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AudioItem(audio: MediaItem, onClick: (MediaItem) -> Unit, onDelete: (MediaItem) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(audio) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        var showDetailsDialog by remember { mutableStateOf(false) }
        if (showDetailsDialog) {
            MediaDetailsDialog(item = audio, onDismiss = { showDetailsDialog = false })
        }
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Audiotrack,
                    contentDescription = "Audio",
                    tint = Primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDate(audio.dateAdded)} • ${formatDuration(audio.duration)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(onClick = { onDelete(audio) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = { showDetailsDialog = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun openMedia(context: Context, item: MediaItem, onPlayMedia: (String, String) -> Unit) {
    if (item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/")) {
        onPlayMedia(item.uri.toString(), item.mimeType)
    } else {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }
}

private fun loadVideos(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION
    )
    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Movies/CamLauncher%")
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Video"
            val date = cursor.getLong(dateCol) * 1000L
            val mime = cursor.getString(mimeCol) ?: "video/mp4"
            val size = cursor.getLong(sizeCol)
            val duration = cursor.getLong(durationCol)
            
            val uri = ContentUris.withAppendedId(collection, id)
            items.add(MediaItem(id, uri, name, date, mime, size, duration))
        }
    }
    return items
}

private fun loadPhotos(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Pictures/CamLauncher%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Photo"
            val date = cursor.getLong(dateCol) * 1000L
            val mime = cursor.getString(mimeCol) ?: "image/jpeg"
            val size = cursor.getLong(sizeCol)
            val uri = ContentUris.withAppendedId(collection, id)
            items.add(MediaItem(id, uri, name, date, mime, size))
        }
    }
    return items
}

private fun groupPhotosIntoBursts(photos: List<MediaItem>): List<BurstFolder> {
    val sorted = photos.sortedByDescending { it.dateAdded }
    if (sorted.isEmpty()) return emptyList()

    val folders = mutableListOf<BurstFolder>()
    val sessionGroups = mutableMapOf<String, MutableList<MediaItem>>()
    val fallbackGroup = mutableListOf<MediaItem>()

    for (photo in sorted) {
        val name = photo.name
        // Expected format: CamLauncher_Burst_SESSIONID_TIMESTAMP.jpg
        val parts = name.split("_")
        if (parts.size >= 4 && parts[1] == "Burst") {
            // New format: CamLauncher_Burst_yyyyMMddHHmmss_...
            val sessionId = parts[2]
            sessionGroups.getOrPut(sessionId) { mutableListOf() }.add(photo)
        } else if (parts.size >= 5 && parts[1] == "Burst") {
            // Handle edge case if previous underscored format is still present: 
            // CamLauncher_Burst_yyyyMMdd_HHmmss_... -> parts[2] + parts[3]
            val sessionId = parts[2] + parts[3]
            sessionGroups.getOrPut(sessionId) { mutableListOf() }.add(photo)
        } else {
            fallbackGroup.add(photo)
        }
    }

    // Add session groups to folders
    sessionGroups.keys.sortedDescending().forEach { sessionId ->
        val group = sessionGroups[sessionId]!!
        val folderName = "Burst ${formatBurstName(group.first().dateAdded)}"
        folders.add(BurstFolder(folderName, group, group.first().uri))
    }

    // Process fallback group with time-based logic
    if (fallbackGroup.isNotEmpty()) {
        var currentGroup = mutableListOf<MediaItem>()
        for (photo in fallbackGroup) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(photo)
            } else {
                val lastPhoto = currentGroup.last()
                if (Math.abs(lastPhoto.dateAdded - photo.dateAdded) < 1500) {
                    currentGroup.add(photo)
                } else {
                    val folderName = "Burst ${formatBurstName(currentGroup.first().dateAdded)}"
                    folders.add(BurstFolder(folderName, currentGroup, currentGroup.first().uri))
                    currentGroup = mutableListOf(photo)
                }
            }
        }
        if (currentGroup.isNotEmpty()) {
            val folderName = "Burst ${formatBurstName(currentGroup.first().dateAdded)}"
            folders.add(BurstFolder(folderName, currentGroup, currentGroup.first().uri))
        }
    }

    return folders.sortedByDescending { it.items.first().dateAdded }
}

private fun loadAudios(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%Music/CamLauncher%")
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Audio"
            val date = cursor.getLong(dateCol) * 1000L
            val mime = cursor.getString(mimeCol) ?: "audio/mp4"
            val size = cursor.getLong(sizeCol)
            val duration = cursor.getLong(durationCol)
            
            val uri = ContentUris.withAppendedId(collection, id)
            items.add(MediaItem(id, uri, name, date, mime, size, duration))
        }
    }
    return items
}

fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatBurstName(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun VideoPreviewCard(video: MediaItem, imageLoader: ImageLoader, onClose: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val hashStore = remember { com.camlauncher.app.data.VideoHashStore(context) }
    val existingHash = remember(video.uri) { hashStore.getHash(video.uri.toString()) }
    var showHashDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showHashDialog && existingHash != null) {
        AlertDialog(
            onDismissRequest = { showHashDialog = false },
            title = { Text("SHA-256 Hash", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = existingHash,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHashDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDetailsDialog) {
        MediaDetailsDialog(item = video, onDismiss = { showDetailsDialog = false })
    }

    val model = remember(video.uri) {
        ImageRequest.Builder(context)
            .data(video.uri)
            .videoFrameMillis(1000)
            .build()
    }
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                
                IconButton(
                    onClick = { showDetailsDialog = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "Details", tint = Color.White)
                }

                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Primary.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = video.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Saved: ${formatDate(video.dateAdded)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Length: ${formatDuration(video.duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (existingHash != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = existingHash.take(16) + "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showHashDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("View", style = MaterialTheme.typography.labelMedium, color = Primary)
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT,
                                        "SHA-256: $existingHash\nFile: ${video.name}")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Hash"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Hash", tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BurstPreviewCard(folder: BurstFolder, onClose: () -> Unit, onPlay: (MediaItem) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { folder.items.size })
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val item = folder.items[page]
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Burst image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPlay(item) }
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${folder.items.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = folder.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Saved: ${formatDate(folder.items.first().dateAdded)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AudioPreviewCard(audio: MediaItem, onClose: () -> Unit, onPlay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = "Audio", tint = Primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = audio.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(text = "Saved: ${formatDate(audio.dateAdded)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Length: ${formatDuration(audio.duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Audio")
                }
            }
        }
    }
}

@Composable
fun MediaDetailsDialog(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hashStore = remember { com.camlauncher.app.data.VideoHashStore(context) }
    val existingHash = remember(item.uri) { hashStore.getHash(item.uri.toString()) }
    
    // State variables for dynamic location fetching
    var dynamicLat by remember { mutableStateOf<Double?>(null) }
    var dynamicLon by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        val loc = extractLocationFromMedia(context, item.uri, item.mimeType)
        if (loc != null) {
            dynamicLat = loc.first
            dynamicLon = loc.second
        }
        isFetchingLocation = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailRow("Name", item.name)
                DetailRow("Date", formatDate(item.dateAdded))
                DetailRow("Type", item.mimeType)
                DetailRow("Size", String.format("%.2f MB", item.size / (1024.0 * 1024.0)))
                if (item.duration > 0) {
                    DetailRow("Duration", formatDuration(item.duration))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Location", style = MaterialTheme.typography.labelLarge, color = Primary)
                
                if (isFetchingLocation) {
                    Text("Extracting location...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (dynamicLat != null && dynamicLon != null) {
                    DetailRow("Latitude", String.format("%.6f", dynamicLat))
                    DetailRow("Longitude", String.format("%.6f", dynamicLon))
                    
                    TextButton(
                        onClick = {
                            val uri = "geo:${dynamicLat},${dynamicLon}?q=${dynamicLat},${dynamicLon}"
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open in Maps", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text("No location data available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (existingHash != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Evidence Integrity", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                    DetailRow("SHA-256", existingHash, isMonospaced = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, isMonospaced: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = if (isMonospaced) MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

suspend fun extractLocationFromMedia(context: android.content.Context, uri: android.net.Uri, mimeType: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val originalUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                android.provider.MediaStore.setRequireOriginal(uri)
            } catch (e: Exception) {
                uri
            }
        } else {
            uri
        }

        // ExifInterface now natively supports MP4 EXIF on modern Android, making it highly reliable
        context.contentResolver.openInputStream(originalUri)?.use { stream ->
            val exif = android.media.ExifInterface(stream)
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                return@withContext Pair(latLong[0].toDouble(), latLong[1].toDouble())
            }
        }

        // Fallback specifically for audio files or if EXIF fails
        if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, originalUri)
            val locationString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION)
            retriever.release()
            
            if (locationString != null) {
                // Parses standard ISO-6709 format (e.g., "+37.7749-122.4194/")
                val match = Regex("([+-][0-9.]+)([+-][0-9.]+)/?").find(locationString)
                if (match != null) {
                    val lat = match.groupValues[1].toDoubleOrNull()
                    val lon = match.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        return@withContext Pair(lat, lon)
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("GalleryScreen", "Location extraction failed: ${e.message}")
    }
    return@withContext null
}

private fun loadSafFiles(context: android.content.Context, treeUriStr: String, mimePrefix: String): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    try {
        val treeUri = Uri.parse(treeUriStr)
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.DocumentsContract.Document.COLUMN_SIZE
        )
        
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_SIZE)
            
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeCol) ?: ""
                if (!mime.startsWith(mimePrefix)) continue
                
                val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                
                // Extract duration for video/audio to avoid 00:00 display
                var duration = 0L
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        retriever.release()
                    } catch (e: Exception) {
                        Log.e("GalleryScreen", "Failed to extract SAF duration for $uri", e)
                    }
                }

                items.add(MediaItem(
                    id = uri.hashCode().toLong(),
                    uri = uri,
                    name = cursor.getString(nameCol) ?: "Unknown",
                    dateAdded = cursor.getLong(dateCol),
                    mimeType = mime,
                    size = cursor.getLong(sizeCol),
                    duration = duration
                ))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return items
}
