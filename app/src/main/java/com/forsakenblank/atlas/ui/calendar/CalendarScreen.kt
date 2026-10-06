package com.forsakenblank.atlas.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forsakenblank.atlas.ui.common.EmptyState

// the real calendar, timetable and tracker layer land in phase 2
@Composable
fun CalendarScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Outlined.CalendarMonth,
            title = "Calendar is on the way",
            body = "Month, week and day views, the timetable and your tracker history arrive in the next update.",
        )
    }
}
