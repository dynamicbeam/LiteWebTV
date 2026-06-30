## LiteWebTV 项目架构与核心技术说明文档

### 1. 项目概述

LiteWebTV 是一款专为 Android TV 平台打造的现代化直播客户端。本项目采用“原生 Compose TV UI + 极速 WebView SPA 劫持”的混合式架构，以“寄生/提纯”的方式将臃肿的现代化 Web 单页应用（央视频网页版）改造为具有原生级丝滑体验的电视端 App。

### 2. 项目目录与脚本组织结构

```text
com.yukon.litewebtv/
├── MainActivity.kt                 # 【应用入口与神经中枢】
│                                   # 负责挂载 Compose 根节点，初始化焦点引擎，并对遥控器全局物理按键（DPAD_UP/DOWN/LEFT/RIGHT/BACK）进行顶层拦截与事件分发。
│
├── MainViewModel.kt                # 【MVI 状态机】
│                                   # 采用 Kotlin Coroutines Flow 管理全局 UI 状态（幕布显隐、频道/节目单数据流、悬浮 OSD 控制）。
│                                   # 包含切台防抖/节流阀逻辑，并通过 SharedFlow 向底层 Web 发送 JS 模拟点击指令。
│
├── engine/                         # 【核心引擎层】
│   ├── LiteWebViewEngine.kt        # 极致优化的 WebView 引擎。负责：
│   │                               # 1. 物理级断流：拦截所有无用图片、SVG及 Trace 分析脚本请求。
│   │                               # 2. 注入“鸠占鹊巢”CSS：强制隐藏原网页头尾，将视频容器提权并拉伸至 100vw/100vh。
│   │                               # 3. 注入“刺客”JS 脚本：开启自动 1080P/解除静音轮询，提纯免费频道 DOM，劫持 SPA 路由进行无刷新热切台。
│   └── TvJsBridge.kt               # 原生与 JS 的双向通信桥梁（@JavascriptInterface），接收 Web 端异步回调的频道数据、节目单及播放状态。
│
└── ui/                             # 【现代化 TV UI 表现层】
    ├── components/
    │   └── TvUiComponents.kt       # 包含四大核心高阶组件：
    │                               # 1. LoadingCurtain (加载幕布)：采用纯静态深色径向渐变，零 GPU 渲染损耗，配合发光 Logo 掩盖 Web 加载黑屏。
    │                               # 2. ChannelSidebar (频道侧边栏)：左侧呼出，基于 LazyListState 实现精准焦点吸附与智能居中滚动。
    │                               # 3. ProgramSidebar (节目单侧边栏)：右侧呼出，动态标记并吸附当前正在播放的节目条目。
    │                               # 4. OsdTitle (悬浮标题)：顶部居中药丸状 UI，切台出画面后自动淡入，3秒后淡出。
    └── theme/                      # 【视觉基座】
        ├── Color.kt                # 定义 PureBlack、DarkFrostedBackground 磨砂底色及 NeonPurple 等霓虹光感调色盘。
        └── Theme.kt                # 全局沉浸式深色主题与无状态栏/导航栏配置。

```

### 3. 核心技术痛点与解决方案 (Technical Highlights)

* **性能极限压榨 (Zero GPU Overdraw)**：针对 TV 盒子孱弱的 GPU，放弃了传统的 `Modifier.blur()` 实时高斯模糊和无尽动画循环，改用纯静态 `Brush.radialGradient` 色彩过渡与透明度叠加，在零性能损耗下实现了以假乱真的“深色磨砂与呼吸灯”高级视效。
* **无缝热切台 (SPA Routing Hijack)**：绝不通过 `loadUrl()` 刷新页面切台。而是通过 JS 提取 Web DOM 节点池，安卓端下发指令直接触发 Vue 路由的 `click()` 事件。切台速度逼近物理极限（仅需等待 HLS 视频流首帧）。
* **TV 焦点引擎冲突突围 (Focus Management)**：彻底解决了 `LazyColumn` 在 TV 端由于视图未渲染导致的焦点获取异常问题。通过 `scrollToItem` + 协程延迟 + `FocusRequester` 三步走策略，实现了列表划出时焦点的 100% 完美吸附。