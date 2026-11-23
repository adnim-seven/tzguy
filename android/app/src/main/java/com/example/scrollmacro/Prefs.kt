package com.example.scrollmacro

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("macro", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun i(k: String, d: Int) = sp.getInt(k, d)

    /** 1) 먼저 누를 버튼의 글자 */
    var btn: String get() = s("btn", ""); set(v) = sp.edit().putString("btn", v).apply()
    /** 3) 캡처 후 누를 '다음' 버튼의 글자 (비우면 같은 버튼) */
    var next: String get() = s("next", ""); set(v) = sp.edit().putString("next", v).apply()
    /** 상단 이미지 영역 높이 (화면 높이 대비 %) */
    var topPct: Int get() = i("topPct", 30); set(v) = sp.edit().putInt("topPct", v).apply()
    /** 이미지 변화를 기다리는 최대 시간(초) */
    var waitSec: Int get() = i("waitSec", 15); set(v) = sp.edit().putInt("waitSec", v).apply()
    /** 전체 반복 횟수 */
    var rounds: Int get() = i("rounds", 10); set(v) = sp.edit().putInt("rounds", v).apply()
    var maxPages: Int get() = i("maxPages", 15); set(v) = sp.edit().putInt("maxPages", v).apply()
}
