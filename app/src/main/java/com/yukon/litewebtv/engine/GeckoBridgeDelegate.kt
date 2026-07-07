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
        // Log.d("GeckoBridge", "✓ WebExtension端口已连接: ${port.name}")
        commandPort = port
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                // content.js 现用 sendNativeMessage，不会走这里
                // Log.d("GeckoBridge", "✓ 收到端口消息(忽略): $message")
            }

            override fun onDisconnect(port: WebExtension.Port) {
                // Log.d("GeckoBridge", "✗ WebExtension端口已断开: ${port.name}")
                if (commandPort == port) {
                    commandPort = null
                }
            }
        })
        
        synchronized(pendingCommands) {
            // Log.d("GeckoBridge", "处理 ${pendingCommands.size} 个挂起命令")
            val it = pendingCommands.iterator()
            while (it.hasNext()) {
                val cmd = it.next()
                try {
                    val msg = org.json.JSONObject()
                    msg.put("command", cmd)
                    port.postMessage(msg)
                    // Log.d("GeckoBridge", "✓ 挂起命令已发送: ${cmd.take(80)}")
                } catch (e: Exception) {
                    Log.e("GeckoBridge", "✗ 发送挂起命令失败", e)
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
                is String -> {
                    try {
                        JSONObject(message)
                    } catch (e: Exception) {
                        Log.w("GeckoBridge", "无效的 JSON 字符串: $message")
                        return
                    }
                }
                else -> {
                    Log.w("GeckoBridge", "未知的消息类型: ${message.javaClass.simpleName}")
                    return
                }
            } ?: return

            val method = obj.optString("method", "")
            val data = obj.optString("data", "")
            
            // Log.d("GeckoBridge", "✓ 处理消息: method=$method, dataLength=${data.length}")
            
            when (method) {
                "notifyVideoPlaying" -> {
                    // Log.d("GeckoBridge", "→ 视频开始播放")
                    Handler(Looper.getMainLooper()).post { onVideoReady() }
                }
                "sendChannelList" -> {
                    // Log.d("GeckoBridge", "→ 收到频道列表: ${data.length} 字符")
                    Handler(Looper.getMainLooper()).post { onChannelListExtracted(data) }
                }
                "sendProgramList" -> {
                    // Log.d("GeckoBridge", "→ 收到节目单: ${data.length} 字符")
                    Handler(Looper.getMainLooper()).post { onProgramListExtracted(data) }
                }
                else -> {
                    Log.w("GeckoBridge", "✗ 未知的方法: $method")
                }
            }
        } catch (e: Exception) {
            Log.e("GeckoBridge", "✗ 处理消息失败", e)
        }
    }

    fun executeJsCommand(command: String) {
        val port = commandPort
        if (port != null) {
            try {
                val msg = JSONObject()
                msg.put("command", command)
                port.postMessage(msg)
                // Log.d("GeckoBridge", "✓ JS命令已发送 (${command.length}字符): ${command.take(80)}")
            } catch (e: Exception) {
                Log.e("GeckoBridge", "✗ 发送JS命令失败", e)
                synchronized(pendingCommands) {
                    pendingCommands.add(command)
                    // Log.d("GeckoBridge", "命令已入队待发送，当前队列大小: ${pendingCommands.size}")
                }
            }
        } else {
            synchronized(pendingCommands) {
                pendingCommands.add(command)
                Log.w("GeckoBridge", "⏳ 端口未连接，命令已入队 (${command.length}字符)，队列大小: ${pendingCommands.size}")
            }
        }
    }
}
