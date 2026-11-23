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

        fun label(t: String) = root.addView(TextView(this).apply { text = t; setPadding(0, pad, 0, 0) })
        fun field(hint: String, value: String, number: Boolean = false) = EditText(this).apply {
            this.hint = hint; setText(value)
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
            root.addView(this)
        }

        label("② 이미지 변화 확인용 탭 위치 (화면 대비 %, 오른쪽 사진 넘김 영역=75/34)")
        val tapX = field("가로 X %", p.tapX.toString(), true)
        val tapY = field("세로 Y %", p.tapY.toString(), true)
        val maxTaps = field("안 바뀌면 재탭 최대 횟수", p.maxTaps.toString(), true)
        val tapWait = field("탭 후 변화 기다리는 시간(초)", p.tapWaitSec.toString(), true)

        label("⑤ 다음 넘어가기 버튼 (글자 or 위치)")
        val next = field("버튼 글자 (비우면 아래 위치를 탭)", p.next)
        val nextX = field("위치 X %", p.nextX.toString(), true)
        val nextY = field("위치 Y %", p.nextY.toString(), true)

        label("공통")
        val regionTop = field("이미지 확인 영역 시작: 화면 위에서 몇 %부터 (헤더 제외)", p.regionTop.toString(), true)
        val topPct = field("이미지 확인 영역 끝: 화면 위에서 몇 %까지", p.topPct.toString(), true)
        val wait = field("⑥ 로딩 대기 최대 시간(초)", p.waitSec.toString(), true)
        val rounds = field("반복 횟수", p.rounds.toString(), true)
        val pages = field("스크롤 캡처 최대 페이지 수", p.maxPages.toString(), true)
        val scroll = CheckBox(this).apply { text = "스크롤 캡처 사용 (끄면 현재 화면 1장만)"; isChecked = p.scroll; root.addView(this) }
        val first = CheckBox(this).apply { text = "① 라운드 시작 때도 캡처"; isChecked = p.captureFirst; root.addView(this) }

        fun n(e: EditText, d: Int, lo: Int = 0, hi: Int = 100000) = e.text.toString().toIntOrNull()?.coerceIn(lo, hi) ?: d
        fun save() {
            p.tapX = n(tapX, 75, 0, 100); p.tapY = n(tapY, 50, 0, 100)
            p.maxTaps = n(maxTaps, 5, 1); p.tapWaitSec = n(tapWait, 4, 1)
            p.next = next.text.toString().trim()
            p.nextX = n(nextX, 90, 0, 100); p.nextY = n(nextY, 90, 0, 100)
            p.regionTop = n(regionTop, 13, 0, 80); p.topPct = n(topPct, 54, 10, 95); p.scroll = scroll.isChecked; p.waitSec = n(wait, 15, 1)
            p.rounds = n(rounds, 10, 1); p.maxPages = n(pages, 15, 1)
            p.captureFirst = first.isChecked
        }

        root.addView(Button(this).apply {
            text = "시작 (5초 뒤 현재 화면에서 실행)"
            setOnClickListener {
                save()
                val svc = MacroService.instance
                if (svc == null) toast("먼저 접근성에서 ScrollMacro를 켜세요")
                else { svc.start(p); toast("5초 뒤 시작합니다. 대상 앱 화면으로 이동하세요") }
            }
        })
        root.addView(Button(this).apply { text = "중지"; setOnClickListener { MacroService.instance?.stop() } })
        root.addView(Button(this).apply {
            text = "접근성 설정 열기"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "흐름: ①캡처 → ②지정 위치 탭 → ③이미지 변경 확인 → ④안 바뀌면 재탭 → ⑤캡처 후 다음 버튼 → ⑥로딩 대기 → 반복\n결과: 사진 > Pictures/ScrollMacro (파일명: 년월일_시분초_l=탭 전, _r=탭 후)"
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
