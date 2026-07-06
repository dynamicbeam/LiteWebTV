package com.yukon.litewebtv

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.smtt.export.external.TbsCoreSettings
import com.tencent.smtt.sdk.ProgressListener
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.TbsFramework
import com.tencent.smtt.sdk.core.dynamicinstall.DynamicInstallManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LiteWebTVApp : Application() {
    private var mContext: Context? = null

    override fun onCreate() {
        super.onCreate()
        mContext = this
        initPublicTBS()
    }

    private val preInitCallback = object : QbSdk.PreInitCallback {
        override fun onCoreInitFinished() {
            Log.i(TAG, "onCoreInitFinished: X5内核初始化成功")
        }

        override fun onViewInitFinished(isX5Code: Boolean) {
            Log.i(TAG, "是否使用X5内核: $isX5Code")
        }
    }

    private fun initPublicTBS() {
        val map = HashMap<String, Any>()
        map[TbsCoreSettings.MULTI_PROCESS_ENABLE] = 1
        QbSdk.initTbsSettings(map)

        val configFile = getConfigFile()
        if (configFile != null && configFile.exists()) {
            Log.i(TAG, "拿到配置文件: ${configFile.absolutePath}")
            downloadConfigTBS(mContext!!, configFile)
        } else {
            Log.w(TAG, "未拿到配置文件，X5内核无法动态安装")
        }
    }

    private fun downloadConfigTBS(context: Context, configFile: File) {
        TbsFramework.setUp(context, configFile)
        val manager = DynamicInstallManager(context)
        manager.registerListener(object : ProgressListener {
            override fun onProgress(progress: Int) {
                Log.i(TAG, "X5内核下载进度: $progress")
                Handler(Looper.getMainLooper()).post {
                    onDownloadProgress?.invoke(progress)
                }
            }

            override fun onFinished() {
                Log.i(TAG, "X5内核下载完成，开始预初始化")
                Handler(Looper.getMainLooper()).post {
                    onDownloadFinished?.invoke()
                }
                QbSdk.preInit(mContext, preInitCallback)
            }

            override fun onFailed(code: Int, msg: String?) {
                Log.e(TAG, "X5内核下载失败 code=$code, msg=$msg")
                Handler(Looper.getMainLooper()).post {
                    onDownloadFailed?.invoke(code, msg)
                }
            }
        })
        manager.startInstall()
    }

    private fun getConfigFile(): File? {
        try {
            val inputStream = assets.open(CONFIG_PATH)
            val inputFileName = CONFIG_PATH.substringAfter("/")
            return saveInputStreamToFile(inputStream, inputFileName)
        } catch (e: Exception) {
            Log.e(TAG, "读取配置文件失败", e)
            return null
        }
    }

    private fun saveInputStreamToFile(inputStream: java.io.InputStream, fileName: String): File {
        val file = File(filesDir, fileName)
        FileOutputStream(file).use { out ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
        inputStream.close()
        Log.i(TAG, "配置文件已保存到: ${file.absolutePath}")
        return file
    }

    companion object {
        private const val TAG = "LiteWebTV-X5"
        private const val CONFIG_PATH = "tbs/config.tbs"

        var onDownloadProgress: ((Int) -> Unit)? = null
        var onDownloadFinished: (() -> Unit)? = null
        var onDownloadFailed: ((Int, String?) -> Unit)? = null
    }
}
