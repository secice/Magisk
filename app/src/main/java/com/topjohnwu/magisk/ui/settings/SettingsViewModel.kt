package com.topjohnwu.magisk.ui.settings

import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.arch.adapterOf
import com.topjohnwu.magisk.arch.diffListOf
import com.topjohnwu.magisk.arch.itemBindingOf
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.Configuration
import com.topjohnwu.magisk.core.download.DownloadService
import com.topjohnwu.magisk.core.download.DownloadSubject
import com.topjohnwu.magisk.core.utils.PatchAPK
import com.topjohnwu.magisk.data.database.RepoDao
import com.topjohnwu.magisk.events.RecreateEvent
import com.topjohnwu.magisk.events.dialog.BiometricDialog
import com.topjohnwu.magisk.utils.Utils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import org.koin.core.get

class SettingsViewModel(
    private val repositoryDao: RepoDao
) : BaseViewModel(), BaseSettingsItem.Callback {

    val adapter = adapterOf<BaseSettingsItem>()
    val itemBinding = itemBindingOf<BaseSettingsItem> { it.bindExtra(BR.callback, this) }
    val items = diffListOf(createItems())

    init {
        viewModelScope.launch {
            Language.loadLanguages(this)
        }
    }

    private fun createItems(): List<BaseSettingsItem> {
        // Customization
        val list = mutableListOf(
            Customization,
            Theme, Language
        )
        if (Build.VERSION.SDK_INT < 21) {
            // Pre 5.0 does not support getting colors from attributes,
            // making theming a pain in the ass. Just forget about it
            list.remove(Theme)
        }

        // Manager
        list.addAll(listOf(
            Manager,
            UpdateChannel, UpdateChannelUrl, UpdateChecker, DownloadPath
        ))
        if (Info.env.isActive) {
            list.add(ClearRepoCache)
            if (Const.USER_ID == 0 && Info.isConnected.get())
                list.add(HideOrRestore())
        }

        // Magisk
        if (Info.env.isActive) {
            list.addAll(listOf(
                Magisk,
                MagiskHide, SystemlessHosts
            ))
        }

        // Superuser
        if (Utils.showSuperUser()) {
            list.addAll(listOf(
                Superuser,
                Biometrics, AccessMode, MultiuserMode, MountNamespaceMode,
                AutomaticResponse, RequestTimeout, SUNotification
            ))
            if (Build.VERSION.SDK_INT < 23) {
                // Biometric is only available on 6.0+
                list.remove(Biometrics)
            }
            if (Build.VERSION.SDK_INT < 26) {
                // Re-authenticate is not feasible on 8.0+
                list.add(Reauthenticate)
            }
        }

        return list
    }

    override fun onItemPressed(view: View, item: BaseSettingsItem, callback: () -> Unit) = when (item) {
        is DownloadPath -> withExternalRW(callback)
        is Biometrics -> authenticate(callback)
        is Theme -> SettingsFragmentDirections.actionSettingsFragmentToThemeFragment().publish()
        is ClearRepoCache -> clearRepoCache()
        is SystemlessHosts -> createHosts()
        is Restore -> restoreManager()
        else -> callback()
    }

    override fun onItemChanged(view: View, item: BaseSettingsItem) = when (item) {
        is Language -> RecreateEvent().publish()
        is UpdateChannel -> openUrlIfNecessary(view)
        is Hide -> PatchAPK.hideManager(view.context, item.value)
        else -> Unit
    }

    private fun openUrlIfNecessary(view: View) {
        UpdateChannelUrl.refresh()
        if (UpdateChannelUrl.isEnabled && UpdateChannelUrl.value.isBlank()) {
            UpdateChannelUrl.onPressed(view, this)
        }
    }

    private fun authenticate(callback: () -> Unit) {
        BiometricDialog {
            // allow the change on success
            onSuccess { callback() }
        }.publish()
    }

    private fun clearRepoCache() {
        viewModelScope.launch {
            repositoryDao.clear()
            Utils.toast(R.string.repo_cache_cleared, Toast.LENGTH_SHORT)
        }
    }

    private fun createHosts() {
        Shell.su("add_hosts_module").submit {
            Utils.toast(R.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }

    private fun restoreManager() {
        DownloadService(get()) {
            subject = DownloadSubject.Manager(Configuration.APK.Restore)
        }
    }

}
