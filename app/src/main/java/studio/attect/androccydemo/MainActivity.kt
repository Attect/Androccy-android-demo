package studio.attect.androccydemo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.attect.androccydemo.AccessoryPermissionReceiver.Companion.ACTION_REQUEST_ACCESSORY_PERMISSION
import studio.attect.androccydemo.ui.theme.AndroccyDemoTheme

class MainActivity : ComponentActivity() {

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val accessoryPermissionReceiver by lazy {
        AccessoryPermissionReceiver()
    }

    private val accessoryReceiver by lazy {
        AccessoryReceiver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(accessoryPermissionReceiver, IntentFilter(ACTION_REQUEST_ACCESSORY_PERMISSION))
        registerReceiver(accessoryReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        })
        setContent {
            AndroccyDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        Row {
                            RefreshAccessoryItemButton(Modifier.clickable { refreshUsbAccessoryList(this@MainActivity, usbManager).exceptionOrNull()?.printStackTrace() })
                            AccessoryTabBar()
                        }

                        Divider()
                        activeUsbAccessorySession?.let {
                            MessageList(
                                session = it, modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            AccessoryStatusBar(session = it) { session ->
                                usbManager.requestPermission(
                                    session.usbAccessory, PendingIntent.getBroadcast(
                                        this@MainActivity,
                                        0,
                                        Intent(ACTION_REQUEST_ACCESSORY_PERMISSION),
                                        PendingIntent.FLAG_MUTABLE
                                    )
                                )
                            }
                        } ?: kotlin.run {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(text = stringResource(id = R.string.no_any_accessory), modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsbAccessoryList(this, usbManager)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(accessoryPermissionReceiver)
        unregisterReceiver(accessoryReceiver)
    }


}

val usbAccessorySessionList = mutableStateListOf<UsbAccessorySession>()
var activeUsbAccessorySession: UsbAccessorySession? by mutableStateOf(null)

fun refreshUsbAccessoryList(context: Context, usbManager: UsbManager): Result<Unit> = runCatching {
    usbManager.accessoryList?.forEach {
        var found = false
        for (i in 0 until usbAccessorySessionList.size) {
            val currentUsbAccessorySession = usbAccessorySessionList[i]
            if (currentUsbAccessorySession.usbAccessory == it) {
                found = true
                if (!currentUsbAccessorySession.active) {
                    currentUsbAccessorySession.start(usbManager)
                }
                break
            }
        }
        if (!found) {
            val session = UsbAccessorySession(it)
            usbAccessorySessionList.add(session)
            session.start(usbManager)
            session.sendString("hello")
        }
    }
    usbAccessorySessionList.forEach {
        it.refreshState(context, usbManager)
    }
    if (activeUsbAccessorySession == null && usbAccessorySessionList.isNotEmpty()) {
        activeUsbAccessorySession = usbAccessorySessionList.first()
    }
}

@Composable
@Preview
fun RefreshAccessoryItemButton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.tertiary)
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(id = R.string.refresh), tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun AccessoryTabBar(modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier) {
        items(usbAccessorySessionList) { item ->
            AccessoryTabItem(item)
        }
    }
}

@Composable
fun AccessoryTabItem(session: UsbAccessorySession, modifier: Modifier = Modifier) {
    val backgroundColor = if (session != activeUsbAccessorySession) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary
    val textColor = if (session != activeUsbAccessorySession) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onPrimary

    Box(modifier = modifier
        .background(backgroundColor)
        .clickable {
            activeUsbAccessorySession = session
        }
        .padding(8.dp)) {
        Text(text = buildString {
            append(session.usbAccessory.manufacturer)
            append(":")
            append(session.usbAccessory.model)
            if (session.active) {
                append("(已连接)")
            }
        }, color = textColor, fontSize = 12.sp)
    }
}

@Composable
fun MessageList(modifier: Modifier = Modifier, session: UsbAccessorySession) {
    LazyColumn(modifier, session.scrollState) {
        items(session.textLines) {
            Text(text = it)
        }
    }
}

@Composable
fun AccessoryStatusBar(modifier: Modifier = Modifier, session: UsbAccessorySession, doRequestPermission: (session: UsbAccessorySession) -> Unit) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Checkbox(checked = session.autoScroll, onCheckedChange = {
            session.autoScroll = it
        })
        Text(text = stringResource(id = R.string.auto_scroll), Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
        Box(modifier = Modifier.weight(1f))
        val backgroundColor: Color
        val textColor: Color
        val text: String
        if (session.hasPermission) {
            backgroundColor = MaterialTheme.colorScheme.primaryContainer
            textColor = MaterialTheme.colorScheme.onPrimaryContainer
            text = stringResource(id = R.string.has_permission)
        } else {
            backgroundColor = MaterialTheme.colorScheme.primary
            textColor = MaterialTheme.colorScheme.onPrimary
            text = stringResource(id = R.string.requestPermission)
        }
        Box(modifier = Modifier
            .background(backgroundColor)
            .clickable(!session.hasPermission) {
                doRequestPermission.invoke(session)
            }
            .align(Alignment.CenterVertically)
        ) {
            Text(text = text, fontSize = 14.sp, color = textColor, modifier = modifier.padding(16.dp))
        }
    }
}
