package com.teethut.twiprobot

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.teethut.twiprobot.data.repository.PresetHistoryRepositoryImpl
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.presentation.MainRobotApp
import com.teethut.twiprobot.presentation.drive.DriveViewModel
import com.teethut.twiprobot.presentation.history.HistoryViewModel
import com.teethut.twiprobot.presentation.telemetry.TelemetryViewModel
import com.teethut.twiprobot.presentation.tracker.TrackerViewModel
import com.teethut.twiprobot.presentation.tuning.TuningViewModel
import com.teethut.twiprobot.theme.TWIPRobotControllerTheme

class MainActivity : ComponentActivity() {

    private val driveVm: DriveViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DriveViewModel.createWithContext(applicationContext) as T
            }
        }
    }

    private val trackerVm: TrackerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrackerViewModel.createWithContext(applicationContext) as T
            }
        }
    }

    private val telemetryVm: TelemetryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TelemetryViewModel(RobotRepositoryImpl.instance) as T
            }
        }
    }

    private val tuningVm: TuningViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TuningViewModel(
                    repository = RobotRepositoryImpl.instance,
                    historyRepository = PresetHistoryRepositoryImpl.getInstance(applicationContext)
                ) as T
            }
        }
    }

    private val historyVm: HistoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel.createWithContext(applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash logger to capture any uncaught background exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TWIP_CRASH", "FATAL CRASH on ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            Log.e("MainActivity", "enableEdgeToEdge error (ignored): ${e.message}")
        }

        // Auto-route all sockets through Wi-Fi when connected to TeethutBOT_AP, bypassing mobile data
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try {
                        cm.bindProcessToNetwork(network)
                        Log.d("MainActivity", "Bound socket process to Wi-Fi for ESP32 connection")
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Failed to bind to Wi-Fi: ${e.message}")
                    }
                }
                override fun onLost(network: Network) {
                    try {
                        cm.bindProcessToNetwork(null)
                    } catch (ignored: Exception) {}
                }
            })
        } catch (e: Exception) {
            Log.w("MainActivity", "Network callback registration failed: ${e.message}")
        }

        setContent {
            TWIPRobotControllerTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainRobotApp(
                        driveViewModel = driveVm,
                        trackerViewModel = trackerVm,
                        telemetryViewModel = telemetryVm,
                        tuningViewModel = tuningVm,
                        historyViewModel = historyVm
                    )
                }
            }
        }
    }
}
