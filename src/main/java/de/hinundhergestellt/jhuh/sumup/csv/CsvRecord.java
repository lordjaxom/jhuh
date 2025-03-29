package de.hinundhergestellt.jhuh.sumup.csv;

import com.opencsv.bean.CsvBindByName;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

public class CsvRecord {

    @CsvBindByName(column = "Item name")
    private @Nullable String itemName;

    @CsvBindByName(column = "Variations")
    private @Nullable String variations;

    @CsvBindByName(column = "Option set 1")
    private @Nullable String optionSet1;

    @CsvBindByName(column = "Option 1")
    private @Nullable String option1;

    @CsvBindByName(column = "Option set 2")
    private @Nullable String optionSet2;

    @CsvBindByName(column = "Option 2")
    private @Nullable String option2;

    @CsvBindByName(column = "Option set 3")
    private @Nullable String optionSet3;

    @CsvBindByName(column = "Option 3")
    private @Nullable String option3;

    @CsvBindByName(column = "Option set 4")
    private @Nullable String optionSet4;

    @CsvBindByName(column = "Option 4")
    private @Nullable String option4;

    @CsvBindByName(column = "Is variation visible? (Yes/No)")
    private boolean variationVisible;

    @CsvBindByName(column = "Price")
    private @Nullable BigDecimal price;

    @CsvBindByName(column = "On sale in Online Store?")
    private @Nullable String onSaleInOnlineStore;

    @CsvBindByName(column = "Regular price (before sale)")
    private @Nullable String regularPrice;

    @CsvBindByName(column = "Tax rate (%)")
    private @Nullable BigDecimal taxRate;

    @CsvBindByName(column = "Unit")
    private @Nullable String unit;

    @CsvBindByName(column = "Track inventory? (Yes/No)")
    private @Nullable String trackInventory;

    @CsvBindByName(column = "Quantity")
    private int quantity;

    @CsvBindByName(column = "Low stock threshold")
    private int lowStockThreshold;

    @CsvBindByName(column = "SKU")
    private @Nullable String sku;

    @CsvBindByName(column = "Barcode")
    private @Nullable String barcode;

    @CsvBindByName(column = "Description (Online Store and Invoices only)")
    private @Nullable String description;

    @CsvBindByName(column = "Category")
    private @Nullable String category;

    @CsvBindByName(column = "Display colour in POS checkout")
    private @Nullable String displayColourInPOSCheckout;

    @CsvBindByName(column = "Image 1")
    private @Nullable String image1;

    @CsvBindByName(column = "Image 2")
    private @Nullable String image2;

    @CsvBindByName(column = "Image 3")
    private @Nullable String image3;

    @CsvBindByName(column = "Image 4")
    private @Nullable String image4;

    @CsvBindByName(column = "Image 5")
    private @Nullable String image5;

    @CsvBindByName(column = "Image 6")
    private @Nullable String image6;

    @CsvBindByName(column = "Image 7")
    private @Nullable String image7;

    @CsvBindByName(column = "Display item in Online Store? (Yes/No)")
    private @Nullable String displayItemInOnlineStore;

    @CsvBindByName(column = "SEO title (Online Store only)")
    private @Nullable String seoTitle;

    @CsvBindByName(column = "SEO description (Online Store only)")
    private @Nullable String seoDescription;

    @CsvBindByName(column = "Shipping weight [kg] (Online Store only)")
    private @Nullable String shippingWeight;

    @CsvBindByName(column = "Item id (Do not change)")
    private @Nullable UUID itemId;

    @CsvBindByName(column = "Variant id (Do not change)")
    private @Nullable UUID variantId;

    public String getItemName() {
        return requireNonNull(itemName);
    }

    public String getVariations() {
        return requireNonNull(variations);
    }

    public List<Option> getOptions() {
        return Stream.of(new NullableOption(optionSet1, option1), new NullableOption(optionSet2, option2),
                        new NullableOption(optionSet3, option3), new NullableOption(optionSet4, option4))
                .filter(it -> StringUtils.hasLength(it.value()))
                .map(NullableOption::toOption)
                .toList();
    }

    public boolean isVariationVisible() {
        return variationVisible;
    }

    public BigDecimal getPrice() {
        return requireNonNull(price);
    }

    public BigDecimal getTaxRate() {
        return requireNonNull(taxRate);
    }

    public String getUnit() {
        return requireNonNull(unit);
    }

    public boolean isTrackInventory() {
        return toBoolean(trackInventory);
    }

    public int getQuantity() {
        return quantity;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public String getSku() {
        return requireNonNull(sku);
    }

    public String getBarcode() {
        return requireNonNull(barcode);
    }

    public String getDescription() {
        return requireNonNull(description);
    }

    public String getCategory() {
        return requireNonNull(category);
    }

    public String getDisplayColourInPOSCheckout() {
        return requireNonNull(displayColourInPOSCheckout);
    }

    public List<String> getImages() {
        return Stream.of(image1, image2, image3, image4, image5, image6, image7)
                .filter(Objects::nonNull)
                .filter(not(String::isEmpty))
                .toList();
    }

    public String getSeoTitle() {
        return requireNonNull(seoTitle);
    }

    public String getSeoDescription() {
        return requireNonNull(seoDescription);
    }

    public @Nullable UUID getItemId() {
        return itemId;
    }

    public @Nullable UUID getVariantId() {
        return variantId;
    }

    private static boolean toBoolean(@Nullable String value) {
        return "Yes".equals(value);
    }

    private record NullableOption(@Nullable String name, @Nullable String value) {

        Option toOption() {
            return new Option(requireNonNull(name), requireNonNull(value));
        }
    }

    public record Option(String name, String value) {
    }
}
