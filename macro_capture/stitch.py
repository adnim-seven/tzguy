"""스크롤하며 찍은 프레임들을 하나의 긴 이미지로 이어붙인다."""
import cv2
import numpy as np


def find_shift(prev, new, strip=None, min_score=0.9):
    """new의 윗부분이 prev의 어디에 있는지 찾아 스크롤된 픽셀 수를 반환.
    못 찾으면 None, 완전히 동일(끝에 도달)하면 0."""
    h = prev.shape[0]
    if np.array_equal(prev, new):
        return 0
    strip = strip or max(40, h // 5)
    tpl = new[:strip]
    res = cv2.matchTemplate(prev, tpl, cv2.TM_CCOEFF_NORMED)
    _, score, _, loc = cv2.minMaxLoc(res)
    if score < min_score:
        return None
    return loc[1]  # prev에서 new 상단이 위치한 y == 스크롤 거리


def stitch(frames):
    """frames: BGR numpy 배열 리스트. 반환: 이어붙인 이미지."""
    out = [frames[0]]
    for prev, new in zip(frames, frames[1:]):
        d = find_shift(prev, new)
        if d is None:          # 겹침을 못 찾으면 그냥 이어붙임
            out.append(new)
        elif d > 0:
            out.append(new[new.shape[0] - d:])
    return np.vstack(out)
