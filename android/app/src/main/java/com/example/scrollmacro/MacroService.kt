package com.example.scrollmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
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
 * 모드 1: 한 라운드: 캡처 → 지정 위치 탭(안 바뀌면 재탭) → 이미지 변경 확인 → 캡처 → 다음 버튼 클릭 → 로딩 대기
 */
class MacroService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    companion object {
        @Volatile var instance: MacroService? = null
        val running get() = instance?.job?.isActive == true
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; hideFloating(); return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; hideFloating(); scope.cancel(); super.onDestroy() }
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
            if (p.todayCount() >= p.dailyLimit) { toast("오늘 ${p.dailyLimit}세트를 모두 채워 종료"); return }
            toast("라운드 $round / ${p.rounds} (오늘 ${p.todayCount()}/${p.dailyLimit})")

            // 첫 라운드는 사진이 뜰 때까지(흰색 아님 + 안정) 기다린 뒤 캡처
            if (round == 1 && !waitStable(p, p.waitSec)) { toast("로딩이 ${p.waitSec}초 안에 안 끝나 종료"); return }

            // 1) 탭 전 화면 캡처
            if (p.captureFirst) {
                val img = scrollCapture(p) ?: run { toast("캡처 실패"); return }
                save(img, round, "l")
            }

            // 2~4) 지정 위치 탭 → 상단 이미지가 바뀌었는지 확인, 안 바뀌면 다시 탭
            val before = topLuma(p) ?: run { toast("스크린샷 실패"); return }
            var changed = false
            for (attempt in 1..p.maxTaps) {
                tap(p.tapX, p.tapY)
                if (waitChangedAndStable(p, before, p.tapWaitSec)) { changed = true; break }
                toast("이미지 안 바뀜 → 다시 탭 ($attempt/${p.maxTaps})")
            }
            if (!changed) { toast("탭을 ${p.maxTaps}번 했지만 이미지가 안 바뀌어 종료"); return }

            // 5) 바뀌었으면 캡처
            val img = scrollCapture(p) ?: run { toast("캡처 실패"); return }
            save(img, round, "r")

            // 5) 다음 넘어가기(LIKE) — 마지막 세트에도 누른다. 한 세트 완료로 집계
            val ok = if (p.next.isNotBlank()) clickByText(p.next) else tap(p.nextX, p.nextY)
            if (!ok) { toast("다음 버튼을 누르지 못해 종료"); return }
            p.addToday()

            // 6) 계속할 거라면 다음 카드 이미지 로딩 대기
            if (round < p.rounds && p.todayCount() < p.dailyLimit) {
                delay(1500)
                if (!waitStable(p, p.waitSec)) { toast("로딩이 ${p.waitSec}초 안에 안 끝나 종료"); return }
            }
        }
        toast("모두 완료")
    }

    // ---------- 상단 이미지 변화 감지 ----------
    private var statusTop = 0
    private var screenW = 0
    private var screenH = 0
    private suspend fun topLuma(p: Prefs): Stitcher.Luma? {
        val bmp = shot() ?: return null
        screenW = bmp.width; screenH = bmp.height
        val start = (bmp.height * p.regionTop / 100.0).toInt()
        val end = (bmp.height * p.topPct / 100.0).toInt().coerceAtLeast(start + 50)
        return Stitcher.luma(bmp, start, bmp.height - end)
    }

    /** before 와 달라진 뒤, 연속 2번 같아질 때(=다 바뀜)까지 대기. 시간 내 못 하면 false */
    private suspend fun waitChangedAndStable(p: Prefs, before: Stitcher.Luma, timeoutSec: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        var changed = false
        var prev: Stitcher.Luma? = null
        var stableCount = 0
        while (System.currentTimeMillis() < deadline) {
            delay(1200) // takeScreenshot 간격 제한
            val cur = topLuma(p) ?: continue
            if (!changed) {
                if (Stitcher.diff(before, cur) > 3.0) { changed = true; prev = cur }
            } else {
                stableCount = nextStable(stableCount, Stitcher.diff(prev!!, cur) < 0.7)
                prev = cur
                if (loaded(stableCount, cur)) return true
            }
        }
        return false
    }

    /** 같은 화면이 연속으로 나온 횟수 갱신 (달라지면 0부터) */
    private fun nextStable(count: Int, same: Boolean): Int = if (same) count + 1 else 0

    /** 사진: 연속 2번 같으면 로딩 끝. 흰 화면: 연속 5번(약 6초) 같아야 흰 배경 사진으로 간주 */
    private fun loaded(count: Int, cur: Stitcher.Luma): Boolean =
        count >= if (Stitcher.isBlank(cur)) 5 else 2

    /** 이미지가 더 이상 변하지 않을 때까지 대기 (로딩 완료) */
    private suspend fun waitStable(p: Prefs, timeoutSec: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        var prev = topLuma(p) ?: return false
        var stableCount = 0
        while (System.currentTimeMillis() < deadline) {
            delay(1200)
            val cur = topLuma(p) ?: continue
            stableCount = nextStable(stableCount, Stitcher.diff(prev, cur) < 0.7)
            prev = cur
            if (loaded(stableCount, cur)) return true
        }
        return false
    }

    // ---------- 좌표 탭 (화면 대비 %) ----------
    private suspend fun tap(xPct: Int, yPct: Int): Boolean = suspendCancellableCoroutine { c ->
        val x = screenW * xPct / 100f; val y = screenH * yPct / 100f
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, null)
        if (!ok && c.isActive) c.resume(false)
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

    // ---------- 모드 2: 떠 있는 버튼 → 아래까지 스크롤 캡처 ----------
    private var floating: View? = null
    private val wm get() = getSystemService(WINDOW_SERVICE) as WindowManager
    private fun onMain(r: () -> Unit) { Handler(Looper.getMainLooper()).post(r) }

    fun showFloating() = onMain {
        if (floating != null) return@onMain
        val d = resources.displayMetrics.density
        val size = (64 * d).toInt()
        val v = TextView(this).apply {
            text = "캡처"; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xDD5B47A6.toInt()) }
        }
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * d).toInt()
            y = resources.displayMetrics.heightPixels / 2
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) moved = true
                    if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onFloatingClick()
            }
            true
        }
        wm.addView(v, lp)
        floating = v
    }

    fun hideFloating() = onMain {
        floating?.let { runCatching { wm.removeView(it) } }
        floating = null
    }

    private fun onFloatingClick() {
        if (running) return
        val p = Prefs(this)
        job = scope.launch {
            try {
                onMain { floating?.visibility = View.INVISIBLE } // 캡처에 버튼이 찍히지 않게
                delay(500)
                val img = scrollCapture(p, force = true, fromTop = p.m2Top)
                    ?: run { toast("캡처 실패"); return@launch }
                save(img, 0, "s")
                toast("저장 완료: ${img.width}x${img.height}")
            } catch (e: CancellationException) {
                toast("중지됨")
            } catch (t: Throwable) {
                toast("오류: ${t.message}")
            } finally {
                onMain { floating?.visibility = View.VISIBLE }
            }
        }
    }

    // ---------- 스크롤 캡처 ----------
    private suspend fun scrollCapture(p: Prefs, force: Boolean = false, fromTop: Boolean = true): Bitmap? {
        if (!p.scroll && !force) return shot() // 스크롤 없이 현재 화면 1장
        val first0 = shot() ?: return null
        val top = (first0.height * 0.04).toInt()
        val bottom = (first0.height * 0.05).toInt()
        val h = first0.height - top - bottom
        val w = first0.width

        // 맨 위로 이동 (더 안 움직일 때까지)
        var prevL = Stitcher.luma(first0, top, bottom)
        var first = first0
        for (i in 0 until (if (fromTop) 8 else 0)) {
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

    private fun save(bmp: Bitmap, round: Int, tag: String) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "${stamp}_$tag.png" // 예: 20261123_153045_l.png
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
