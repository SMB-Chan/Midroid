package jp.example.budsswitch

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
import jp.example.budsswitch.privileged.IMotorolaNativeAuditService
import jp.example.budsswitch.privileged.MotorolaNativeAuditService
import rikka.shizuku.Shizuku

class MotorolaNativeAuditActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private var remote: IMotorolaNativeAuditService? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bindService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IMotorolaNativeAuditService.Stub.asInterface(service)
            status.text = "Native Audit Service接続済み / ${runCatching { remote?.ping() }.getOrNull()}"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            status.text = "Native Audit Service切断"
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

        root.addView(TextView(this).apply { text = "Motorola SSC Native Audit v0.4.1"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "SSC設定の強制投入がAACへfallbackしたため、Bluetooth APEX / vendor / native codec / audio offload層にSSC実装が潜んでいないか読み取り専用で監査します。システムファイルは変更しません。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })
        status = TextView(this).apply { text = "初期化中…"; setPadding(0, 0, 0, dp(10)) }
        root.addView(status)

        val bind = Button(this).apply { text = "Shizuku / Native Audit Service接続" }
        val audit = Button(this).apply { text = "Motorola native SSC層を監査" }
        val copy = Button(this).apply { text = "監査結果をクリップボードへコピー" }
        listOf(bind, audit, copy).forEach(root::addView)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11.5f
            text = "未実行"
            setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(output)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        bind.setOnClickListener { ensureBound() }
        audit.setOnClickListener {
            ensureBound()
            val svc = remote ?: run { output.text = "Service未接続です。接続後もう一度押してください。"; return@setOnClickListener }
            output.text = "監査中…（最大1分程度）"
            Thread {
                val result = runCatching { svc.audit() }
                    .getOrElse { "audit failed: ${it.javaClass.simpleName}: ${it.message}" }
                runOnUiThread { output.text = result }
            }.start()
        }
        copy.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Motorola SSC native audit", output.text))
            status.text = "監査結果をコピーしました"
        }

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindService()
        else status.text = "Shizuku permission/service未接続"
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
        val args = Shizuku.UserServiceArgs(ComponentName(packageName, MotorolaNativeAuditService::class.java.name))
            .tag("motorola_ssc_native_audit")
            .version(1)
            .daemon(false)
            .debuggable(true)
            .processNameSuffix("moto_native_audit")
        status.text = "Native Audit Service接続中…"
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { status.text = "bind failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_CODE = 43005 }
}
