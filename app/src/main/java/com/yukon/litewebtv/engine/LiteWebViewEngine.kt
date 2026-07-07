package com.yukon.litewebtv.engine

import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.SharedFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoView

@Composable
fun LiteWebViewEngine(
    modifier: Modifier = Modifier,
    url: String,
    jsCommandFlow: SharedFlow<String>,
    onPageLoaded: () -> Unit,
    onVideoReady: () -> Unit,
    onChannelListExtracted: (String) -> Unit,
    onProgramListExtracted: (String) -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<GeckoSession?>(null) }

    val bridgeDelegate = remember {
        GeckoBridgeDelegate(onVideoReady, onChannelListExtracted, onProgramListExtracted)
    }

    // 使用单例管理器获取 GeckoRuntime 实例（全应用只创建一次）
    val runtime = remember {
        GeckoRuntimeManager.getInstance(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            Handler(Looper.getMainLooper()).post {
                try {
                    session?.close()
                    Log.d("LiteWebViewEngine", "✓ Session 已关闭")
                } catch (e: Exception) {
                    Log.w("LiteWebViewEngine", "✗ 关闭 session 时出错", e)
                }
                session = null
                // 注意: 不在这里关闭 GeckoRuntime，由单例管理器管理
            }
        }
    }

    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        Log.d("LiteWebViewEngine", "→ 开始加载WebExtension")
        runtime.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/tvbridge/",
                "tvbridge@litewebtv"
            )
            .accept({ ext ->
                if (ext == null) {
                    Log.e("LiteWebViewEngine", "✗ WebExtension为null - 加载失败！")
                    return@accept
                }
                Log.d("LiteWebViewEngine", "✓ WebExtension已加载: ${ext.id}")
                
                s.webExtensionController.setMessageDelegate(
                    ext,
                    bridgeDelegate,
                    "tvbridge"
                )
                Log.d("LiteWebViewEngine", "✓ 消息委托已设置")
            }, { exception ->
                Log.e("LiteWebViewEngine", "✗ WebExtension安装失败: ${exception?.message}", exception)
            })
    }

    LaunchedEffect(Unit) {
        jsCommandFlow.collect { command ->
            Log.d("LiteWebViewEngine", "执行注入指令: $command")
            bridgeDelegate.executeJsCommand(command)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GeckoView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val sessionSettings = GeckoSessionSettings.Builder()
                    .userAgentOverride("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                val geckoSession = GeckoSession(sessionSettings).apply {
                    navigationDelegate = object : GeckoSession.NavigationDelegate {
                        override fun onLoadRequest(
                            session: GeckoSession,
                            request: GeckoSession.NavigationDelegate.LoadRequest
                        ): GeckoResult<AllowOrDeny>? {
                            return GeckoResult.allow()
                        }
                    }

                    progressDelegate = object : GeckoSession.ProgressDelegate {
                        override fun onPageStop(
                            session: GeckoSession,
                            success: Boolean
                        ) {
                            val status = if (success) "✓ 成功" else "✗ 失败"
                            Log.d("LiteWebViewEngine", "$status 页面加载完成")
                            if (success) {
                                Handler(Looper.getMainLooper()).post {
                                    onPageLoaded()
                                }
                            }
                        }
                    }

                    permissionDelegate = object : GeckoSession.PermissionDelegate {
                        override fun onMediaPermissionRequest(
                            session: GeckoSession,
                            uri: String,
                            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                            callback: GeckoSession.PermissionDelegate.MediaCallback
                        ) {
                            callback.grant(
                                video?.firstOrNull(),
                                audio?.firstOrNull()
                            )
                        }
                    }
                }

                // Open session on runtime first, then bind to the view and load URI.
                geckoSession.open(runtime)
                setSession(geckoSession)
                session = geckoSession
                geckoSession.loadUri(url)
            }
        },
        update = { view ->
            // Avoid unnecessary state updates to prevent recomposition loops.
            val vSession = view.session
            if (session !== vSession) {
                session = vSession
            }
        }
    )
}
