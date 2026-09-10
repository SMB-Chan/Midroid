package jp.example.budsswitch.shizuku

import android.Manifest
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import jp.example.budsswitch.privileged.IBluetoothPrivilegedService
import jp.example.budsswitch.privileged.PrivilegedBluetoothService
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuBridge {
    const val REQUEST_CODE = 42001
    private const val SERVICE_VERSION = 3
    private const val SERVICE_TAG = "buds_switch_bluetooth"

    private var remote: IBluetoothPrivilegedService? = null
    private var binding = false
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IBluetoothPrivilegedService.Stub.asInterface(service)
            binding = false
            notifyState("Shizuku UserService connected: ${safePing()}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
            notifyState("Shizuku UserService disconnected")
        }
    }

    fun addListener(listener: (String) -> Unit) { listeners += listener }
    fun removeListener(listener: (String) -> Unit) { listeners -= listener }

    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun permissionGranted(): Boolean =
        binderAlive() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun serverUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    fun remotePermissionSummary(): String {
        if (!permissionGranted()) return "unavailable"
        fun check(name: String): String = runCatching {
            if (Shizuku.checkRemotePermission(name) == PackageManager.PERMISSION_GRANTED) "yes" else "no"
        }.getOrDefault("error")

        return "CONNECT=${check(Manifest.permission.BLUETOOTH_CONNECT)} " +
            "PRIVILEGED=${check("android.permission.BLUETOOTH_PRIVILEGED")} " +
            "PHONE=${check("android.permission.MODIFY_PHONE_STATE")}"
    }

    fun requestPermission() {
        if (!binderAlive()) {
            notifyState("Shizuku binder is not available. Start Shizuku first.")
            return
        }
        if (permissionGranted()) {
            notifyState("Shizuku permission already granted")
            bindUserService()
            return
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun bindUserService() {
        if (!permissionGranted()) {
            notifyState("Shizuku permission is not granted")
            return
        }
        if (remote != null || binding) return
        binding = true

        val args = Shizuku.UserServiceArgs(
            ComponentName(
                "jp.example.budsswitch",
                PrivilegedBluetoothService::class.java.name
            )
        )
            .tag(SERVICE_TAG)
            .version(SERVICE_VERSION)
            .daemon(false)
            .debuggable(true)
            .processNameSuffix("bluetooth")

        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                binding = false
                notifyState("UserService bind failed: ${it.javaClass.simpleName}: ${it.message}")
            }
    }

    fun connect(address: String): String =
        remote?.runCatching { connectDevice(address) }?.getOrElse { "remote error: ${it.message}" }
            ?: "UserService not connected"

    fun disconnect(address: String): String =
        remote?.runCatching { disconnectDevice(address) }?.getOrElse { "remote error: ${it.message}" }
            ?: "UserService not connected"

    fun summary(address: String): String =
        remote?.runCatching { connectionSummary(address) }?.getOrElse { "remote error: ${it.message}" }
            ?: "UserService not connected"

    fun ready(): Boolean = remote != null

    private fun safePing(): String =
        remote?.runCatching { ping() }?.getOrDefault("ping failed") ?: "not connected"

    private fun notifyState(message: String) { listeners.forEach { it(message) } }
}
