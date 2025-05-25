package de.hinundhergestellt.jhuh.vendors.sumup;

import de.hinundhergestellt.jhuh.vendors.sumup.csv.CsvRecord;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record SumUpVariant(
        String variations,
        List<SumUpOption> options,
        boolean visible,
        BigDecimal price,
        int quantity,
        int lowStockThreshold,
        String sku,
        String barcode,
        UUID variantId
) {

    public static SumUpVariant loadVariant(CsvRecord record) {
        return new SumUpVariant(
                record.getVariations(),
                record.getOptions().stream()
                        .map(it -> new SumUpOption(it.name(), it.value()))
                        .toList(),
                record.isVariationVisible(),
                record.getPrice(),
                record.getQuantity(),
                record.getLowStockThreshold(),
                record.getSku(),
                record.getBarcode(),
                requireNonNull(record.getVariantId())
        );
    }
}
