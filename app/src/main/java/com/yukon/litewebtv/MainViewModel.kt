package com.yukon.litewebtv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

data class Channel(val name: String, val domIndex: Int)
data class Program(val time: String, val title: String, val isPlaying: Boolean)

class MainViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _currentChannelName = MutableStateFlow("加载中...")
    val currentChannelName = _currentChannelName.asStateFlow()

    // 【新增】：当前正在播放的节目名称
    private val _currentProgramTitle = MutableStateFlow("精彩节目直播中")
    val currentProgramTitle = _currentProgramTitle.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs = _programs.asStateFlow()

    private val _showChannelSidebar = MutableStateFlow(false)
    val showChannelSidebar = _showChannelSidebar.asStateFlow()

    private val _showProgramSidebar = MutableStateFlow(false)
    val showProgramSidebar = _showProgramSidebar.asStateFlow()

    private val _jsCommand = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val jsCommand = _jsCommand.asSharedFlow()

    private val _showOsd = MutableStateFlow(false)
    val showOsd = _showOsd.asStateFlow()

    private var currentListIndex = 0

    // OSD 定时器与触发标记
    private var osdJob: Job? = null
    private var pendingOsdShow = false

    private var loadingTimeoutJob: Job? = null
    private var sidebarJob: Job? = null

    private fun startLoadingTimeout() {
        loadingTimeoutJob?.cancel()
        loadingTimeoutJob = viewModelScope.launch {
            delay(20000)
            _isLoading.value = false
        }
    }

    init {
        startLoadingTimeout()
    }

    fun setVideoReady() {
        loadingTimeoutJob?.cancel()
        _isLoading.value = false
        // 【核心修改】：视频加载完毕出画面后，主动向网页索要最新的节目单，准备展示 OSD
        pendingOsdShow = true
        _jsCommand.tryEmit("window.LiteWebTV.extractPrograms();")
    }

    fun parseChannelList(jsonString: String) {
        try {
            val list = mutableListOf<Channel>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Channel(obj.getString("name"), obj.getInt("domIndex")))
            }
            _channels.value = list
            if (list.isNotEmpty() && _currentChannelName.value == "加载中...") {
                _currentChannelName.value = list[0].name
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "解析频道列表失败", e)
        }
    }

    fun parseProgramList(jsonString: String) {
        try {
            val list = mutableListOf<Program>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Program(obj.getString("time"), obj.getString("title"), obj.getBoolean("isPlaying")))
            }
            _programs.value = list

            // 【核心修改】：提取出当前正在播放的节目单标题
            val currentPlaying = list.find { it.isPlaying }?.title ?: "精彩节目直播中"
            _currentProgramTitle.value = currentPlaying

            // 如果此时有待展示的 OSD 任务，立即执行上方居中淡入淡出动画
            if (pendingOsdShow) {
                pendingOsdShow = false
                osdJob?.cancel() // 清除之前的定时器防止冲突
                osdJob = viewModelScope.launch {
                    _showOsd.value = true
                    delay(3000)
                    _showOsd.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "解析节目单失败", e)
        }
    }

    private fun resetSidebarTimer() {
        sidebarJob?.cancel()
        sidebarJob = viewModelScope.launch {
            delay(8000)
            _showChannelSidebar.value = false
            _showProgramSidebar.value = false
        }
    }

    fun toggleChannelSidebar(show: Boolean) {
        _showChannelSidebar.value = show
        if (show) {
            _showProgramSidebar.value = false
            resetSidebarTimer()
        } else {
            sidebarJob?.cancel()
        }
    }

    fun toggleProgramSidebar(show: Boolean) {
        _showProgramSidebar.value = show
        if (show) {
            _showChannelSidebar.value = false
            _jsCommand.tryEmit("window.LiteWebTV.extractPrograms();")
            resetSidebarTimer()
        } else {
            sidebarJob?.cancel()
        }
    }

    fun switchChannelOffset(offset: Int) {
        if (_isLoading.value) return

        val list = _channels.value
        if (list.isEmpty()) return

        currentListIndex += offset
        if (currentListIndex < 0) currentListIndex = list.size - 1
        if (currentListIndex >= list.size) currentListIndex = 0

        switchToChannel(list[currentListIndex], currentListIndex)
    }

    fun switchToChannel(channel: Channel, listIndex: Int? = null) {
        if (_isLoading.value) return

        if (listIndex != null) {
            currentListIndex = listIndex
        } else {
            currentListIndex = _channels.value.indexOf(channel).takeIf { it >= 0 } ?: 0
        }

        _currentChannelName.value = channel.name

        // 切台时立刻阻断并隐藏之前的 OSD 悬浮窗
        _showOsd.value = false
        osdJob?.cancel()
        pendingOsdShow = false

        _isLoading.value = true
        _showChannelSidebar.value = false

        startLoadingTimeout()
        _jsCommand.tryEmit("window.LiteWebTV.switchChannel(${channel.domIndex});")
    }
}