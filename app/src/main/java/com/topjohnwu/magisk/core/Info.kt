package com.topjohnwu.magisk.core

import androidx.databinding.ObservableBoolean
import com.topjohnwu.magisk.DynAPK
import com.topjohnwu.magisk.core.model.UpdateInfo
import com.topjohnwu.magisk.ktx.get
import com.topjohnwu.magisk.utils.CachedValue
import com.topjohnwu.magisk.utils.net.NetworkObserver
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils.fastCmd
import com.topjohnwu.superuser.internal.UiThreadHandler
import java.io.FileInputStream
import java.io.IOException

val isRunningAsStub get() = Info.stub != null

object Info {

    val envRef = CachedValue { loadState() }

    @JvmStatic val env by envRef              // Local
    @JvmStatic var stub: DynAPK.Data? = null  // Stub
    var remote = UpdateInfo()      // Remote

    // Toggle-able options
    @JvmStatic var keepVerity = false
    @JvmStatic var keepEnc = false
    @JvmStatic var recovery = false

    // Immutable device state
    @JvmStatic var isSAR = false
    @JvmStatic var isAB = false
    @JvmStatic var ramdisk = false
    @JvmStatic var hasGMS = true

    val isConnected by lazy {
        ObservableBoolean(false).also { field ->
            NetworkObserver.observe(get()) {
                UiThreadHandler.run { field.set(it.isAvailable) }
            }
        }
    }

    val isNewReboot by lazy {
        try {
            FileInputStream("/proc/sys/kernel/random/boot_id").bufferedReader().use {
                val id = it.readLine()
                if (id != Config.bootId) {
                    Config.bootId = id
                    true
                } else {
                    false
                }
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun loadState() = Env(
        fastCmd("magisk -v").split(":".toRegex())[0],
        runCatching { fastCmd("magisk -V").toInt() }.getOrDefault(-1),
        Shell.su("magiskhide --status").exec().isSuccess
    )

    class Env(
        val magiskVersionString: String = "",
        code: Int = -1,
        hide: Boolean = false
    ) {
        val magiskHide get() = Config.magiskHide
        val magiskVersionCode = when (code) {
            in Int.MIN_VALUE..Const.Version.MIN_VERCODE -> -1
            else -> if (Shell.rootAccess()) code else -1
        }
        val isUnsupported = code > 0 && code < Const.Version.MIN_VERCODE
        val isActive = magiskVersionCode >= 0

        init {
            Config.magiskHide = hide
        }
    }
}
