package com.dextray.melklamp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dextray.melklamp.LampViewModel
import com.dextray.melklamp.ble.LampDevice
import com.dextray.melklamp.ble.LinkState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: LampViewModel) {
    val context = LocalContext.current
    val controller = vm.controller
    val devices by controller.devices.collectAsStateWithLifecycle()
    val scanning by controller.scanning.collectAsStateWithLifecycle()
    val link by controller.link.collectAsStateWithLifecycle()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    fun permissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permissionsOk by remember { mutableStateOf(permissionsGranted()) }
    var bluetoothOn by remember { mutableStateOf(controller.isBluetoothOn) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsOk = permissionsGranted()
        bluetoothOn = controller.isBluetoothOn
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionsOk = result.values.all { it }
    }

    LaunchedEffect(permissionsOk, bluetoothOn) {
        if (permissionsOk && bluetoothOn) controller.startScan()
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(link) {
        (link as? LinkState.Error)?.let {
            snackbar.showSnackbar(it.message)
            controller.clearError()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Melk Lamp", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (permissionsOk && bluetoothOn) {
                ExtendedFloatingActionButton(
                    onClick = { if (scanning) controller.stopScan() else controller.startScan() },
                    icon = {
                        Icon(
                            if (scanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (scanning) "Остановить" else "Искать") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !permissionsOk -> item {
                    InfoCard(
                        icon = Icons.Rounded.Bluetooth,
                        title = "Нужен доступ к Bluetooth",
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Чтобы найти лампу и подключиться к ней, приложению нужно разрешение на поиск устройств рядом."
                        } else {
                            "На Android 11 и ниже для поиска Bluetooth-устройств системе требуется доступ к геолокации."
                        },
                        action = "Разрешить",
                        onAction = { launcher.launch(permissions) },
                    )
                }

                !bluetoothOn -> item {
                    InfoCard(
                        icon = Icons.Rounded.BluetoothDisabled,
                        title = "Bluetooth выключен",
                        text = "Включите Bluetooth, затем вернитесь в приложение — поиск начнётся сам.",
                        action = "Открыть настройки",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    )
                }

                else -> {
                    if (scanning) {
                        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    }

                    val lastAddress = vm.lastAddress
                    if (lastAddress != null) {
                        item {
                            LastDeviceCard(
                                name = vm.lastName ?: "Последняя лампа",
                                onClick = { vm.connect(lastAddress, vm.lastName ?: "Лампа") },
                            )
                        }
                    }

                    if (devices.isEmpty() && !scanning) {
                        item {
                            InfoCard(
                                icon = Icons.Rounded.Lightbulb,
                                title = "Лампа не найдена",
                                text = "Включите ленту в сеть и подойдите ближе. Если к ней уже подключён другой телефон, " +
                                    "закройте там оригинальное приложение — лампа принимает одно соединение.",
                                action = "Искать снова",
                                onAction = { controller.startScan() },
                            )
                        }
                    }

                    items(devices, key = { it.address }) { d ->
                        DeviceCard(d, onClick = { vm.connect(d.address, d.name) })
                    }
                }
            }
        }
    }

    (link as? LinkState.Connecting)?.let { connecting ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Подключение") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text("Подключаюсь к ${connecting.name}…")
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.disconnect() }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun LastDeviceCard(name: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
            headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Подключиться к последней лампе") },
        )
    }
}

@Composable
private fun DeviceCard(d: LampDevice, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (d.isLamp) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (d.isLamp) scheme.primary else scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (d.isLamp) Icons.Rounded.Lightbulb else Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = if (d.isLamp) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
            },
            headlineContent = { Text(d.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("${d.address} · ${d.rssi} дБм") },
            trailingContent = {
                if (d.isLamp) {
                    SuggestionChip(onClick = onClick, label = { Text("Лампа") })
                } else {
                    Icon(
                        when {
                            d.rssi > -65 -> Icons.Rounded.SignalCellularAlt
                            d.rssi > -80 -> Icons.Rounded.SignalCellularAlt2Bar
                            else -> Icons.Rounded.SignalCellularAlt1Bar
                        },
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
