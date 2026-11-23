"""폰에서 저장한 원본 프레임 폴더를 하나의 긴 이미지로 합친다.

사용법:
  python stitch_folder.py <프레임폴더>            # 01.png, 02.png ... 가 들어있는 폴더
  python stitch_folder.py <raw 폴더>              # 하위 폴더를 전부 처리
  옵션: --top 0.04 --bottom 0.05   (상태바/내비게이션바로 잘라낼 비율)
결과는 각 폴더 옆에 <폴더이름>_pc.png 로 저장된다.
"""
import argparse
import sys
from pathlib import Path

import cv2

from stitch import stitch


def process(folder, top, bottom):
    files = sorted(folder.glob("*.png"))
    if len(files) < 2:
        return False
    frames = []
    for f in files:
        img = cv2.imread(str(f))
        h = img.shape[0]
        frames.append(img[int(h * top): h - int(h * bottom)])
    out = folder.parent / f"{folder.name}_pc.png"
    cv2.imwrite(str(out), stitch(frames))
    print(f"{folder.name}: {len(frames)}장 -> {out.name}")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--top", type=float, default=0.04)
    ap.add_argument("--bottom", type=float, default=0.05)
    a = ap.parse_args()
    root = Path(a.path)
    targets = [root] if list(root.glob("*.png")) else sorted(d for d in root.iterdir() if d.is_dir())
    done = sum(process(d, a.top, a.bottom) for d in targets)
    if not done:
        sys.exit("합칠 폴더를 찾지 못했습니다.")


if __name__ == "__main__":
    main()
