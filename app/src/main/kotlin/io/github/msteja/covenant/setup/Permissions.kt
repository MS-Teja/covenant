package io.github.msteja.covenant.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.guard.CovenantNotificationListener
import io.github.msteja.covenant.guard.Grayscale
import io.github.msteja.covenant.home.accessibilityEnabled
import io.github.msteja.covenant.home.listenerEnabled

fun isDefaultHome(context: Context): Boolean = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

/** Setup step 9: the permissions Covenant needs, each granted on Android's own screen. */
@SuppressLint("BatteryLife")
@Composable
fun PermissionRows() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val home = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    key(refresh) { Column(Modifier.fillMaxWidth()) {
        PermissionRow("Home app", "Covenant becomes your launcher", isDefaultHome(context)) {
            home.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
        PermissionRow("Accessibility", "to see which app comes to the front; it never reads the screen", accessibilityEnabled(context)) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        PermissionRow("Notification access", "to hold messages until their block", listenerEnabled(context)) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, CovenantNotificationListener::class.java).flattenToString(),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        PermissionRow(
            "Notifications",
            "for held-message summaries",
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        ) { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        PermissionRow(
            "Battery",
            "so Android doesn't put Covenant to sleep",
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        BasicText(
            "If Android says “Restricted setting”, open Covenant's App info, tap ⋮, then Allow restricted settings.",
            Modifier.fillMaxWidth().padding(top = 10.dp),
            style = Type.text(13.sp, Ink.dim, italic = true).copy(textAlign = TextAlign.Center),
        )
        QuietWord("App info", Modifier.align(Alignment.CenterHorizontally)) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        BasicText(
            if (Grayscale.canApply(context)) "Grayscale is ready."
            else "Grayscale blocks need one command from a computer:\nadb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
            Modifier.fillMaxWidth().padding(top = 6.dp),
            style = Type.text(13.sp, Ink.dim, italic = true).copy(textAlign = TextAlign.Center),
        )
    } }
}

@Composable
private fun PermissionRow(name: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(name, style = Type.text(18.sp))
            BasicText(why, style = Type.text(13.sp, Ink.mist, italic = true))
        }
        if (granted) BasicText("done", Modifier.padding(horizontal = 20.dp), style = Type.smallCaps(14.sp, Ink.dim))
        else QuietWord("Grant", onClick = onGrant)
    }
}
