package de.hinundhergestellt.jhuh;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("NewClassNamingConvention")
class CsvFieldsGenerator {

    private static final String FIELDS = "Item name,Variations,Option set 1,Option 1,Option set 2,Option 2,Option set 3,Option 3,Option set 4,Option 4,Is variation visible? (Yes/No),Price,On sale in Online Store?,Regular price (before sale),Tax rate (%),Unit,Track inventory? (Yes/No),Quantity,Low stock threshold,SKU,Barcode,Description (Online Store and Invoices only),Category,Display colour in POS checkout,Image 1,Image 2,Image 3,Image 4,Image 5,Image 6,Image 7,Display item in Online Store? (Yes/No),SEO title (Online Store only),SEO description (Online Store only),Shipping weight [kg] (Online Store only),Item id (Do not change),Variant id (Do not change)";

    @Test
    void generate() {
        var output =Arrays.stream(FIELDS.split(","))
                .map(CsvFieldsGenerator::generateField)
                .collect(Collectors.joining(""));
        System.out.println(output);
    }

    private static String generateField(String column) {
        var fieldName = column;
        fieldName = fieldName.replaceAll("SEO", "seo");
        fieldName = Pattern.compile("\\b\\w")
                .matcher(fieldName)
                .replaceAll(r -> r.group().toUpperCase(Locale.ROOT));
        fieldName = Pattern.compile("^\\w")
                .matcher(fieldName)
                .replaceAll(r -> r.group().toLowerCase(Locale.ROOT));
        fieldName = fieldName.replaceFirst("[^\\w ].*$", "");
        fieldName = fieldName.replaceAll("\\s+", "");
        return "@CsvBindByName(column=\"" + column + "\")\n" +
                "private String " + fieldName + ";\n";
    }
}
