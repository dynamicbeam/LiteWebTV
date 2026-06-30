package com.yukon.litewebtv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yukon.litewebtv.engine.LiteWebViewEngine
import com.yukon.litewebtv.ui.components.ChannelSidebar
import com.yukon.litewebtv.ui.components.LoadingCurtain
import com.yukon.litewebtv.ui.components.OsdTitle
import com.yukon.litewebtv.ui.components.ProgramSidebar
import com.yukon.litewebtv.ui.theme.LiteWebTVTheme
import com.yukon.litewebtv.ui.theme.PureBlack

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LiteWebTVTheme {
                val viewModel: MainViewModel = viewModel()

                val isLoading by viewModel.isLoading.collectAsState()
                val currentChannelName by viewModel.currentChannelName.collectAsState()
                val currentProgramTitle by viewModel.currentProgramTitle.collectAsState() // 订阅当前节目名
                val channels by viewModel.channels.collectAsState()
                val programs by viewModel.programs.collectAsState()
                val showChannelSidebar by viewModel.showChannelSidebar.collectAsState()
                val showProgramSidebar by viewModel.showProgramSidebar.collectAsState()
                val showOsd by viewModel.showOsd.collectAsState()

                val rootFocusRequester = remember { FocusRequester() }
                val context = LocalContext.current

                var backPressedTime by remember { mutableLongStateOf(0L) }

                LaunchedEffect(Unit) {
                    rootFocusRequester.requestFocus()
                }

                LaunchedEffect(showChannelSidebar, showProgramSidebar) {
                    if (!showChannelSidebar && !showProgramSidebar) {
                        try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .focusRequester(rootFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp) {
                                val keyCode = event.nativeKeyEvent.keyCode
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (showProgramSidebar) { viewModel.toggleProgramSidebar(false); true }
                                        else if (!showChannelSidebar) { viewModel.toggleChannelSidebar(true); true }
                                        else { viewModel.toggleChannelSidebar(false); true }
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (showChannelSidebar) { viewModel.toggleChannelSidebar(false); true }
                                        else if (!showProgramSidebar) { viewModel.toggleProgramSidebar(true); true }
                                        else { viewModel.toggleProgramSidebar(false); true }
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (!showChannelSidebar && !showProgramSidebar) {
                                            viewModel.switchChannelOffset(-1)
                                            true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (!showChannelSidebar && !showProgramSidebar) {
                                            viewModel.switchChannelOffset(1)
                                            true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        if (showChannelSidebar) { viewModel.toggleChannelSidebar(false); true }
                                        else if (showProgramSidebar) { viewModel.toggleProgramSidebar(false); true }
                                        else {
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - backPressedTime > 2000) {
                                                Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
                                                backPressedTime = currentTime
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    LiteWebViewEngine(
                        modifier = Modifier.fillMaxSize(),
                        url = "https://www.yangshipin.cn/tv/home",
                        jsCommandFlow = viewModel.jsCommand,
                        onPageLoaded = { Log.d("LiteWebTV", "引擎已就绪") },
                        onVideoReady = { viewModel.setVideoReady() },
                        onChannelListExtracted = { viewModel.parseChannelList(it) },
                        onProgramListExtracted = { viewModel.parseProgramList(it) }
                    )

                    ChannelSidebar(
                        isVisible = showChannelSidebar,
                        channels = channels,
                        currentChannelName = currentChannelName,
                        onChannelSelected = { channel ->
                            viewModel.switchToChannel(channel)
                        }
                    )

                    ProgramSidebar(isVisible = showProgramSidebar, programs = programs)
                    LoadingCurtain(isVisible = isLoading, channelName = currentChannelName)

                    // 【核心修改】：传入动态获取到的当前节目名
                    OsdTitle(isVisible = showOsd, programTitle = currentProgramTitle)
                }
            }
        }
    }
}