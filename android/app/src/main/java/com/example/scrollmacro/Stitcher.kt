package com.example.scrollmacro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlin.math.abs

/** 스크롤하며 찍은 프레임들을 세로로 이어붙인다. */
object Stitcher {
    private const val COL_STEP = 4
    const val END = 0   // 더 이상 스크롤되지 않음
    const val FAIL = -1 // 겹침을 못 찾음

    /** 상태바/내비게이션바를 잘라낸 영역의 밝기값 샘플 (행 × 샘플열) */
    class Luma(val rows: Array<IntArray>)

    fun luma(bmp: Bitmap, top: Int, bottom: Int): Luma {
        val h = bmp.height - top - bottom
        val w = bmp.width
        val line = IntArray(w)
        val cols = (w + COL_STEP - 1) / COL_STEP
        return Luma(Array(h) { y ->
            bmp.getPixels(line, 0, w, 0, top + y, w, 1)
            IntArray(cols) { c ->
                val p = line[c * COL_STEP]
                ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10
            }
        })
    }

    /** 두 Luma의 평균 밝기 차이 (0~255). 크기가 다르면 큰 값 반환 */
    fun diff(a: Luma, b: Luma): Double {
        if (a.rows.size != b.rows.size) return 255.0
        var sum = 0.0; var n = 0
        for (y in a.rows.indices step 2) {
            val x = a.rows[y]; val z = b.rows[y]
            for (c in x.indices) sum += abs(x[c] - z[c])
            n += x.size
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** 반환: 스크롤된 픽셀 수(>0), END, 또는 FAIL */
    fun findShift(prev: Luma, new: Luma, expected: Int): Int {
        val h = prev.rows.size
        val strip = h / 5
        var best = Double.MAX_VALUE
        var bestD = -1
        for (d in 0..(h - strip)) {
            val limit = best
            var sum = 0.0
            var n = 0
            var r = 0
            var bail = false
            while (r < strip) {
                val a = prev.rows[d + r]; val b = new.rows[r]
                for (c in a.indices) sum += abs(a[c] - b[c])
                n += a.size
                r += 2
                if (sum / n > limit + 1) { bail = true; break }
            }
            if (bail) continue
            // 균일한 영역에서 엉뚱한 위치가 잡히지 않도록 예상 이동거리 쪽에 약한 가중치
            val cost = sum / n + 0.5 * abs(d - expected) / h
            if (cost < best) { best = cost; bestD = d }
        }
        if (best > 8.0) return FAIL
        return if (bestD < 3) END else bestD
    }

    fun stitch(frames: List<Bitmap>, shifts: List<Int>, top: Int, bottom: Int): Bitmap {
        val h = frames[0].height - top - bottom
        val w = frames[0].width
        val total = h + shifts.sum()
        val out = Bitmap.createBitmap(w, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(frames[0], Rect(0, top, w, top + h), Rect(0, 0, w, h), null)
        var y = h
        for (i in 1 until frames.size) {
            val d = shifts[i - 1]
            canvas.drawBitmap(frames[i], Rect(0, top + h - d, w, top + h), Rect(0, y, w, y + d), null)
            y += d
        }
        return out
    }
}
