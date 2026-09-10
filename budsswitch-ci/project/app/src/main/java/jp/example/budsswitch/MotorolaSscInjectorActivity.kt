package jp.example.budsswitch

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
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
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.privileged.IMotorolaSscInjectorService
import jp.example.budsswitch.privileged.MotorolaSscInjectorService
import rikka.shizuku.Shizuku

class MotorolaSscInjectorActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private var remote: IMotorolaSscInjectorService? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bindService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IMotorolaSscInjectorService.Stub.asInterface(service)
            status.text = "Injector Service接続済み / ${runCatching { remote?.ping() }.getOrNull()}"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            status.text = "Injector Service切断"
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

        root.addView(TextView(this).apply { text = "Motorola SSC-UHQ Injector v0.4.0"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "Galaxy S25+Buds3 Proで実測したSSC vendor codec 0x0075/0x0003をMotorolaのA2DP Binderへ投入し、休眠しているSSC経路が存在するか実機検証します。Shizukuを使用します。"
            textSize = 14f; setPadding(0, dp(8), 0, dp(12))
        })
        status = TextView(this).apply { text = "初期化中…"; setPadding(0, 0, 0, dp(10)) }
        root.addView(status)

        val bind = Button(this).apply { text = "Shizuku / Injector Service接続" }
        val diagnose = Button(this).apply { text = "MotorolaのSSC対応状態を診断" }
        val ssc48 = Button(this).apply { text = "SSC 48kHz / 24-bit を試行" }
        val uhq = Button(this).apply { text = "SSC-UHQ 96kHz を試行" }
        val copy = Button(this).apply { text = "結果をクリップボードへコピー" }
        listOf(bind, diagnose, ssc48, uhq, copy).forEach(root::addView)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12.5f
            text = "未実行"
            setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        bind.setOnClickListener { ensureBound() }
        diagnose.setOnClickListener { runAgainstSelected(false, false) }
        ssc48.setOnClickListener { confirmAndRun(false) }
        uhq.setOnClickListener { confirmAndRun(true) }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Motorola SSC injector", output.text))
            status.text = "結果をコピーしました"
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindService()
        else status.text = "Shizuku permission/service未接続"
    }

    private fun confirmAndRun(uhq: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (uhq) "SSC-UHQ 96kHzを試行" else "SSC 48kHzを試行")
            .setMessage("MotorolaのA2DP codec preferenceを書き換える実験です。音が一時的に途切れる可能性があります。失敗時は直前のcodec preferenceへ復帰を試みます。続行しますか？")
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("実行") { _, _ -> runAgainstSelected(true, uhq) }
            .show()
    }

    private fun runAgainstSelected(activate: Boolean, uhq: Boolean) {
        ensureBound()
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            output.text = "BLUETOOTH_CONNECT permissionがありません。BudsSwitch本体でAndroid権限を許可してください。"
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: run {
            output.text = "BluetoothAdapter unavailable"; return
        }
        val saved = AppPrefs.selectedDevice(this)
        val bonded = runCatching { adapter.bondedDevices.toList() }.getOrElse {
            output.text = "bondedDevices取得失敗: ${it.message}"; return
        }
        val buds = saved?.let { s -> bonded.firstOrNull { it.address.equals(s, true) } }
            ?: bonded.filter { safeName(it).contains("Buds", true) }.singleOrNull()
        if (buds == null) {
            output.text = buildString {
                appendLine("対象Budsを一意に決められません。")
                appendLine("まずBudsSwitch本体でGalaxy Buds3 Proを選択してください。")
                appendLine("savedSelected=${saved ?: "none"}")
                bonded.filter { safeName(it).contains("Buds", true) }.forEach { appendLine("${safeName(it)} [${it.address}]") }
            }.trimEnd()
            return
        }
        if (remote == null) {
            output.text = "Injector Service未接続です。Shizuku接続後にもう一度押してください。"
            return
        }
        output.text = "target=${safeName(buds)} [${buds.address}]\n実行中…"
        Thread {
            val result = runCatching {
                if (activate) remote!!.tryActivate(buds.address, uhq) else remote!!.diagnose(buds.address)
            }.getOrElse { "remote error: ${it.javaClass.simpleName}: ${it.message}" }
            runOnUiThread {
                output.text = "target=${safeName(buds)} [${buds.address}]\n$result"
            }
        }.start()
    }

    private fun ensureBound() {
        if (remote != null) return
        if (!Shizuku.pingBinder()) { status.text = "Shizukuが起動していません"; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
            return
        }
        bindService()
    }

    private fun bindService() {
        if (remote != null) return
        val args = Shizuku.UserServiceArgs(ComponentName(packageName, MotorolaSscInjectorService::class.java.name))
            .tag("motorola_ssc_injector")
            .version(1)
            .daemon(false)
            .debuggable(true)
            .processNameSuffix("moto_ssc")
        status.text = "Injector Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { status.text = "bind failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Galaxy Buds" }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_CODE = 43004 }
}
