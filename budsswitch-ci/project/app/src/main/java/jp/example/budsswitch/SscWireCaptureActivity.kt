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

        root.addView(TextView(this).apply { text = "SSC Wire Capture v0.3.6"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "Samsung user buildではpersist.bluetooth.*をshellから直接読めないため、Bluetooth managerの実効状態 sSnoopLogSettingAtEnable を判定します。Developer optionsでHCI snoop=Fullにした後、Bluetoothを再起動してACTIVE=FULLを確認してからcaptureしてください。"
            textSize = 14f; setPadding(0, dp(8), 0, dp(12))
        })
        status = TextView(this).apply { text = "初期化中…"; setPadding(0, 0, 0, dp(10)) }
        root.addView(status)

        val bind = Button(this).apply { text = "Shizuku / Wire Capture Service接続" }
        val dev = Button(this).apply { text = "開発者向けオプションを開く（HCI snoop=Full）" }
        val restart = Button(this).apply { text = "Bluetooth再起動（Full設定を実効化）" }
        val snoop = Button(this).apply { text = "HCI snoop実効状態を確認" }
        val capture = Button(this).apply { text = "時刻マーカー付き SSC-UHQ OFF → ON実験" }
        val bugreport = Button(this).apply { text = "capture後のbugreport ZIPをDownloadへ保存" }
        val copy = Button(this).apply { text = "結果をクリップボードへコピー" }
        listOf(bind, dev, restart, snoop, capture, bugreport, copy).forEach(root::addView)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12.5f; text = "未実行"; setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        bind.setOnClickListener { ensureBound() }
        dev.setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } }
        restart.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Bluetoothを再起動")
                .setMessage("Bluetooth接続が一時的に切断されます。Developer optionsでBluetooth HCI snoop logをFullに設定済みであることを確認してください。再起動後、Galaxy Buds3 Proを再接続してからcaptureします。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("再起動") { _, _ -> runBluetoothRestart() }
                .show()
        }
        snoop.setOnClickListener { runSnoopStatus() }
        capture.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SSC-UHQ wire capture")
                .setMessage("Buds3 Proの音が一時的に途切れます。HCI snoopの実効状態がFULLで、Buds3 ProがSSC-UHQ 96kHzで接続されている場合だけ実験します。type=8はfinallyでONへ戻します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("実行") { _, _ -> runCapture() }
                .show()
        }
        bugreport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("bugreportを生成")
                .setMessage("Android bugreportには端末・アプリ・ネットワーク・アカウント関連の診断情報が含まれる場合があります。capture直後の解析用ZIPを生成し、Downloadフォルダへコピーします。続行しますか？")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("生成") { _, _ -> runBugreport() }
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
            .tag("ssc_wire_capture").version(3).daemon(false).debuggable(true).processNameSuffix("sscwire")
        status.text = "Wire Capture Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { status.text = "bind failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun runSnoopStatus() {
        ensureBound()
        status.text = "Bluetooth managerの実効snoop状態を確認中…"
        Thread {
            val result = remote?.runCatching { snoopStatus }?.getOrElse { "error: ${it.message}" }
                ?: "Service未接続。接続後もう一度押してください。"
            runOnUiThread {
                output.text = result
                status.text = if (result.contains("READY_FOR_CAPTURE=YES")) "HCI snoop FULLでcapture可能" else "HCI snoopはまだ実効FULLではありません"
            }
        }.start()
    }

    private fun runBluetoothRestart() {
        ensureBound()
        status.text = "Bluetooth再起動中… 接続が一時切断されます"
        output.text = "Bluetoothを再起動してHCI snoop Full設定を実効化しています…"
        Thread {
            val result = remote?.runCatching { restartBluetoothForSnoop() }
                ?.getOrElse { "restart remote error: ${it.javaClass.simpleName}: ${it.message}" }
                ?: "Service未接続"
            runOnUiThread {
                output.text = result
                status.text = if (result.contains("RESTART_OK_ACTIVE_SNOOP_FULL")) {
                    "実効FULLを確認。Buds3 Proを再接続してください"
                } else {
                    "再起動結果を確認してください"
                }
            }
        }.start()
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
        status.text = "capture実験中…"
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
                    appendLine("NEXT: CAPTURE成功後すぐ『bugreport ZIPをDownloadへ保存』を押してください。")
                }.trimEnd()
                status.text = if (result.contains("CAPTURE_MARKERS_COMPLETE")) "capture完了。次にbugreportを生成してください" else "capture結果を確認してください"
            }
        }.start()
    }

    private fun runBugreport() {
        ensureBound()
        status.text = "bugreport生成中… 数分かかる場合があります"
        val existing = output.text.toString()
        output.text = existing + "\n\n=== BUGREPORT REQUESTED ===\n生成中…"
        Thread {
            val result = remote?.runCatching { generateBugreport("BudsSwitch-SSC-Wire") }
                ?.getOrElse { "bugreport remote error: ${it.javaClass.simpleName}: ${it.message}" }
                ?: "Service未接続"
            runOnUiThread {
                output.text = existing + "\n\n" + result
                status.text = if (result.contains("BUGREPORT_READY")) "bugreport保存完了。Downloadフォルダを確認してください" else "bugreport生成に失敗しました"
            }
        }.start()
    }

    private fun safeName(device: BluetoothDevice): String = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Galaxy Buds" }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_CODE = 43002 }
}
