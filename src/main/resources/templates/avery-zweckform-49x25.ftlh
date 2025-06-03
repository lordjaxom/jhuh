<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Etiketten DIN A4</title>
    <style>
        @page {
            size: A4;
            margin: 0;
        }

        @media print {
            body {
                margin: 0;
            }
        }

        body {
            width: 210mm;
            height: 297mm;
            margin: 0;
            padding: 0;
            display: block;
            background: lightgray;
        }

        .sheet {
            display: grid;
            grid-template-columns: repeat(4, 48.5mm);
            grid-template-rows: repeat(10, 25.4mm);
            width: 194mm;
            height: 254mm;
            margin: auto;
        }

        @media print {
            .sheet {
                padding-top: 21.5mm;
                box-sizing: border-box;
                page-break-after: always;
            }

            .sheet:last-child {
                page-break-after: auto;
            }
        }

        @media screen {
            .sheet {
                margin: 5mm;
                padding: 21.5mm 8mm;
                box-shadow: 0 0 5mm rgba(0, 0, 0, 0.1);
                background: white;
            }
        }

        .label {
            width: 48.5mm;
            height: 25.4mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            padding: 1mm;
        }

        @media screen {
            .label {
                border: 1px dashed lightgray;
            }
        }

        .label-text {
            flex: 1;
            display: flex;
            flex-direction: column;
            justify-content: flex-start;
            gap: 0;
        }

        .line {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            font-size: 3mm;
            text-align: center;
        }

        .barcode {
            display: flex;
            justify-content: center;
            align-items: flex-end;
        }

        .barcode img {
            max-height: 100%;
            max-width: 100%;
            object-fit: contain;
        }
    </style>
</head>
<body>
<#list labels?chunk(40) as sheet>
    <div class="sheet">
        <#list sheet as label>
            <div id="label-1" class="label">
                <div class="label-text">
                    <div class="line">${label.name}</div>
<#--                    <div class="line">${label.text}</div>-->
<#--                    <div class="line">${label.description}</div>-->
                </div>
                <div class="barcode">
                    <img src="/api/labels/barcode/${label.barcode}" alt="${label.barcode}"/>
                </div>
            </div>
        </#list>
    </div>
</#list>
</body>
</html>
