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
                        // 只注入主页面，忽略 iframe
                        if (url == null || !url.contains("yangshipin.cn/tv/home")) return
                        val script = """
                            (function() {
                                if (window.__LiteWebTV_injected) return;
                                window.__LiteWebTV_injected = true;
                                try {
                                    var css = "::-webkit-scrollbar { display: none !important; } " +
                                              "html, body, #app, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background: #000 !important; } " +
                                              ".tv-main-con-l-vid, video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; background: #000 !important; }";
                                    var style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);
                                    window.LiteWebTV = {
                                        channelNodes: [],
                                        init: function() {
                                            this.setupVideoListener();
                                            this.startAutoOptimizer();
                                            this.scheduleExtract();
                                            if (!window.__videoTimeout) {
                                                window.__videoTimeout = setTimeout(function() {
                                                    if (window.TVBridge) window.TVBridge.notifyVideoPlaying();
                                                }, 15000);
                                            }
                                        },
                                        setupVideoListener: function() {
                                            document.addEventListener('playing', function(e) {
                                                if (e.target && e.target.tagName === 'VIDEO') {
                                                    clearTimeout(window.__videoTimeout);
                                                    if (window.TVBridge) window.TVBridge.notifyVideoPlaying();
                                                }
                                            }, true);
                                        },
                                        startAutoOptimizer: function() {
                                            setInterval(function() {
                                                var btn = document.querySelector('.voice.off');
                                                if (btn && btn.style.display !== 'none') btn.click();
                                                document.querySelectorAll('.bei-list .item').forEach(function(item) {
                                                    if (item.innerText.includes('1080P') && !item.classList.contains('active')) item.click();
                                                });
                                            }, 3000);
                                        },
                                        scheduleExtract: function() {
                                            var retries = 0;
                                            var maxRetries = 10;
                                            function tryExtract() {
                                                if (window.TVBridge) {
                                                    window.LiteWebTV.extractFreeChannels();
                                                    window.LiteWebTV.extractPrograms();
                                                }
                                                if (++retries < maxRetries) setTimeout(tryExtract, 1000);
                                            }
                                            setTimeout(tryExtract, 1500);
                                        },
                                        extractFreeChannels: function() {
                                            if (document.querySelectorAll('.tv-main-con-r-list-left-imga').length === 0) return;
                                            var results = [];
                                            this.channelNodes = [];
                                            document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb').forEach(function(node) {
                                                var text = (node.innerText || '').trim();
                                                if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
                                                    var name = text.replace(/\(VIP\)|\(限免\)/g, '').split('\n')[0].trim();
                                                    if (name) {
                                                        this.channelNodes.push(node);
                                                        results.push({ name: name, domIndex: this.channelNodes.length - 1 });
                                                    }
                                                }
                                            }, this);
                                            if (results.length > 0 && window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
                                        },
                                        switchChannel: function(domIndex) {
                                            if (this.channelNodes[domIndex]) this.channelNodes[domIndex].click();
                                        },
                                        extractPrograms: function() {
                                            var items = document.querySelectorAll('.tv-zhan-list-b-r-item');
                                            if (items.length === 0) return;
                                            var results = [];
                                            items.forEach(function(item) {
                                                var timeNode = item.querySelector('div:first-child');
                                                var titleNode = item.querySelector('.overflow-1');
                                                if (timeNode && titleNode) results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: item.classList.contains('now') });
                                            });
                                            if (results.length > 0 && window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
                                        }
                                    };
                                    window.LiteWebTV.init();
                                } catch (e) { console.error("LiteWebTV:", e.message); }
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