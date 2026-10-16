// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.lkmc.chessandcocoa.R
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.GameReview

/** A short review of meaningful moments, with intentions fully revealed (Section 4.2). */
@Composable
fun ReviewScreen(review: GameReview, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (review.outcome == null) Text(stringResource(R.string.review_in_progress), style = MaterialTheme.typography.bodySmall)

            if (review.episodes.isEmpty() && review.moments.isEmpty()) {
                Text(stringResource(R.string.review_nothing), style = MaterialTheme.typography.bodyLarge)
            }

            if (review.moments.isNotEmpty()) {
                Text(stringResource(R.string.review_moments), style = MaterialTheme.typography.titleMedium)
                review.moments.forEach { moment ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("${Renderer.moveNumber(moment.ply)}. ${moment.san}", style = MaterialTheme.typography.titleSmall)
                            Text(moment.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (review.episodes.isNotEmpty()) {
                Text(stringResource(R.string.review_plans), style = MaterialTheme.typography.titleMedium)
                review.episodes.forEach { episode ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            episode.steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            if (review.concessions.isNotEmpty()) {
                Text(stringResource(R.string.review_concessions), style = MaterialTheme.typography.titleMedium)
                review.concessions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            if (review.humanMoves > 0) {
                Text(stringResource(R.string.review_assisted, review.assistedMoves, review.humanMoves), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
