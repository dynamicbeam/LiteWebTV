package com.yukon.litewebtv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yukon.litewebtv.Channel
import com.yukon.litewebtv.Program
import com.yukon.litewebtv.ui.theme.*
import kotlinx.coroutines.delay

val NeonBrush = Brush.linearGradient(
    colors = listOf(NeonPurple, NeonRed, NeonPink)
)

// 静态的高级深色紫红渐变，直接取代高耗能的呼吸灯与遮罩图层
val StaticAuraBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF3A0040), // 暗紫红中心
        Color(0xFF150020), // 深邃紫过渡
        PureBlack          // 纯黑边缘
    ),
    radius = 1200f
)

@Composable
fun OsdTitle(
    isVisible: Boolean,
    programTitle: String
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)), // 缩短动画时间，提升响应干脆感
        exit = fadeOut(animationSpec = tween(500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "正在播放：$programTitle",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .background(DarkFrostedBackground, RoundedCornerShape(50))
                    .border(1.dp, FocusBorderLight, RoundedCornerShape(50))
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun LoadingCurtain(
    isVisible: Boolean,
    channelName: String
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(400)),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
        ) {
            // 【性能优化 1】：完全静态的高级感径向渐变背景，0 性能损耗
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = StaticAuraBrush)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(DarkFrostedBackground, shape = RoundedCornerShape(16.dp))
                        .border(width = 2.dp, brush = NeonBrush, shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LITE WEB TV",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 12.sp,
                        style = TextStyle(
                            brush = NeonBrush,
                            shadow = Shadow(color = NeonPurple.copy(alpha = 0.8f), blurRadius = 40f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(56.dp))

                CircularProgressIndicator(
                    color = NeonPink,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "即将呈现",
                    fontSize = 16.sp,
                    color = TextHint,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = channelName,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    style = TextStyle(
                        shadow = Shadow(color = NeonRed, blurRadius = 16f)
                    )
                )
            }

            Text(
                text = "开源仓库Github/Gitee：YukonKong/LiteWebTV",
                fontSize = 12.sp,
                color = TextHint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
fun ChannelSidebar(
    isVisible: Boolean,
    channels: List<Channel>,
    currentChannelName: String,
    onChannelSelected: (Channel) -> Unit
) {
    val listState = rememberLazyListState()
    val targetFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }

    val targetIndex = remember(channels, currentChannelName) {
        channels.indexOfFirst { it.name == currentChannelName }.coerceAtLeast(0)
    }

    LaunchedEffect(isVisible) {
        if (isVisible && channels.isNotEmpty()) {
            val scrollIndex = (targetIndex - 3).coerceAtLeast(0)
            listState.scrollToItem(scrollIndex)
            delay(350)
            try { targetFocusRequester.requestFocus() } catch (e: Exception) {}
            try { sidebarFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(DarkFrostedBackground)
                .focusRequester(sidebarFocusRequester)
                .focusable()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "频道列表",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // 【性能优化 2】：强制加入唯一 key，阻止 Compose 在滑动时进行全局重组计算
                    itemsIndexed(items = channels, key = { _, channel -> channel.name }) { index, channel ->
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .then(if (index == targetIndex) Modifier.focusRequester(targetFocusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onChannelSelected(channel) }
                                .background(if (isFocused) FocusBorderLight else Color.Transparent, RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isFocused) 2.dp else 0.dp,
                                    brush = if (isFocused) NeonBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = channel.name,
                                color = if (isFocused || index == targetIndex) TextPrimary else TextSecondary,
                                fontSize = 20.sp,
                                fontWeight = if (isFocused || index == targetIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramSidebar(
    isVisible: Boolean,
    programs: List<Program>
) {
    val listState = rememberLazyListState()
    val targetFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }

    val targetIndex = remember(programs) {
        programs.indexOfFirst { it.isPlaying }.coerceAtLeast(0)
    }

    LaunchedEffect(isVisible, programs) {
        if (isVisible && programs.isNotEmpty()) {
            val scrollIndex = (targetIndex - 3).coerceAtLeast(0)
            listState.scrollToItem(scrollIndex)
            delay(350)
            try { targetFocusRequester.requestFocus() } catch (e: Exception) {}
            try { sidebarFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxSize().focusRequester(sidebarFocusRequester).focusable(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .background(DarkFrostedBackground)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "节目单列表",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        // 【性能优化 2】：强制加入唯一 key（利用时间和标题组合防止重复）
                        itemsIndexed(items = programs, key = { _, program -> "${program.time}_${program.title}" }) { index, program ->
                            var isFocused by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .then(if (index == targetIndex) Modifier.focusRequester(targetFocusRequester) else Modifier)
                                    .focusable()
                                    .background(if (isFocused) FocusBorderLight else Color.Transparent, RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 0.dp,
                                        brush = if (isFocused) NeonBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = program.time,
                                        color = if (program.isPlaying) NeonPink else TextHint,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = program.title,
                                        color = if (program.isPlaying) TextPrimary else TextSecondary,
                                        fontSize = 18.sp,
                                        fontWeight = if (program.isPlaying || isFocused) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}