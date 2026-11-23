package io.github.mikhasrahulia.pixelclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.mikhasrahulia.pixelclock.alarm.AlarmScheduler
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import io.github.mikhasrahulia.pixelclock.timer.TimerManager
import io.github.mikhasrahulia.pixelclock.ui.main.MainScreen
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()

        checkPermissions()

        // Offload cold-start alarm/timer reconciliation off the main thread for instant TTID
        val appContext = applicationContext
        backgroundScope.launch {
            AlarmRepository.getInstance(appContext).ensureLoadedAndMigrated()
            AlarmScheduler.getInstance(appContext).rescheduleAllAlarms()
            TimerManager.getInstance(appContext).reconcile()
        }

        setContent {
            PixelClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val appContext = applicationContext
        backgroundScope.launch {
            AlarmRepository.getInstance(appContext).flushBlocking()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
