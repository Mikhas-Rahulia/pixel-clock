package com.example.pixelclock.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.pixelclock.alarm.AlarmScheduler
import com.example.pixelclock.data.AlarmRepository
import com.example.pixelclock.timer.TimerManager
import com.example.pixelclock.ui.alarm.AlarmScreen
import com.example.pixelclock.ui.components.AppTab
import com.example.pixelclock.ui.components.ExpressiveNavBar
import com.example.pixelclock.ui.components.LocalModalGate
import com.example.pixelclock.ui.components.ModalGate
import com.example.pixelclock.ui.timer.TimerScreen

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alarmRepository = remember { AlarmRepository.getInstance(context) }
    val alarmScheduler = remember { AlarmScheduler.getInstance(context) }
    val timerManager = remember { TimerManager.getInstance(context) }
    val gate = remember { ModalGate() }
    var selectedTab by remember { mutableStateOf(AppTab.ALARM) }

    CompositionLocalProvider(LocalModalGate provides gate) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(
                        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                    ) { full -> if (forward) full / 5 else -full / 5 } + fadeIn(
                        spring(stiffness = Spring.StiffnessMedium)
                    )
                    val exit = slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { full -> if (forward) -full / 6 else full / 6 } + fadeOut(
                        spring(stiffness = Spring.StiffnessMedium)
                    )
                    enter togetherWith exit
                },
                label = "tab"
            ) { tab ->
                when (tab) {
                    AppTab.ALARM -> AlarmScreen(
                        repository = alarmRepository,
                        scheduler = alarmScheduler,
                        modifier = Modifier.fillMaxSize()
                    )
                    AppTab.TIMER -> TimerScreen(
                        timerManager = timerManager,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (gate.depth == 0) {
                ExpressiveNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
}
