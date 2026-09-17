package com.signalcheck.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var ipInfoText: TextView
    private lateinit var bottomIpText: TextView
    private lateinit var pingHostInput: EditText
    private lateinit var pingResultText: TextView
    private lateinit var tracerouteHostInput: EditText
    private lateinit var tracerouteResultText: TextView
    private lateinit var wifiScanResultText: TextView
    private lateinit var wifiAnalyzerButton: Button
    private lateinit var wifiAnalyzerSummaryText: TextView
    private lateinit var wifiAnalyzerChart: LinearLayout
    private lateinit var speedTestResultText: TextView

    private var wifiScanReceiver: BroadcastReceiver? = null
    private var isAnalyzerRunning = false
    private val analyzerHandler = Handler(Looper.getMainLooper())
    private val analyzerIntervalMs = 4000L

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            if (crashFile.exists()) {
                val text = crashFile.readText()
                crashFile.delete()
                showCrashScreen(RuntimeException("PREVIOUS CRASH LOG:\n\n$text"))
                return
            }
            setupUi()
        } catch (e: Throwable) {
            showCrashScreen(e)
        }
    }

    private fun showCrashScreen(e: Throwable) {
        val tv = TextView(this)
        tv.text = "CRASH DETAILS:\n\n" + android.util.Log.getStackTraceString(e)
        tv.setTextColor(android.graphics.Color.WHITE)
        tv.setBackgroundColor(android.graphics.Color.BLACK)
        tv.textSize = 11f
        tv.setPadding(24, 48, 24, 24)
        val scroll = android.widget.ScrollView(this)
        scroll.addView(tv)
        setContentView(scroll)
    }

    private fun setupUi() {
        setContentView(R.layout.activity_main)

        ipInfoText = findViewById(R.id.ipInfoText)
        bottomIpText = findViewById(R.id.bottomIpText)
        pingHostInput = findViewById(R.id.pingHostInput)
        pingResultText = findViewById(R.id.pingResultText)
        tracerouteHostInput = findViewById(R.id.tracerouteHostInput)
        tracerouteResultText = findViewById(R.id.tracerouteResultText)
        wifiScanResultText = findViewById(R.id.wifiScanResultText)
        wifiAnalyzerButton = findViewById(R.id.wifiAnalyzerButton)
        wifiAnalyzerSummaryText = findViewById(R.id.wifiAnalyzerSummaryText)
        wifiAnalyzerChart = findViewById(R.id.wifiAnalyzerChart)
        speedTestResultText = findViewById(R.id.speedTestResultText)

        requestNeededPermissions()

        findViewById<Button>(R.id.pingButton).setOnClickListener {
            runPing(pingHostInput.text.toString().trim())
        }
        findViewById<Button>(R.id.tracerouteButton).setOnClickListener {
            runTraceroute(tracerouteHostInput.text.toString().trim())
        }
        findViewById<Button>(R.id.wifiScanButton).setOnClickListener {
            runWifiScan()
        }
wifiAnalyzerButton.setOnClickListener {
            if (isAnalyzerRunning) stopWifiAnalyzer() else startWifiAnalyzer()
        }
        findViewById<Button>(R.id.speedTestButton).setOnClickListener {
            runSpeedTest()
        }

        loadIpInfo()
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val notGranted = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
// ---------------- IP & ISP ----------------
    private fun loadIpInfo() {
        Thread {
            try {
                val url = URL("https://ipwho.is/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val connection = json.optJSONObject("connection")
                val ip = json.optString("ip", "-")
                val isp = connection?.optString("isp") ?: "-"
                val text = buildString {
                    append("Public IP   : $ip\n")
                    append("ISP         : $isp\n")
                    append("Organization: ${connection?.optString("org") ?: "-"}\n")
                    append("ASN         : ${connection?.optString("asn") ?: "-"}\n")
                    append("City/Country: ${json.optString("city", "-")}, ${json.optString("country", "-")}\n")
                    append("Type        : ${json.optString("type", "-")}")
                }
                runOnUiThread {
                    ipInfoText.text = text
                    bottomIpText.text = "IP: $ip   |   ISP: $isp"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    ipInfoText.text = "Could not load IP info: ${e.message}"
                    bottomIpText.text = "IP: unavailable"
                }
            }
        }.start()
    }// ---------------- PING ----------------
    private fun runPing(host: String) {
        if (host.isEmpty()) return
        pingResultText.text = "Pinging $host ...\n"
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "4", "-W", "2", host))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                    val current = output.toString()
                    runOnUiThread { pingResultText.text = current }
                }
                process.waitFor()
            } catch (e: Exception) {
                runOnUiThread { pingResultText.text = "Ping failed: ${e.message}\n(/system/bin/ping may be restricted on some devices)" }
            }
        }.start()
    }
