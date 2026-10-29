package com.dextray.melklamp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dextray.melklamp.LampViewModel
import com.dextray.melklamp.ble.LinkState

@Composable
fun MelkApp(vm: LampViewModel) {
    val link by vm.controller.link.collectAsStateWithLifecycle()
    val connected = link as? LinkState.Connected

    // При каждом новом подключении переносим на лампу последние настройки.
    LaunchedEffect(connected?.address) {
        if (connected != null) vm.pushState()
    }
    BackHandler(enabled = connected != null) { vm.controller.disconnect() }

    AnimatedContent(
        targetState = connected != null,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "root",
    ) { isConnected ->
        if (isConnected) {
            ControlScreen(
                vm = vm,
                deviceName = connected?.name ?: "Лампа",
                onDisconnect = { vm.controller.disconnect() },
            )
        } else {
            ScanScreen(vm)
        }
    }
}
