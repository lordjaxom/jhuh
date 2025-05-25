package de.hinundhergestellt.jhuh.vendors.ready2order.mapping;

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.sumup.SumUpArticle;
import de.hinundhergestellt.jhuh.vendors.sumup.SumUpVariant;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;

@Component
public class ProductMapper {

    public ArtooProductGroup mapArticleToProductGroup(SumUpArticle article, ArtooProductGroup productGroup) {
        Assert.isTrue(article.variants().size() > 1, "Article must have more than one variant.");

        return new ArtooProductGroup(
                article.itemName(),
                article.description(),
                "",
                true,
                productGroup.getId(),
                0,
                3
        );
    }

    public ArtooProduct mapArticleToProduct(SumUpArticle article, ArtooProductGroup productGroup) {
        Assert.isTrue(article.variants().size() == 1, "Article must have exactly one variant.");

        return new ArtooProduct(
                article.itemName(),
                ofSingleVariant(article, SumUpVariant::sku).orElse(null),
                ofSingleVariant(article, SumUpVariant::barcode).orElse(null),
                article.description(),
                ofSingleVariant(article, SumUpVariant::price).orElseGet(() -> findLowestPrice(article)),
                true,
                article.taxRate(),
                ifSingleVariant(article, article.trackInventory()).orElse(false),
                ifSingleVariant(article, false).orElse(true),
                ofSingleVariant(article, SumUpVariant::quantity).map(i -> Math.max(i, 0)).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO),
                ifSingleVariant(article, "piece").orElse(null),
                ofSingleVariant(article, SumUpVariant::lowStockThreshold).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO),
                BigDecimal.ZERO,
                0,
                true,
                true,
                null,
                null,
                productGroup.getId()
        );
    }

    public ArtooProduct mapVariantToProduct(SumUpArticle article, SumUpVariant variant, ArtooProduct base) {
        return new ArtooProduct(
                Optional.of(variant.variations()).filter(StringUtils::hasText).orElse("Standard"),
                variant.sku(),
                variant.barcode(),
                "",
                variant.price().subtract(findLowestPrice(article)),
                true,
                article.taxRate(),
                article.trackInventory(),
                false,
                BigDecimal.valueOf(variant.quantity()),
                "piece",
                BigDecimal.valueOf(variant.lowStockThreshold()),
                BigDecimal.ZERO,
                0,
                true,
                true,
                null,
                base.getId(),
                base.getProductGroupId()
        );
    }

    public ArtooProduct mapVariantToProduct(SumUpArticle article, SumUpVariant variant, ArtooProductGroup articleGroup) {
        return new ArtooProduct(
                Stream.of(article.itemName(), variant.variations()).filter(StringUtils::hasText).collect(Collectors.joining(" ")),
                variant.sku(),
                variant.barcode(),
                "",
                variant.price(),
                true,
                article.taxRate(),
                article.trackInventory(),
                false,
                BigDecimal.valueOf(Math.max(variant.quantity(), 0)),
                "piece",
                BigDecimal.valueOf(variant.lowStockThreshold()),
                BigDecimal.ZERO,
                0,
                true,
                true,
                "standard",
                null,
                articleGroup.getId()
        );
    }

    public Stream<ArtooProduct> mapVariantsToProducts(SumUpArticle article, ArtooProduct base) {
        if (article.variants().size() == 1) {
            return Stream.empty();
        }

        return article.variants().stream()
                .map(it -> mapVariantToProduct(article, it, base));
    }

    public Stream<ArtooProduct> mapVariantsToProducts(SumUpArticle article, ArtooProductGroup articleGroup) {
        Assert.isTrue(article.variants().size() > 1, "Article must have more than one variant.");

        return article.variants().stream().map(it -> mapVariantToProduct(article, it, articleGroup));
    }

    private static BigDecimal findLowestPrice(SumUpArticle article) {
        return article.variants().stream()
                .map(SumUpVariant::price)
                .min(naturalOrder())
                .orElseThrow();
    }

    private static <T> Optional<T> ofSingleVariant(SumUpArticle article, Function<SumUpVariant, T> getter) {
        return article.variants().size() == 1
                ? Optional.of(getter.apply(article.variants().getFirst()))
                : Optional.empty();
    }

    private static <T> Optional<T> ifSingleVariant(SumUpArticle article, T value) {
        return Optional.ofNullable(article.variants().size() == 1 ? value : null);
    }
}
