package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private val DotSize = 10.dp
private val CurrentDotSize = 14.dp
private val TrackHeight = 2.dp
private const val FUTURE_ALPHA = 0.4f

@Composable
fun SpaStepper(steps: List<String>, reached: Int, modifier: Modifier = Modifier, description: String? = null) {
    val done = MaterialTheme.colorScheme.primary
    val future = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FUTURE_ALPHA)

    Row(
        modifier = if (description == null) modifier else modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Top,
    ) {
        steps.forEachIndexed { index, label ->
            val passed = index <= reached
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Track(if (passed) done else future, drawn = index > 0, modifier = Modifier.weight(1f))
                    Dot(color = if (passed) done else future, current = index == reached)
                    Track(if (index < reached) done else future, drawn = index < steps.lastIndex, modifier = Modifier.weight(1f))
                }
                Text(
                    text = label,
                    style = SpaTheme.textStyles.label,
                    color = if (passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Track(color: Color, drawn: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.height(TrackHeight).background(if (drawn) color else Color.Transparent))
}

@Composable
private fun Dot(color: Color, current: Boolean) {
    Box(Modifier.size(if (current) CurrentDotSize else DotSize).background(color, CircleShape))
}
