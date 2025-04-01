package de.hinundhergestellt.jhuh;

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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.hinundhergestellt.jhuh.ready2order.ApiUtils.withDefault;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

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
            new Category("Flockfolien", "Plotterfolien"),

            new Category("Mietfächer", null),
            new Category("Simone Friedhoff", "Mietfächer"),
            new Category("Ingrid Schiffmann", "Mietfächer"),
            new Category("Yvonne Schwarz", "Mietfächer"),
            new Category("Michelle Fischer", "Mietfächer"),

            new Category("Häkeln und Stricken", null),
            new Category("myboshi", "Häkeln und Stricken"),
            new Category("Gründl", "Häkeln und Stricken"),
            new Category("Rico Design", "Häkeln und Stricken"),

            new Category("Bastelmaterial", null),
            new Category("Rayher", "Bastelmaterial"),
            new Category("Rayher Silikonformen", "Rayher"),
            new Category("Rayher Sonstiges", "Rayher"),
            new Category("HobbyFun", "Bastelmaterial"),
            new Category("Creartec", "Bastelmaterial"),
            new Category("Sublimation", "Bastelmaterial"),
            new Category("Sublimation Tassen Gläser Flaschen", "Sublimation"),
            new Category("Sublimation Sonstiges", "Sublimation"),

            // TODO
            new Category("3D-Druck", null),
            new Category("Klötzchen", null),
            new Category("Dienstleistungen", null),
            new Category("Marktstand", null),
            new Category("Diverses", null),
            new Category("Papier", null),
            new Category("Holz", null),
            new Category("Moni", null),
            new Category("Verpackungen", null),
            new Category("Silhouette", null)
    );

    private static final Map<String, Category> CATEGORIES_BY_NAME =
            CATEGORIES.stream().collect(Collectors.toMap(Category::name, identity()));

    private static final Map<String, String> CATEGORIES_MAPPING = Map.of(
            "Flexfolien", "Flexfolien Standard",
            "Vinylfolien Uni", "Vinylfolien Standard",
            "Vinylfolie Glasdekor", "Vinylfolien Glasdekor",
            "Flexfolien spezial", "Flexfolien Spezial",
            "MyBoshi", "myboshi",
            "Kerzen - Michelle Fischer", "Michelle Fischer",
            "Rayher", "Rayher Sonstiges",
            "Rayher, Silikonformen", "Rayher Silikonformen",
            "Subli Tassen, Gläser, Flaschen", "Sublimation Tassen Gläser Flaschen",
            "Subli Sonstiges", "Sublimation Sonstiges"
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
        var sumUpBook = loadSumUpBook();
        if (!verifySumUpBook(sumUpBook)) {
            return;
        }

        var apiClient = application.apiClient();
        var productGroupClient = new ProductGroupClient(apiClient);
        var productClient = new ProductClient(apiClient);

        var productGroups = productGroupClient.findAll().stream()
                .collect(Collectors.toMap(
                        ProductGroup::getName,
                        identity()
                ));

        long count = 0;
        for (var article : sumUpBook.articles()) {
            count += 1;

            LOGGER.info("[{}/{}] Creating product {}", count, sumUpBook.articles().size(), article.itemName());

            var categoryName = CATEGORIES_MAPPING.getOrDefault(article.category(), article.category());
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
                    article.trackInventory() && article.variants().size() == 1,
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
                        withDefault(variant.variations(), "Standard"),
                        variant.sku(),
                        variant.barcode(),
                        "",
                        variant.price().subtract(lowestPrice),
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
                        product.getId(),
                        productGroup.getId()
                );
                productClient.save(productVariation);
            }
        }
    }

    @Test
    void removeItemNumberFromProduct() {
        var apiClient = application.apiClient();
        var productClient = new ProductClient(apiClient);

        var products = productClient.findAll();

        var product = products.stream()
                .filter(it -> it.getItemNumber().isPresent())
                .findFirst()
                .orElseThrow();
        product.setItemNumber(null);
        productClient.save(product);

        assertThat(productClient.findById(product.getId()).getItemNumber()).isNotPresent();
    }

    private static SumUpArticleBook loadSumUpBook() throws IOException {
        var bookPath = Path.of("/home/lordjaxom/Downloads/2025-03-30_11-12-33_items-export_MDS2FTSP.csv");
        try (var reader = Files.newBufferedReader(bookPath)) {
            return SumUpArticleBook.loadBook(reader);
        }
    }

    private boolean verifySumUpBook(SumUpArticleBook book) {
        long errors = 0;
        errors += checkSumUpBookForDuplicates(book, "SKU", ArticleVariant::sku);
        errors += checkSumUpBookForDuplicates(book, "Barcode", ArticleVariant::barcode);
        errors += checkSumUpBookForMissingCategories(book);

        if (errors == 0) {
            checkSumUpBookForTinyCategories(book);
        }

        return errors == 0;
    }

    private long checkSumUpBookForDuplicates(SumUpArticleBook book, String name, Function<ArticleVariant, String> getter) {
        var duplicates = book.articles().stream()
                .flatMap(article -> article.variants().stream()
                        .map(variant -> new ArticleVariant(article, variant)))
                .filter(it -> StringUtils.hasLength(getter.apply(it)))
                .collect(Collectors.groupingBy(getter))
                .entrySet().stream()
                .filter(it -> it.getValue().size() > 1)
                .toList();
        duplicates.forEach(it ->
                LOGGER.error("Duplicate {} {} found in {}", name, it.getKey(), it.getValue()));
        return duplicates.size();
    }

    private long checkSumUpBookForMissingCategories(SumUpArticleBook book) {
        var missing = book.articles().stream()
                .map(SumUpArticle::category)
                .map(it -> CATEGORIES_MAPPING.getOrDefault(it, it))
                .distinct()
                .filter(it -> !CATEGORIES_BY_NAME.containsKey(it))
                .toList();
        missing.forEach(it -> LOGGER.error("Missing category {}", it));
        return missing.size();
    }

    private void checkSumUpBookForTinyCategories(SumUpArticleBook book) {
        var articlesByCategory = book.articles().stream()
                .collect(Collectors.groupingBy(it -> CATEGORIES_MAPPING.getOrDefault(it.category(), it.category())))
                .entrySet().stream()
                .filter(it -> it.getValue().size() <= 5)
                .toList();
        articlesByCategory.forEach(it ->
                LOGGER.warn("Category {} only has {} articles, consider combining with another category", it.getKey(),
                        it.getValue().size()));
    }

    private static <T> @Nullable T ifSingleVariant(SumUpArticle article, Function<SumUpVariant, T> getter) {
        return article.variants().size() == 1 ? getter.apply(article.variants().getFirst()) : null;
    }

    private record Category(String name, @Nullable String parent) {
    }

    private record ArticleVariant(SumUpArticle article, SumUpVariant variant) {

        String sku() {
            return variant.sku();
        }

        String barcode() {
            return variant.barcode();
        }

        @Override
        public String toString() {
            return Stream.of(article.itemName(), variant.variations())
                    .filter(StringUtils::hasLength)
                    .collect(Collectors.joining(" "));
        }
    }
}
