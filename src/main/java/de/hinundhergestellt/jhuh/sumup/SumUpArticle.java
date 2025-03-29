package de.hinundhergestellt.jhuh.sumup;

import de.hinundhergestellt.jhuh.sumup.csv.CsvRecord;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public record SumUpArticle(
        String itemName,
        BigDecimal taxRate,
        boolean trackInventory,
        String description,
        String category,
        String color,
        List<String> images,
        String unit,
        String seoTitle,
        String seoDescription,
        UUID itemId,
        List<SumUpVariant> variants
) {

    public static SumUpArticle loadArticle(CsvRecord record) {
        return new SumUpArticle(
                record.getItemName(),
                record.getTaxRate(),
                record.isTrackInventory(),
                record.getDescription(),
                record.getCategory(),
                record.getDisplayColourInPOSCheckout(),
                record.getImages(),
                record.getUnit(),
                record.getSeoTitle(),
                record.getSeoDescription(),
                requireNonNull(record.getItemId()),
                Stream.ofNullable(record.getVariantId())
                        .map(it -> SumUpVariant.loadVariant(record))
                        .collect(Collectors.toList())
        );
    }

    public boolean loadVariant(CsvRecord record) {
        if (record.getItemId() != null) {
            return false;
        }
        variants.add(SumUpVariant.loadVariant(record));
        return true;
    }
}
