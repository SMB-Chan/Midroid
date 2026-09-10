package jp.example.budsswitch

import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.example.budsswitch.privileged.ISscPrivilegedProbeService
import jp.example.budsswitch.privileged.SscPrivilegedProbeService
import rikka.shizuku.Shizuku

class SscPrivilegedProbeActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var output: TextView
    private var remote: ISscPrivilegedProbeService? = null
    private var pendingProbe = false

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
            if (pendingProbe) {
                pendingProbe = false
                runProbeNow()
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
            text = "SSC Privileged Probe v0.3.1"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "通常アプリで発生したCDM association制限を避け、Shizuku UserService (shell UID) からSamsung A2DP Binderへ type=8 を読み取り照会します。状態変更は行いません。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        status = TextView(this).apply {
            text = "初期化中…"
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(status)

        val bindButton = Button(this).apply { text = "Shizuku権限 / Probe Service接続" }
        val probeButton = Button(this).apply { text = "Buds3 ProのSSC-UHQ(type=8)を特権照会" }
        root.addView(bindButton)
        root.addView(probeButton)

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

        bindButton.setOnClickListener {
            if (!Shizuku.pingBinder()) {
                status.text = "Shizukuが起動していません"
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
            } else {
                bindProbeService()
            }
        }

        probeButton.setOnClickListener {
            if (remote == null) {
                pendingProbe = true
                if (!Shizuku.pingBinder()) {
                    pendingProbe = false
                    status.text = "Shizukuが起動していません"
                } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(REQUEST_CODE)
                } else {
                    bindProbeService()
                }
            } else {
                runProbeNow()
            }
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindProbeService()
        } else {
            status.text = "Shizuku permission/service未接続"
        }
    }

    private fun bindProbeService() {
        if (remote != null) return
        val args = Shizuku.UserServiceArgs(
            ComponentName(packageName, SscPrivilegedProbeService::class.java.name)
        )
            .tag("ssc_privileged_probe")
            .version(1)
            .daemon(false)
            .debuggable(true)
            .processNameSuffix("sscprobe")
        status.text = "Probe Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { status.text = "Probe Service bind failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun runProbeNow() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            output.text = "BLUETOOTH_CONNECT permissionがありません。BudsSwitch本体でAndroid権限を許可してください。"
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        val device = runCatching {
            adapter?.bondedDevices?.firstOrNull { it.name.orEmpty().contains("Buds", ignoreCase = true) }
        }.getOrNull()
        if (device == null) {
            output.text = "ペアリング済みGalaxy Budsが見つかりません"
            return
        }
        output.text = "${device.name} [${device.address}] をshell UIDから照会中…"
        Thread {
            val result = remote?.runCatching { probe(device.address) }
                ?.getOrElse { "remote probe error: ${it.javaClass.simpleName}: ${it.message}" }
                ?: "Probe Service not connected"
            runOnUiThread { output.text = result }
        }.start()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CODE = 43001
    }
}
