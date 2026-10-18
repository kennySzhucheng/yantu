package com.monesy.kaoyan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 真机/模拟器冒烟测试（`connectedDebugAndroidTest`）：
 * 1. 安装包不再申请 INTERNET 权限（隐私承诺可被 CI 校验）；
 * 2. 在没有网络权限的进程里，内置中文 OCR 模型仍然能识别中文——证明「离线识别」成立。
 */
@RunWith(AndroidJUnit4::class)
class OcrOfflineTest {

    @Test
    fun apkDoesNotRequestInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertFalse("安装包不应申请 INTERNET", requested.contains(Manifest.permission.INTERNET))
        assertFalse("安装包不应申请 ACCESS_NETWORK_STATE", requested.contains(Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test
    fun bundledChineseOcrWorksWithoutNetwork() {
        val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 72f
            isAntiAlias = true
        }
        canvas.drawText("高等数学", 60f, 150f, paint)
        canvas.drawText("星期一 第3-4节", 60f, 290f, paint)

        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val result = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                120,
                TimeUnit.SECONDS,
            )
            assertTrue("OCR 结果为空，离线模型可能未正确加载", result.text.isNotBlank())
            assertTrue("未识别出预期中文：${result.text}", result.text.contains("数学") || result.text.contains("星期"))
        } finally {
            recognizer.close()
        }
    }
}
