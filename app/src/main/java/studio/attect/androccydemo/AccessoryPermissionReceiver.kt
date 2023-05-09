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
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (intent.action == ACTION_REQUEST_ACCESSORY_PERMISSION) {
            val accessory = intent.getParcelableExtra<UsbAccessory?>(UsbManager.EXTRA_ACCESSORY) ?: return
            usbAccessorySessionList.forEach { session ->
                if (session.usbAccessory == accessory) {
                    session.hasPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!session.active) {
                        session.start(usbManager)
                    }
                    Log.d("DEBUG", "配件已获取授权：${session.usbAccessory}")
                }
            }
            refreshUsbAccessoryList(context, usbManager)
        }
    }

    companion object {
        const val ACTION_REQUEST_ACCESSORY_PERMISSION = "request.accessory.permission"
    }
}