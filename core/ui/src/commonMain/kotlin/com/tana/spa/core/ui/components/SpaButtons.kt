package com.tana.spa.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaColors

private val ButtonShape = RoundedCornerShape(10.dp)

/** Full-width pink call-to-action. While [loading] it shows a spinner and ignores taps. */
@Composable
fun SpaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SpaColors.Primary,
            contentColor = SpaColors.OnPrimary,
            disabledContainerColor = SpaColors.Primary.copy(alpha = if (loading) 1f else 0.5f),
            disabledContentColor = SpaColors.OnPrimary,
        ),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = SpaColors.OnPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Full-width outlined button with a leading brand image (e.g. "Đăng nhập với Google"). */
@Composable
fun SpaSocialButton(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, SpaColors.ButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SpaColors.Surface,
            contentColor = SpaColors.TextSecondary,
            disabledContainerColor = SpaColors.Surface,
            disabledContentColor = SpaColors.TextSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth().height(60.dp),
    ) {
        Image(painter = icon, contentDescription = null, modifier = Modifier.size(30.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
