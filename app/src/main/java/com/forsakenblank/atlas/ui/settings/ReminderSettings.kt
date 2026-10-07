package com.forsakenblank.atlas.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.forsakenblank.atlas.notify.needsNotificationPermission
import com.forsakenblank.atlas.notify.openNotificationSettings
import com.forsakenblank.atlas.notify.remindersAllowed
import com.forsakenblank.atlas.notify.sendTestReminder
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch

// only shows while reminders are on but android is not letting them through
@Composable
fun NotificationsBlockedRow(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(remindersAllowed(context)) }
    // checked again on coming back, in case they were just switched on
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = remindersAllowed(context) }
    if (allowed || !LocalSettings.current.remindersOn) return
    ListItem(
        leadingContent = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        headlineContent = { Text("Notifications are blocked") },
        supportingContent = {
            Text("Atlas can't show reminders until notifications are allowed. Tap here, then switch on notifications for Atlas.")
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.clickable(onClick = onOpenSettings),
    )
}

// posts a reminder straight away, asking for permission first when android 13 and up still needs it
@Composable
fun rememberTestReminder(): () -> Unit {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    fun explainBlocked() {
        scope.launch {
            val result = snackbar.showSnackbar("Notifications are blocked for Atlas", actionLabel = "Settings")
            if (result == SnackbarResult.ActionPerformed) openNotificationSettings(context)
        }
    }

    fun send() {
        sendTestReminder(context)
        scope.launch { snackbar.showSnackbar("Test reminder sent") }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && remindersAllowed(context)) send() else explainBlocked()
    }

    return {
        when {
            remindersAllowed(context) -> send()
            needsNotificationPermission(context) -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> explainBlocked()
        }
    }
}
