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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * 한 라운드:  버튼 클릭 → 상단 이미지가 바뀌고 안정될 때까지 대기 → 스크롤 캡처 → '다음' 버튼 클릭
 */
class MacroService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    companion object {
        @Volatile var instance: MacroService? = null
        val running get() = instance?.job?.isActive == true
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; scope.cancel(); super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent) {}
    override fun onInterrupt() {}

    fun start(p: Prefs) {
        if (running) return
        job = scope.launch {
            try { runMacro(p) }
            catch (e: CancellationException) { toast("중지됨") }
            catch (t: Throwable) { toast("오류: ${t.message}") }
        }
    }

    fun stop() { job?.cancel() }

    private suspend fun runMacro(p: Prefs) {
        delay(5000) // 대상 화면으로 이동할 시간
        for (round in 1..p.rounds) {
            toast("라운드 $round / ${p.rounds}")

            // 1) 클릭 전 상단 이미지 기록
            val before = topLuma(p) ?: run { toast("스크린샷 실패"); return }

            // 2) 버튼 클릭
            if (!clickByText(p.btn)) { toast("버튼 '${p.btn}'을(를) 찾지 못해 종료"); return }

            // 3) 상단 이미지가 바뀌고 → 더 이상 안 바뀔 때까지 대기
            if (!waitChangedAndStable(p, before)) { toast("상단 이미지가 ${p.waitSec}초 안에 안 바뀌어 종료"); return }

            // 4) 스크롤 캡처 후 저장
            val img = scrollCapture(p) ?: run { toast("캡처 실패"); return }
            save(img, round)

            // 5) 다음 버튼 (마지막 라운드가 아니면)
            if (round < p.rounds) {
                val label = p.next.ifBlank { p.btn }
                if (!clickByText(label)) { toast("다음 버튼 '$label'을(를) 찾지 못해 종료"); return }
                delay(1500)
            }
        }
        toast("모두 완료")
    }

    // ---------- 상단 이미지 변화 감지 ----------
    private var statusTop = 0
    private suspend fun topLuma(p: Prefs): Stitcher.Luma? {
        val bmp = shot() ?: return null
        statusTop = (bmp.height * 0.04).toInt()
        val end = (bmp.height * p.topPct / 100.0).toInt().coerceAtLeast(statusTop + 50)
        return Stitcher.luma(bmp, statusTop, bmp.height - end)
    }

    private suspend fun waitChangedAndStable(p: Prefs, before: Stitcher.Luma): Boolean {
        val deadline = System.currentTimeMillis() + p.waitSec * 1000L
        var changed = false
        var prev: Stitcher.Luma? = null
        var stableCount = 0
        while (System.currentTimeMillis() < deadline) {
            delay(1200) // takeScreenshot 간격 제한
            val cur = topLuma(p) ?: continue
            if (!changed) {
                if (Stitcher.diff(before, cur) > 3.0) { changed = true; prev = cur }
            } else {
                // 변경 후 연속 2번 같으면 '다 바뀜'
                if (Stitcher.diff(prev!!, cur) < 0.7) stableCount++ else stableCount = 0
                prev = cur
                if (stableCount >= 2) return true
            }
        }
        return false
    }

    // ---------- 버튼 클릭 ----------
    private fun clickByText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text) ?: return false
        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cur = cur.parent
            }
        }
        return false
    }

    // ---------- 스크롤 캡처 ----------
    private suspend fun scrollCapture(p: Prefs): Bitmap? {
        val first0 = shot() ?: return null
        val top = (first0.height * 0.04).toInt()
        val bottom = (first0.height * 0.05).toInt()
        val h = first0.height - top - bottom
        val w = first0.width

        // 맨 위로 이동 (더 안 움직일 때까지)
        var prevL = Stitcher.luma(first0, top, bottom)
        var first = first0
        for (i in 0 until 8) {
            if (!swipe(w, first0.height, down = true)) break
            delay(1200)
            val b = shot() ?: break
            val l = Stitcher.luma(b, top, bottom)
            val atTop = Stitcher.diff(prevL, l) < 0.5
            first = b; prevL = l
            if (atTop) break
        }

        val frames = mutableListOf(first)
        val shifts = mutableListOf<Int>()
        for (i in 0 until p.maxPages) {
            if (!swipe(w, first.height, down = false)) break
            delay(1200)
            val bmp = shot() ?: break
            val luma = Stitcher.luma(bmp, top, bottom)
            val d = Stitcher.findShift(prevL, luma, expected = (h * 0.45).toInt())
            if (d == Stitcher.END) break
            frames += bmp
            shifts += if (d == Stitcher.FAIL) h else d
            prevL = luma
        }
        return Stitcher.stitch(frames, shifts, top, bottom)
    }

    private suspend fun shot(): Bitmap? = suspendCancellableCoroutine { c ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hb = r.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                if (c.isActive) c.resume(bmp)
            }
            override fun onFailure(errorCode: Int) { if (c.isActive) c.resume(null) }
        })
    }

    private suspend fun swipe(w: Int, h: Int, down: Boolean): Boolean = suspendCancellableCoroutine { c ->
        val a = h * 0.75f; val b = h * 0.30f
        val path = Path().apply { moveTo(w / 2f, if (down) b else a); lineTo(w / 2f, if (down) a else b) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450)) // 느리게 → 관성 스크롤 최소화
            .build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, null)
        if (!ok && c.isActive) c.resume(false)
    }

    private fun save(bmp: Bitmap, round: Int) {
        val name = "scroll_${System.currentTimeMillis()}_r$round.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScrollMacro")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun toast(msg: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
