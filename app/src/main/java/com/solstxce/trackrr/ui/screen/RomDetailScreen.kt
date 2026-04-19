package com.solstxce.trackrr.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solstxce.trackrr.data.downloader.AssetDownloadState
import com.solstxce.trackrr.data.downloader.DownloadPhase
import com.solstxce.trackrr.data.downloader.VerificationPhase
import com.solstxce.trackrr.data.model.GithubAsset
import com.solstxce.trackrr.viewmodel.RomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomDetailScreen(
    releaseId: Long,
    viewModel: RomViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val releases = viewModel.releases.collectAsState().value
    val downloadStates = viewModel.downloadStates.collectAsState().value
    val release = releases.find { it.id == releaseId }

    val imageUrls = remember(release?.body) { extractImageUrls(release?.body) }
    val bannerImage = imageUrls.firstOrNull()
    val galleryImages = if (imageUrls.size > 1) imageUrls.drop(1) else emptyList()
    val markdownBody = remember(release?.body) { cleanReleaseBodyForMarkdown(release?.body) }
    val primaryAsset = release?.assets?.maxByOrNull { it.downloadCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(release?.name ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (release == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Release not found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ReleaseBannerImage(
                        imageUrl = bannerImage,
                        title = release.name ?: release.tagName,
                        subtitle = release.publishedAt?.let(::formatPublishedAt) ?: release.tagName,
                        height = 260.dp,
                        cornerRadius = 12.dp
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = {}, enabled = false, label = { Text(release.tagName) })
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (release.prerelease) "Prerelease" else "Stable") }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${release.assets.size} assets") }
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { openUrl(context, release.htmlUrl) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open GitHub")
                        }

                        if (primaryAsset != null) {
                            val primaryState = downloadStates[primaryAsset.id]
                            Button(
                                onClick = { viewModel.onDownloadAction(release, primaryAsset) },
                                enabled = primaryState?.phase != DownloadPhase.PREPARING
                            ) {
                                Icon(
                                    imageVector = if (primaryState?.phase == DownloadPhase.DOWNLOADING) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.Download
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(primaryDownloadLabel(primaryState))
                            }
                        }
                    }
                }

                if (galleryImages.isNotEmpty()) {
                    item {
                        Text(
                            text = "More Images",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(galleryImages) { imageUrl ->
                                Box(
                                    modifier = Modifier.clickable { openUrl(context, imageUrl) }
                                ) {
                                    ReleaseBannerImage(
                                        imageUrl = imageUrl,
                                        height = 140.dp,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.width(220.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Release Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownContent(
                        markdown = if (markdownBody.isBlank()) {
                            "No release description provided."
                        } else {
                            markdownBody
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (release.assets.isNotEmpty()) {
                    item {
                        Text(
                            text = "Assets",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(release.assets) { asset ->
                        AssetItem(
                            asset = asset,
                            state = downloadStates[asset.id],
                            onDownloadToggle = { viewModel.onDownloadAction(release, asset) },
                            onOpenExternal = { openUrl(context, asset.browserDownloadUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssetItem(
    asset: GithubAsset,
    state: AssetDownloadState?,
    onDownloadToggle: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val progress = state?.progress ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(asset.name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Downloads: ${asset.downloadCount}", style = MaterialTheme.typography.bodySmall)
                Text("Size: ${formatBytes(asset.size)}", style = MaterialTheme.typography.bodySmall)
            }

            if (state != null && state.phase != DownloadPhase.IDLE) {
                Spacer(modifier = Modifier.height(10.dp))

                if (
                    state.phase == DownloadPhase.DOWNLOADING ||
                    state.phase == DownloadPhase.PAUSED ||
                    state.phase == DownloadPhase.COMPLETED
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val progressText = if (state.totalBytes > 0L) {
                    "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
                } else {
                    formatBytes(state.downloadedBytes)
                }

                Text(
                    text = "$progressText • ${state.threadCount} thread(s)${if (state.resumable) " • resume on" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.message?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (state.verificationPhase) {
                            VerificationPhase.VERIFIED -> Color(0xFF2E7D32)
                            VerificationPhase.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                state.computedSha256?.let { hash ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SHA-256: ${hash.take(20)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDownloadToggle,
                    enabled = state?.phase != DownloadPhase.PREPARING
                ) {
                    Icon(
                        imageVector = if (state?.phase == DownloadPhase.DOWNLOADING) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.Download
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(downloadActionLabel(state))
                }

                TextButton(onClick = onOpenExternal) {
                    Text("Open URL")
                }
            }
        }
    }
}

private fun primaryDownloadLabel(state: AssetDownloadState?): String {
    return when (state?.phase) {
        DownloadPhase.PREPARING -> "Preparing"
        DownloadPhase.DOWNLOADING -> "Pause"
        DownloadPhase.PAUSED -> "Resume"
        DownloadPhase.COMPLETED -> "Re-download"
        DownloadPhase.FAILED -> "Retry"
        else -> "Top Download"
    }
}

private fun downloadActionLabel(state: AssetDownloadState?): String {
    return when (state?.phase) {
        DownloadPhase.PREPARING -> "Preparing"
        DownloadPhase.DOWNLOADING -> "Pause"
        DownloadPhase.PAUSED -> "Resume"
        DownloadPhase.COMPLETED -> "Re-download"
        DownloadPhase.FAILED -> "Retry"
        else -> "Download"
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
