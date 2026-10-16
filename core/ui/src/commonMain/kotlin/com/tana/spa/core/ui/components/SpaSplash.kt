package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.brand_name
import org.jetbrains.compose.resources.stringResource

/** The house mark and name over the contour texture, shown while the app opens. */
@Composable
fun SpaSplash(modifier: Modifier = Modifier) {
    // A Surface swallows pointer input, so nothing underneath can be tapped while it is up.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().spaTopo(Alignment.Center), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SpaIconImage(SpaIcon.Leaf, size = 52.dp, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = stringResource(Res.string.brand_name),
                    style = SpaTheme.textStyles.brandTitle,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
