package studio.attect.androccydemo

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

class UsbAccessorySession(val usbAccessory: UsbAccessory) : CoroutineScope {

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job + CoroutineName(usbAccessory.manufacturer + usbAccessory.model)


    val textLines = mutableStateListOf<String>()
    private val channel = Channel<String>()

    var hasPermission by mutableStateOf(false)
    var active by mutableStateOf(false)
    val scrollState = LazyListState(0)
    var autoScroll by mutableStateOf(true)

    fun refreshState(context: Context, usbManager: UsbManager) {
        hasPermission = usbManager.hasPermission(usbAccessory)
    }

    fun sendString(content: String) {
        if (!active) return
        launch {
            channel.send(content)
        }
    }

    fun start(usbManager: UsbManager) {
        launch {
            delay(2000) //给授权逻辑一点时间
            val parcelFileDescriptor = runCatching { usbManager.openAccessory(usbAccessory) }.getOrNull() ?: return@launch


            val readJob = launch {
                val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)

                active = true
                val readBuffer = ByteArray(USB_ACCESSORY_MAX_READ)
                val byteArrayOutputStream = ByteArrayOutputStream()
                var textLength = -1
                var readCount = 0
                while (this.isActive) {
                    try {
                        readCount = withContext(Dispatchers.IO) {
                            inputStream.read(readBuffer)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        break
                    }
                    if (readCount < 0) {
                        break
                    }
                    if (textLength < 0) {
                        if (readCount < 2) {
                            Log.e(TAG, "数据不足，无法读取足够的长度，请检查发送端逻辑和数据")
                            break
                        }
                        val byteBuffer = ByteBuffer.allocate(2)
                        byteBuffer.put(readBuffer, 0, 2)
                        byteBuffer.position(0)
                        textLength = byteBuffer.short.toInt()
                        byteArrayOutputStream.write(readBuffer, 2, readCount - 2)
                    } else {
                        byteArrayOutputStream.write(readBuffer, 0, readCount)
                    }
                    if (byteArrayOutputStream.size() == textLength) {
                        textLines.add(byteArrayOutputStream.toString().also { sendString(it) })
                        if (autoScroll) {
                            withContext(Dispatchers.Main) {
                                scrollState.scrollToItem(textLines.size - 1)
                            }
                        }
                        byteArrayOutputStream.reset()
                        textLength = -1
                    }
                }
                withContext(Dispatchers.IO) {
                    inputStream.close()
                }
            }
            val writeJob = launch {
                val outputStream = FileOutputStream(parcelFileDescriptor.fileDescriptor)
                val lengthBuffer = ByteBuffer.allocate(2)
                while (isActive) {
                    val content = channel.receive()
                    lengthBuffer.position(0)
                    lengthBuffer.putShort(content.length.toShort())
                    val lengthByteArray = lengthBuffer.array()
                    val textByteArray = content.toByteArray()
                    val sendLength = lengthByteArray.size + textByteArray.size
                    val sendByteArray = ByteArray(sendLength)
                    for (i in 0 until sendLength) {
                        if (i < 2) {
                            sendByteArray[i] = lengthByteArray[i]
                        } else {
                            sendByteArray[i] = textByteArray[i - 2]
                        }
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            outputStream.write(sendByteArray)
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.IO) {
                            outputStream.close()
                        }
                    }

                }
            }
            readJob.join()
            if (writeJob.isActive) {
                writeJob.cancel()
            }
            parcelFileDescriptor.close() //一定要记得释放，否则设备可能无法接受下一次切换为配件模式的请求
            Log.d("DEBUG", "释放UsbAccessory")
            active = false
        }

    }


    companion object {
        private const val TAG = "UAS"
        const val USB_ACCESSORY_MAX_READ = 16384
    }


}