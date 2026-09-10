package jp.example.budsswitch

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import jp.example.budsswitch.autoswitch.AutoSwitchService
import jp.example.budsswitch.databinding.ActivityMainBinding
import jp.example.budsswitch.model.BondedDevice
import jp.example.budsswitch.prefs.AppPrefs
import jp.example.budsswitch.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var devices: List<BondedDevice> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshDevices()
            updateStatus()
        }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuBridge.REQUEST_CODE) {
                appendLog("Shizuku permission result=$grantResult")
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ShizukuBridge.bindUserService()
                }
                updateStatus()
            }
        }

    private val bridgeListener: (String) -> Unit = {
        runOnUiThread {
            appendLog(it)
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuBridge.addListener(bridgeListener)

        binding.permissionButton.setOnClickListener { requestAndroidPermissions() }
        binding.shizukuButton.setOnClickListener {
            ShizukuBridge.requestPermission()
            ShizukuBridge.bindUserService()
        }
        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.refreshButton.setOnClickListener { refreshDevices() }
        binding.connectButton.setOnClickListener { selectedDevice()?.let(::connect) }
        binding.disconnectButton.setOnClickListener { selectedDevice()?.let(::disconnect) }
        binding.startAutoButton.setOnClickListener { startAutoSwitch() }
        binding.stopAutoButton.setOnClickListener {
            stopService(Intent(this, AutoSwitchService::class.java))
            appendLog("Auto Switch stopped")
        }

        if (ShizukuBridge.permissionGranted()) ShizukuBridge.bindUserService()
        requestAndroidPermissions()
        refreshDevices()
        updateStatus()
    }

    private fun requestAndroidPermissions() {
        val wanted = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.READ_PHONE_STATE)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun refreshDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            appendLog("BLUETOOTH_CONNECT permission required")
            return
        }

        val adapter = getSystemService(BluetoothManager::class.java).adapter
        devices = adapter.bondedDevices
            .map {
                BondedDevice(
                    it.name ?: "Unknown",
                    it.address
                )
            }
            .sortedWith(
                compareByDescending<BondedDevice> {
                    it.name.contains("Buds", ignoreCase = true) ||
                        it.name.contains("Samsung", ignoreCase = true)
                }.thenBy { it.name.lowercase() }
            )

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            devices
        )
        binding.deviceSpinner.adapter = spinnerAdapter

        val saved = AppPrefs.selectedDevice(this)
        val index = devices.indexOfFirst { it.address == saved }
        if (index >= 0) binding.deviceSpinner.setSelection(index)

        appendLog("Bonded devices=${devices.size}")
    }

    private fun selectedDevice(): BondedDevice? {
        val position = binding.deviceSpinner.selectedItemPosition
        if (position !in devices.indices) {
            Toast.makeText(this, "Bluetooth機器を選択してください", Toast.LENGTH_SHORT).show()
            return null
        }
        val d = devices[position]
        AppPrefs.setSelectedDevice(this, d.address)
        return d
    }

    private fun connect(device: BondedDevice) {
        if (!ShizukuBridge.ready()) {
            appendLog("Shizuku UserService not ready")
            ShizukuBridge.bindUserService()
            return
        }
        AppPrefs.setSelectedDevice(this, device.address)
        appendLog("${device.name}: ${ShizukuBridge.connect(device.address)}")
        binding.root.postDelayed({
            appendLog("status: ${ShizukuBridge.summary(device.address)}")
        }, 1800)
    }

    private fun disconnect(device: BondedDevice) {
        if (!ShizukuBridge.ready()) {
            appendLog("Shizuku UserService not ready")
            return
        }
        appendLog("${device.name}: ${ShizukuBridge.disconnect(device.address)}")
        binding.root.postDelayed({
            appendLog("status: ${ShizukuBridge.summary(device.address)}")
        }, 1800)
    }

    private fun startAutoSwitch() {
        val device = selectedDevice() ?: return
        AppPrefs.setSelectedDevice(this, device.address)

        if (!ShizukuBridge.ready()) {
            ShizukuBridge.bindUserService()
            appendLog("UserServiceを接続してから自動監視を開始してください")
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, AutoSwitchService::class.java)
        )
        appendLog("Auto Switch started for ${device.name}")
    }

    private fun updateStatus() {
        binding.statusText.text = buildString {
            append("Shizuku binder: ")
            append(if (ShizukuBridge.binderAlive()) "OK" else "NG")
            append("\nShizuku permission: ")
            append(if (ShizukuBridge.permissionGranted()) "OK" else "NG")
            append("\nShizuku uid: ")
            append(ShizukuBridge.serverUid())
            append("\nRemote perms: ")
            append(ShizukuBridge.remotePermissionSummary())
            append("\nUserService: ")
            append(if (ShizukuBridge.ready()) "OK" else "NG")
        }
    }

    private fun appendLog(message: String) {
        val old = binding.logText.text?.toString().orEmpty()
        binding.logText.text = (message + "\n" + old).take(12000)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        ShizukuBridge.removeListener(bridgeListener)
        super.onDestroy()
    }
}
