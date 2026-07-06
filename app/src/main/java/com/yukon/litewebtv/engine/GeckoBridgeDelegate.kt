package com.yukon.litewebtv.engine

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

class GeckoBridgeDelegate(
    private val onVideoReady: () -> Unit,
    private val onChannelListExtracted: (String) -> Unit,
    private val onProgramListExtracted: (String) -> Unit
) : WebExtension.MessageDelegate {

    private var commandPort: WebExtension.Port? = null

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
        if (message is JSONObject) {
            val method = message.optString("method")
            val data = message.optString("data", "")
            Log.d("GeckoBridge", "收到来自WebExtension的消息: method=$method")
            when (method) {
                "notifyVideoPlaying" -> onVideoReady()
                "sendChannelList" -> onChannelListExtracted(data)
                "sendProgramList" -> onProgramListExtracted(data)
            }
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
            Log.w("GeckoBridge", "端口未连接，无法发送命令: $command")
        }
    }
}
