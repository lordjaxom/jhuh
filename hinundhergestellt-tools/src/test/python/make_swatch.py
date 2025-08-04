import sys
import re
from PIL import Image
import numpy as np
import os

product_name = "poli-tape-tubitherm"

def main():
    if len(sys.argv) < 2:
        print("Bitte einen Dateinamen als Argument angeben!")
        sys.exit(1)
    filename = sys.argv[1]

    # 2. Artikelnummer & Farbe aus Dateinamen extrahieren (sheet-NUMMER-FARBE.jpg)
    m = re.match(r'.*sheet-PLT-(\d+)-([A-Za-z-]+)\.(jpg|png)$', os.path.basename(filename))
    if not m:
        print(f"Dateiname {os.path.basename(filename)} passt nicht zum erwarteten Muster (sheet-NUMMER-FARBE.jpg)!")
        sys.exit(1)
    nummer = m.group(1).lower()
    farbe = m.group(2)
    farbe_lower = farbe.lower()

    # 1. Bild laden
    img = Image.open(filename).convert("RGB")
    w, h = img.size

    # 3. 100x100-Ausschnitt aus der Bildmitte holen
    cx, cy = w // 2, h // 2
    half = 50
    box = (cx - half, cy - half, cx + half, cy + half)
    swatch = img.crop(box)

    # 4. Swatch speichern
    #swatch_name = f"{product_name}-{nummer}-{farbe_lower}-swatch.png"
    #swatch.save(swatch_name)

    # 5. Komplettes Bild speichern (neuer Name)
    img_name = f"{product_name}-{nummer}-{farbe_lower}.png"
    img.save(img_name)

if __name__ == "__main__":
    main()
