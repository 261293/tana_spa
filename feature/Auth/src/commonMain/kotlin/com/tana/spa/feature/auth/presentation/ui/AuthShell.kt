package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaBrand
import com.tana.spa.core.ui.components.SpaDisplayTitle
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.spaTopo

private val PANEL_HEIGHT = 220.dp
private val FORM_MAX_WIDTH = 420.dp

/**
 * The sign-in frame from the design file: a photo crop above the form on a phone, a photo panel
 * beside it on a desk screen, with the contour texture behind the form either way.
 */
@Composable
internal fun AuthShell(title: String, accent: String, intro: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        if (SpaWindow.size.isWide) {
            Row(Modifier.fillMaxSize()) {
                Panel(Modifier.weight(1.1f).fillMaxHeight())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .spaTopo(Alignment.BottomEnd)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Form(title, accent, intro, content)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .spaTopo(Alignment.BottomCenter)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Panel(Modifier.fillMaxWidth().height(PANEL_HEIGHT))
                Form(title, accent, intro, content)
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier) {
    Box(modifier) {
        SpaImageTile(Modifier.fillMaxSize(), shape = RectangleShape)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(16.dp),
        ) {
            SpaBrand(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun Form(title: String, accent: String, intro: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = FORM_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SpaDisplayTitle(title, accent)
            Text(intro, style = SpaTheme.textStyles.bodyRelaxed, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}
