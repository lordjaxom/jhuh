<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Etiketten</title>
    <link rel="stylesheet" href="/fonts/saira-condensed-v11-latin-regular.css">
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
            font-family: 'Saira Condensed', sans-serif;
        }

        .sheet {
            display: grid;
            grid-template-columns: repeat(5, 38mm);
            grid-template-rows: repeat(13, 21.2mm);
            width: 190mm;
            height: 275.6mm;
            margin: auto;
        }

        @media print {
            .sheet {
                padding-top: 10mm;
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
                padding: 11.35mm 10mm;
                box-shadow: 0 0 5mm rgba(0, 0, 0, 0.1);
                background: white;
            }
        }

        .label {
            width: 38mm;
            height: 21.2mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            padding-top: 1mm;
        }

        @media screen {
            .label {
                border: 1px dashed gray;
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
            text-align: center;
            padding: 0 1mm 0 1mm;
        }

        <#include "fragments/${type}.css">
    </style>
</head>
<body>
<#list labels?chunk(65) as sheet>
    <div class="sheet">
        <#list sheet as label>
            <div class="label">
                <#include "fragments/${type}.ftlh">
            </div>
        </#list>
    </div>
</#list>
</body>
</html>
