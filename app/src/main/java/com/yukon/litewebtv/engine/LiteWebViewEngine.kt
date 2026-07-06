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

    val runtime = remember {
        GeckoRuntime.create(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            Handler(Looper.getMainLooper()).post {
                try {
                    session?.close()
                } catch (e: Exception) {
                    Log.w("LiteWebViewEngine", "关闭 session 时出错", e)
                }
                try {
                    runtime.shutdown()
                } catch (e: Exception) {
                    Log.w("LiteWebViewEngine", "关闭 runtime 时出错", e)
                }
                session = null
            }
        }
    }

    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        runtime.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/tvbridge/",
                "tvbridge@litewebtv"
            )
            .accept({ ext ->
                if (ext == null) {
                    Log.w("LiteWebViewEngine", "WebExtension为null")
                    return@accept
                }
                s.webExtensionController.setMessageDelegate(
                    ext,
                    bridgeDelegate,
                    "tvbridge"
                )
            }, { exception ->
                Log.e("LiteWebViewEngine", "WebExtension安装失败", exception)
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

                val geckoSession = GeckoSession().apply {
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
