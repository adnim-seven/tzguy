package com.example.scrollmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class MacroService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var busy = false
    private var lastRun = 0L

    override fun onInterrupt() {}

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val p = Prefs(this)
        if (!p.enabled || busy) return
        if (p.pkg.isNotBlank() && e.packageName?.toString() != p.pkg) return
        if (System.currentTimeMillis() - lastRun < p.cooldownSec * 1000L) return
        val root = rootInActiveWindow ?: return
        if (p.text.isNotBlank() && root.findAccessibilityNodeInfosByText(p.text).isNullOrEmpty()) return

        busy = true
        scope.launch {
            try { capture(p) }
            catch (t: Throwable) { toast("캡처 실패: ${t.message}") }
            finally { lastRun = System.currentTimeMillis(); busy = false }
        }
    }

    private suspend fun capture(p: Prefs) {
        val frames = mutableListOf<Bitmap>()
        val shifts = mutableListOf<Int>()
        delay(800)
        val first = shot() ?: run { toast("스크린샷 실패"); return }
        // 상태바/내비게이션바 영역 제외 (대략값)
        val top = (first.height * 0.04).toInt()
        val bottom = (first.height * 0.05).toInt()
        val h = first.height - top - bottom
        frames += first
        var prevLuma = Stitcher.luma(first, top, bottom)

        for (i in 0 until p.maxPages) {
            if (!swipe(first.width, first.height)) break
            delay(1200) // 스크롤 정착 + takeScreenshot 호출 간격 제한(약 1초) 회피
            val bmp = shot() ?: break
            val luma = Stitcher.luma(bmp, top, bottom)
            val d = Stitcher.findShift(prevLuma, luma, expected = (h * 0.45).toInt())
            if (d == Stitcher.END) break // 더 이상 스크롤되지 않음 = 페이지 끝
            frames += bmp
            shifts += if (d == Stitcher.FAIL) h else d
            prevLuma = luma
        }
        val result = Stitcher.stitch(frames, shifts, top, bottom)
        save(result)
        toast("저장 완료: ${result.width}x${result.height}")
    }

    private suspend fun shot(): Bitmap? = suspendCancellableCoroutine { c ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hb = r.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                c.resume(bmp)
            }
            override fun onFailure(errorCode: Int) { c.resume(null) }
        })
    }

    private suspend fun swipe(w: Int, h: Int): Boolean = suspendCancellableCoroutine { c ->
        val path = Path().apply { moveTo(w / 2f, h * 0.75f); lineTo(w / 2f, h * 0.30f) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450)) // 느리게 → 관성 스크롤 최소화
            .build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, null)
        if (!ok && c.isActive) c.resume(false)
    }

    private fun save(bmp: Bitmap) {
        val name = "scroll_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScrollMacro")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun toast(msg: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
}
