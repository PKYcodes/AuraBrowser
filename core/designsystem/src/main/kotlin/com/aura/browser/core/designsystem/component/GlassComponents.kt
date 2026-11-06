package com.aura.browser.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.browser.core.designsystem.theme.AuraColors

/**
 * Lightweight, GPU-friendly Glassmorphism card surface.
 * Uses translucent gradient layers + 1dp specular border instead of expensive real-time RenderEffect blurs,
 * ensuring 60/120fps scrolling performance on Android 8.0+ phones.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = AuraColors.GlassBorder,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val gradient = Brush.verticalGradient(
        colors = if (isHighlighted) {
            listOf(
                AuraColors.PrimaryAccent.copy(alpha = 0.25f),
                AuraColors.GlassSurface.copy(alpha = 0.92f),
            )
        } else {
            listOf(
                AuraColors.GlassSurfaceElevated.copy(alpha = 0.88f),
                AuraColors.GlassSurface.copy(alpha = 0.78f),
            )
        }
    )

    val cardModifier = modifier
        .clip(shape)
        .background(brush = gradient, shape = shape)
        .border(
            BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isHighlighted) AuraColors.PrimaryAccent else borderColor,
                        AuraColors.GlassBorderSubtle,
                    )
                ),
            ),
            shape = shape,
        )
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }
        .padding(contentPadding)

    Column(
        modifier = cardModifier,
        content = content,
    )
}

@Composable
fun GlassPillButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    accentColor: Color = AuraColors.PrimaryAccent,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = if (isPrimary) accentColor else AuraColors.GlassSurfaceTranslucent,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPrimary) accentColor.copy(alpha = 0.6f) else AuraColors.GlassBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = AuraColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AuraColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = AuraColors.GlassSurfaceTranslucent,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        border = BorderStroke(1.dp, AuraColors.GlassBorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AuraColors.ElevatedBackground.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "Navigate back",
                        tint = AuraColors.TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions,
            )
        }
    }
}

@Composable
fun GlassStatBadge(
    label: String,
    value: String,
    accentColor: Color = AuraColors.PrimaryAccent,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                color = accentColor,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
