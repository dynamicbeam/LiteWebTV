package com.yukon.litewebtv

import android.app.Application
import android.util.Log
import com.tencent.smtt.export.external.TbsCoreSettings
import com.tencent.smtt.sdk.QbSdk

/**
 * 在 Application.onCreate 里尽早预初始化 X5 内核。
 *
 * 关键点：
 * 1. QbSdk.initX5Environment 只是"预加载"，不保证内核一定加载成功；
 *    如果本机没有 X5 内核、下载失败、或架构不支持，WebView 会自动无感回落到系统内核，
 *    LiteWebViewEngine 里不需要写任何 fallback 逻辑。
 * 2. initTbsSettings 开启 dex2oat 优化，避免内核首次加载时 ANR。
 */
class LiteWebTVApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initX5()
    }

    private fun initX5() {
        val settings = HashMap<String, Any>()
        settings[TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER] = true
        settings[TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE] = true
        QbSdk.initTbsSettings(settings)

        // 电视盒子类设备大多常年联网/流量不敏感，允许非 WiFi 环境下下发内核，
        // 否则很多盒子首次开机会一直用系统内核，等不到 X5 生效
        QbSdk.setDownloadWithoutWifi(true)

        QbSdk.initX5Environment(this, object : QbSdk.PreInitCallback {
            override fun onCoreInitFinished() {
                Log.d(TAG, "X5内核初始化流程结束")
            }

            override fun onViewInitFinished(isX5Core: Boolean) {
                // isX5Core = true 表示本次 WebView 用的是 X5 内核；
                // false 表示回落到系统内核（不是失败，是正常的降级路径）
                Log.d(TAG, if (isX5Core) "X5内核加载成功" else "X5内核未命中，已使用系统内核")
            }
        })
    }

    companion object {
        private const val TAG = "LiteWebTV-X5"
    }
}