package com.solstxce.trackrr.ui.screen

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solstxce.trackrr.data.model.GithubRelease
import com.solstxce.trackrr.viewmodel.RomViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class DateFilter(val label: String) {
    ALL("All"),
    LAST_30_DAYS("30d"),
    LAST_90_DAYS("90d"),
    THIS_YEAR("This Year")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RomListScreen(
    viewModel: RomViewModel,
    onReleaseClick: (GithubRelease) -> Unit
) {
    val releases by viewModel.releases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var includePrerelease by rememberSaveable { mutableStateOf(false) }
    var onlyWithAssets by rememberSaveable { mutableStateOf(false) }
    var selectedDateFilter by rememberSaveable { mutableStateOf(DateFilter.ALL.name) }

    val dateFilter = remember(selectedDateFilter) { DateFilter.valueOf(selectedDateFilter) }

    val filteredReleases = remember(
        releases,
        query,
        includePrerelease,
        onlyWithAssets,
        dateFilter
    ) {
        releases
            .asSequence()
            .filter { includePrerelease || !it.prerelease }
            .filter { !onlyWithAssets || it.assets.isNotEmpty() }
            .filter { matchesDateFilter(it, dateFilter) }
            .filter { matchesSearchQuery(it, query) }
            .sortedByDescending { parsePublishedInstant(it.publishedAt) ?: Instant.EPOCH }
            .toList()
    }

    val latestDateLabel = filteredReleases
        .firstOrNull()
        ?.publishedAt
        ?.let(::formatPublishedAt)
        ?: "--"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trackrr") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ReleasesHeroCard(total = filteredReleases.size, latestDate = latestDateLabel)
            }

            item {
                CompactSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = includePrerelease,
                        onClick = { includePrerelease = !includePrerelease },
                        label = { Text("Include prerelease") }
                    )
                    FilterChip(
                        selected = onlyWithAssets,
                        onClick = { onlyWithAssets = !onlyWithAssets },
                        label = { Text("Has assets") }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Date:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DateFilter.entries.forEach { option ->
                        FilterChip(
                            selected = dateFilter == option,
                            onClick = { selectedDateFilter = option.name },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (isLoading && releases.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredReleases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "No matching releases",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(errorMessage ?: "Adjust filters or refresh.")
                        }
                    }
                }
            } else {
                items(filteredReleases, key = { it.id }) { release ->
                    ReleaseItem(release = release, onClick = { onReleaseClick(release) })
                }
            }
        }
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                text = "Search release, tag, changelog, asset...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReleaseItem(release: GithubRelease, onClick: () -> Unit) {
    val banner = extractImageUrls(release.body).firstOrNull()
    val summary = releaseSummary(release.body)
    val totalDownloads = release.assets.sumOf { it.downloadCount }
    val version = extractVersionFromZipDownloadUrl(release)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ReleaseBannerImage(
                imageUrl = banner,
                height = 156.dp,
                cornerRadius = 10.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (release.prerelease) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (release.prerelease) "Prerelease" else "Stable",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.name ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (!version.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = formatVersionLabel(version),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                release.publishedAt?.let {
                    Text(
                        text = formatPublishedAt(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${release.assets.size} assets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$totalDownloads downloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = summary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ReleasesHeroCard(total: Int, latestDate: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Release Feed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Latest custom ROM updates, release notes, and flash assets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("$total results") }
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Latest: $latestDate") }
                )
            }
        }
    }
}

private fun matchesDateFilter(release: GithubRelease, filter: DateFilter): Boolean {
    if (filter == DateFilter.ALL) return true

    val publishedAt = parsePublishedInstant(release.publishedAt) ?: return false
    val now = Instant.now()

    return when (filter) {
        DateFilter.ALL -> true
        DateFilter.LAST_30_DAYS -> publishedAt >= now.minus(30, ChronoUnit.DAYS)
        DateFilter.LAST_90_DAYS -> publishedAt >= now.minus(90, ChronoUnit.DAYS)
        DateFilter.THIS_YEAR -> {
            val publishedYear = publishedAt.atZone(ZoneId.systemDefault()).year
            publishedYear == LocalDate.now().year
        }
    }
}

private fun formatVersionLabel(version: String): String {
    return if (version.startsWith("v", ignoreCase = true)) version else "v$version"
}