// ---------------- TRACEROUTE (best-effort via TTL-limited ping) ----------------
    private fun runTraceroute(host: String) {
        if (host.isEmpty()) return
        tracerouteResultText.text = "Starting traceroute to $host\n(Best-effort - hop IPs may not appear on some devices/Android versions)\n\n"
        Thread {
            val resultBuilder = StringBuilder(tracerouteResultText.text.toString())
            try {
                for (ttl in 1..30) {
                    val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-W", "2", "-t", ttl.toString(), host))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val lines = reader.readText()
                    process.waitFor()

                    val hopLine = when {
                        lines.contains("Time to live exceeded") || lines.contains("Time exceeded") -> {
                            val ipMatch = Regex("From ([0-9.]+)").find(lines)
                            "Hop $ttl: ${ipMatch?.groupValues?.get(1) ?: "unknown"}"
                        }
                        lines.contains("1 received") || lines.contains("1 packets received") -> {
                            "Hop $ttl: $host (destination reached)"
                        }
                        else -> "Hop $ttl: * * * (no reply)"
                    }
                    resultBuilder.append(hopLine).append("\n")
                    val current = resultBuilder.toString()
                    runOnUiThread { tracerouteResultText.text = current }

                    if (hopLine.contains("destination reached")) break
                }
            } catch (e: Exception) {
                resultBuilder.append("\nStopped: ${e.message}")
                val current = resultBuilder.toString()
                runOnUiThread { tracerouteResultText.text = current }
            }
        }.start()
    }
    // ---------------- WIFI DEVICE SCAN ----------------
    private fun runWifiScan() {
        wifiScanResultText.text = "Scanning local network...\n"
        Thread {
            try {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val dhcp = wifiManager.dhcpInfo
                val myIp = Formatter.formatIpAddress(dhcp.ipAddress)
                val prefix = myIp.substringBeforeLast(".")

                val found = StringBuilder()
                var count = 0
                val executor = Executors.newFixedThreadPool(48)
                val futures = (1..254).map { i ->
                    executor.submit {
                        val ip = "$prefix.$i"
                        try {
                            val addr = InetAddress.getByName(ip)
                            if (addr.isReachable(400)) {
                                val hostname = try { addr.canonicalHostName } catch (e: Exception) { ip }
                                val mac = readMacFromArpCache(ip)
                                synchronized(found) {
                                    count++
                                    found.append("$ip   $hostname   MAC: $mac\n")
                                }
                            }
                        } catch (e: Exception) { }
                    }
                }
                futures.forEach { it.get(2, TimeUnit.SECONDS) }
                executor.shutdown()

                val summary = "Devices found: $count (subnet $prefix.0/24)\n\n$found"
                runOnUiThread { wifiScanResultText.text = summary }
            } catch (e: Exception) {
                runOnUiThread { wifiScanResultText.text = "Scan failed: ${e.message}" }
            }
        }.start()
    }

    private fun readMacFromArpCache(ip: String): String {
        return try {
            val arpFile = java.io.File("/proc/net/arp")
            if (!arpFile.canRead()) return "N/A (restricted)"
            arpFile.readLines().drop(1).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[0] == ip) {
                    val mac = parts[3]
    if (mac != "00:00:00:00:00:00") return mac
                }
            }
            "N/A"
        } catch (e: Exception) {
            "N/A (restricted)"
        }
    }  
    // ---------------- WIFI ANALYZER (live, graphical) ----------------
    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            else -> -1
        }
    }

    private fun startWifiAnalyzer() {
        isAnalyzerRunning = true
        wifiAnalyzerButton.text = "Stop Live Analyzer"
        wifiAnalyzerSummaryText.text = "Scanning nearby WiFi networks...\n(updates roughly every few seconds - Android limits how often apps may scan)"

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (wifiScanReceiver == null) {
            wifiScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    renderAnalyzerResults(wifiManager.scanResults)
                }
            }
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        }

        val scanLoop = object : Runnable {
            override fun run() {
                if (!isAnalyzerRunning) return
                wifiManager.startScan()
                analyzerHandler.postDelayed(this, analyzerIntervalMs)
            }
        }
        analyzerHandler.post(scanLoop)
    }
