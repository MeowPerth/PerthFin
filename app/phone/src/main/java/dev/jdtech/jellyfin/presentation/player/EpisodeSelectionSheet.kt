package dev.jdtech.jellyfin.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSelectionSheet(
    episodes: List<PlayerViewModel.EpisodeChoice>,
    currentEpisodeId: UUID?,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onSelect: (PlayerViewModel.EpisodeChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { PlayerSheetDragHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(PlayerR.string.episodes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    episodes.firstOrNull()?.seasonName?.let { season ->
                        Text(
                            text = season,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(PlayerR.string.auto_play_next),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = autoPlayNext, onCheckedChange = onAutoPlayNextChange)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.FixedSize(48.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val selected = episode.id == currentEpisodeId
                    Box(
                        modifier =
                            Modifier.width(44.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { onSelect(episode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = episode.indexNumber.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color =
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
