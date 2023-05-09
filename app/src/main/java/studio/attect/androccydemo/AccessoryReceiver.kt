package studio.attect.androccydemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class AccessoryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        when (intent.action) {
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
            UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                refreshUsbAccessoryList(context, usbManager)
            }
        }
    }
}