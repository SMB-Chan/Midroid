package jp.example.budsswitch.privileged

import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

@Keep
class MotorolaNativeAuditService(private val context: Context) : IMotorolaNativeAuditService.Stub() {

    override fun ping(): String =
        "ok uid=${Process.myUid()} pid=${Process.myPid()} api=${android.os.Build.VERSION.SDK_INT}"

    override fun audit(): String {
        val identity = sh("""
            echo "id=$(id 2>&1)"
            echo "manufacturer=$(getprop ro.product.manufacturer)"
            echo "model=$(getprop ro.product.model)"
            echo "device=$(getprop ro.product.device)"
            echo "fingerprint=$(getprop ro.build.fingerprint)"
            echo "abi=$(getprop ro.product.cpu.abi)"
            echo "build_type=$(getprop ro.build.type)"
            echo "verifiedboot=$(getprop ro.boot.verifiedbootstate)"
            echo "vbmeta_state=$(getprop ro.boot.vbmeta.device_state)"
            echo "selinux=$(getenforce 2>/dev/null)"
            echo "su=$(command -v su 2>/dev/null || true)"
            echo "magisk=$(command -v magisk 2>/dev/null || true)"
        """.trimIndent())

        val btLayout = sh("""
            echo "-- package/apex --"
            pm path com.android.bluetooth 2>&1 | head -n 20
            dumpsys package com.android.bluetooth 2>/dev/null | grep -E 'codePath=|resourcePath=|versionName=|versionCode=' | head -n 40
            ls -ld /apex/com.android.btservices* /apex/com.android.bluetooth* 2>/dev/null | head -n 40
            echo "-- relevant properties --"
            getprop | grep -Ei 'bluetooth|a2dp|codec|lhdc|ldac|aptx|offload' | head -n 220
        """.trimIndent(), 10_000)

        val libraryList = sh("""
            for d in /apex/com.android.btservices/lib64 /apex/com.android.bluetooth/lib64 /system/lib64 /system_ext/lib64 /product/lib64 /vendor/lib64 /odm/lib64; do
              [ -d "$d" ] || continue
              find "$d" -maxdepth 2 -type f -name '*.so' 2>/dev/null
            done | grep -Ei '/[^/]*(bluetooth|libbt|bt_|a2dp|codec|lhdc|ldac|aptx|aac|audio|scalable)[^/]*\\.so$' | sort -u | head -n 320
        """.trimIndent(), 12_000)

        val sscNamedFiles = sh("""
            find /system /system_ext /product /vendor /odm /apex/com.android.btservices /apex/com.android.bluetooth \
              -type f \( -iname '*scalable*' -o -iname '*seamless*' -o -iname '*ssc*encoder*' -o -iname 'libScalable_Encoder.so' -o -iname 'lib_bt_bundle.so' \) \
              2>/dev/null | head -n 160
        """.trimIndent(), 12_000)

        val configHits = sh("""
            grep -R -I -n -E 'Samsung[ _-]*(Scalable|Seamless)|SSC_UHQ|CODEC_TYPE_SSC|SEM_CODEC_TYPE_SSC_UHQ|0x0*075' \
              /vendor/etc /odm/etc /product/etc /system_ext/etc /system/etc /apex/com.android.btservices/etc /apex/com.android.bluetooth/etc \
              2>/dev/null | head -n 180
        """.trimIndent(), 14_000)

        val binaryHits = sh("""
            files="$(for d in /apex/com.android.btservices/lib64 /apex/com.android.bluetooth/lib64 /system/lib64 /system_ext/lib64 /product/lib64 /vendor/lib64 /odm/lib64; do [ -d "$d" ] && find "$d" -maxdepth 2 -type f -name '*.so' 2>/dev/null; done | grep -Ei '/[^/]*(bluetooth|libbt|bt_|a2dp|codec|lhdc|ldac|aptx|audio|scalable)[^/]*\\.so$' | head -n 220)"
            for f in $files; do
              if grep -a -q -E 'Samsung Scalable|Samsung Seamless|SSC_UHQ|SEM_CODEC_TYPE_SSC_UHQ|CODEC_TYPE_SSC|libScalable_Encoder|ssc_encoder_(get_size|init|create)|ssc_encode' "$f" 2>/dev/null; then
                echo "SSC_STRING_HIT $f"
              fi
            done | head -n 120
        """.trimIndent(), 18_000)

        val btDump = sh("""
            dumpsys bluetooth_manager 2>/dev/null | grep -Ei 'A2DP|codec|LDAC|LHDC|aptX|AAC|SSC|Samsung|offload' | head -n 260
        """.trimIndent(), 12_000)

        val blobFound = Regex("(?i)(libScalable_Encoder\\.so|lib_bt_bundle\\.so)").containsMatchIn(sscNamedFiles)
        val configSsc = Regex("(?i)(Samsung[ _-]*(Scalable|Seamless)|SSC_UHQ|CODEC_TYPE_SSC|SEM_CODEC_TYPE_SSC_UHQ)").containsMatchIn(configHits)
        val binarySsc = binaryHits.contains("SSC_STRING_HIT")
        val rootTool = Regex("(?m)^(su|magisk)=/.+").containsMatchIn(identity)
        val evidence = blobFound || configSsc || binarySsc

        return buildString {
            appendLine("=== MOTOROLA SSC NATIVE BACKEND AUDIT v1 ===")
            appendLine("This audit is READ-ONLY; no system/vendor files are modified.")
            appendLine()
            appendLine("--- device / privilege ---")
            appendLine(identity.trim())
            appendLine()
            appendLine("--- Bluetooth package / properties ---")
            appendLine(btLayout.trim().ifBlank { "(no output)" })
            appendLine()
            appendLine("--- candidate native libraries ---")
            appendLine(libraryList.trim().ifBlank { "(none readable/matched)" })
            appendLine()
            appendLine("--- SSC/scalable named files ---")
            appendLine(sscNamedFiles.trim().ifBlank { "(none found)" })
            appendLine()
            appendLine("--- SSC config references ---")
            appendLine(configHits.trim().ifBlank { "(none found)" })
            appendLine()
            appendLine("--- strong SSC strings in native libraries ---")
            appendLine(binaryHits.trim().ifBlank { "(none found)" })
            appendLine()
            appendLine("--- Bluetooth manager codec excerpt ---")
            appendLine(btDump.trim().ifBlank { "(no matching dump output)" })
            appendLine()
            appendLine("--- machine verdict ---")
            appendLine("encoderOrBundleFileFound=$blobFound")
            appendLine("sscConfigReferenceFound=$configSsc")
            appendLine("sscNativeStringHitFound=$binarySsc")
            appendLine("rootOrMagiskCommandPresent=$rootTool")
            appendLine("verdict=${if (evidence) "SSC_NATIVE_EVIDENCE_FOUND_REVIEW_OUTPUT" else "NO_REGISTERED_OR_DORMANT_SSC_EVIDENCE_FOUND"}")
            if (!evidence) {
                appendLine("NEXT: do not keep forcing BluetoothCodecConfig. A real encoder + AVDTP source path is required.")
                appendLine("NEXT_TEST: privileged raw L2CAP/AVDTP socket feasibility, then locally supplied Samsung encoder-blob ABI probe or clean-room encoder work.")
            } else {
                appendLine("NEXT: inspect the exact hit before deciding whether it can be activated without replacing the Bluetooth native stack.")
            }
        }.trimEnd()
    }

    private fun sh(command: String, timeoutMs: Long = 8_000L): String {
        return runCatching {
            val p = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val sb = StringBuilder()
            val reader = Thread {
                runCatching {
                    p.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (sb.length >= MAX_OUTPUT) break
                            sb.appendLine(line.take(1500))
                        }
                    }
                }
            }
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                sb.appendLine("[command timed out after ${timeoutMs}ms]")
            }
            reader.join(1200L)
            sb.toString().take(MAX_OUTPUT)
        }.getOrElse { "[command failed: ${it.javaClass.simpleName}: ${it.message}]" }
    }

    override fun destroy() { System.exit(0) }

    companion object { private const val MAX_OUTPUT = 32_000 }
}
