#!/usr/bin/env python3
"""
ModernManipulator - Asset & Logo Suite Generator
Generates 100% original, authorial Minecraft pixel art assets:
- logo.png: 512x512 crisp static logo (transparent / clean)
- logo.gif: 512x512 32-frame smooth animated loop (pulsing matter beam, materializing voxel ghost block, quantum particles)
- logo.svg: 1000x1000 crisp pixel vector SVG
- logo_plate.png: 512x512 high-tech dark slate plate with cyan neon border
- icon.png: 512x512 official mod icon deployed to root and mod resources
- banner.png: 1200x380 GitHub README header banner (holographic CAD void grid, 3D MC title, isometric centerpiece, badges)

Theme:
Matter manipulation & structure automation for GregTech CEu Modern and AE2.
Matter Manipulator MK3 held in isometric projection, firing its high-energy cyan matter laser
at a materializing holographic quantum voxel block / machine atop a High Power Casing pedestal.
"""

import os
import math
import glob
import shutil
import zipfile
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRATCH = os.path.join(ROOT, "build/asset_scratch")
ARTIFACT_DIR = "/home/raishxn/.gemini/antigravity/brain/d7cb4446-0616-4498-9182-5cd5ffd2a861"
os.makedirs(SCRATCH, exist_ok=True)
os.makedirs(ARTIFACT_DIR, exist_ok=True)

# 1. Locate and extract required textures from gradle cache
MC_JAR = "/home/raishxn/.gradle/caches/neoformruntime/artifacts/minecraft_1.20.1_client.jar"
if not os.path.exists(MC_JAR):
    # fallback search
    candidates = glob.glob("/home/raishxn/.gradle/caches/**/minecraft_1.20.1_client.jar", recursive=True)
    if candidates:
        MC_JAR = candidates[0]

# Search for GTCEu 1.20.1 jar in gradle caches
GT_JAR = None
gt_candidates = glob.glob("/home/raishxn/.gradle/caches/**/gtceu-1.20.1-*.jar", recursive=True)
for c in gt_candidates:
    if "slim" in c or "sources" not in c:
        GT_JAR = c
        break
if not GT_JAR and gt_candidates:
    GT_JAR = gt_candidates[0]

# Extract MC font
with zipfile.ZipFile(MC_JAR, 'r') as z:
    for tex in ["assets/minecraft/textures/font/ascii.png"]:
        fname = os.path.basename(tex)
        out_f = os.path.join(SCRATCH, fname)
        if not os.path.exists(out_f) and tex in z.namelist():
            with open(out_f, 'wb') as f:
                f.write(z.read(tex))

# Extract GT casings
if GT_JAR and os.path.exists(GT_JAR):
    with zipfile.ZipFile(GT_JAR, 'r') as z:
        for tex in [
            "assets/gtceu/textures/block/casings/hpca/high_power_casing.png",
            "assets/gtceu/textures/block/casings/fusion/fusion_coil.png",
            "assets/gtceu/textures/block/casings/fusion/fusion_casing_mk2.png"
        ]:
            fname = os.path.basename(tex)
            out_f = os.path.join(SCRATCH, fname)
            if not os.path.exists(out_f) and tex in z.namelist():
                with open(out_f, 'wb') as f:
                    f.write(z.read(tex))

# Load base textures
font_sheet = Image.open(os.path.join(SCRATCH, "ascii.png")).convert('RGBA')
hpca_tex = Image.open(os.path.join(SCRATCH, "high_power_casing.png")).convert('RGBA')
coil_tex = Image.open(os.path.join(SCRATCH, "fusion_coil.png")).convert('RGBA')

# Matter Manipulator MK3 sprite sheet (16x192, 12 frames)
MM_MK3_PATH = os.path.join(ROOT, "src/main/resources/assets/modern_manipulator/textures/item/matter_manipulator_mk3.png")
mm_sheet = Image.open(MM_MK3_PATH).convert('RGBA')


