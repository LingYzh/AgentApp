package com.example.myapplication.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Returns whether the app can use the shared-storage access level available on this Android
 * version. Android 11 and later expose this as the special "All files access" setting; older
 * versions use the legacy read/write runtime permissions.
 */
internal fun hasSharedStorageAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }

    val readGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    val writeGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    return readGranted && writeGranted
}

/** Opens the per-app All files access page, falling back to the general settings page. */
@RequiresApi(Build.VERSION_CODES.R)
internal fun openSharedStorageSettings(context: Context) {
    val appSettingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    val generalSettingsIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    try {
        startSettingsActivity(context, appSettingsIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            startSettingsActivity(context, generalSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            // Very old or vendor-specific settings apps may expose neither action. There is no
            // safe automatic grant, so leave the permission unchanged in that case.
        }
    }
}

private fun startSettingsActivity(context: Context, intent: Intent) {
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
fun StorageAccessCard(
    accessGranted: Boolean,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (accessGranted) {
            "状态：已允许“所有文件访问”"
        } else {
            "状态：尚未允许“所有文件访问”"
        }
    } else {
        if (accessGranted) {
            "状态：已获得共享存储读写权限"
        } else {
            "状态：尚未获得共享存储读写权限"
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRequestAccess)
                .padding(vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("共享存储访问", style = MaterialTheme.typography.titleMedium)
                Text(
                    "授权状态由 Android 返回",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (accessGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "用于文件工具和备份访问共享存储；Android 仍会限制其他应用私有数据、系统数据和受保护目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
