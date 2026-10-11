package com.tana.spa.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tana.spa.core.ui.SpaTheme

/** Marked as a heading so screen readers can jump between blocks. */
@Composable
fun SpaSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SpaTheme.textStyles.buttonLabel,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}
