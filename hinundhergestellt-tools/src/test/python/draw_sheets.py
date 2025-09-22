from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import math
import sys

if len(sys.argv) != 3:
    print("Use: draw_sheets input output")
    exit(1)

output_size = 1500
shadow_width = 10
max_angle = 15 # maximaler Rotationswinkel in Grad
ar = 2 / 3     # Seitenverhältnis Blatt

# 1. Dominante Farbe bestimmen
img = Image.open(sys.argv[1]).convert("RGB")
arr = np.array(img)
r, g, b = [int(np.median(arr[:,:,i])) for i in range(3)]
dominant_color = (r, g, b)
hex_code = '#{:02x}{:02x}{:02x}'.format(*dominant_color)
print(f"{hex_code}")

# 2. Blattgröße so bestimmen, dass es (inkl. Schatten) bei maximaler Drehung noch passt
def get_max_sheet_size(canvas, shadow, angle_deg, aspect_ratio):
    theta = math.radians(abs(angle_deg))
    # Für expand=True brauchen wir die längste Diagonale, die das rotiert Layer bekommen kann
    # Berechnung: die Diagonale nach Rotation plus 2x Schatten
    # w = Blattbreite, h = Blatthöhe
    # bounding box nach Rotation: W = w*|cos|+h*|sin|, H = h*|cos|+w*|sin|
    # Aber für expand=True ist das eigentliche Bounding: sqrt(w^2+h^2)
    for tw in range(canvas, 10, -2):
        th = int(tw / aspect_ratio)
        diag = math.hypot(tw, th)
        # Nach Rotation expandiert das Bild auf diag x diag, plus 2*shadow
        if diag + 2*shadow <= canvas:
            return tw, th
    return 100, int(100 / aspect_ratio)

sheet_w, sheet_h = get_max_sheet_size(output_size, shadow_width, max_angle, ar)

def draw_sheet(size, color, angle):
    w, h = size
    # Papier mit vertikalem Verlauf
    grad_arr = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        ratio = y / (h-1)
        blend = [min(255, int(c + (255-c)*0.13*(1-ratio))) for c in color]
        grad_arr[y, :, :3] = blend
        grad_arr[y, :, 3] = 255
    grad = Image.fromarray(grad_arr, "RGBA")
    # Glanz
    gloss = Image.new("L", (w, h), 0)
    for y in range(int(h*0.28)):
        alpha = int(38 * (1 - y/(h*0.28))**2)
        gloss.paste(alpha, (0, y, w, y+1))
    gloss_img = Image.new("RGBA", (w, h), (255,255,255,0))
    gloss_img.putalpha(gloss)
    grad = Image.alpha_composite(grad, gloss_img)
    # Papier mittig in größeres Layer kopieren (für Schatten)
    layer_dim = int(math.ceil(math.hypot(w, h))) + 2*shadow_width
    paper = Image.new("RGBA", (layer_dim, layer_dim), (0,0,0,0))
    px = (layer_dim - w) // 2
    py = (layer_dim - h) // 2
    paper.paste(grad, (px, py), grad)
    # Schatten erzeugen (weiche Outline)
    mask = paper.split()[3]
    shadow = Image.new("RGBA", (layer_dim, layer_dim), (0,0,0,0))
    shadow_mask = mask.filter(ImageFilter.GaussianBlur(radius=shadow_width))
    shadow.paste((0,0,0,60), mask=shadow_mask)
    # Beide synchron rotieren (expand=True!)
    paper_rot = paper.rotate(angle, resample=Image.BICUBIC, expand=True)
    shadow_rot = shadow.rotate(angle, resample=Image.BICUBIC, expand=True)
    return shadow_rot, paper_rot

# 3. Blätter-Positionen (in Relation zur Bildmitte, feintunen möglich!)
positions_angles = [
    (-11, (-32, -38)),
    (7,   (7, 22)),
    (-4,  (47, -12)),
]
center = (output_size//2, output_size//2)
background = Image.new("RGBA", (output_size, output_size), (255,255,255,255))

for angle, (dx, dy) in positions_angles:
    shadow, sheet = draw_sheet((sheet_w, sheet_h), dominant_color, angle)
    # Position so berechnen, dass das rotiert-blatt Layer IMMER ins Bild passt:
    W, H = sheet.size
    pos = (center[0] - W//2 + dx, center[1] - H//2 + dy)
    # Im Notfall kann man absichern, dass pos>=0 bleibt (hier aber nicht nötig)
    background.paste(shadow, pos, shadow)
    background.paste(sheet, pos, sheet)

# 4. Auf Weiß ausgeben
canvas = Image.new("RGB", (output_size, output_size), (255,255,255))
canvas.paste(background, (0, 0), background)
canvas.save(sys.argv[2], "PNG")