private fun stopWifiAnalyzer() {
        isAnalyzerRunning = false
        wifiAnalyzerButton.text = "Start Live Analyzer"
        analyzerHandler.removeCallbacksAndMessages(null)
        if (wifiScanReceiver != null) {
            try { unregisterReceiver(wifiScanReceiver) } catch (e: Exception) { }
            wifiScanReceiver = null
        }
    }

    private fun renderAnalyzerResults(results: List<android.net.wifi.ScanResult>?) {
        if (results.isNullOrEmpty()) {
            wifiAnalyzerSummaryText.text = "No networks found yet. Make sure WiFi and Location are ON."
            return
        }

        val channelCount = mutableMapOf<Int, Int>()
        val channelStrength = mutableMapOf<Int, Int>()

        for (r in results) {
            val channel = frequencyToChannel(r.frequency)
            if (channel == -1 || channel > 14) continue
            channelCount[channel] = (channelCount[channel] ?: 0) + 1
            channelStrength[channel] = max(channelStrength[channel] ?: -100, r.level)
        }

        val candidates = if (channelCount.keys.any { it in listOf(1, 6, 11) }) listOf(1, 6, 11) else channelCount.keys.toList()
        val best = candidates.minByOrNull { ch -> (channelCount[ch] ?: 0) * 100 + (channelStrength[ch] ?: -100) }

        wifiAnalyzerSummaryText.text = "Recommended channel: ${best ?: "-"}\nNetworks seen: ${results.size}  |  Last updated: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"

        drawChannelChart(channelCount)
    }
    private fun drawChannelChart(channelCount: Map<Int, Int>) {
        wifiAnalyzerChart.removeAllViews()
        val maxCount = max(channelCount.values.maxOrNull() ?: 1, 1)

        for (ch in 1..11) {
            val count = channelCount[ch] ?: 0
            val fraction = max(count.toFloat() / maxCount, 0.02f)
            val color = when {
                count == 0 -> "#00D4FF"
                count <= 2 -> "#D4A24C"
                else -> "#E55A4B"
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val label = TextView(this).apply {
                text = "Ch $ch"
                setTextColor(Color.parseColor("#8A96A3"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, dp(14), 1f)
            }

            val bar = View(this).apply {
                  setBackgroundColor(Color.parseColor(color))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fraction)
            }
            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fraction)
            }
            track.addView(bar)
            track.addView(spacer)

            val countLabel = TextView(this).apply {
                text = "$count"
                setTextColor(Color.parseColor("#E9EDF1"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = android.view.Gravity.END
            }

            row.addView(label)
            row.addView(track)
            row.addView(countLabel)
            wifiAnalyzerChart.addView(row)
}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWifiAnalyzer()
    }

    // ---------------- SPEED TEST ----------------
    private fun runSpeedTest() {
        speedTestResultText.text = "Running download test...\n"
        Thread {
            try {
                val downUrl = URL("https://speed.cloudflare.com/__down?bytes=25000000")
                val downConn = downUrl.openConnection() as HttpURLConnection
                downConn.connectTimeout = 10000
                val startDown = System.currentTimeMillis()
                var totalBytes = 0L
                downConn.inputStream.use { input ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        totalBytes += read
                    }
                }
                val downSeconds = (System.currentTimeMillis() - startDown) / 1000.0
                val downMbps = ((totalBytes * 8) / downSeconds / 1_000_000).roundToInt()

                runOnUiThread {
                    speedTestResultText.text = "Download: $downMbps Mbps\nRunning upload test...\n"
                }

                val upUrl = URL("https://speed.cloudflare.com/__up")
                val upConn = upUrl.openConnection() as HttpURLConnection
                upConn.doOutput = true
                upConn.requestMethod = "POST"
                upConn.connectTimeout = 10000
                val uploadSize = 8_000_000
                val payload = ByteArray(uploadSize)
                val startUp = System.currentTimeMillis()
                val out: OutputStream = upConn.outputStream
                out.write(payload)
                out.flush()
                out.close()
                upConn.responseCode
                val upSeconds = (System.currentTimeMillis() - startUp) / 1000.0
                val upMbps = ((uploadSize * 8) / upSeconds / 1_000_000).roundToInt()

                runOnUiThread {
                    speedTestResultText.text = "Download: $downMbps Mbps\nUpload: $upMbps Mbps"
                }
            } catch (e: Exception) {
                runOnUiThread { speedTestResultText.text = "Speed test failed: ${e.message}" }
            }
        }.start()
    }
