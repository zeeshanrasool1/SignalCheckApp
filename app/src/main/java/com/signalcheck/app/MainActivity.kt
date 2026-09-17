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
