"""
Scycher wallpaper pack generator – original, procedurally drawn artwork in an iPadOS-like spirit
(soft layered shapes, depth through shadow, vivid but calm colour). Every image has a dark and a
light variant so the launcher can switch with the system theme.

    python3 tools/wallpapers/generate.py <out_dir> [size]

Writes <id>_<dark|light>.webp at `size` (default 2880, square so it covers both orientations)
plus thumbs/<id>_<variant>.webp at 360 px.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

R = int(os.environ.get('WP_RENDER', '2880'))  # render resolution
E = 0.0006  # edge softness (fraction of the canvas) – crisp but anti-aliased


def grid():
    y, x = np.mgrid[0:R, 0:R].astype(np.float32)
    return x / R, y / R


X, Y = grid()


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], dtype=np.float32)


def box_blur(a, r):
    """Separable 3-pass box blur ≈ gaussian, radius in px at 1600 px (scaled to R)."""
    r = int(max(1, r * R / 1600))
    out = a.astype(np.float32)
    for _ in range(3):
        for axis in (0, 1):
            pad = [(0, 0)] * out.ndim
            pad[axis] = (r + 1, r)
            p = np.pad(out, pad, mode='edge')
            c = np.cumsum(p, axis=axis)
            if axis == 0:
                out = (c[2 * r + 1:] - c[:-2 * r - 1]) / (2 * r + 1)
            else:
                out = (c[:, 2 * r + 1:] - c[:, :-2 * r - 1]) / (2 * r + 1)
    return out


def smooth(edge0, edge1, v):
    t = np.clip((v - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)


def lin_grad(c0, c1, angle_deg, x=X, y=Y):
    a = math.radians(angle_deg)
    t = (x - 0.5) * math.cos(a) + (y - 0.5) * math.sin(a) + 0.5
    t = np.clip(t, 0, 1)[..., None]
    return c0 * (1 - t) + c1 * t


def radial(cx, cy, r):
    return np.sqrt((X - cx) ** 2 + (Y - cy) ** 2) / r


def blobs(base, spots):
    """Mesh-like gradient: base colour + soft coloured fields."""
    img = np.broadcast_to(base, (R, R, 3)).copy()
    for (cx, cy, r, col, a) in spots:
        f = np.exp(-(radial(cx, cy, r) ** 2) * 2.2)[..., None] * a
        img = img * (1 - f) + col * f
    return img


def layer(img, mask, fill, shadow=0.0, shadow_off=(0.0, 0.012), shadow_blur=28, shadow_col=None):
    """Composite a soft-edged shape with an optional drop shadow beneath it."""
    if shadow > 0:
        sh = np.roll(mask, (int(shadow_off[1] * R), int(shadow_off[0] * R)), axis=(0, 1))
        sh = box_blur(sh, shadow_blur)[..., None] * shadow
        sc = np.zeros(3, np.float32) if shadow_col is None else shadow_col
        img = img * (1 - sh) + sc * sh
    m = mask[..., None]
    return img * (1 - m) + fill * m


def ellipse_mask(cx, cy, rx, ry, angle, soft=None):
    soft = E if soft is None else soft
    a = math.radians(angle)
    dx, dy = X - cx, Y - cy
    u = dx * math.cos(a) + dy * math.sin(a)
    v = -dx * math.sin(a) + dy * math.cos(a)
    d = np.sqrt((u / rx) ** 2 + (v / ry) ** 2) - 1.0
    return smooth(soft, -soft, d * min(rx, ry))


def sheen(img, strength):
    """Gentle light from the top-left, like a material catching light."""
    s = np.clip(1.2 - (X * 0.6 + Y * 0.8), 0, 1)[..., None] * strength
    return img * (1 - s) + 1.0 * s


def vignette(img, strength):
    v = np.clip(radial(0.5, 0.5, 0.75), 0, 1.4)[..., None]
    return img * (1 - strength * v ** 2)


# --------------------------------------------------------------------------- designs

def bloom(dark):
    """Petals fanning out from a point low on the canvas."""
    if dark:
        bg = blobs(hexc('#05060c'), [(0.2, 0.15, 0.6, hexc('#1a0f3d'), 0.9), (0.85, 0.9, 0.7, hexc('#0d2347'), 0.8)])
        cols = ['#3b1c8c', '#5b2bd1', '#8a3ff0', '#c04fe0', '#ff5fa8', '#ff8a5c', '#ffb64d']
    else:
        bg = blobs(hexc('#f4f1ff'), [(0.2, 0.1, 0.6, hexc('#e3dcff'), 0.9), (0.9, 0.9, 0.7, hexc('#ffe6f0'), 0.8)])
        cols = ['#b9a7ff', '#9f86ff', '#c08cff', '#ef8fe0', '#ff9ec2', '#ffbf9e', '#ffd98a']
    img = bg
    cx, cy = 0.38, 0.92
    n = len(cols)
    for i, c in enumerate(cols):
        ang = -150 + i * (110 / (n - 1))
        a = math.radians(ang)
        px, py = cx + math.cos(a) * 0.42, cy + math.sin(a) * 0.42
        m = ellipse_mask(px, py, 0.52, 0.17, ang)
        c0, c1 = hexc(c), np.clip(hexc(c) * (0.62 if dark else 0.86) + (0 if dark else 0.08), 0, 1)
        fill = lin_grad(c0 * 1.08, c1, ang + 90)
        img = layer(img, m, np.clip(fill, 0, 1), shadow=0.55 if dark else 0.22, shadow_off=(0.004, 0.016), shadow_blur=34,
                    shadow_col=None if dark else hexc('#5a4a9a'))
    img = sheen(img, 0.05 if dark else 0.10)
    return vignette(img, 0.25 if dark else 0.04)


def tide(dark):
    """Stacked waves rolling in from the bottom."""
    if dark:
        img = lin_grad(hexc('#020814'), hexc('#071a33'), 90)
        cols = ['#0b2a5a', '#0e3f86', '#1361b8', '#1a86e0', '#29b0f0', '#5fd6ff']
    else:
        img = lin_grad(hexc('#eef7ff'), hexc('#d9ecff'), 90)
        cols = ['#cfe6ff', '#a9d2ff', '#7fbaff', '#5aa2ff', '#3f8cf5', '#2f74e0']
    n = len(cols)
    for i, c in enumerate(cols):
        base = 0.30 + i * 0.105
        amp = 0.045 + 0.012 * i
        f1, f2 = 1.4 + i * 0.35, 3.1 + i * 0.2
        edge = base + amp * np.sin(X * math.pi * f1 + i * 1.7) + amp * 0.35 * np.sin(X * math.pi * f2 + i)
        m = smooth(-E, E, Y - edge)
        c0 = hexc(c)
        fill = lin_grad(np.clip(c0 * 1.15, 0, 1), c0 * (0.7 if dark else 0.9), 90)
        img = layer(img, m, fill, shadow=0.6 if dark else 0.25, shadow_off=(0, -0.010), shadow_blur=30,
                    shadow_col=None if dark else hexc('#1d3f7a'))
    img = sheen(img, 0.04 if dark else 0.08)
    return vignette(img, 0.3 if dark else 0.03)


def orbit(dark):
    """Concentric rings around an off-canvas sun."""
    if dark:
        img = lin_grad(hexc('#0a0306'), hexc('#1a0610'), 45)
        cols = ['#3a0a1e', '#5c0f2c', '#8a1538', '#c0263f', '#ea4a3c', '#ff7a3c', '#ffad4a', '#ffd76b']
    else:
        img = lin_grad(hexc('#fff6ef'), hexc('#ffe9dc'), 45)
        cols = ['#ffe0cf', '#ffc9ae', '#ffae8c', '#ff8f73', '#ff6f6a', '#f25a78', '#d84a8a', '#b4449c']
    cx, cy = 1.02, -0.02
    for i, c in enumerate(cols):
        r = 1.25 - i * 0.135
        m = smooth(E, -E, np.sqrt((X - cx) ** 2 + (Y - cy) ** 2) - r)
        c0 = hexc(c)
        fill = lin_grad(np.clip(c0 * 1.1, 0, 1), c0 * (0.75 if dark else 0.92), 135)
        img = layer(img, m, fill, shadow=0.55 if dark else 0.22, shadow_off=(-0.010, 0.012), shadow_blur=30,
                    shadow_col=None if dark else hexc('#8a3a3a'))
    img = sheen(img, 0.03 if dark else 0.07)
    return vignette(img, 0.25 if dark else 0.03)


def ribbon(dark):
    """Aurora ribbons sweeping across."""
    if dark:
        img = blobs(hexc('#02040a'), [(0.5, 0.2, 0.8, hexc('#04122a'), 0.9)])
        cols = ['#00ffb3', '#00d4ff', '#5c6cff', '#b14cff']
        a_glow = 0.85
    else:
        img = blobs(hexc('#f2fbff'), [(0.5, 0.2, 0.8, hexc('#e6f2ff'), 0.9)])
        cols = ['#3fe0b0', '#3fc2f5', '#7b86ff', '#c07bff']
        a_glow = 0.55
    for i, c in enumerate(cols):
        t = X
        mid = 0.32 + i * 0.12 + 0.12 * np.sin(t * math.pi * (1.3 + i * 0.25) + i * 0.9) + 0.05 * np.sin(t * math.pi * 4.2 + i)
        width = 0.035 + 0.02 * np.sin(t * math.pi * 2 + i) ** 2
        d = np.abs(Y - mid) / width
        core = np.exp(-d ** 2 * 1.2)
        glow = box_blur(core, 60) * 1.6
        f = np.clip(core * 0.9 + glow * 0.6, 0, 1)[..., None] * a_glow
        col = hexc(c)
        img = img * (1 - f) + col * f if not dark else np.clip(img + col * f * 0.95, 0, 1)
    # faint stars / dust on dark
    if dark:
        rng = np.random.default_rng(7)
        stars = (rng.random((R, R)) > 0.9993).astype(np.float32)
        stars = box_blur(stars, 1) * 6
        img = np.clip(img + stars[..., None] * 0.6 * np.clip(1 - Y * 1.2, 0, 1)[..., None], 0, 1)
    return vignette(img, 0.3 if dark else 0.03)


def mesh(dark):
    """Pure colour – a soft mesh gradient."""
    if dark:
        spots = [(0.15, 0.2, 0.55, hexc('#3d1bd6'), 0.95), (0.85, 0.15, 0.5, hexc('#0b74ff'), 0.9),
                 (0.75, 0.8, 0.6, hexc('#ff2d7a'), 0.85), (0.2, 0.85, 0.5, hexc('#ff8a00'), 0.7),
                 (0.52, 0.48, 0.32, hexc('#9b1fe8'), 0.55)]
        img = blobs(hexc('#07050f'), spots)
    else:
        spots = [(0.15, 0.2, 0.55, hexc('#c7b8ff'), 0.95), (0.85, 0.15, 0.5, hexc('#9fd0ff'), 0.9),
                 (0.75, 0.8, 0.6, hexc('#ffb3cf'), 0.85), (0.2, 0.85, 0.5, hexc('#ffd59e'), 0.8),
                 (0.5, 0.5, 0.35, hexc('#ffffff'), 0.5)]
        img = blobs(hexc('#f7f4ff'), spots)
    return sheen(img, 0.03 if dark else 0.05)


def dune(dark):
    """Layered hills with atmospheric depth."""
    if dark:
        img = lin_grad(hexc('#140a24'), hexc('#3a1640'), 90)
        sun = np.exp(-(radial(0.7, 0.36, 0.09) ** 2) * 3)[..., None]
        img = np.clip(img + hexc('#ff9a5a') * sun * 0.9 + hexc('#ff5a7a') * box_blur(sun[..., 0], 80)[..., None] * 0.6, 0, 1)
        cols = ['#9a3d7c', '#7a2d6a', '#5a2054', '#3d153d', '#240a26']
    else:
        img = lin_grad(hexc('#fff1e6'), hexc('#ffd9c8'), 90)
        sun = np.exp(-(radial(0.7, 0.36, 0.09) ** 2) * 3)[..., None]
        img = np.clip(img + hexc('#ffffff') * sun * 0.7, 0, 1)
        cols = ['#ffc3a8', '#f7a48f', '#e98783', '#d06d80', '#b25a7d']
    for i, c in enumerate(cols):
        base = 0.46 + i * 0.1
        edge = base + 0.06 * np.sin(X * math.pi * (1.1 + i * 0.3) + i * 2.1) + 0.025 * np.sin(X * math.pi * 3.7 + i)
        m = smooth(-E, E, Y - edge)
        c0 = hexc(c)
        fill = lin_grad(np.clip(c0 * 1.12, 0, 1), c0 * 0.85, 90)
        img = layer(img, m, fill, shadow=0.45 if dark else 0.18, shadow_off=(0, -0.008), shadow_blur=26,
                    shadow_col=None if dark else hexc('#7a3a50'))
    return vignette(img, 0.25 if dark else 0.02)


def prism(dark):
    """Overlapping translucent panes, like stacked glass."""
    if dark:
        img = blobs(hexc('#05070b'), [(0.3, 0.3, 0.7, hexc('#0e1a2a'), 0.9)])
        cols = ['#2ee6c8', '#2aa8ff', '#7a5cff', '#ff4fa3', '#ffb340']
        alpha = 0.55
    else:
        img = blobs(hexc('#f6f8fb'), [(0.3, 0.3, 0.7, hexc('#e9eef7'), 0.9)])
        cols = ['#7ef0dc', '#86c8ff', '#b3a1ff', '#ff9fcf', '#ffd38a']
        alpha = 0.6
    rng = np.random.default_rng(3)
    for i, c in enumerate(cols):
        cx, cy = 0.2 + i * 0.15, 0.25 + (i % 3) * 0.22
        ang = -28 + i * 9
        a = math.radians(ang)
        dx, dy = X - cx, Y - cy
        u = dx * math.cos(a) + dy * math.sin(a)
        v = -dx * math.sin(a) + dy * math.cos(a)
        hw, hh, rr = 0.26, 0.17, 0.06
        qx, qy = np.abs(u) - hw + rr, np.abs(v) - hh + rr
        d = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - rr
        m = smooth(E, -E, d)
        rim = (smooth(0.0025, 0.0, np.abs(d + 0.0025)) * m)[..., None]
        col = hexc(c)
        fill = lin_grad(np.clip(col * 1.15, 0, 1), col * 0.8, ang + 90)
        sh = box_blur(np.roll(m, (int(0.02 * R), int(0.006 * R)), axis=(0, 1)), 36)[..., None] * (0.5 if dark else 0.15)
        img = img * (1 - sh)
        mm = m[..., None] * alpha
        img = img * (1 - mm) + fill * mm
        img = np.clip(img + rim * (0.5 if dark else 0.7), 0, 1)
    _ = rng
    return vignette(sheen(img, 0.03 if dark else 0.06), 0.25 if dark else 0.02)


def liquid(dark):
    """Metaball droplets with a specular highlight – Liquid Glass in wallpaper form."""
    if dark:
        img = lin_grad(hexc('#04040a'), hexc('#10102a'), 120)
        tint = hexc('#7f6bff')
        tint2 = hexc('#36d1ff')
    else:
        img = lin_grad(hexc('#f3f4fb'), hexc('#e2e6f7'), 120)
        tint = hexc('#9d8cff')
        tint2 = hexc('#6fd7ff')
    balls = [(0.30, 0.34, 0.11), (0.43, 0.48, 0.075), (0.61, 0.35, 0.065), (0.67, 0.63, 0.12), (0.27, 0.71, 0.07), (0.85, 0.23, 0.05), (0.50, 0.82, 0.045)]
    field = np.zeros((R, R), np.float32)
    for (cx, cy, r) in balls:
        field += (r ** 2) / ((X - cx) ** 2 + (Y - cy) ** 2 + 1e-4)
    m = smooth(1.42, 1.52, field)
    # shadow
    sh = box_blur(np.roll(m, (int(0.025 * R), int(0.01 * R)), axis=(0, 1)), 48)[..., None] * (0.6 if dark else 0.2)
    img = img * (1 - sh)
    # body: gradient between two tints, darker inside
    body = lin_grad(tint, tint2, 35)
    depth = np.clip(1 - box_blur(m, 40), 0, 1)[..., None]
    fill = body * (0.55 + 0.45 * depth) if dark else body * (0.78 + 0.22 * depth) + 0.12
    img = img * (1 - m[..., None]) + np.clip(fill, 0, 1) * m[..., None]
    # specular: top-left inner highlight
    inner = box_blur(m, 18)
    hl = np.clip(m - np.roll(inner, (int(0.012 * R), int(0.012 * R)), axis=(0, 1)), 0, 1)
    img = np.clip(img + hl[..., None] * (0.9 if dark else 0.8), 0, 1)
    rim = np.clip(m - box_blur(m, 4), 0, 1)
    img = np.clip(img + rim[..., None] * 0.6, 0, 1)
    return vignette(img, 0.28 if dark else 0.02)


DESIGNS = [
    ("bloom", "Bloom", bloom),
    ("tide", "Tide", tide),
    ("orbit", "Orbit", orbit),
    ("ribbon", "Aurora", ribbon),
    ("mesh", "Mesh", mesh),
    ("dune", "Dune", dune),
    ("prism", "Prism", prism),
    ("liquid", "Liquid", liquid),
]


def finish(img, size, seed):
    arr = (np.clip(img, 0, 1) * 255).astype(np.uint8)
    im = Image.fromarray(arr, 'RGB')
    if im.size != (size, size):
        im = im.resize((size, size), Image.LANCZOS)
    # fine grain keeps big gradients free of banding
    rng = np.random.default_rng(seed)
    a = np.asarray(im).astype(np.int16)
    a = a + rng.normal(0, 1.6, a.shape).astype(np.int16)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')


def main():
    out = sys.argv[1]
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 2880
    only = sys.argv[3].split(',') if len(sys.argv) > 3 else None
    os.makedirs(os.path.join(out, 'thumbs'), exist_ok=True)
    for i, (key, _name, fn) in enumerate(DESIGNS):
        if only and key not in only:
            continue
        for variant in ('dark', 'light'):
            im = finish(fn(variant == 'dark'), size, i * 2 + (variant == 'dark'))
            im.save(os.path.join(out, f'{key}_{variant}.webp'), 'WEBP', quality=90, method=6)
            im.resize((360, 360), Image.LANCZOS).save(os.path.join(out, 'thumbs', f'{key}_{variant}.webp'), 'WEBP', quality=85)
            print('wrote', key, variant, flush=True)


if __name__ == '__main__':
    main()
