package jp.example.budsswitch

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.privileged.ISscPrivilegedProbeService
import jp.example.budsswitch.privileged.SscPrivilegedProbeService
import rikka.shizuku.Shizuku

class SscPrivilegedProbeActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var output: TextView
    private var remote: ISscPrivilegedProbeService? = null
    private var pendingAction = PendingAction.NONE

    private enum class PendingAction { NONE, PROBE, EXPERIMENT }

    private data class ProbeTarget(
        val device: BluetoothDevice,
        val selectedAddress: String?
    )

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            status.text = "Shizuku permission result=$grantResult"
            if (grantResult == PackageManager.PERMISSION_GRANTED) bindProbeService()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = ISscPrivilegedProbeService.Stub.asInterface(service)
            status.text = "Shizuku privileged probe: connected / ${runCatching { remote?.ping() }.getOrNull()}"
            val action = pendingAction
            pendingAction = PendingAction.NONE
            when (action) {
                PendingAction.PROBE -> runSelectedAction(experiment = false)
                PendingAction.EXPERIMENT -> runSelectedAction(experiment = true)
                PendingAction.NONE -> Unit
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            status.text = "Shizuku privileged probe: disconnected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "SSC Privileged Probe v0.3.3"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "通常Probeは読み取り専用です。因果実験は、BudsSwitch本体で選択済みのGalaxy Budsに対してSSC-UHQ type=8を一時OFFにし、約1.8秒観測後にfinallyで必ずONへ復帰させます。音が一時途切れる可能性があります。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        status = TextView(this).apply {
            text = "初期化中…"
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(status)

        val bindButton = Button(this).apply { text = "Shizuku権限 / Probe Service接続" }
        val probeButton = Button(this).apply { text = "選択中のBudsをSSC-UHQ(type=8)照会" }
        val experimentButton = Button(this).apply { text = "SSC-UHQ因果実験: OFF → ON自動復帰" }
        root.addView(bindButton)
        root.addView(probeButton)
        root.addView(experimentButton)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            text = "未実行"
            setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        bindButton.setOnClickListener { ensureService(PendingAction.NONE) }
        probeButton.setOnClickListener { ensureService(PendingAction.PROBE) }
        experimentButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SSC-UHQを一時的にOFFにします")
                .setMessage(
                    "type=8が現在supported=true / enabled=trueの場合のみ実行します。" +
                        " 一時的にOFFへ変更して約1.8秒観測し、その後finallyでONへ復帰します。" +
                        " 音が一時途切れる可能性があります。実行しますか？"
                )
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("実行") { _, _ -> ensureService(PendingAction.EXPERIMENT) }
                .show()
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindProbeService()
        } else {
            status.text = "Shizuku permission/service未接続"
        }
    }

    private fun ensureService(action: PendingAction) {
        if (remote != null) {
            when (action) {
                PendingAction.PROBE -> runSelectedAction(experiment = false)
                PendingAction.EXPERIMENT -> runSelectedAction(experiment = true)
                PendingAction.NONE -> status.text = "Probe Service接続済み"
            }
            return
        }

        pendingAction = action
        if (!Shizuku.pingBinder()) {
            pendingAction = PendingAction.NONE
            status.text = "Shizukuが起動していません"
        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        } else {
            bindProbeService()
        }
    }

    private fun bindProbeService() {
        if (remote != null) return
        val args = Shizuku.UserServiceArgs(
            ComponentName(packageName, SscPrivilegedProbeService::class.java.name)
        )
            .tag("ssc_privileged_probe")
            .version(3)
            .daemon(false)
            .debuggable(true)
            .processNameSuffix("sscprobe")
        status.text = "Probe Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                pendingAction = PendingAction.NONE
                status.text = "Probe Service bind failed: ${it.javaClass.simpleName}: ${it.message}"
            }
    }

    private fun resolveTarget(): ProbeTarget? {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            output.text = "BLUETOOTH_CONNECT permissionがありません。BudsSwitch本体でAndroid権限を許可してください。"
            return null
        }

        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null) {
            output.text = "BluetoothAdapter unavailable"
            return null
        }

        val buds = runCatching {
            adapter.bondedDevices
                .filter { it.name.orEmpty().contains("Buds", ignoreCase = true) }
                .sortedBy { it.address }
        }.getOrElse {
            output.text = "bondedDevices取得失敗: ${it.javaClass.simpleName}: ${it.message}"
            return null
        }

        if (buds.isEmpty()) {
            output.text = "ペアリング済みGalaxy Budsが見つかりません"
            return null
        }

        val selectedAddress = AppPrefs.selectedDevice(this)
        val device = selectedAddress?.let { saved ->
            buds.firstOrNull { it.address.equals(saved, ignoreCase = true) }
        } ?: if (buds.size == 1) buds.single() else null

        if (device == null) {
            output.text = buildString {
                appendLine("実行を中止しました: Galaxy Budsのbond entryが複数あります。")
                appendLine("BudsSwitch本体でSSCが96kHzになっているBudsを選択してください。")
                appendLine("savedSelected=${selectedAddress ?: "none"}")
                appendLine("Buds candidates:")
                buds.forEach { appendLine("  ${safeName(it)} [${it.address}]") }
            }.trimEnd()
            return null
        }

        return ProbeTarget(device, selectedAddress)
    }

    private fun runSelectedAction(experiment: Boolean) {
        val target = resolveTarget() ?: return
        val device = target.device
        val mode = if (experiment) "因果実験" else "読み取りProbe"
        output.text = buildString {
            appendLine("target=${safeName(device)} [${device.address}]")
            appendLine("savedSelected=${target.selectedAddress ?: "single-buds-fallback"}")
            appendLine("$mode をshell UIDから実行中…")
        }.trimEnd()

        Thread {
            val result = remote?.runCatching {
                if (experiment) runToggleExperiment(device.address) else probe(device.address)
            }?.getOrElse { "remote error: ${it.javaClass.simpleName}: ${it.message}" }
                ?: "Probe Service not connected"

            runOnUiThread {
                output.text = buildString {
                    appendLine("target=${safeName(device)} [${device.address}]")
                    appendLine("savedSelected=${target.selectedAddress ?: "single-buds-fallback"}")
                    appendLine(result)
                }.trimEnd()
            }
        }.start()
    }

    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Galaxy Buds" }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CODE = 43001
    }
}
