import numpy as np
from stitch import stitch

rng = np.random.default_rng(0)
page = rng.integers(0, 255, (2000, 300, 3), dtype=np.uint8)
H, step = 600, 400
frames, y = [], 0
while True:
    y = min(y, len(page) - H)
    frames.append(page[y:y + H].copy())
    if y == len(page) - H:
        frames.append(page[y:y + H].copy())
        break
    y += step
frames = [f for i, f in enumerate(frames)]
# 마지막 중복 프레임은 macro에서 걸러지므로 제거 후 테스트
frames = frames[:-1]
out = stitch(frames)
assert out.shape == page.shape and np.array_equal(out, page), out.shape
print("OK", out.shape)
