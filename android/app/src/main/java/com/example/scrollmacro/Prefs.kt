package com.example.scrollmacro

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("macro", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    /** 이 앱이 화면에 떠 있을 때만 동작 (비우면 모든 앱) */
    var pkg: String
        get() = sp.getString("pkg", "") ?: ""
        set(v) = sp.edit().putString("pkg", v).apply()
    /** 화면에 이 텍스트가 보이면 캡처 시작 (비우면 조건 없음) */
    var text: String
        get() = sp.getString("text", "") ?: ""
        set(v) = sp.edit().putString("text", v).apply()
    var maxPages: Int
        get() = sp.getInt("maxPages", 15)
        set(v) = sp.edit().putInt("maxPages", v).apply()
    var cooldownSec: Int
        get() = sp.getInt("cooldown", 60)
        set(v) = sp.edit().putInt("cooldown", v).apply()
}
