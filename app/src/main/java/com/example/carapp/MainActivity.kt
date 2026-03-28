package com.example.carapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var readJob: Job? = null

    // State (mirrors Arduino defaults)
    private var speed = 75
    private var turnSpeed = 100
    private var increment = 10
    private var turnIncrement = 10
    private var currentState = "STOP"
    private var demoMode = false

    // Views
    private lateinit var statusDot: View
    private lateinit var tvDeviceName: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var switchDemo: CompoundButton
    private lateinit var tvState: TextView
    private lateinit var tvSpeedDisplay: TextView
    private lateinit var tvTurnSpeedDisplay: TextView
    private lateinit var tvIncrementDisplay: TextView
    private lateinit var tvTurnIncrementDisplay: TextView

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) showDevicePicker()
        else toast("Bluetooth is required")
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) initiateConnection()
        else toast("Bluetooth permissions are required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        statusDot = findViewById(R.id.status_dot)
        tvDeviceName = findViewById(R.id.tv_device_name)
        btnConnect = findViewById(R.id.btn_connect)
        switchDemo = findViewById(R.id.switch_demo)
        tvState = findViewById(R.id.tv_state)
        tvSpeedDisplay = findViewById(R.id.tv_speed_display)
        tvTurnSpeedDisplay = findViewById(R.id.tv_turn_speed_display)
        tvIncrementDisplay = findViewById(R.id.tv_increment_display)
        tvTurnIncrementDisplay = findViewById(R.id.tv_turn_increment_display)
        updateStatusDisplay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Connect / Disconnect
        btnConnect.setOnClickListener {
            if (btSocket?.isConnected == true) disconnect()
            else checkPermissionsAndConnect()
        }

        // Demo mode toggle
        switchDemo.setOnCheckedChangeListener { _, isChecked ->
            demoMode = isChecked
            if (isChecked) {
                btnConnect.isEnabled = false
                speed = 75; turnSpeed = 100; increment = 10; turnIncrement = 10
                currentState = "STOP"
                tvDeviceName.text = "Demo Mode"
                statusDot.backgroundTintList = ColorStateList.valueOf(getColor(R.color.connected_green))
            } else {
                btnConnect.isEnabled = true
                tvDeviceName.text = getString(R.string.not_connected)
                statusDot.backgroundTintList = ColorStateList.valueOf(getColor(R.color.disconnected_red))
                currentState = "STOP"
            }
            updateStatusDisplay()
        }

        // D-pad: hold to move, release to stop
        setupDpadButton(R.id.btn_forward, 'F')
        setupDpadButton(R.id.btn_backward, 'B')
        setupDpadButton(R.id.btn_left, 'L')
        setupDpadButton(R.id.btn_right, 'R')

        findViewById<MaterialButton>(R.id.btn_stop).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleCommand('S')
        }

        // Speed controls
        setupClickButton(R.id.btn_speed_up, '+')
        setupClickButton(R.id.btn_speed_down, '-')
        setupClickButton(R.id.btn_turn_speed_up, ']')
        setupClickButton(R.id.btn_turn_speed_down, '[')
        setupClickButton(R.id.btn_increment_up, '}')
        setupClickButton(R.id.btn_increment_down, '{')
        setupClickButton(R.id.btn_turn_increment_up, '.')
        setupClickButton(R.id.btn_turn_increment_down, ',')
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDpadButton(id: Int, command: Char) {
        val button = findViewById<MaterialButton>(id)
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    handleCommand(command)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handleCommand('S')
                }
            }
            true
        }
    }

    private fun setupClickButton(id: Int, cmd: Char) {
        findViewById<MaterialButton>(id).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleCommand(cmd)
        }
    }

    // --- Command routing ---

    private fun handleCommand(cmd: Char) {
        if (demoMode) {
            simulateCommand(cmd)
        } else {
            sendCommand(cmd)
        }
    }

    private fun simulateCommand(cmd: Char) {
        when (cmd) {
            'F' -> currentState = "FORWARD"
            'B' -> currentState = "BACKWARD"
            'L' -> currentState = "LEFT"
            'R' -> currentState = "RIGHT"
            'S' -> currentState = "STOP"
            '+' -> speed = (speed + increment).coerceAtMost(255)
            '-' -> speed = (speed - increment).coerceAtLeast(0)
            ']' -> turnSpeed = (turnSpeed + turnIncrement).coerceAtMost(255)
            '[' -> turnSpeed = (turnSpeed - turnIncrement).coerceAtLeast(0)
            '}' -> increment = (increment + 5).coerceAtMost(50)
            '{' -> increment = (increment - 5).coerceAtLeast(1)
            '.' -> turnIncrement = (turnIncrement + 5).coerceAtMost(50)
            ',' -> turnIncrement = (turnIncrement - 5).coerceAtLeast(1)
        }
        updateStatusDisplay()
    }

    // --- Bluetooth connection flow ---

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
                return
            }
        }
        initiateConnection()
    }

    @SuppressLint("MissingPermission")
    private fun initiateConnection() {
        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager?.adapter
        if (bluetoothAdapter == null) {
            toast("Bluetooth not supported")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        showDevicePicker()
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        val paired = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        if (paired.isEmpty()) {
            toast("No paired devices. Pair with RoboCar in Bluetooth settings first.")
            return
        }
        val names = paired.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Device")
            .setItems(names) { _, idx -> connectToDevice(paired[idx]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        tvDeviceName.text = getString(R.string.connecting)
        btnConnect.isEnabled = false
        switchDemo.isEnabled = false

        scope.launch(Dispatchers.IO) {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                btSocket = socket

                withContext(Dispatchers.Main) {
                    onConnected(device.name ?: device.address)
                }
                startReading()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    btnConnect.isEnabled = true
                    switchDemo.isEnabled = true
                    tvDeviceName.text = getString(R.string.not_connected)
                    toast("Connection failed: ${e.message}")
                }
            }
        }
    }

    private fun onConnected(name: String) {
        tvDeviceName.text = name
        btnConnect.text = getString(R.string.disconnect)
        btnConnect.isEnabled = true
        switchDemo.isEnabled = false
        statusDot.backgroundTintList = ColorStateList.valueOf(getColor(R.color.connected_green))
        sendCommand('?')
    }

    private fun disconnect() {
        readJob?.cancel()
        scope.launch(Dispatchers.IO) {
            try {
                btSocket?.close()
            } catch (_: IOException) { }
            btSocket = null
            withContext(Dispatchers.Main) { onDisconnected() }
        }
    }

    private fun onDisconnected() {
        tvDeviceName.text = getString(R.string.not_connected)
        btnConnect.text = getString(R.string.connect)
        btnConnect.isEnabled = true
        switchDemo.isEnabled = true
        statusDot.backgroundTintList = ColorStateList.valueOf(getColor(R.color.disconnected_red))
        currentState = "STOP"
        updateStatusDisplay()
    }

    // --- Data transfer ---

    private fun sendCommand(cmd: Char) {
        if (btSocket?.isConnected != true) return
        scope.launch(Dispatchers.IO) {
            try {
                btSocket?.outputStream?.write(cmd.code)
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    toast("Connection lost")
                    onDisconnected()
                }
            }
        }
    }

    private fun startReading() {
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            val sb = StringBuilder()
            while (isActive) {
                try {
                    val bytes = btSocket?.inputStream?.read(buffer) ?: break
                    if (bytes <= 0) break
                    sb.append(String(buffer, 0, bytes))

                    while (sb.contains("\n")) {
                        val idx = sb.indexOf("\n")
                        val line = sb.substring(0, idx).trim()
                        sb.delete(0, idx + 1)
                        if (line.isNotEmpty()) parseResponse(line)
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        withContext(Dispatchers.Main) { onDisconnected() }
                    }
                    break
                }
            }
        }
    }

    private suspend fun parseResponse(json: String) {
        try {
            val obj = JSONObject(json)
            withContext(Dispatchers.Main) {
                currentState = obj.optString("state", currentState)
                speed = obj.optInt("speed", speed)
                turnSpeed = obj.optInt("turnSpeed", turnSpeed)
                increment = obj.optInt("increment", increment)
                turnIncrement = obj.optInt("turnIncrement", turnIncrement)
                updateStatusDisplay()
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
    }

    private fun updateStatusDisplay() {
        tvState.text = currentState
        tvSpeedDisplay.text = speed.toString()
        tvTurnSpeedDisplay.text = turnSpeed.toString()
        tvIncrementDisplay.text = increment.toString()
        tvTurnIncrementDisplay.text = turnIncrement.toString()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        readJob?.cancel()
        scope.cancel()
        try {
            btSocket?.close()
        } catch (_: IOException) { }
    }
}
