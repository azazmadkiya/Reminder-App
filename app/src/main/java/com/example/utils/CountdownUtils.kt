package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Calendar
import kotlin.math.abs

enum class CountdownType {
    UPCOMING,
    TODAY,
    OVERDUE,
    COMPLETED
}

enum class UrgencyLevel {
    NORMAL,    // > 3 days left
    ATTENTION, // 1 - 3 days left
    CRITICAL,  // Today
    OVERDUE,   // Past days
    DONE       // Completed
}

data class ReminderCountdownInfo(
    val days: Int,
    val type: CountdownType,
    val badgeLabel: String,         // e.g. "IN 5 DAYS", "TOMORROW", "DUE TODAY", "3 DAYS OVERDUE", "COMPLETED"
    val daysCountLabel: String,    // e.g. "5 days left", "1 day left", "Due today", "3 days past due"
    val fullStatusText: String,    // e.g. "Upcoming in 5 days", "Due tomorrow", "Due today", "Overdue by 3 days"
    val primaryText: String,       // e.g. "5d left", "1d left", "Today", "3d past"
    val urgencyLevel: UrgencyLevel,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val badgeColor: Color
)

object CountdownUtils {

    fun getCountdown(
        dueDateMillis: Long,
        isCompleted: Boolean,
        dueTimeMillis: Long? = null
    ): ReminderCountdownInfo {
        if (isCompleted) {
            return ReminderCountdownInfo(
                days = 0,
                type = CountdownType.COMPLETED,
                badgeLabel = "COMPLETED",
                daysCountLabel = "Completed",
                fullStatusText = "Task marked completed",
                primaryText = "Done",
                urgencyLevel = UrgencyLevel.DONE,
                icon = Icons.Filled.CheckCircle,
                containerColor = Color(0xFF10B981).copy(alpha = 0.12f),
                contentColor = Color(0xFF047857),
                badgeColor = Color(0xFF10B981)
            )
        }

        val nowCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dueCal = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = dueCal.timeInMillis - nowCal.timeInMillis
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        return when {
            diffDays == 0 -> {
                ReminderCountdownInfo(
                    days = 0,
                    type = CountdownType.TODAY,
                    badgeLabel = "DUE TODAY",
                    daysCountLabel = "Due today",
                    fullStatusText = "Action required today",
                    primaryText = "Today",
                    urgencyLevel = UrgencyLevel.CRITICAL,
                    icon = Icons.Filled.Alarm,
                    containerColor = Color(0xFFFEF3C7),
                    contentColor = Color(0xFFB45309),
                    badgeColor = Color(0xFFF59E0B)
                )
            }
            diffDays == 1 -> {
                ReminderCountdownInfo(
                    days = 1,
                    type = CountdownType.UPCOMING,
                    badgeLabel = "TOMORROW",
                    daysCountLabel = "1 day left",
                    fullStatusText = "Due tomorrow (1 day left)",
                    primaryText = "1d left",
                    urgencyLevel = UrgencyLevel.ATTENTION,
                    icon = Icons.Filled.HourglassTop,
                    containerColor = Color(0xFFE0E7FF),
                    contentColor = Color(0xFF4338CA),
                    badgeColor = Color(0xFF6366F1)
                )
            }
            diffDays > 1 -> {
                val urgency = if (diffDays <= 3) UrgencyLevel.ATTENTION else UrgencyLevel.NORMAL
                val containerColor = if (diffDays <= 3) Color(0xFFDBEAFE) else Color(0xFFF0FDF4)
                val contentColor = if (diffDays <= 3) Color(0xFF1D4ED8) else Color(0xFF15803D)
                val badgeColor = if (diffDays <= 3) Color(0xFF3B82F6) else Color(0xFF22C55E)

                ReminderCountdownInfo(
                    days = diffDays,
                    type = CountdownType.UPCOMING,
                    badgeLabel = "IN $diffDays DAYS",
                    daysCountLabel = "$diffDays days left",
                    fullStatusText = "Upcoming in $diffDays days ($diffDays days left)",
                    primaryText = "${diffDays}d left",
                    urgencyLevel = urgency,
                    icon = Icons.Filled.HourglassTop,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    badgeColor = badgeColor
                )
            }
            diffDays == -1 -> {
                ReminderCountdownInfo(
                    days = 1,
                    type = CountdownType.OVERDUE,
                    badgeLabel = "1 DAY OVERDUE",
                    daysCountLabel = "1 day overdue",
                    fullStatusText = "Passed yesterday (1 day overdue)",
                    primaryText = "1d overdue",
                    urgencyLevel = UrgencyLevel.OVERDUE,
                    icon = Icons.Filled.WarningAmber,
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = Color(0xFFB91C1C),
                    badgeColor = Color(0xFFEF4444)
                )
            }
            else -> {
                val pastDays = abs(diffDays)
                ReminderCountdownInfo(
                    days = pastDays,
                    type = CountdownType.OVERDUE,
                    badgeLabel = "$pastDays DAYS OVERDUE",
                    daysCountLabel = "$pastDays days overdue",
                    fullStatusText = "Passed $pastDays days ago ($pastDays days overdue)",
                    primaryText = "${pastDays}d overdue",
                    urgencyLevel = UrgencyLevel.OVERDUE,
                    icon = Icons.Filled.WarningAmber,
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = Color(0xFFB91C1C),
                    badgeColor = Color(0xFFEF4444)
                )
            }
        }
    }
}
