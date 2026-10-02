/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.skrpld.fiscalnest.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skrpld.fiscalnest.R
import java.math.BigDecimal

/** Extra bottom space so the last item can scroll above a floating action button. */
val FabClearance: Dp = 88.dp

/** Tabular figures, so amounts line up in columns. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

/**
 * Adds [horizontal] and [vertical] spacing to scaffold padding, plus [extraBottom].
 */
@Composable
fun PaddingValues.withContentPadding(
    horizontal: Dp = 16.dp,
    vertical: Dp = 8.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        top = calculateTopPadding() + vertical,
        end = calculateEndPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + vertical + extraBottom,
    )
}

/**
 * A titled card grouping related figures.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

/**
 * A label with an amount on the right.
 *
 * @param dotColor marks the row as a segment of a [DistributionBar]
 * @param negativeIsError colors negative amounts with the error color
 */
@Composable
fun AmountRow(
    label: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    dotColor: Color? = null,
    negativeIsError: Boolean = false,
    supportingText: String? = null,
) {
    val money = LocalMoneyFormatter.current
    val amountColor = if (negativeIsError && amount.signum() < 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = money.format(amount),
            style = (if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).tabular,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = amountColor,
            textAlign = TextAlign.End,
        )
    }
}

/** One coloured part of a [DistributionBar]. */
data class BarSegment(val value: BigDecimal, val color: Color)

/**
 * A horizontal bar split proportionally into [segments]; non-positive segments are skipped.
 */
@Composable
fun DistributionBar(segments: List<BarSegment>, modifier: Modifier = Modifier) {
    val visible = segments.filter { it.value.signum() > 0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visible.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.value.toFloat().coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(segment.color),
            )
        }
    }
}

/** Severity of a [StatusBanner]. */
enum class BannerTone { ERROR, WARNING, INFO }

/**
 * A tonal message about the state of the budget.
 */
@Composable
fun StatusBanner(
    tone: BannerTone,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BannerTone.ERROR -> colors.errorContainer to colors.onErrorContainer
        BannerTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
        BannerTone.INFO -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Placeholder shown when a list has nothing to show yet.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actions()
    }
}

/**
 * A round tonal container for a list item icon.
 */
@Composable
fun IconBadge(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/**
 * Single-choice chips laid out in a flowing row.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * A confirmation dialog; [destructive] colors the confirm button with the error color.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null) }
        } else {
            null
        },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Full-size progress indicator shown while data loads.
 */
@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
