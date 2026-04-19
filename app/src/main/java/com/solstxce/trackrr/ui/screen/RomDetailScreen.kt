package com.solstxce.trackrr.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                        height = 260.dp
                    )
                }

                    item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(release.tagName) },
                            enabled = false
                        )

                        AssistChip(
                            onClick = {},
                            label = { Text(if (release.prerelease) "Prerelease" else "Stable") },
                            enabled = false
                        )

                        AssistChip(
                            onClick = {},
                            label = { Text("${release.assets.size} assets") },
                            enabled = false
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
                            Button(onClick = { openUrl(context, primaryAsset.browserDownloadUrl) }) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                Text("Top Download")
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
                                    modifier = Modifier
                                        .clickable { openUrl(context, imageUrl) }
                                ) {
                                    ReleaseBannerImage(
                                        imageUrl = imageUrl,
                                        height = 140.dp,
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
                        markdown = if (markdownBody.isBlank()) "No release description provided." else markdownBody,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (release.assets.isNotEmpty()) {
                    item {
                        Text("Assets:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    items(release.assets) { asset ->
                        AssetItem(asset = asset, onOpen = { openUrl(context, asset.browserDownloadUrl) })
                    }
                }
            }
        }
    }
}

@Composable
fun AssetItem(asset: GithubAsset, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(asset.name, fontWeight = FontWeight.Bold)
            Text("Downloads: ${asset.downloadCount}", style = MaterialTheme.typography.bodySmall)
            Text("Size: ${formatBytes(asset.size)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpen) {
                Text("Download")
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
