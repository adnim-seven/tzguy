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
        val btn = field("누를 버튼의 글자 (예: 확인)", p.btn)
        val next = field("캡처 후 누를 '다음' 버튼 글자 (비우면 위와 같음)", p.next)
        val topPct = field("상단 이미지 영역 높이 % (화면 대비)", p.topPct.toString(), true)
        val wait = field("이미지 변화 대기 최대 시간(초)", p.waitSec.toString(), true)
        val rounds = field("반복 횟수", p.rounds.toString(), true)
        val pages = field("스크롤 캡처 최대 페이지 수", p.maxPages.toString(), true)

        fun save() {
            p.btn = btn.text.toString().trim()
            p.next = next.text.toString().trim()
            p.topPct = topPct.text.toString().toIntOrNull()?.coerceIn(10, 90) ?: 30
            p.waitSec = wait.text.toString().toIntOrNull() ?: 15
            p.rounds = rounds.text.toString().toIntOrNull() ?: 10
            p.maxPages = pages.text.toString().toIntOrNull() ?: 15
        }

        root.addView(Button(this).apply {
            text = "시작 (5초 뒤 현재 화면에서 실행)"
            setOnClickListener {
                save()
                val svc = MacroService.instance
                if (svc == null) toast("먼저 접근성에서 ScrollMacro를 켜세요")
                else if (p.btn.isBlank()) toast("버튼 글자를 입력하세요")
                else { svc.start(p); toast("5초 뒤 시작합니다. 대상 앱 화면으로 이동하세요") }
            }
        })
        root.addView(Button(this).apply {
            text = "중지"
            setOnClickListener { MacroService.instance?.stop() }
        })
        root.addView(Button(this).apply {
            text = "접근성 설정 열기"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "흐름: 버튼 클릭 → 상단 이미지 변경·안정 대기 → 스크롤 캡처 → 다음 버튼 클릭 (반복)\n결과: 사진 > Pictures/ScrollMacro"
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
