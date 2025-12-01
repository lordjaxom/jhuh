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
            left: var(--mx); top: var(--my);
            /*width: var(--sheet-w); height: var(--sheet-h);*/
            /*transform: translate(-50%, -50%);*/
            display: grid;
            grid-template-columns: repeat(2, var(--card));
            grid-template-rows: repeat(3, var(--card));
            gap: 0; /* keine Abstände */
        }

        .card {
            width: var(--card);
            height: var(--card);
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: flex-start;  /* statt center */
            padding-top: 4mm;             /* optional, nach Geschmack */
            font: 10pt/1.2 system-ui, sans-serif;
            overflow: hidden;
        }
        /*.card {*/
        /*    width: var(--card); height: var(--card);*/
        /*    box-sizing: border-box;*/
        /*    !* Keine sichtbaren Ränder – Kanten sollen „sauber“ aneinanderliegen *!*/
        /*    display: flex; align-items: center; justify-content: center;*/
        /*    font: 10pt/1.2 system-ui, sans-serif;*/
        /*    overflow: hidden;*/
        /*    flex-direction: column;*/
        /*}*/

        /* nur für Bildschirmhilfen: Kartenfläche als leichte Kontur */
        @media screen {
            .card { outline: 0.2mm dashed #ddd; }
        }

        /* --- Schneidehilfen: außerhalb der Karten, in den Außenrändern --- */
        .crop { position: absolute; inset: 0; pointer-events: none; }

        /* Vertikale Marken oben/unten an den 3 Spaltenlinien (links, Mitte, rechts) */
        .vmark {
            position: absolute;
            width: 0;
            height: var(--tick-l);
            border-left: var(--tick-t) solid #000;
        }
        /* Obere Reihe der Marken (im oberen Rand, direkt über der Nutzfläche) */
        .vmark.top { top: calc(var(--my) - var(--tick-l)); }
        /* Untere Reihe (im unteren Rand, direkt unter der Nutzfläche) */
        .vmark.bottom { top: calc(var(--my) + var(--sheet-h)); }

        /* horizontale Marken links/rechts an den 4 Reihenlinien (oben, 1, 2, unten) */
        .hmark {
            position: absolute;
            height: 0;
            width: var(--tick-l);
            border-top: var(--tick-t) solid #000;
        }
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

        .footer-block {
            width: 100%;
            margin-top: 6mm;
            margin-left: 8mm;
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 4mm;
            font-size: 8pt;
            line-height: 1.3;
        }

        .footer-col {
            text-align: left;
            display: flex;
            flex-direction: column;
            /*gap: 0.8mm;*/
        }

        .footer-col .small {
            font-size: 6pt;
            opacity: 0.85;
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
                    <img src="/huh-logo.png" style="max-width:80%; max-height:80%; object-fit:contain; margin-top: 1em; margin-bottom: 1em;" alt="">
                    <div style="max-width: 80%;">
                        <div style="font-weight:700; font-size:12pt; text-align:center;">
                            Danke! Wirklich.
                        </div>
                        <div style="font-size:10pt; text-align:center; margin-top:2mm;">
                            Mit Deinem Kauf zauberst<br>du uns ein Lächeln ins Gesicht.
                        </div>
                        <div style="font-size:10pt; text-align:center; margin-top:2mm;">
                            Als Dankeschön erhälst Du <strong>10% Rabatt</strong> auf<br>Deinen nächsten Einkauf mit dem Code:
                        </div>
                        <div style="font-size:20pt; text-align:center; margin-top:2mm;">
                            ${card.code}
                        </div>
                        <div style="font-size:10pt; text-align:center; margin-top:2mm;">
                            Einlösbar auf shop.hinundhergestellt.de oder vor Ort im Laden.
                        </div>
                        <div style="font-size:7pt; text-align:center; margin-top:2mm;">
                            Gültig bis 31.03.2026. Nicht kombinierbar mit anderen Rabatten.
                        </div>
                    </div>
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

    <div class="page">
        <div class="sheet">
            <#list sheet as card>
                <div class="card">
                    <#-- Beispielinhalt (nach Bedarf ersetzen) -->
                    <div class="qr-section" style="margin-top: 1em;">
                        <div class="qr-text">
                            Wir freuen uns sehr über Deine Rückmeldung –
                            und wenn Du möchtest, folge uns gern auf Instagram.
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

                    <div class="footer-block">
                        <div class="footer-col" style="margin-left: 3em;">
                            <div>Hin- und Hergestellt</div>
                            <div>Hauptstr. 133</div>
                            <div>58332 Schwelm</div>
                            <div>02336 - 81 92 670</div>
                            <div>0160 - 92 72 30 87</div>
                        </div>

                        <div class="footer-col">
                            <div>DI–DO 12–18 Uhr</div>
                            <div>FR+SA 10–15 Uhr</div>
                            <div class="small">und nach Vereinbarung</div>
                            <div>www.hinundhergestellt.de</div>
                            <div>info@hinundhergestellt.de</div>
                        </div>
                    </div>
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
