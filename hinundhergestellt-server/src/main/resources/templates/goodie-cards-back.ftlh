<!DOCTYPE html>
<html lang="de" >
<head>
    <meta charset="UTF-8">
    <title>Kärtchen 9×9 cm – DIN A4</title>
    <style>
        /* --- Seitengröße & Druck-Reset --- */
        @page { size: A4; margin: 0; }
        html, body {
            width: 210mm; height: 297mm; margin: 0; padding: 0;
        }
        @media print {
            body { background: transparent; }
        }
        @media screen {
            body { background: #e9e9e9; }
        }

        /* --- Konstanten: A4 (210×297), Nutzfläche (180×270) -> zentriert --- */
        :root{
            --page-w: 210mm;
            --page-h: 297mm;
            --sheet-w: 180mm; /* 2 × 90mm */
            --sheet-h: 270mm; /* 3 × 90mm */
            --card:     90mm;
            --mx: calc((var(--page-w) - var(--sheet-w)) / 2);  /* 15mm links/rechts */
            --my: calc((var(--page-h) - var(--sheet-h)) / 2);  /* 13.5mm oben/unten */

            --tick-l: 5mm;    /* Länge der Schnittmarken */
            --tick-t: 0.25mm; /* Strichstärke */
        }

        .page {
            position: relative;
            width: var(--page-w); height: var(--page-h);
            page-break-after: always;
        }
        .page:last-child { page-break-after: auto; }

        /* Screen-Vorschau */
        @media screen {
            .page {
                margin: 8mm auto;
                background: white;
                box-shadow: 0 0 6mm rgba(0,0,0,.12);
            }
        }

        /* --- Kartenraster: 2×3 ohne Abstände, exakt mittig --- */
        .sheet {
            position: absolute;
            left: 50%; top: 50%;
            width: var(--sheet-w); height: var(--sheet-h);
            transform: translate(-50%, -50%);
            display: grid;
            grid-template-columns: repeat(2, var(--card));
            grid-template-rows: repeat(3, var(--card));
            gap: 0; /* keine Abstände */
        }

        .card {
            width: var(--card); height: var(--card);
            box-sizing: border-box;
            /* Keine sichtbaren Ränder – Kanten sollen „sauber“ aneinanderliegen */
            display: flex; align-items: center; justify-content: center;
            font: 10pt/1.2 system-ui, sans-serif;
            overflow: hidden;
            flex-direction: column;
        }

        /* nur für Bildschirmhilfen: Kartenfläche als leichte Kontur */
        @media screen {
            .card { outline: 0.2mm dashed #ddd; }
        }

        /* --- Schneidehilfen: außerhalb der Karten, in den Außenrändern --- */
        .crop { position: absolute; inset: 0; pointer-events: none; }

        /* Vertikale Marken oben/unten an den 3 Spaltenlinien (links, Mitte, rechts) */
        .vmark { position: absolute; width: var(--tick-t); height: var(--tick-l); background: #000; }
        /* Obere Reihe der Marken (im oberen Rand, direkt über der Nutzfläche) */
        .vmark.top { top: calc(var(--my) - var(--tick-l)); }
        /* Untere Reihe (im unteren Rand, direkt unter der Nutzfläche) */
        .vmark.bottom { top: calc(var(--my) + var(--sheet-h)); }

        /* horizontale Marken links/rechts an den 4 Reihenlinien (oben, 1, 2, unten) */
        .hmark { position: absolute; height: var(--tick-t); width: var(--tick-l); background: #000; }
        /* Linke Spalte der Marken (im linken Rand, direkt neben der Nutzfläche) */
        .hmark.left { left: calc(var(--mx) - var(--tick-l)); }
        /* Rechte Spalte der Marken (im rechten Rand, direkt neben der Nutzfläche) */
        .hmark.right { left: calc(var(--mx) + var(--sheet-w)); }

        /* X-Positionen der vertikalen Marken */
        .x-left  { left:  calc(var(--mx)); }
        .x-mid   { left:  calc(var(--mx) + var(--card)); }
        .x-right { left:  calc(var(--mx) + var(--sheet-w)); }
        /* Y-Positionen der horizontalen Marken */
        .y-top    { top: calc(var(--my)); }
        .y-row1   { top: calc(var(--my) + var(--card)); }
        .y-row2   { top: calc(var(--my) + 2 * var(--card)); }
        .y-bottom { top: calc(var(--my) + var(--sheet-h)); }

        /* optisch mittig zentrierte Striche */
        .vmark { transform: translateX(-50%); }
        .hmark { transform: translateY(-50%); }

        .qr-section {
            width: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
        }

        .qr-text {
            margin-bottom: 4mm;
            font-size: 10pt;
            line-height: 1.35;
            padding: 0 3mm;
        }

        .qr-row {
            display: flex;
            justify-content: center;
            gap: 6mm;              /* Abstand zwischen QR-Codes */
        }

        .qr-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            width: 32mm;           /* quadratische Darstellung */
        }

        .qr-label {
            margin-bottom: 2mm;
            font-size: 9pt;
            font-weight: 600;
        }

        .qr-item img {
            width: 32mm;
            height: 32mm;
            object-fit: contain;
            display: block;
        }
    </style>
</head>
<body>

<#-- Eingabe erwartet: Liste "cards". Jede Karte befüllt den 90×90mm Slot. -->
<#list cards?chunk(6) as sheet>
    <div class="page">
        <div class="sheet">
            <#list sheet as card>
                <div class="card">
                    <#-- Beispielinhalt (nach Bedarf ersetzen) -->
                    <div class="qr-section">
                        <div class="qr-text">
                            Wir freuen uns sehr über deine Rückmeldung –
                            und wenn du möchtest, folge uns gern auf Instagram.
                        </div>

                        <div class="qr-row">
                            <div class="qr-item">
                                <div class="qr-label">Bewertung</div>
                                <img src="/qr-google-huh-logo.png" alt="">
                            </div>

                            <div class="qr-item">
                                <div class="qr-label">Instagram</div>
                                <img src="/qr-instagram-i-logo.png" alt="">
                            </div>
                        </div>
                    </div>

<#--                    <div style="max-width: 80%;">-->
<#--                        <div style="font-size:10pt; text-align:center; margin-top:2mm;">-->
<#--                            Wir freuen uns sehr, wenn du dir einen Moment Zeit nimmst, um deine Erfahrung zu teilen – jede Rückmeldung hilft uns weiter. Und wenn du Lust hast, begleite uns gern auf Instagram für Einblicke hinter die Kulissen.-->
<#--                        </div>-->
<#--                    </div>-->
                </div>
            </#list>
        </div>

        <!-- Schneidehilfen: nur in den Außenrändern, exakt an den Kartenkanten ausgerichtet -->
        <div class="crop">
            <!-- oben -->
            <div class="vmark top x-left"></div>
            <div class="vmark top x-mid"></div>
            <div class="vmark top x-right"></div>
            <!-- unten -->
            <div class="vmark bottom x-left"></div>
            <div class="vmark bottom x-mid"></div>
            <div class="vmark bottom x-right"></div>

            <!-- links -->
            <div class="hmark left y-top"></div>
            <div class="hmark left y-row1"></div>
            <div class="hmark left y-row2"></div>
            <div class="hmark left y-bottom"></div>
            <!-- rechts -->
            <div class="hmark right y-top"></div>
            <div class="hmark right y-row1"></div>
            <div class="hmark right y-row2"></div>
            <div class="hmark right y-bottom"></div>
        </div>
    </div>
</#list>

</body>
</html>
