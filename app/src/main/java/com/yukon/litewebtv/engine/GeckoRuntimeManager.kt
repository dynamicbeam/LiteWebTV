package com.yukon.litewebtv.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * GeckoRuntime 单例管理器
 * 
 * GeckoView 规定只能有一个 GeckoRuntime 实例，此类确保全局只创建一次
 */
object GeckoRuntimeManager {
    @Volatile
    private var runtimeInstance: GeckoRuntime? = null

    fun getInstance(context: Context): GeckoRuntime {
        return runtimeInstance ?: synchronized(this) {
            runtimeInstance ?: createNewRuntime(context).also { runtimeInstance = it }
        }
    }

    private fun createNewRuntime(context: Context): GeckoRuntime {
        // Log.d("GeckoRuntimeManager", "→ 创建新的 GeckoRuntime 实例")
//        val settings = GeckoRuntimeSettings.Builder()
//            .consoleOutput(true)
//            .build()
        return GeckoRuntime.create(context).also {
            // Log.d("GeckoRuntimeManager", "✓ GeckoRuntime 实例已创建")
        }
    }

    fun shutdown() {
        synchronized(this) {
            runtimeInstance?.let {
                try {
                    // Log.d("GeckoRuntimeManager", "→ 关闭 GeckoRuntime")
                    it.shutdown()
                    // Log.d("GeckoRuntimeManager", "✓ GeckoRuntime 已关闭")
                } catch (e: Exception) {
                    Log.e("GeckoRuntimeManager", "✗ 关闭 GeckoRuntime 失败", e)
                }
            }
            runtimeInstance = null
        }
    }
}
