package io.github.msteja.covenant.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.github.msteja.covenant.core.model.AppId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** An app with a launcher entry, in any of your profiles. */
data class InstalledApp(
    val id: AppId,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
)

/**
 * The apps on the phone, kept current as apps are installed and removed. Work-profile apps included.
 * Reading every label is slow, so it happens off the main thread; the list is empty until the first read.
 */
class AppCatalog(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val collator = Collator.getInstance()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback)
        refresh()
    }

    fun refresh() {
        scope.launch { _apps.value = load() }
    }

    fun find(id: AppId): InstalledApp? = _apps.value.firstOrNull { it.id == id }

    fun label(id: AppId): String = find(id)?.label ?: id.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    fun launch(app: InstalledApp) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun launch(id: AppId): Boolean {
        val app = find(id) ?: return false
        launch(app)
        return true
    }

    private fun load(): List<InstalledApp> {
        val own = context.packageName
        return userManager.userProfiles
            .flatMap { user -> launcherApps.getActivityList(null, user).map { it.toApp() } }
            .filter { it.id.packageName != own }
            .distinctBy { it.id to it.user }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun LauncherActivityInfo.toApp() =
        InstalledApp(AppId(componentName.packageName), label.toString(), componentName, user)

    companion object {
        val ownUser: UserHandle = Process.myUserHandle()
    }
}
