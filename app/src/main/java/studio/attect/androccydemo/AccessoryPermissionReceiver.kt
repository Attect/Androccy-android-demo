package studio.attect.androccydemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.util.Log

class AccessoryPermissionReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == ACTION_REQUEST_ACCESSORY_PERMISSION) {
            val accessory = intent.getParcelableExtra<UsbAccessory?>(UsbManager.EXTRA_ACCESSORY) ?: return
            usbAccessorySessionList.forEach { session ->
                if (session.usbAccessory == accessory) {
                    session.hasPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d("DEBUG", "配件已获取授权：${session.usbAccessory}")
                }
            }
        }
    }

    companion object {
        const val ACTION_REQUEST_ACCESSORY_PERMISSION = "request.accessory.permission"
    }
}