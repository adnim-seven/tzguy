package com.example.scrollmacro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun field(hint: String, value: String, number: Boolean = false) = EditText(this).apply {
            this.hint = hint; setText(value)
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
            root.addView(this)
        }
        val pkg = field("대상 앱 패키지명 (예: com.android.chrome, 비우면 모든 앱)", p.pkg)
        val text = field("이 텍스트가 화면에 보이면 캡처", p.text)
        val pages = field("최대 페이지 수", p.maxPages.toString(), true)
        val cool = field("재실행 대기(초)", p.cooldownSec.toString(), true)
        val sw = Switch(this).apply { text = "매크로 켜기"; isChecked = p.enabled; root.addView(this) }

        fun save() {
            p.pkg = pkg.text.toString().trim()
            p.text = text.text.toString().trim()
            p.maxPages = pages.text.toString().toIntOrNull() ?: 15
            p.cooldownSec = cool.text.toString().toIntOrNull() ?: 60
            p.enabled = sw.isChecked
        }
        sw.setOnCheckedChangeListener { _, _ -> save() }
        root.addView(Button(this).apply {
            text = "설정 저장"; setOnClickListener { save(); Toast.makeText(this@MainActivity, "저장됨", Toast.LENGTH_SHORT).show() }
        })
        root.addView(Button(this).apply {
            text = "접근성 설정 열기 (ScrollMacro 켜기)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "결과는 사진 > Pictures/ScrollMacro 에 저장됩니다."
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
