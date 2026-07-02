package com.yukon.litewebtv.engine

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiteWebViewEngine(
    modifier: Modifier = Modifier,
    url: String,
    jsCommandFlow: SharedFlow<String>, // 【新增】接收外部指令
    onPageLoaded: () -> Unit,
    onVideoReady: () -> Unit,
    onChannelListExtracted: (String) -> Unit,
    onProgramListExtracted: (String) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // 【新增】持续监听指令通道，在不刷新页面的前提下执行 JS 操作 DOM
    LaunchedEffect(Unit) {
        jsCommandFlow.collect { command ->
            Log.d("LiteWebViewEngine", "执行注入指令: $command")
            webViewInstance?.evaluateJavascript(command, null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                addJavascriptInterface(TvJsBridge(onVideoReady, onChannelListExtracted, onProgramListExtracted), "TVBridge")
                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()?.lowercase() ?: return null
                        if (reqUrl.endsWith(".png") || reqUrl.endsWith(".jpg") || reqUrl.endsWith(".svg") || reqUrl.contains("trace.min.js")) {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // （此处保持之前的注入代码完全不变，为节省空间简略显示）
                        val script = """
                            (function() {
                                try {
                                    var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
                                              ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
                                              "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
                                              ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
                                              "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
                                    var style = document.createElement('style'); style.type = 'text/css'; style.innerHTML = css; document.head.appendChild(style);
                                    window.LiteWebTV = {
                                        channelNodes: [],
                                        init: function() { this.setupVideoListener(); this.startAutoOptimizer(); setTimeout(() => this.extractFreeChannels(), 2000); setTimeout(() => this.extractPrograms(), 2000); },
                                        setupVideoListener: function() { document.addEventListener('playing', function(e) { if(e.target && e.target.tagName === 'VIDEO') { if(window.TVBridge) window.TVBridge.notifyVideoPlaying(); } }, true); },
                                        startAutoOptimizer: function() { setInterval(() => { let muteBtn = document.querySelector('.voice.off'); if (muteBtn && muteBtn.style.display !== 'none') { muteBtn.click(); } let qualityItems = document.querySelectorAll('.bei-list .item'); qualityItems.forEach(item => { if(item.innerText.includes('1080P') && !item.classList.contains('active')) { item.click(); } }); }, 3000); },
                                        extractFreeChannels: function() { let results = []; this.channelNodes = []; let nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb'); nodes.forEach((node) => { let text = node.innerText || ""; if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) { let channelName = text.replace('(VIP)', '').replace('(限免)', '').trim(); channelName = channelName.split('\n')[0].trim(); this.channelNodes.push(node); results.push({ name: channelName, domIndex: this.channelNodes.length - 1 }); } }); if(window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results)); },
                                        switchChannel: function(domIndex) { if(this.channelNodes[domIndex]) { this.channelNodes[domIndex].click(); } },
                                        extractPrograms: function() { let results = []; let items = document.querySelectorAll('.tv-zhan-list-b-r-item'); items.forEach(item => { let isNow = item.classList.contains('now'); let timeNode = item.querySelector('div:first-child'); let titleNode = item.querySelector('.overflow-1'); if (timeNode && titleNode) { results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: isNow }); } }); if(window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results)); }
                                    };
                                    window.LiteWebTV.init();
                                } catch (e) { console.error("LiteWebTV JS Injection Error: " + e.message); }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(script, null)
                        onPageLoaded()
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            // 确保组件重组时能获取到 WebView 的最新实例
            webViewInstance = webView
        }
    )
}