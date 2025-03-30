package de.hinundhergestellt.jhuh;

import de.hinundhergestellt.jhuh.ready2order.Product;
import de.hinundhergestellt.jhuh.ready2order.ProductClient;
import de.hinundhergestellt.jhuh.ready2order.ProductGroup;
import de.hinundhergestellt.jhuh.ready2order.ProductGroupClient;
import de.hinundhergestellt.jhuh.ready2order.ProductVariation;
import de.hinundhergestellt.jhuh.sumup.SumUpArticle;
import de.hinundhergestellt.jhuh.sumup.SumUpArticleBook;
import de.hinundhergestellt.jhuh.sumup.SumUpVariant;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.hinundhergestellt.jhuh.ready2order.ApiUtils.withDefault;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

@SuppressWarnings("NewClassNamingConvention")
class SumUpImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SumUpImporter.class);

    private static final List<Category> CATEGORIES = List.of(
            new Category("Plotterfolien", null),
            new Category("Vinylfolien", "Plotterfolien"),
            new Category("Vinylfolien Standard", "Vinylfolien"),
            new Category("Vinylfolien Bunt", "Vinylfolien"),
            new Category("Vinylfolien Glitzer", "Vinylfolien"),
            new Category("Vinylfolien Glasdekor", "Vinylfolien"),
            new Category("Flexfolien", "Plotterfolien"),
            new Category("Flexfolien Standard", "Flexfolien"),
            new Category("Flexfolien Glitter", "Flexfolien"),
            new Category("Flexfolien Spezial", "Flexfolien"),
            new Category("Flockfolien", "Plotterfolien")
    );

    private static final Map<String, Category> CATEGORIES_BY_NAME =
            CATEGORIES.stream().collect(Collectors.toMap(Category::name, identity()));

    private static final Map<String, String> CATEGORIES_MAPPING = Map.of(
            "Flexfolien", "Flexfolien Standard",
            "Vinylfolien Uni", "Vinylfolien Standard",
            "Vinylfolie Glasdekor", "Vinylfolien Glasdekor",
            "Flexfolien spezial", "Flexfolien Spezial"
    );

    private final HuhApplication application = new HuhApplication(new RestTemplateBuilder());

    @Test
    void prepareCategories() {
        var apiClient = application.apiClient();
        var productGroupClient = new ProductGroupClient(apiClient);
        var productGroups = productGroupClient.findAll().stream()
                .collect(Collectors.toMap(
                        ProductGroup::getName,
                        identity()
                ));

        for (var category : CATEGORIES) {
            var productGroup = productGroups.get(category.name());
            if (productGroup == null) {
                var parent = Optional.ofNullable(category.parent()).map(productGroups::get).map(ProductGroup::getId).orElse(null);
                productGroup = new ProductGroup(category.name(), "", "", true, parent, 0, 7);
                productGroupClient.save(productGroup);
                productGroups.put(category.name(), productGroup);
            }
        }
    }

    @Test
    void importSumUp() throws IOException {
        var sumUpBook = loadTheBook();

        var apiClient = application.apiClient();
        var productGroupClient = new ProductGroupClient(apiClient);
        var productGroups = productGroupClient.findAll().stream()
                .collect(Collectors.toMap(
                        ProductGroup::getName,
                        identity()
                ));

        var productClient = new ProductClient(apiClient);
        var products = productClient.findAll().stream()
                .collect(Collectors.toMap(
                        Product::getName,
                        identity()
                ));

//        var product = products.values().stream().findFirst().orElseThrow();
//        product.setItemNumber(null);
//        productClient.save(product);
        // TODO: How!?

        for (var article : sumUpBook.articles()) {
            var categoryName = CATEGORIES_MAPPING.getOrDefault(article.category(), article.category());
            if (!CATEGORIES_BY_NAME.containsKey(categoryName)) {
                continue; // not imported yet
            }

            LOGGER.info("Creating product {}", article.itemName());

            var lowestPrice = article.variants().stream().map(SumUpVariant::price).min(naturalOrder()).orElseThrow();
            var productGroup = requireNonNull(productGroups.get(categoryName));

            var product = new ProductVariation(
                    article.itemName(),
                    ifSingleVariant(article, SumUpVariant::sku),
                    ifSingleVariant(article, SumUpVariant::barcode),
                    article.description(),
                    withDefault(ifSingleVariant(article, SumUpVariant::price), lowestPrice),
                    true,
                    article.taxRate(),
                    article.variants().size() == 1,
                    article.variants().size() > 1,
                    withDefault(ifSingleVariant(article, it -> BigDecimal.valueOf(it.quantity())), BigDecimal.ZERO),
                    ifSingleVariant(article, it -> "piece"),
                    withDefault(ifSingleVariant(article, it -> BigDecimal.valueOf(it.lowStockThreshold())), BigDecimal.ZERO),
                    BigDecimal.ZERO,
                    0,
                    true,
                    true,
                    null,
                    null,
                    productGroup.getId()
            );
            productClient.save(product);

            if (article.variants().size() == 1) {
                continue;
            }

            for (var variant : article.variants()) {
                var productVariation = new ProductVariation(
                        variant.variations(),
                        variant.sku(),
                        variant.barcode(),
                        "",
                        variant.price().subtract(lowestPrice),
                        true,
                        article.taxRate(),
                        true,
                        false,
                        BigDecimal.valueOf(variant.quantity()),
                        "piece",
                        BigDecimal.valueOf(variant.lowStockThreshold()),
                        BigDecimal.ZERO,
                        0,
                        true,
                        true,
                        null,
                        product.getId(),
                        productGroup.getId()
                );
                productClient.save(productVariation);
            }
        }
    }

    private static SumUpArticleBook loadTheBook() throws IOException {
        var bookPath = Path.of("/home/lordjaxom/Downloads/2025-03-28_14-35-02_items-export_MDS2FTSP.csv");
        try (var reader = Files.newBufferedReader(bookPath)) {
            return SumUpArticleBook.loadBook(reader);
        }
    }

    private static <T> @Nullable T ifSingleVariant(SumUpArticle article, Function<SumUpVariant, T> getter) {
        return article.variants().size() == 1 ? getter.apply(article.variants().getFirst()) : null;
    }

    private record Category(String name, @Nullable String parent) {
    }
}
