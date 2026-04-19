package com.solstxce.trackrr.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.solstxce.trackrr.data.model.GithubAsset
import com.solstxce.trackrr.viewmodel.RomViewModel
import java.time.Instant
import java.time.ZoneId

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
    val cleanedBody = remember(release?.body) { cleanReleaseBody(release?.body) }

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
                    Text("Tag: ${release.tagName}", style = MaterialTheme.typography.titleMedium)
                    release.publishedAt?.let {
                        Text("Published: ${formatPublishedAt(it)}", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(onClick = { openUrl(context, release.htmlUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("View on GitHub")
                    }
                }

                if (cleanedBody.isNotBlank()) {
                    item {
                        Text("Description", fontWeight = FontWeight.Bold)
                        Text(cleanedBody)
                    }
                }

                if (imageUrls.isNotEmpty()) {
                    item {
                        Text("Images", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    items(imageUrls) { imageUrl ->
                        ReleaseImage(url = imageUrl)
                    }
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

@Composable
private fun ReleaseImage(url: String) {
    Card(shape = RoundedCornerShape(12.dp)) {
        AsyncImage(
            model = url,
            contentDescription = "Release image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp)
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}

private fun extractImageUrls(body: String?): List<String> {
    if (body.isNullOrBlank()) return emptyList()

    val htmlImageRegex = Regex("""<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)
    val markdownImageRegex = Regex("""!\[[^\]]*]\((https?://[^)\s]+)\)""")

    val htmlUrls = htmlImageRegex.findAll(body).map { it.groupValues[1] }
    val markdownUrls = markdownImageRegex.findAll(body).map { it.groupValues[1] }

    return (htmlUrls + markdownUrls)
        .filter { it.startsWith("http") }
        .distinct()
        .toList()
}

private fun cleanReleaseBody(body: String?): String {
    if (body.isNullOrBlank()) return ""

    val htmlImageRegex = Regex("""<img[^>]*src=[\"'][^\"']+[\"'][^>]*>""", RegexOption.IGNORE_CASE)
    val markdownImageRegex = Regex("""!\[[^\]]*]\(https?://[^)\s]+\)""")

    return body
        .replace(htmlImageRegex, " ")
        .replace(markdownImageRegex, " ")
        .replace("\r", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun formatPublishedAt(value: String): String {
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }.getOrDefault(value)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
