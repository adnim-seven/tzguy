package com.example.scrollmacro

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("macro", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun si(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    /** 이미지 변화를 확인하려고 누를 위치 (화면 대비 %) */
    var tapX: Int get() = i("tapX", 75); set(v) = si("tapX", v)
    var tapY: Int get() = i("tapY", 34); set(v) = si("tapY", v)
    /** 이미지가 안 바뀌면 다시 탭하는 최대 횟수 / 탭 후 변화를 기다리는 시간(초) */
    var maxTaps: Int get() = i("maxTaps", 5); set(v) = si("maxTaps", v)
    var tapWaitSec: Int get() = i("tapWaitSec", 4); set(v) = si("tapWaitSec", v)
    /** '다음 넘어가기' 버튼: 글자로 찾기 (비우면 아래 좌표를 탭) */
    var next: String get() = s("next", ""); set(v) = sp.edit().putString("next", v).apply()
    var nextX: Int get() = i("nextX", 77); set(v) = si("nextX", v)
    var nextY: Int get() = i("nextY", 65); set(v) = si("nextY", v)
    /** 이미지 확인 영역: 위쪽 시작 % (헤더/타이머 제외) ~ 아래쪽 끝 % */
    var regionTop: Int get() = i("regionTop", 13); set(v) = si("regionTop", v)
    var topPct: Int get() = i("topPct", 54); set(v) = si("topPct", v)
    /** 로딩 대기 최대 시간(초) */
    var waitSec: Int get() = i("waitSec", 15); set(v) = si("waitSec", v)
    var rounds: Int get() = i("rounds", 200); set(v) = si("rounds", v)
    var maxPages: Int get() = i("maxPages", 15); set(v) = si("maxPages", v)
    /** 라운드 시작 때(탭 전) 캡처 여부 */
    var captureFirst: Boolean
        get() = sp.getBoolean("captureFirst", true)
        set(v) = sp.edit().putBoolean("captureFirst", v).apply()
    /** 스크롤 캡처 사용 여부 (끄면 현재 화면 1장만 캡처, 스와이프 안 함) */
    var scroll: Boolean
        get() = sp.getBoolean("scroll", false)
        set(v) = sp.edit().putBoolean("scroll", v).apply()

    /** 하루 최대 세트 수 (앱을 껐다 켜도 날짜별로 합산, 자정에 초기화) */
    var dailyLimit: Int get() = i("dailyLimit", 200); set(v) = si("dailyLimit", v)

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /** 오늘 지금까지 한 세트 수 */
    fun todayCount(): Int = if (s("dailyDate", "") == today()) i("dailyCount", 0) else 0

    fun addToday() {
        sp.edit().putString("dailyDate", today()).putInt("dailyCount", todayCount() + 1).apply()
    }

    fun resetToday() { sp.edit().putInt("dailyCount", 0).putString("dailyDate", today()).apply() }

    /** 모드 2: 캡처 전에 맨 위로 올릴지 (끄면 현재 위치부터 아래로) */
    var m2Top: Boolean
        get() = sp.getBoolean("m2Top", false)
        set(v) = sp.edit().putBoolean("m2Top", v).apply()

    /** 스크롤 캡처 시 원본 프레임도 PNG로 저장 (PC에서 다시 합칠 때 사용) */
    var saveRaw: Boolean
        get() = sp.getBoolean("saveRaw", true)
        set(v) = sp.edit().putBoolean("saveRaw", v).apply()
}