def make_iso_block(top_img, left_img, right_img, height_px=16, top_light=1.0, left_light=0.80, right_light=0.62):
    """Renders a standard 2:1 dimetric isometric Minecraft block from textures."""
    W = 32
    H = 16 + height_px
    arr = np.zeros((H, W, 4), dtype=np.uint8)

    top_pixels = np.array(top_img.convert('RGBA'))
    left_pixels = np.array(left_img.convert('RGBA'))
    right_pixels = np.array(right_img.convert('RGBA'))

    # Top rhombus (32x16)
    for y in range(16):
        if y < 8:
            x_min = 15 - (y * 2 + 1)
            x_max = 16 + (y * 2 + 1)
        else:
            dy = 15 - y
            x_min = 15 - (dy * 2 + 1)
            x_max = 16 + (dy * 2 + 1)
        x_min = max(0, x_min)
        x_max = min(32, x_max)
        for x in range(x_min, x_max):
            nx = (x - 15.5) / 16.0
            ny = (y - 7.5) / 8.0
            u = int(np.clip(((nx + ny) / 2.0 + 0.5) * 16.0, 0, 15))
            v = int(np.clip(((-nx + ny) / 2.0 + 0.5) * 16.0, 0, 15))
            c = top_pixels[v, u].astype(float)
            c[:3] *= top_light
            arr[y, x] = c.astype(np.uint8)

    # Left face
    for x in range(16):
        top_y = 8 + x // 2
        for dy in range(height_px):
            y = top_y + dy
            u = x
            v = int(dy * 16.0 / height_px)
            v = np.clip(v, 0, 15)
            c = left_pixels[v, u].astype(float)
            c[:3] *= left_light
            arr[y, x] = c.astype(np.uint8)

    # Right face
    for x in range(16, 32):
        top_y = 15 - (x - 16) // 2
        for dy in range(height_px):
            y = top_y + dy
            u = x - 16
            v = int(dy * 16.0 / height_px)
            v = np.clip(v, 0, 15)
            c = right_pixels[v, u].astype(float)
            c[:3] *= right_light
            arr[y, x] = c.astype(np.uint8)

    return Image.fromarray(arr)


# Pre-render isometric blocks
block_hpca_base = make_iso_block(hpca_tex, hpca_tex, hpca_tex, height_px=8)
block_coil = make_iso_block(coil_tex, coil_tex, coil_tex, height_px=8)
iso_hpca_full = make_iso_block(hpca_tex, hpca_tex, hpca_tex, height_px=16)


