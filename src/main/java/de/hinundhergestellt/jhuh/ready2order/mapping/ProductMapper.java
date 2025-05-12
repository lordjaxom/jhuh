package de.hinundhergestellt.jhuh.ready2order.mapping;

import de.hinundhergestellt.jhuh.ready2order.Product;
import de.hinundhergestellt.jhuh.ready2order.ProductGroup;
import de.hinundhergestellt.jhuh.sumup.SumUpArticle;
import de.hinundhergestellt.jhuh.sumup.SumUpVariant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;

@Component
public class ProductMapper {

    public Product mapArticleToProduct(SumUpArticle article, ProductGroup productGroup) {
        return new Product(
                article.itemName(),
                ofSingleVariant(article, SumUpVariant::sku).orElse(null),
                ofSingleVariant(article, SumUpVariant::barcode).orElse(null),
                article.description(),
                ofSingleVariant(article, SumUpVariant::price).orElseGet(() -> findLowestPrice(article)),
                true,
                article.taxRate(),
                ifSingleVariant(article, article.trackInventory()).orElse(false),
                ifSingleVariant(article, false).orElse(true),
                ofSingleVariant(article, SumUpVariant::quantity).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO),
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

    public Product mapVariantToProduct(SumUpArticle article, SumUpVariant variant, Product base) {
        return new Product(
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

    public Stream<Product> mapVariantsToProducts(SumUpArticle article, Product base) {
        if (article.variants().size() == 1) {
            return Stream.empty();
        }

        return article.variants().stream()
                .map(it -> mapVariantToProduct(article, it, base));
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
