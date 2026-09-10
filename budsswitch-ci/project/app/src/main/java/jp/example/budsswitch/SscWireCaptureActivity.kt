package jp.example.budsswitch

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.privileged.ISscWireCaptureService
import jp.example.budsswitch.privileged.SscWireCaptureService
import rikka.shizuku.Shizuku

class SscWireCaptureActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private var remote: ISscWireCaptureService? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bindService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = ISscWireCaptureService.Stub.asInterface(service)
            status.text = "Wire Capture Service接続済み / ${runCatching { remote?.ping() }.getOrNull()}"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            status.text = "Wire Capture Service切断"
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

        root.addView(TextView(this).apply { text = "SSC Wire Capture v0.3.4"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "HCI snoopとSSC-UHQ OFF→ON因果実験を時刻マーカーで同期します。まず開発者向けオプションでBluetooth HCI snoop logをFullにしてください。実験はtype=8を一時OFFにし、finallyで必ずONへ戻します。"
            textSize = 14f; setPadding(0, dp(8), 0, dp(12))
        })
        status = TextView(this).apply { text = "初期化中…"; setPadding(0, 0, 0, dp(10)) }
        root.addView(status)

        val bind = Button(this).apply { text = "Shizuku / Wire Capture Service接続" }
        val dev = Button(this).apply { text = "開発者向けオプションを開く（HCI snoop=Full）" }
        val snoop = Button(this).apply { text = "HCI snoop状態 / ログパス確認" }
        val capture = Button(this).apply { text = "時刻マーカー付き SSC-UHQ OFF → ON実験" }
        val copy = Button(this).apply { text = "結果をクリップボードへコピー" }
        listOf(bind, dev, snoop, capture, copy).forEach(root::addView)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12.5f; text = "未実行"; setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        bind.setOnClickListener { ensureBound() }
        dev.setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } }
        snoop.setOnClickListener {
            ensureBound()
            Thread {
                val result = remote?.runCatching { snoopStatus }?.getOrElse { "error: ${it.message}" } ?: "Service未接続。接続後もう一度押してください。"
                runOnUiThread { output.text = result }
            }.start()
        }
        capture.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SSC-UHQ wire capture")
                .setMessage("Buds3 Proの音が一時的に途切れます。HCI snoopがFullであることを確認しましたか？ type=8は実験後に自動復帰します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("実行") { _, _ -> runCapture() }
                .show()
        }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SSC wire capture", output.text))
            status.text = "結果をコピーしました"
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindService()
        else status.text = "Shizuku permission/service未接続"
    }

    private fun ensureBound() {
        if (remote != null) return
        if (!Shizuku.pingBinder()) { status.text = "Shizukuが起動していません"; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE); return
        }
        bindService()
    }

    private fun bindService() {
        if (remote != null) return
        val args = Shizuku.UserServiceArgs(ComponentName(packageName, SscWireCaptureService::class.java.name))
            .tag("ssc_wire_capture").version(1).daemon(false).debuggable(true).processNameSuffix("sscwire")
        status.text = "Wire Capture Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { status.text = "bind failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun runCapture() {
        ensureBound()
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            output.text = "BLUETOOTH_CONNECT permissionがありません。BudsSwitch本体でAndroid権限を許可してください。"; return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: run { output.text = "BluetoothAdapter unavailable"; return }
        val buds = runCatching { adapter.bondedDevices.filter { it.name.orEmpty().contains("Buds", true) } }.getOrElse {
            output.text = "bondedDevices取得失敗: ${it.message}"; return
        }
        val saved = AppPrefs.selectedDevice(this)
        val device = saved?.let { s -> buds.firstOrNull { it.address.equals(s, true) } } ?: if (buds.size == 1) buds.single() else null
        if (device == null) {
            output.text = "対象Budsを一意に決められません。BudsSwitch本体でSSC 96kHzのBudsを選択してください。\nsavedSelected=${saved ?: "none"}\n" + buds.joinToString("\n") { "${safeName(it)} [${it.address}]" }
            return
        }
        output.text = "target=${safeName(device)} [${device.address}]\nWire capture実験中…"
        Thread {
            val snoopBefore = remote?.runCatching { snoopStatus }?.getOrNull().orEmpty()
            val result = remote?.runCatching { runMarkedToggleExperiment(device.address) }
                ?.getOrElse { "remote error: ${it.javaClass.simpleName}: ${it.message}" } ?: "Service未接続"
            val snoopAfter = remote?.runCatching { snoopStatus }?.getOrNull().orEmpty()
            runOnUiThread {
                output.text = buildString {
                    appendLine("target=${safeName(device)} [${device.address}]")
                    appendLine("--- snoop before ---"); appendLine(snoopBefore)
                    appendLine("--- experiment ---"); appendLine(result)
                    appendLine("--- snoop after ---"); appendLine(snoopAfter)
                    appendLine("NEXT: 実験直後にAndroidのバグレポートを取得し、bugreport.zipをChatGPTへ渡してください。")
                }.trimEnd()
            }
        }.start()
    }

    private fun safeName(device: BluetoothDevice): String = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Galaxy Buds" }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_CODE = 43002 }
}
