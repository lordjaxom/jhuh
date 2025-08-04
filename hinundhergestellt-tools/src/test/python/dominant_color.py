import sys
import re
from PIL import Image
import numpy as np
import os

def main():
    if len(sys.argv) < 2 or len(sys.argv) > 3:
        print("Usage: dominant_color INPUT [BOX]")
        sys.exit(1)

    filename = sys.argv[1]
    if len(sys.argv) == 3:
        box = int(sys.argv[2])
    else:
        box = None

    # 1. Bild laden
    img = Image.open(filename).convert("RGB")
    w, h = img.size

    # 2. Ausschnitt aus der Bildmitte holen
    if box:
        cx, cy = w // 2, h // 2
        half = box // 2
        box = (cx - half, cy - half, cx + half, cy + half)
        swatch = img.crop(box)
    else:
        swatch = img

    # 3. Gemittelte Farbe bestimmen (vom Swatch)
    arr = np.array(swatch)
    mean_color = arr.mean(axis=(0,1)).astype(int)
    hex_code = '#{:02x}{:02x}{:02x}'.format(*mean_color)

    # 8. Ausgabe
    print(f"{hex_code}")

if __name__ == "__main__":
    main()
