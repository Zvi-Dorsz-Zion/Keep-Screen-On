package com.keepscreenon.app

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class MainActivity : ComponentActivity() {
    // גדל בכל חזרה למסך, כדי לבדוק שוב הרשאה ואפליקציות שהותקנו בינתיים
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        MainScreen(resumeCount)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }
}

private class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun MainScreen(resumeCount: Int) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Prefs.isEnabled(context)) }
    var selected by remember { mutableStateOf(Prefs.selected(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var hasAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        KeepOnService.sync(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(resumeCount) {
        hasAccess = hasUsageAccess(context)
        val current = selected
        val previous = apps
        val loaded = withContext(Dispatchers.Default) { loadApps(context, current) }
        apps = loaded
        // הרשימה שומרת על מיקום לפי מפתח, ואפליקציה שעברה לראש הרשימה הייתה נשארת מעל הקצה
        if (previous != null && previous.map { it.packageName } != loaded.map { it.packageName }) {
            listState.scrollToItem(0)
        }
    }

    val installed = apps
    val selectedCount = installed?.count { it.packageName in selected } ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("מסך דלוק", style = MaterialTheme.typography.titleLarge)
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    Prefs.setEnabled(context, it)
                    KeepOnService.sync(context)
                }
            )
        }
        Text(
            text = when {
                !enabled -> "כבוי. המסך נכבה כרגיל."
                selectedCount == 0 -> "בחר מהרשימה אפליקציות שהמסך יישאר דלוק כשהן פתוחות."
                selectedCount == 1 -> "המסך יישאר דלוק כשהאפליקציה שבחרת פתוחה."
                else -> "המסך יישאר דלוק כשאחת מ-$selectedCount האפליקציות שבחרת פתוחה."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (!hasAccess) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "כדי לדעת איזו אפליקציה פתוחה, צריך להפעיל את \"מסך דלוק\" במסך \"גישה לנתוני שימוש\".",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { openUsageAccess(context) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("פתח את ההגדרה")
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("חיפוש אפליקציה") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "ניקוי החיפוש")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (installed == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = installed.filter { it.label.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text("לא נמצאו אפליקציות", Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, checked = app.packageName in selected) {
                        selected = if (app.packageName in selected) {
                            selected - app.packageName
                        } else {
                            selected + app.packageName
                        }
                        Prefs.setSelected(context, selected)
                        KeepOnService.sync(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(app.icon, contentDescription = null, Modifier.size(40.dp))
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun loadApps(context: Context, selected: Set<String>): List<AppEntry> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val iconSize = (40 * context.resources.displayMetrics.density).toInt()
    val collator = Collator.getInstance(Locale.forLanguageTag("he"))
    return pm.queryIntentActivities(launcher, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            val icon = try {
                info.loadIcon(pm).toBitmap(iconSize, iconSize).asImageBitmap()
            } catch (_: Exception) {
                null
            }
            AppEntry(info.activityInfo.packageName, info.loadLabel(pm).toString(), icon)
        }
        // אלה שנבחרו קודם. מסדרים רק בטעינה, כדי ששורה לא תקפוץ מתחת לאצבע ברגע הסימון
        .sortedWith(compareByDescending<AppEntry> { it.packageName in selected }.thenBy(collator) { it.label })
}

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    if (mode == AppOpsManager.MODE_DEFAULT) {
        return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun openUsageAccess(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
