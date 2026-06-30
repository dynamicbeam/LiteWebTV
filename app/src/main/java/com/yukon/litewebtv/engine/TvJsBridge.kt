package com.yukon.litewebtv.engine

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * 神经中枢：负责接收来自 JavaScript 的异步回调数据
 */
class TvJsBridge(
    private val onVideoReady: () -> Unit,
    private val onChannelListExtracted: (String) -> Unit,
    private val onProgramListExtracted: (String) -> Unit
) {
    // 网页视频真正开始播放时触发
    @JavascriptInterface
    fun notifyVideoPlaying() {
        Log.d("TvJsBridge", "收到信号：视频已开始播放/出帧")
        onVideoReady()
    }

    // 网页解析完永久免费频道后触发，返回 JSON 字符串
    @JavascriptInterface
    fun sendChannelList(jsonArray: String) {
        Log.d("TvJsBridge", "收到频道列表：$jsonArray")
        onChannelListExtracted(jsonArray)
    }

    // 网页解析完当前频道的节目单后触发，返回 JSON 字符串
    @JavascriptInterface
    fun sendProgramList(jsonArray: String) {
        Log.d("TvJsBridge", "收到节目单：$jsonArray")
        onProgramListExtracted(jsonArray)
    }
}