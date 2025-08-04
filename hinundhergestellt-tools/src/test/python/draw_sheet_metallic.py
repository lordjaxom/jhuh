from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import math
import sys

if len(sys.argv) != 3:
    print("Use: draw_sheets input output")
    exit(1)

output_size = 1500
shadow_width = 10
max_angle = 15
ar = 2 / 3

img = Image.open(sys.argv[1]).convert("RGB")
arr = np.array(img)
r, g, b = [int(np.median(arr[:,:,i])) for i in range(3)]
dominant_color = (r, g, b)

def get_max_sheet_size(canvas, shadow, angle_deg, aspect_ratio):
    theta = math.radians(abs(angle_deg))
    for tw in range(canvas, 10, -2):
        th = int(tw / aspect_ratio)
        diag = math.hypot(tw, th)
        if diag + 2*shadow <= canvas:
            return tw, th
    return 100, int(100 / aspect_ratio)
sheet_w, sheet_h = get_max_sheet_size(output_size, shadow_width, max_angle, ar)

def draw_sheet(size, color, angle):
    w, h = size
    # Gradient für das Papier
    grad_arr = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        ratio = y / (h-1)
        blend = [min(255, int(c + (255-c)*0.13*(1-ratio))) for c in color]
        grad_arr[y, :, :3] = blend
        grad_arr[y, :, 3] = 255
    grad = Image.fromarray(grad_arr, "RGBA")
    # Metallic-radialer Glanz
    def radial_gloss_layer(w, h, max_alpha=75, rel_radius=0.4):
        cx = int(w * 0.6)
        cy = int(h * 0.4)
        Y, X = np.ogrid[:h, :w]
        dist = np.sqrt((X-cx)**2 + (Y-cy)**2)
        max_r = rel_radius * np.hypot(w, h)
        gloss_alpha = np.clip(max_alpha * (1 - (dist / max_r)), 0, max_alpha).astype(np.uint8)
        gloss_img = np.zeros((h, w, 4), dtype=np.uint8)
        gloss_img[:,:,3] = gloss_alpha
        gloss_img[:,:,:3] = 255
        return Image.fromarray(gloss_img, 'RGBA')
    radial_gloss = radial_gloss_layer(w, h, max_alpha=100, rel_radius=0.60)
    grad = Image.alpha_composite(grad, radial_gloss)
    # Papier mittig ins große Layer (für Schatten)
    layer_dim = int(math.ceil(math.hypot(w, h))) + 2*shadow_width
    paper = Image.new("RGBA", (layer_dim, layer_dim), (0,0,0,0))
    px = (layer_dim - w) // 2
    py = (layer_dim - h) // 2
    paper.paste(grad, (px, py), grad)
    # Schatten erzeugen (wie gehabt)
    mask = paper.split()[3]
    shadow = Image.new("RGBA", (layer_dim, layer_dim), (0,0,0,0))
    shadow_mask = mask.filter(ImageFilter.GaussianBlur(radius=shadow_width))
    shadow.paste((0,0,0,60), mask=shadow_mask)
    # Rotieren mit expand=True
    paper_rot = paper.rotate(angle, resample=Image.BICUBIC, expand=True)
    shadow_rot = shadow.rotate(angle, resample=Image.BICUBIC, expand=True)
    return shadow_rot, paper_rot

positions_angles = [
    (-11, (-32, -38)),
    (7,   (7, 22)),
    (-4,  (47, -12)),
]
center = (output_size//2, output_size//2)
background = Image.new("RGBA", (output_size, output_size), (255,255,255,255))

for angle, (dx, dy) in positions_angles:
    shadow, sheet = draw_sheet((sheet_w, sheet_h), dominant_color, angle)
    W, H = sheet.size
    pos = (center[0] - W//2 + dx, center[1] - H//2 + dy)
    background.paste(shadow, pos, shadow)
    background.paste(sheet, pos, sheet)

canvas = Image.new("RGB", (output_size, output_size), (255,255,255))
canvas.paste(background, (0, 0), background)
canvas.save(sys.argv[2], "PNG")
