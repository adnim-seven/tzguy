"""조건이 맞으면 스크롤 스크린캡처를 저장하는 매크로.

사용법:  python macro.py config.json
중단:    마우스를 화면 왼쪽 위 모서리로 이동 (pyautogui failsafe) 또는 Ctrl+C
"""
import json
import sys
import time
from datetime import datetime
from pathlib import Path

import cv2
import mss
import numpy as np
import pyautogui

from stitch import find_shift, stitch


def grab(sct, region):
    """region = [x, y, w, h] (None이면 주 모니터 전체) -> BGR 배열"""
    if region:
        x, y, w, h = region
        mon = {"left": x, "top": y, "width": w, "height": h}
    else:
        mon = sct.monitors[1]
    return np.array(sct.grab(mon))[:, :, :3].copy()


# ---------- 조건 검사 ----------
def check_condition(sct, cond):
    kind = cond["type"]
    if kind == "image":      # 화면에 템플릿 이미지가 나타나면
        screen = grab(sct, cond.get("region"))
        tpl = cv2.imread(cond["template"])
        if tpl is None:
            raise FileNotFoundError(cond["template"])
        res = cv2.matchTemplate(screen, tpl, cv2.TM_CCOEFF_NORMED)
        return cv2.minMaxLoc(res)[1] >= cond.get("threshold", 0.9)
    if kind == "pixel":      # 특정 좌표 픽셀 색이 일치하면
        x, y = cond["x"], cond["y"]
        b, g, r = grab(sct, [x, y, 1, 1])[0, 0]
        tr, tg, tb = cond["rgb"]
        tol = cond.get("tolerance", 10)
        return max(abs(int(r) - tr), abs(int(g) - tg), abs(int(b) - tb)) <= tol
    if kind == "time":       # 매일 지정 시각 (HH:MM)
        return datetime.now().strftime("%H:%M") == cond["at"]
    if kind == "always":
        return True
    raise ValueError(f"알 수 없는 조건: {kind}")


# ---------- 스크롤 캡처 ----------
def scroll_capture(sct, cfg):
    region = cfg.get("region")
    if cfg.get("click_to_focus"):      # 스크롤할 창에 포커스
        pyautogui.click(*cfg["click_to_focus"])
    time.sleep(cfg.get("settle", 0.5))
    frames = [grab(sct, region)]
    for _ in range(cfg.get("max_pages", 30)):
        pyautogui.scroll(-cfg.get("scroll_clicks", 5))
        time.sleep(cfg.get("delay", 0.6))   # 렌더링 대기
        new = grab(sct, region)
        if find_shift(frames[-1], new) == 0:   # 더 이상 안 움직임 = 끝
            break
        frames.append(new)
    return stitch(frames)


def main():
    cfg = json.loads(Path(sys.argv[1] if len(sys.argv) > 1 else "config.json").read_text(encoding="utf-8"))
    out_dir = Path(cfg.get("output_dir", "captures"))
    out_dir.mkdir(exist_ok=True)
    poll, cooldown = cfg.get("poll_interval", 1.0), cfg.get("cooldown", 30)
    last = 0
    print("감시 시작... (Ctrl+C로 종료)")
    with mss.mss() as sct:
        while True:
            if time.time() - last >= cooldown and check_condition(sct, cfg["condition"]):
                img = scroll_capture(sct, cfg["scroll"])
                path = out_dir / f"{datetime.now():%Y%m%d_%H%M%S}.png"
                cv2.imwrite(str(path), img)
                print("저장:", path, img.shape[:2])
                last = time.time()
            time.sleep(poll)


if __name__ == "__main__":
    main()
