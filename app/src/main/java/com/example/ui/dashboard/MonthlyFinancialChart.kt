package com.example.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.FinancialTimeBucket
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlyFinancialChart(
    buckets: List<FinancialTimeBucket>,
    maxAmount: Double,
    modifier: Modifier = Modifier,
    title: String = "Payables vs Receivables",
    onBucketSelected: ((FinancialTimeBucket?) -> Unit)? = null
) {
    if (buckets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No transactions recorded in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(buckets.indices.lastOrNull()) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(buckets) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val selectedBucket = selectedIndex?.let { if (it in buckets.indices) buckets[it] else null }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Chart Header with Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendItem(color = Color(0xFF10B981), label = "Receivable")
                LegendItem(color = Color(0xFFEF4444), label = "Payable")
                LegendItem(color = Color(0xFF6366F1), label = "Net", isLine = true)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected Tooltip Banner (Recharts-style active interactive tooltip)
        selectedBucket?.let { bucket ->
            ActiveTooltipCard(bucket = bucket, format = currencyFormat)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Native Canvas Recharts-style Visualizer
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val density = LocalDensity.current
            val leftAxisPadding = with(density) { 46.dp.toPx() }
            val bottomAxisPadding = with(density) { 26.dp.toPx() }
            val topPadding = with(density) { 16.dp.toPx() }
            val rightPadding = with(density) { 12.dp.toPx() }

            val chartWidth = constraints.maxWidth.toFloat() - leftAxisPadding - rightPadding
            val chartHeight = constraints.maxHeight.toFloat() - bottomAxisPadding - topPadding

            val slotWidth = chartWidth / buckets.size.coerceAtLeast(1)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val touchX = offset.x - leftAxisPadding
                            if (touchX >= 0 && touchX <= chartWidth) {
                                val index = (touchX / slotWidth).toInt().coerceIn(0, buckets.size - 1)
                                selectedIndex = index
                                onBucketSelected?.invoke(buckets[index])
                            }
                        }
                    }
            ) {
                val effectiveMax = if (maxAmount > 0) maxAmount else 10000.0

                // 1. Draw horizontal dashed grid lines and Y-axis tick labels
                val gridSteps = 4
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                val gridColor = Color.LightGray.copy(alpha = 0.45f)
                val textColor = android.graphics.Color.GRAY

                val textPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                for (i in 0..gridSteps) {
                    val ratio = i.toFloat() / gridSteps
                    val y = topPadding + chartHeight * (1f - ratio)
                    val value = effectiveMax * ratio

                    // Grid line
                    drawLine(
                        color = gridColor,
                        start = Offset(leftAxisPadding, y),
                        end = Offset(leftAxisPadding + chartWidth, y),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )

                    // Y-Axis formatted tick
                    val label = formatCompactNumber(value)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        leftAxisPadding - 10f,
                        y + 10f,
                        textPaint
                    )
                }

                // 2. Bar Dimensions based on bucket count
                val barMaxWidth = when {
                    buckets.size <= 7 -> (slotWidth * 0.28f).coerceIn(10f, 26f)
                    buckets.size <= 14 -> (slotWidth * 0.32f).coerceIn(6f, 18f)
                    else -> (slotWidth * 0.36f).coerceIn(3f, 10f)
                }
                val barSpacing = when {
                    buckets.size <= 7 -> 4f
                    buckets.size <= 14 -> 2.5f
                    else -> 1.5f
                }
                val cornerRadius = CornerRadius(barMaxWidth / 2f, barMaxWidth / 2f)

                // Track net cash flow points to connect line
                val netLinePoints = mutableListOf<Offset>()

                // Determine label skip interval for dense bucketing
                val labelSkip = when {
                    buckets.size <= 8 -> 1
                    buckets.size <= 16 -> 2
                    else -> 5
                }

                buckets.forEachIndexed { index, bucket ->
                    val slotCenterX = leftAxisPadding + (index * slotWidth) + (slotWidth / 2f)

                    // Active column highlight band
                    if (index == selectedIndex) {
                        drawRect(
                            color = Color(0xFF6366F1).copy(alpha = 0.08f),
                            topLeft = Offset(slotCenterX - (slotWidth / 2f) + 1f, topPadding),
                            size = Size(slotWidth - 2f, chartHeight)
                        )
                    }

                    // Receivables Bar (Left)
                    val recRatio = ((bucket.receivableAmount / effectiveMax) * animationProgress.value).toFloat().coerceIn(0f, 1f)
                    val recBarHeight = chartHeight * recRatio
                    val recTopLeftX = slotCenterX - barMaxWidth - (barSpacing / 2f)
                    val recTopLeftY = topPadding + chartHeight - recBarHeight

                    if (recBarHeight > 0) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                            ),
                            topLeft = Offset(recTopLeftX, recTopLeftY),
                            size = Size(barMaxWidth, recBarHeight),
                            cornerRadius = cornerRadius
                        )
                    }

                    // Payables Bar (Right)
                    val payRatio = ((bucket.payableAmount / effectiveMax) * animationProgress.value).toFloat().coerceIn(0f, 1f)
                    val payBarHeight = chartHeight * payRatio
                    val payTopLeftX = slotCenterX + (barSpacing / 2f)
                    val payTopLeftY = topPadding + chartHeight - payBarHeight

                    if (payBarHeight > 0) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF87171), Color(0xFFDC2626))
                            ),
                            topLeft = Offset(payTopLeftX, payTopLeftY),
                            size = Size(barMaxWidth, payBarHeight),
                            cornerRadius = cornerRadius
                        )
                    }

                    // Net Trend Line Point
                    val netY = topPadding + chartHeight - (chartHeight * ((bucket.netCashflow.coerceAtLeast(0.0) / effectiveMax) * animationProgress.value).toFloat())
                    netLinePoints.add(Offset(slotCenterX, netY))

                    // Draw X-axis label (respecting skip interval to prevent crowding, but always drawing selected or first/last)
                    val shouldDrawLabel = (index % labelSkip == 0) || (index == selectedIndex) || (index == buckets.lastIndex)
                    if (shouldDrawLabel) {
                        val isSelected = index == selectedIndex
                        val xLabelPaint = android.graphics.Paint().apply {
                            color = if (isSelected) android.graphics.Color.BLACK else textColor
                            textSize = if (buckets.size > 14) 22f else if (isSelected) 28f else 25f
                            isFakeBoldText = isSelected
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            bucket.label,
                            slotCenterX,
                            topPadding + chartHeight + bottomAxisPadding - 6f,
                            xLabelPaint
                        )
                    }
                }

                // 3. Draw Net Cashflow Connected Spline Line & Dots
                if (netLinePoints.size > 1) {
                    val path = Path()
                    path.moveTo(netLinePoints.first().x, netLinePoints.first().y)
                    for (i in 1 until netLinePoints.size) {
                        val prev = netLinePoints[i - 1]
                        val curr = netLinePoints[i]
                        val cx = (prev.x + curr.x) / 2f
                        path.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF6366F1),
                        style = Stroke(width = if (buckets.size > 14) 2.5f else 3.5f)
                    )

                    // Draw node circles
                    netLinePoints.forEachIndexed { idx, pt ->
                        val isSel = idx == selectedIndex
                        if (buckets.size <= 14 || isSel || idx % 2 == 0) {
                            drawCircle(
                                color = Color.White,
                                radius = if (isSel) 5.5f else 3.5f,
                                center = pt
                            )
                            drawCircle(
                                color = Color(0xFF6366F1),
                                radius = if (isSel) 4.2f else 2.2f,
                                center = pt
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTooltipCard(
    bucket: FinancialTimeBucket,
    format: NumberFormat
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bucket.fullLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${bucket.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rec: ${format.format(bucket.receivableAmount)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                    Text("Pay: ${format.format(bucket.payableAmount)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                }
                val net = bucket.netCashflow
                val netColor = if (net >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                Surface(
                    color = netColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = (if (net >= 0) "+ " else "- ") + format.format(Math.abs(net)),
                        color = netColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isLine: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isLine) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(3.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCompactNumber(value: Double): String {
    return when {
        value >= 10000000 -> String.format(Locale.getDefault(), "%.1fCr", value / 10000000)
        value >= 100000 -> String.format(Locale.getDefault(), "%.1fL", value / 100000)
        value >= 1000 -> String.format(Locale.getDefault(), "%.0fk", value / 1000)
        else -> String.format(Locale.getDefault(), "%.0f", value)
    }
}
