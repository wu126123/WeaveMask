package io.github.seyud.weave.core.utils

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.UserManager
import android.system.Os
import androidx.core.content.getSystemService
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.AbstractQueuedSynchronizer

class RootUtils(stub: Any?) : RootService() {

    private val className: String = stub?.javaClass?.name ?: javaClass.name
    private lateinit var am: ActivityManager

    constructor() : this(null)

    init {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                super.log(priority, "Magisk", message, t)
            }
        })
    }

    override fun onCreate() {
        am = getSystemService()!!
    }

    override fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }

    override fun onBind(intent: Intent): IBinder {
        return object : IRootUtils.Stub() {
            override fun getAppProcess(pid: Int) = safe(null) { getAppProcessImpl(pid) }
            override fun getFileSystem(): IBinder = FileSystemManager.getService()
            override fun addSystemlessHosts() = safe(false) { addSystemlessHostsImpl() }
            override fun getInstalledApplications(flags: Int) = safe(emptyList()) { getInstalledApplicationsImpl(flags) }
            override fun getUserIds() = safe(intArrayOf(0)) { getAllUserIds() }
        }
    }

    private fun getAppProcessImpl(_pid: Int): ActivityManager.RunningAppProcessInfo? {
        val procList = am.runningAppProcesses
        var pid = _pid
        while (pid > 1) {
            val proc = procList.find { it.pid == pid }
            if (proc != null)
                return proc

            // Stop find when root process
            if (Os.stat("/proc/$pid").st_uid == 0) {
                return null
            }

            // Find PPID
            File("/proc/$pid/status").useLines {
                val line = it.find { l -> l.startsWith("PPid:") } ?: return null
                pid = line.substring(5).trim().toInt()
            }
        }
        return null
    }

    private fun addSystemlessHostsImpl(): Boolean {
        val module = File(Const.MODULE_PATH, "hosts")
        if (module.exists()) return true
        val hosts = File(module, "system/etc/hosts")
        if (hosts.parentFile?.mkdirs() != true) return false
        File(module, "module.prop").outputStream().writer().use {
            it.write("""
                id=hosts
                name=Systemless Hosts
                version=1.0
                versionCode=1
                author=Magisk
                description=Magisk app built-in systemless hosts module
            """.trimIndent())
        }
        File("/system/etc/hosts").copyTo(hosts)
        File(module, "update").createNewFile()
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun getInstalledApplicationsAsUser(flags: Int, userId: Int): List<ApplicationInfo> {
        return try {
            val pm: PackageManager = packageManager
            val method = pm.javaClass.getDeclaredMethod(
                "getInstalledApplicationsAsUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, flags, userId) as List<ApplicationInfo>
        } catch (e: Throwable) {
            Timber.e(e, "getInstalledApplicationsAsUser reflection failed")
            emptyList()
        }
    }

    private fun getAllUserIds(): IntArray {
        val um = getSystemService(USER_SERVICE) as? UserManager ?: return intArrayOf(0)
        // Try getUsers(boolean excludeDying) - API 17+
        try {
            val method = um.javaClass.getMethod("getUsers", Boolean::class.javaPrimitiveType)
            val users = method.invoke(um, true) as? List<*>
            if (!users.isNullOrEmpty()) {
                return users.mapNotNull { 
                    it?.javaClass?.getMethod("getUserId")?.invoke(it) as? Int 
                }.toIntArray()
            }
        } catch (e: Exception) {
            Timber.w(e, "getUsers reflection failed")
        }
        // Try getAliveUsers() - API 31+
        try {
            val method = um.javaClass.getMethod("getAliveUsers")
            val users = method.invoke(um) as? List<*>
            if (!users.isNullOrEmpty()) {
                return users.mapNotNull { 
                    it?.javaClass?.getMethod("getUserId")?.invoke(it) as? Int 
                }.toIntArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "getAliveUsers reflection failed")
        }
        return intArrayOf(0)
    }

    private fun getInstalledApplicationsImpl(flags: Int): List<ApplicationInfo> {
        val apps = ArrayList<ApplicationInfo>()
        for (userId in getAllUserIds()) {
            apps.addAll(getInstalledApplicationsAsUser(flags, userId))
        }
        return apps
    }

    object Connection : AbstractQueuedSynchronizer(), ServiceConnection {
        init {
            state = 1
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.d("onServiceConnected")
            IRootUtils.Stub.asInterface(service).let {
                obj = it
                fs = FileSystemManager.getRemote(it.fileSystem)
            }
            releaseShared(1)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            state = 1
            obj = null
            bind(Intent().setComponent(name), this)
        }

        override fun tryAcquireShared(acquires: Int) = if (state == 0) 1 else -1

        override fun tryReleaseShared(releases: Int): Boolean {
            // Decrement count; signal when transition to zero
            while (true) {
                val c = state
                if (c == 0)
                    return false
                val n = c - 1
                if (compareAndSetState(c, n))
                    return n == 0
            }
        }

        fun await() {
            if (!Info.isRooted)
                return
            if (!ShellUtils.onMainThread()) {
                acquireSharedInterruptibly(1)
            } else if (state != 0) {
                throw IllegalStateException("Cannot await on the main thread")
            }
        }
    }

    companion object {
        var bindTask: Shell.Task? = null
        var fs: FileSystemManager = FileSystemManager.getLocal()
            get() {
                Connection.await()
                return field
            }
            private set
        private var obj: IRootUtils? = null
            get() {
                Connection.await()
                return field
            }

        fun getAppProcess(pid: Int) = safe(null) { obj?.getAppProcess(pid) }

        suspend fun addSystemlessHosts() =
            withContext(Dispatchers.IO) { safe(false) { obj?.addSystemlessHosts() ?: false } }

        fun getInstalledApplications(flags: Int): List<ApplicationInfo> =
            safe(emptyList()) { obj?.getInstalledApplications(flags) ?: emptyList() }

        fun getUserIds(): IntArray = safe(intArrayOf(0)) { obj?.userIds ?: intArrayOf(0) }

        private inline fun <T> safe(default: T, block: () -> T): T {
            return try {
                block()
            } catch (e: Throwable) {
                // The process died unexpectedly
                Timber.e(e)
                default
            }
        }
    }
}
