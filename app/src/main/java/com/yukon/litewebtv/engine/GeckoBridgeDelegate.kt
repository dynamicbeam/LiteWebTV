package com.yukon.litewebtv.engine

import android.util.Log
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

class GeckoBridgeDelegate(
    private val onVideoReady: () -> Unit,
    private val onChannelListExtracted: (String) -> Unit,
    private val onProgramListExtracted: (String) -> Unit
) : WebExtension.MessageDelegate {

    private var commandPort: WebExtension.Port? = null
    private val pendingCommands = ArrayList<String>()

    override fun onConnect(port: WebExtension.Port) {
        Log.d("GeckoBridge", "WebExtension端口已连接")
        commandPort = port
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                handleMessage(message)
            }

            override fun onDisconnect(port: WebExtension.Port) {
                Log.d("GeckoBridge", "WebExtension端口已断开")
                if (commandPort == port) {
                    commandPort = null
                }
            }
        })
        // Drain pending commands (if any)
        synchronized(pendingCommands) {
            val it = pendingCommands.iterator()
            while (it.hasNext()) {
                val cmd = it.next()
                try {
                    val msg = org.json.JSONObject()
                    msg.put("command", cmd)
                    port.postMessage(msg)
                } catch (e: Exception) {
                    Log.e("GeckoBridge", "发送挂起命令失败", e)
                }
                it.remove()
            }
        }
    }

    override fun onMessage(
        nativeApp: String,
        message: Any,
        sender: WebExtension.MessageSender
    ): GeckoResult<Any>? {
        handleMessage(message)
        return null
    }

    private fun handleMessage(message: Any) {
        try {
            val obj = when (message) {
                is JSONObject -> message
                is String -> JSONObject(message)
                else -> null
            } ?: return

            val method = obj.optString("method")
            val data = obj.optString("data", "")
            Log.d("GeckoBridge", "收到来自WebExtension的消息: method=$method")
            when (method) {
                "notifyVideoPlaying" -> Handler(Looper.getMainLooper()).post { onVideoReady() }
                "sendChannelList" -> Handler(Looper.getMainLooper()).post { onChannelListExtracted(data) }
                "sendProgramList" -> Handler(Looper.getMainLooper()).post { onProgramListExtracted(data) }
            }
        } catch (e: Exception) {
            Log.e("GeckoBridge", "处理来自WebExtension的消息失败", e)
        }
    }

    fun executeJsCommand(command: String) {
        val port = commandPort
        if (port != null) {
            try {
                val msg = JSONObject()
                msg.put("command", command)
                port.postMessage(msg)
            } catch (e: Exception) {
                Log.e("GeckoBridge", "发送JS命令失败", e)
            }
        } else {
            // Queue the command until the port is connected
            synchronized(pendingCommands) {
                pendingCommands.add(command)
            }
            Log.w("GeckoBridge", "端口未连接，已入队命令: $command")
        }
    }
}
