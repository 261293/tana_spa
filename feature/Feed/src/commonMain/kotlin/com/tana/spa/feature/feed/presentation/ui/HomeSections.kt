package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaSectionTitle
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.feature.feed.presentation.manipulator.SectionState
import com.tana.spa.feature.feed.resources.Res
import com.tana.spa.feature.feed.resources.home_load_failed
import com.tana.spa.feature.feed.resources.home_retry
import org.jetbrains.compose.resources.stringResource

internal val ScreenPadding = 16.dp

/**
 * One block of the home screen, titled unless [title] is null. An empty [SectionState.Ready] draws
 * nothing, title included, so a block with nothing to offer never shows as a blank card.
 */
@Composable
internal fun <T> HomeSection(
    title: String?,
    state: SectionState<T>,
    onRetry: () -> Unit,
    placeholderHeight: Dp,
    content: @Composable (List<T>) -> Unit,
) {
    if (state.isEmptyReady()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) {
            SpaSectionTitle(text = title, modifier = Modifier.padding(horizontal = ScreenPadding))
        }
        when (state) {
            SectionState.Loading -> SpaSkeleton(height = placeholderHeight, modifier = Modifier.padding(horizontal = ScreenPadding))
            SectionState.Failed -> SectionFailed(onRetry)
            is SectionState.Ready -> content(state.items)
        }
    }
}

/** An empty block is not drawn, so it must not take a slot (and the spacing around it) in the list either. */
internal fun SectionState<*>.isEmptyReady(): Boolean = this is SectionState.Ready<*> && items.isEmpty()

@Composable
private fun SectionFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.home_load_failed),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(Res.string.home_retry),
                style = SpaTheme.textStyles.buttonLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
