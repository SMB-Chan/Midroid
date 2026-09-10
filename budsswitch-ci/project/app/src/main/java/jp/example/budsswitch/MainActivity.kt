package jp.example.budsswitch

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import jp.example.budsswitch.autoswitch.AutoSwitchService
import jp.example.budsswitch.databinding.ActivityMainBinding
import jp.example.budsswitch.model.BondedDevice
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.shizuku.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var devices: List<BondedDevice> = emptyList()
    private var connectionAttempt = 0
    private var successLoggedAttempt = -1

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshDevices(); updateStatus()
        }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuBridge.REQUEST_CODE) {
                appendLog("Shizuku permission result=$grantResult")
                if (grantResult == PackageManager.PERMISSION_GRANTED) ShizukuBridge.bindUserService()
                updateStatus()
            }
        }

    private val bridgeListener: (String) -> Unit = {
        runOnUiThread { appendLog(it); updateStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuBridge.addListener(bridgeListener)

        binding.permissionButton.setOnClickListener { requestAndroidPermissions() }
        binding.shizukuButton.setOnClickListener {
            ShizukuBridge.requestPermission(); ShizukuBridge.bindUserService()
        }
        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.refreshButton.setOnClickListener { refreshDevices() }
        binding.connectButton.setOnClickListener { selectedDevice()?.let(::connect) }
        binding.disconnectButton.setOnClickListener { selectedDevice()?.let(::disconnect) }
        binding.codecDiagnosticsButton.setOnClickListener { selectedDevice()?.let(::analyzeCodec) }
        binding.savePeerCodeButton.setOnClickListener { savePeerCode() }
        binding.startAutoButton.setOnClickListener { startAutoSwitch() }
        binding.stopAutoButton.setOnClickListener {
            stopService(Intent(this, AutoSwitchService::class.java)); appendLog("Auto Switch stopped")
        }

        binding.peerCodeEdit.setText(AppPrefs.peerCode(this))
        updatePeerStatus()
        if (ShizukuBridge.permissionGranted()) ShizukuBridge.bindUserService()
        requestAndroidPermissions(); refreshDevices(); updateStatus()
    }

    private fun requestAndroidPermissions() {
        val wanted = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.READ_PHONE_STATE)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun refreshDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            appendLog("BLUETOOTH_CONNECT permission required"); return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        devices = adapter.bondedDevices
            .map { BondedDevice(it.name ?: "Unknown", it.address) }
            .sortedWith(compareByDescending<BondedDevice> {
                it.name.contains("Buds", true) || it.name.contains("Samsung", true)
            }.thenBy { it.name.lowercase() })
        binding.deviceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, devices
        )
        val saved = AppPrefs.selectedDevice(this)
        val index = devices.indexOfFirst { it.address == saved }
        if (index >= 0) binding.deviceSpinner.setSelection(index)
        appendLog("Bonded devices=${devices.size}")
    }

    private fun selectedDevice(): BondedDevice? {
        val position = binding.deviceSpinner.selectedItemPosition
        if (position !in devices.indices) {
            Toast.makeText(this, "Bluetooth機器を選択してください", Toast.LENGTH_SHORT).show(); return null
        }
        return devices[position].also { AppPrefs.setSelectedDevice(this, it.address) }
    }

    private fun connect(device: BondedDevice) {
        if (!ShizukuBridge.ready()) {
            appendLog("Shizuku UserService not ready"); ShizukuBridge.bindUserService(); return
        }
        AppPrefs.setSelectedDevice(this, device.address)
        val attempt = ++connectionAttempt
        successLoggedAttempt = -1
        val started = SystemClock.elapsedRealtime()
        appendLog("${device.name}: ${ShizukuBridge.connect(device.address)}")

        longArrayOf(700, 1_200, 2_000, 3_000, 5_000, 8_000).forEach { delay ->
            binding.root.postDelayed({
                if (attempt != connectionAttempt) return@postDelayed
                val summary = ShizukuBridge.summary(device.address)
                val elapsed = SystemClock.elapsedRealtime() - started
                val a2dpConnected = summary.contains("A2DP=connected")
                val hfpConnected = summary.contains("HFP=connected")
                if (a2dpConnected && successLoggedAttempt != attempt) {
                    successLoggedAttempt = attempt
                    appendLog("✓ ${device.name} 接続成功 / A2DP=connected HFP=${if (hfpConnected) "connected" else "pending"} / ${elapsed}ms")
                } else if (delay == 8_000L && !a2dpConnected) {
                    appendLog("接続未完了(8s): $summary")
                } else if (successLoggedAttempt != attempt) {
                    appendLog("status ${elapsed}ms: $summary")
                }
            }, delay)
        }
    }

    private fun disconnect(device: BondedDevice) {
        if (!ShizukuBridge.ready()) { appendLog("Shizuku UserService not ready"); return }
        ++connectionAttempt
        appendLog("${device.name}: ${ShizukuBridge.disconnect(device.address)}")
        longArrayOf(700, 2_000).forEach { delay ->
            binding.root.postDelayed({ appendLog("status: ${ShizukuBridge.summary(device.address)}") }, delay)
        }
    }

    private fun analyzeCodec(device: BondedDevice) {
        if (!ShizukuBridge.ready()) {
            binding.codecDiagnosticsText.text = "Shizuku UserServiceが未接続です"
            ShizukuBridge.bindUserService(); return
        }
        binding.codecDiagnosticsText.text = "${device.name} のA2DP codecを分析中…"
        lifecycleScope.launch(Dispatchers.IO) {
            val report = ShizukuBridge.codecDiagnostics(device.address)
            withContext(Dispatchers.Main) {
                binding.codecDiagnosticsText.text = report
                appendLog("Codec analysis completed for ${device.name}")
            }
        }
    }

    private fun savePeerCode() {
        val code = binding.peerCodeEdit.text?.toString()?.trim().orEmpty()
        if (code.isNotEmpty() && code.length < 6) {
            Toast.makeText(this, "共有コードは6文字以上にしてください", Toast.LENGTH_SHORT).show(); return
        }
        AppPrefs.setPeerCode(this, code)
        appendLog(if (code.isEmpty()) "Peer Link disabled" else "Peer Link code saved")
        updatePeerStatus(); updateStatus()
    }

    private fun updatePeerStatus() {
        binding.peerStatusText.text = if (AppPrefs.peerLinkEnabled(this))
            "設定済み：同じコードをもう1台のBudsSwitchにも設定してください"
        else "未設定：Peer Linkは無効です"
    }

    private fun startAutoSwitch() {
        val device = selectedDevice() ?: return
        AppPrefs.setSelectedDevice(this, device.address)
        if (!ShizukuBridge.ready()) {
            ShizukuBridge.bindUserService(); appendLog("UserServiceを接続してから自動監視を開始してください"); return
        }
        ContextCompat.startForegroundService(this, Intent(this, AutoSwitchService::class.java))
        appendLog("Auto Switch started for ${device.name} / Peer Link=${if (AppPrefs.peerLinkEnabled(this)) "ON" else "OFF"}")
    }

    private fun updateStatus() {
        binding.statusText.text = buildString {
            append("Shizuku binder: ${if (ShizukuBridge.binderAlive()) "OK" else "NG"}")
            append("\nShizuku permission: ${if (ShizukuBridge.permissionGranted()) "OK" else "NG"}")
            append("\nShizuku uid: ${ShizukuBridge.serverUid()}")
            append("\nRemote perms: ${ShizukuBridge.remotePermissionSummary()}")
            append("\nUserService: ${if (ShizukuBridge.ready()) "OK" else "NG"}")
            append("\nPeer Link: ${if (AppPrefs.peerLinkEnabled(this@MainActivity)) "configured" else "off"}")
        }
    }

    private fun appendLog(message: String) {
        val old = binding.logText.text?.toString().orEmpty()
        binding.logText.text = (message + "\n" + old).take(20_000)
    }

    override fun onDestroy() {
        ++connectionAttempt
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuBridge.removeListener(bridgeListener)
        super.onDestroy()
    }
}