def make_materializing_block(frame=0, total_frames=32):
    """
    Renders an isometric 32x32 voxel ghost block being materialized into a GregTech High Power Casing.
    Combines authentic ghost block wireframes, node vertices, grid lines and materializing block state.
    """
    W, H = 32, 32
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))

    pulse = (math.sin(2.0 * math.pi * frame / total_frames) + 1.0) / 2.0
    alpha_machine = 0.35 + 0.15 * pulse

    # 1. Base machine texture at partial opacity (materializing into reality)
    mach_arr = np.array(iso_hpca_full).astype(float)
    mach_arr[:, :, 0] = mach_arr[:, :, 0] * 0.3 + 20 * 0.7
    mach_arr[:, :, 1] = mach_arr[:, :, 1] * 0.6 + 180 * 0.4
    mach_arr[:, :, 2] = mach_arr[:, :, 2] * 0.7 + 255 * 0.3
    mach_arr[:, :, 3] = mach_arr[:, :, 3] * alpha_machine
    im.alpha_composite(Image.fromarray(mach_arr.astype(np.uint8)))

    # 2. Holographic wireframe overlay (ModernManipulator ghost block shader style)
    draw = ImageDraw.Draw(im)
    height_px = 16

    wire_alpha = int(210 + 45 * pulse)
    wire_color = (56, 238, 251, wire_alpha)
    face_color = (20, 140, 220, int(40 + 25 * pulse))

    # Top rhombus (32x16)
    top_poly = [(16, 0), (31, 7), (16, 15), (0, 7)]
    draw.polygon(top_poly, fill=face_color, outline=wire_color)

    # Left face
    left_poly = [(0, 7), (16, 15), (16, 15 + height_px), (0, 7 + height_px)]
    draw.polygon(left_poly, fill=(face_color[0], face_color[1], face_color[2], int(face_color[3] * 0.8)), outline=wire_color)

    # Right face
    right_poly = [(16, 15), (31, 7), (31, 7 + height_px), (16, 15 + height_px)]
    draw.polygon(right_poly, fill=(face_color[0], face_color[1], face_color[2], int(face_color[3] * 0.6)), outline=wire_color)

    # Internal blueprint grid lines (subdivisions)
    grid_wire = (wire_color[0], wire_color[1], wire_color[2], int(wire_alpha * 0.45))
    draw.line([(8, 3), (23, 11)], fill=grid_wire, width=1)
    draw.line([(23, 3), (8, 11)], fill=grid_wire, width=1)
    draw.line([(8, 11), (8, 11 + height_px)], fill=grid_wire, width=1)
    draw.line([(0, 7 + height_px // 2), (16, 15 + height_px // 2)], fill=grid_wire, width=1)
    draw.line([(24, 11), (24, 11 + height_px)], fill=grid_wire, width=1)
    draw.line([(16, 15 + height_px // 2), (31, 7 + height_px // 2)], fill=grid_wire, width=1)

    # Bright corner vertices (nodes)
    nodes = [(16, 0), (31, 7), (16, 15), (0, 7), (0, 7 + height_px), (16, 15 + height_px), (31, 7 + height_px)]
    for nx, ny in nodes:
        im.putpixel((nx, ny), (255, 255, 255, 255))
        if nx > 0: im.putpixel((nx - 1, ny), (188, 247, 255, 220))
        if nx < W - 1: im.putpixel((nx + 1, ny), (188, 247, 255, 220))
        if ny > 0: im.putpixel((nx, ny - 1), (188, 247, 255, 220))
        if ny < H - 1: im.putpixel((nx, ny + 1), (188, 247, 255, 220))

    return im


def render_manipulator_scene(frame=0, total_frames=32, background_style="transparent"):
    """
    Renders 128x128 native pixel art:
    - background_style:
        'transparent' (default, pure clean isolated Minecraft 3D voxel scene)
        'rounded' (high-tech dark slate titanium plate with subtle 1-2px cyan neon border)
    - 3x3 High Power Casing pedestal with active fusion coil at center
    - Floating holographic voxel ghost block materializing into existence
    - CAD bounding brackets and measurement crosshairs
    - Handheld Matter Manipulator MK3 held dynamically in the foreground
    - Concentrated cyan matter laser beam with traveling pulse rings
    - Drifting quantum data fragments and glowing energy sparks
    """
    W, H = 128, 128
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    if background_style == "rounded":
        draw.rounded_rectangle([4, 4, 123, 123], radius=16, fill=(16, 20, 28, 255), outline=(32, 60, 92, 255), width=2)
        draw.rounded_rectangle([6, 6, 121, 121], radius=14, outline=(44, 219, 247, 85), width=1)

    # Base platform: 3x3 isometric grid centered at CX=64, CY=48
    CX = 64
    CY = 48

    for sum_xz in range(5):
        for gx in range(3):
            gz = sum_xz - gx
            if 0 <= gz < 3:
                bx = CX - 16 + (gx - gz) * 16
                by_base = CY + (gx + gz) * 8
                if gx == 1 and gz == 1:
                    blk = block_coil
                else:
                    blk = block_hpca_base
                canvas.alpha_composite(blk, (bx, by_base))

    # Center floating ghost block with smooth vertical bobbing
    t_bob = math.sin(2.0 * math.pi * frame / total_frames)
    bob_px = int(round(2.0 * t_bob))

    ghost = make_materializing_block(frame, total_frames)
    cube_x = CX - 16
    cube_y = CY - 20 + bob_px

    # Floating CAD bracket corners around ghost cube
    pulse = (math.sin(2.0 * math.pi * frame / total_frames) + 1.0) / 2.0
    bracket_col = (188, 247, 255, int(180 + 75 * pulse))
    blen = 3
    # Top-left bracket
    draw.line([cube_x - 3, cube_y + 4, cube_x - 3 + blen, cube_y + 4], fill=bracket_col, width=1)
    draw.line([cube_x - 3, cube_y + 4, cube_x - 3, cube_y + 4 + blen], fill=bracket_col, width=1)
    # Top-right bracket
    draw.line([cube_x + 34, cube_y + 4, cube_x + 34 - blen, cube_y + 4], fill=bracket_col, width=1)
    draw.line([cube_x + 34, cube_y + 4, cube_x + 34, cube_y + 4 + blen], fill=bracket_col, width=1)
    # Bottom bracket
    draw.line([cube_x + 16 - blen, cube_y + 35, cube_x + 16 + blen, cube_y + 35], fill=bracket_col, width=1)

    canvas.alpha_composite(ghost, (cube_x, cube_y))

    # Matter Manipulator MK3 held in the foreground
    mm_frame_idx = int((frame / float(total_frames)) * 12) % 12
    mm_frame = mm_sheet.crop((0, mm_frame_idx * 16, 16, (mm_frame_idx + 1) * 16))
    mm_sprite = mm_frame.resize((36, 36), Image.Resampling.NEAREST)

    # Position on right side, aimed up-left into the cube
    mm_x = CX + 12
    mm_y = CY - 4 - bob_px // 2
    canvas.alpha_composite(mm_sprite, (mm_x, mm_y))

    # Emitter position on the 36x36 sprite:
    emit_x = mm_x + 11
    emit_y = mm_y + 7
    target_x = cube_x + 18
    target_y = cube_y + 16

    # Laser beam from manipulator to ghost block
    beam_pulse = int(190 + 65 * math.sin(4.0 * math.pi * frame / total_frames))
    draw.line([emit_x, emit_y, target_x, target_y], fill=(44, 219, 247, beam_pulse), width=2)
    draw.line([emit_x, emit_y, target_x, target_y], fill=(255, 255, 255, 240), width=1)

    # Beam pulse ring traveling along the beam
    ring_phase = ((frame / float(total_frames)) * 2.0) % 1.0
    rx = int(emit_x + (target_x - emit_x) * ring_phase)
    ry = int(emit_y + (target_y - emit_y) * ring_phase)
    draw.ellipse([rx - 2, ry - 2, rx + 2, ry + 2], outline=(188, 247, 255, 220), width=1)

    # Quantum telemetry data particles / sparks floating up from the ghost block
    particles = [
        (cube_x + 14, cube_y - 4, (188, 247, 255)),
        (cube_x + 24, cube_y + 2, (56, 238, 251)),
        (cube_x + 4, cube_y + 8, (125, 211, 252)),
        (cube_x + 20, cube_y + 28, (255, 255, 255)),
        (emit_x - 3, emit_y - 4, (56, 238, 251)),
    ]
    for idx, (px, py, col) in enumerate(particles):
        phase = (frame + idx * 6) % total_frames
        progress = phase / float(total_frames)
        alpha = int(240 * math.sin(math.pi * progress))
        drift_y = int(round(progress * 14))
        drift_x = int(round(math.sin(progress * 2 * math.pi) * 2.0))
        pt_x = px + drift_x
        pt_y = py - drift_y
        if 0 <= pt_x < W and 0 <= pt_y < H:
            canvas.putpixel((pt_x, pt_y), (col[0], col[1], col[2], alpha))

    return canvas


def get_char_glyph(ch):
    """Extracts an 8x8 character glyph from vanilla ascii.png."""
    c = ord(ch)
    if c >= 256: return None, 6
    row = c // 16; col = c % 16
    glyph = font_sheet.crop((col * 8, row * 8, (col + 1) * 8, (row + 1) * 8))
    arr = np.array(glyph)
    if ch == ' ': return glyph, 4
    non_empty = np.where(arr[:, :, 3] > 0)
    if len(non_empty[1]) == 0: return glyph, 4
    w = np.max(non_empty[1]) + 2
    return glyph, w


def render_mc_text(text, color=(255, 255, 255), shadow_color=(40, 40, 40), scale=1):
    """Renders text with authentic Minecraft font glyphs, kerning and drop shadows."""
    total_w = 0
    glyphs = []
    for ch in text:
        g, w = get_char_glyph(ch)
        glyphs.append((g, w, ch))
        total_w += w

    img = Image.new('RGBA', (total_w + 3, 11), (0, 0, 0, 0))
    if shadow_color:
        cur_x = 1
        for g, w, ch in glyphs:
            if ch != ' ':
                arr = np.array(g).copy()
                mask = arr[:, :, 3] > 0
                arr[mask, 0] = shadow_color[0]
                arr[mask, 1] = shadow_color[1]
                arr[mask, 2] = shadow_color[2]
                img.alpha_composite(Image.fromarray(arr), (cur_x, 1))
            cur_x += w

    cur_x = 0
    for g, w, ch in glyphs:
        if ch != ' ':
            arr = np.array(g).copy()
            mask = arr[:, :, 3] > 0
            arr[mask, 0] = color[0]
            arr[mask, 1] = color[1]
            arr[mask, 2] = color[2]
            img.alpha_composite(Image.fromarray(arr), (cur_x, 0))
        cur_x += w

    if scale > 1:
        img = img.resize((img.width * scale, img.height * scale), Image.Resampling.NEAREST)
    return img


def build_banner():
    """Generates 1200x380 GitHub README header banner."""
    BW, BH = 1200, 380
    banner = Image.new('RGBA', (BW, BH), (15, 18, 26, 255))
    draw = ImageDraw.Draw(banner)

    # 1. Subtle Dark Void & Tech Gradient (Deep Slate Navy to GregTech Charcoal)
    for y in range(BH):
        t = y / float(BH)
        r = int(14 + 6 * (1.0 - t))
        g = int(18 + 8 * (1.0 - t))
        b = int(28 + 14 * (1.0 - t))
        draw.line([(0, y), (BW, y)], fill=(r, g, b, 255))

    # 2. Subtle Blueprint / Holographic Grid Lines
    grid_col_blue = (44, 219, 247, 18)
    grid_col_dark = (56, 189, 248, 12)
    for gy in range(0, BH, 24):
        draw.line([(0, gy), (BW, gy)], fill=grid_col_dark, width=1)
    for gx in range(0, BW, 24):
        draw.line([(gx, 0), (gx, BH)], fill=grid_col_dark, width=1)

    for gx in range(0, BW, 120):
        draw.line([(gx, 0), (gx, BH)], fill=grid_col_blue, width=1)
    for gy in range(0, BH, 120):
        draw.line([(0, gy), (BW, gy)], fill=grid_col_blue, width=1)

    # 3. Outer border frame with high-tech double lining
    draw.rectangle([0, 0, BW - 1, BH - 1], outline=(44, 219, 247, 180), width=2)
    draw.rectangle([2, 2, BW - 3, BH - 3], outline=(56, 189, 248, 80), width=1)

    # High-tech corner accents
    for cx, cy in [(0, 0), (BW - 1, 0), (0, BH - 1), (BW - 1, BH - 1)]:
        sx = 1 if cx == 0 else -1
        sy = 1 if cy == 0 else -1
        draw.line([cx, cy, cx + sx * 16, cy], fill=(255, 255, 255, 200), width=2)
        draw.line([cx, cy, cx, cy + sy * 16], fill=(255, 255, 255, 200), width=2)

    # 4. Render Isometric Centerpiece on the Left (320x320 centered vertically)
    plat_raw = render_manipulator_scene(0, 32, background_style="transparent")
    plat_scaled = plat_raw.resize((320, 320), Image.Resampling.NEAREST)
    banner.alpha_composite(plat_scaled, (40, 30))

    # 5. Right Side Typography:
    TITLE_X = 395
    TITLE_Y = 50

    title_img = render_mc_text("MODERN MANIPULATOR", color=(255, 255, 255), shadow_color=(12, 20, 30), scale=6)
    t_arr = np.array(title_img)
    for ty in range(t_arr.shape[0]):
        ratio = ty / float(t_arr.shape[0])
        for tx in range(t_arr.shape[1]):
            if t_arr[ty, tx, 0] == 255 and t_arr[ty, tx, 1] == 255 and t_arr[ty, tx, 2] == 255:
                t_arr[ty, tx, 0] = int(255 - 190 * ratio)
                t_arr[ty, tx, 1] = int(255 - 40 * ratio)
                t_arr[ty, tx, 2] = int(255 - 10 * ratio)
    title_tinted = Image.fromarray(t_arr)
    banner.alpha_composite(title_tinted, (TITLE_X, TITLE_Y))

    # Subtitle
    sub_img = render_mc_text("Structure Manipulation & Blueprint Automation", color=(226, 232, 240), shadow_color=(15, 22, 35), scale=3)
    banner.alpha_composite(sub_img, (TITLE_X + 2, TITLE_Y + 76))

    # Description
    desc_img = render_mc_text("Copy, move, wire and fabricate complex multiblocks with AE2 & GTCEu Modern.", color=(147, 197, 253), shadow_color=(12, 18, 30), scale=2)
    banner.alpha_composite(desc_img, (TITLE_X + 2, TITLE_Y + 118))

    # Badges arranged in 2 organized rows
    badges_row1 = [
        ("FORGE 1.20.1", (56, 189, 248)),
        ("GREGTECH CEu 7.5.3+", (245, 158, 11)),
        ("AE2 INTEGRATION", (168, 85, 247)),
    ]
    badges_row2 = [
        ("GTNH PORT", (244, 114, 182)),
        ("TIERS MK0 - MK3", (74, 222, 128)),
        ("QUANTUM UPLINK", (45, 212, 191)),
    ]

    # Draw Row 1
    bx = TITLE_X + 2
    by = TITLE_Y + 160
    for b_text, b_col in badges_row1:
        b_img = render_mc_text(b_text, color=b_col, shadow_color=(10, 15, 25), scale=2)
        pw = b_img.width + 16
        ph = b_img.height + 10
        draw.rectangle([bx, by, bx + pw, by + ph], fill=(20, 26, 38, 230), outline=b_col, width=1)
        banner.alpha_composite(b_img, (bx + 8, by + 5))
        bx += pw + 12

    # Draw Row 2
    bx = TITLE_X + 2
    by = TITLE_Y + 208
    for b_text, b_col in badges_row2:
        b_img = render_mc_text(b_text, color=b_col, shadow_color=(10, 15, 25), scale=2)
        pw = b_img.width + 16
        ph = b_img.height + 10
        draw.rectangle([bx, by, bx + pw, by + ph], fill=(20, 26, 38, 230), outline=b_col, width=1)
        banner.alpha_composite(b_img, (bx + 8, by + 5))
        bx += pw + 12

    return banner


def export_svg(im_128, out_svg_path):
    """Exports crisp vector SVG from 128x128 pixel art."""
    w, h = im_128.size
    rects = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = im_128.getpixel((x, y))
            if a > 0:
                hex_col = f"#{r:02x}{g:02x}{b:02x}"
                rects.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="{hex_col}" />')
    svg_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="1000" height="1000" shape-rendering="crispEdges">
  <g>
    {''.join(rects)}
  </g>
</svg>"""
    with open(out_svg_path, 'w', encoding='utf-8') as f:
        f.write(svg_content)


def main():
    print("Building ModernManipulator Asset Suite...")

    # 1. Generate Static Logo (Clean transparent default)
    f0_trans = render_manipulator_scene(0, 32, background_style="transparent")
    f0_trans_512 = f0_trans.resize((512, 512), Image.Resampling.NEAREST)

    logo_png_path = os.path.join(ROOT, "logo.png")
    f0_trans_512.save(logo_png_path, format="PNG")
    print(f"Generated static logo: {logo_png_path} (512x512 transparent)")

    # 2. Save alternative plate version
    f0_plate = render_manipulator_scene(0, 32, background_style="rounded")
    f0_plate_512 = f0_plate.resize((512, 512), Image.Resampling.NEAREST)
    logo_plate_path = os.path.join(ROOT, "logo_plate.png")
    f0_plate_512.save(logo_plate_path, format="PNG")
    print(f"Generated alternative plate logo: {logo_plate_path} (512x512)")

    # 3. Deploy in-game Forge logo and icons
    mc_logo_path = os.path.join(ROOT, "src/main/resources/logo.png")
    mc_icon_path = os.path.join(ROOT, "src/main/resources/icon.png")
    root_icon_path = os.path.join(ROOT, "icon.png")
    os.makedirs(os.path.dirname(mc_logo_path), exist_ok=True)
    shutil.copyfile(logo_png_path, mc_logo_path)
    shutil.copyfile(logo_png_path, mc_icon_path)
    shutil.copyfile(logo_png_path, root_icon_path)
    print(f"Deployed mod icons: {mc_icon_path}, {mc_logo_path}, {root_icon_path}")

    # 4. Generate Vector SVG
    logo_svg_path = os.path.join(ROOT, "logo.svg")
    export_svg(f0_trans, logo_svg_path)
    print(f"Generated vector SVG: {logo_svg_path} (1000x1000 crispEdges)")

    # 5. Generate Animated GIF (32 frames loop on clean transparent background)
    TOTAL_FRAMES = 32
    frames_512 = []
    print(f"Rendering {TOTAL_FRAMES} frames for animated GIF...")
    for f in range(TOTAL_FRAMES):
        frame_128 = render_manipulator_scene(f, TOTAL_FRAMES, background_style="transparent")
        frame_512 = frame_128.resize((512, 512), Image.Resampling.NEAREST)
        frames_512.append(frame_512)

    logo_gif_path = os.path.join(ROOT, "logo.gif")
    frames_512[0].save(
        logo_gif_path,
        save_all=True,
        append_images=frames_512[1:],
        duration=50,  # 20 FPS smooth
        loop=0,
        disposal=2
    )
    print(f"Generated animated logo: {logo_gif_path} ({os.path.getsize(logo_gif_path)} bytes)")

    # 6. Generate GitHub Banner
    banner_img = build_banner()
    banner_path = os.path.join(ROOT, "banner.png")
    banner_img.save(banner_path, format="PNG")
    print(f"Generated GitHub header banner: {banner_path} (1200x380)")

    # 7. Mirror generated assets to artifact showcase directory
    for fname in ["logo.png", "logo.gif", "logo.svg", "logo_plate.png", "icon.png", "banner.png"]:
        src_f = os.path.join(ROOT, fname)
        dst_f = os.path.join(ARTIFACT_DIR, fname)
        if os.path.exists(src_f):
            shutil.copyfile(src_f, dst_f)
    print(f"Copied all assets to artifact directory: {ARTIFACT_DIR}")

    print("\nAll ModernManipulator assets generated successfully!")


if __name__ == "__main__":
    main()
