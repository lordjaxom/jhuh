package de.hinundhergestellt.jhuh;

import com.google.common.collect.ImmutableMap;
import de.hinundhergestellt.jhuh.ready2order.ApiClient;
import de.hinundhergestellt.jhuh.ready2order.ProductClient;
import de.hinundhergestellt.jhuh.ready2order.ProductGroup;
import de.hinundhergestellt.jhuh.ready2order.ProductGroupClient;
import de.hinundhergestellt.jhuh.ready2order.mapping.ProductMapper;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NewClassNamingConvention")
class SumUpImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SumUpImporter.class);

    private static final List<Category> CATEGORIES = List.of(
            new Category("Plotten", null),
            new Category("Vinylfolien", "Plotten"),
            new Category("Vinylfolien Standard", "Vinylfolien"),
            new Category("Vinylfolien Spezial", "Vinylfolien"),
            new Category("Flexfolien", "Plotten"),
            new Category("Flexfolien Standard", "Flexfolien"),
            new Category("Flexfolien Spezial", "Flexfolien"),
            new Category("Flockfolien", "Plotten"),
            new Category("Zubehör", "Plotten"),

            new Category("Mietfächer", null),
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
            new Category("Papier", "Bastelmaterial"),
            new Category("Diverses", "Bastelmaterial"),

            new Category("Anfertigungen", null),
            new Category("Holz", "Anfertigungen"),
            new Category("Dienstleistungen", "Anfertigungen"),
            new Category("Diverses", "Anfertigungen"),

            new Category("Verpackungen", null)
    );

    private static final Map<String, Category> CATEGORIES_BY_PATH = CATEGORIES.stream()
            .collect(Collectors.toMap(
                    it -> it.path(CATEGORIES),
                    identity()
            ));

    private static final Map<String, String> CATEGORIES_MAPPING = ImmutableMap.<String, String>builder()
            .put("Flexfolien", "Plotten/Flexfolien/Flexfolien Standard")
            .put("Flexfolien Glitter", "Plotten/Flexfolien/Flexfolien Spezial")
            .put("Flexfolien spezial", "Plotten/Flexfolien/Flexfolien Spezial")
            .put("Vinylfolien Uni", "Plotten/Vinylfolien/Vinylfolien Standard")
            .put("Vinylfolie Glasdekor", "Plotten/Vinylfolien/Vinylfolien Spezial")
            .put("Vinylfolien Bunt", "Plotten/Vinylfolien/Vinylfolien Spezial")
            .put("Vinylfolien Glitzer", "Plotten/Vinylfolien/Vinylfolien Spezial")
            .put("Flockfolien", "Plotten/Flockfolien")
            .put("Silhouette", "Plotten/Zubehör")

            .put("Ingrid Schiffmann", "Mietfächer/Ingrid Schiffmann")
            .put("Yvonne Schwarz", "Mietfächer/Yvonne Schwarz")
            .put("Kerzen - Michelle Fischer", "Mietfächer/Michelle Fischer")

            .put("MyBoshi", "Häkeln und Stricken/myboshi")
            .put("Gründl", "Häkeln und Stricken/Gründl")
            .put("Rico Design", "Häkeln und Stricken/Rico Design")

            .put("Rayher, Silikonformen", "Bastelmaterial/Rayher/Rayher Silikonformen")
            .put("Rayher", "Bastelmaterial/Rayher/Rayher Sonstiges")
            .put("HobbyFun", "Bastelmaterial/HobbyFun")
            .put("Creartec", "Bastelmaterial/Creartec")
            .put("Subli Sonstiges", "Bastelmaterial/Sublimation")
            .put("Subli Tassen, Gläser, Flaschen", "Bastelmaterial/Sublimation")
            .put("Papier", "Bastelmaterial/Papier")
            .put("Klötzchen", "Bastelmaterial/Diverses")
            .put("Diverses", "Bastelmaterial/Diverses")

            .put("Holz", "Anfertigungen/Holz")
            .put("Dienstleistungen", "Anfertigungen/Dienstleistungen")
            .put("3D-Druck", "Anfertigungen/Diverses")
            .put("Marktstand", "Anfertigungen/Diverses")
            .put("Moni", "Anfertigungen/Diverses")

            .put("Verpackungen", "Verpackungen")

            .build();

    private final HuhApplication application = new HuhApplication(new RestTemplateBuilder());
    private final ApiClient apiClient = application.ready2orderApiClient();

    @Test
    void prepareCategories() {
        var productGroupClient = new ProductGroupClient(apiClient);
        var productGroups = productGroupClient.findAllMappedByPath();

        for (var category : CATEGORIES) {
            var path = category.path(CATEGORIES);
            var productGroup = productGroups.get(path);
            if (productGroup == null) {
                var parent = Optional.ofNullable(category.parentPath(CATEGORIES))
                        .map(productGroups::get)
                        .map(ProductGroup::getId)
                        .orElse(null);
                productGroup = new ProductGroup(category.name(), "", "", true, parent, 0, 7);
                productGroupClient.save(productGroup);
                productGroups.put(path, productGroup);
            }
        }
    }

    @Test
    void importSumUp() throws IOException {
        var sumUpBook = loadSumUpBook();
        if (!verifySumUpBook(sumUpBook)) {
            return;
        }

        var productMapper = new ProductMapper();

        var productGroupClient = new ProductGroupClient(apiClient);
        var productClient = new ProductClient(apiClient);

        var productGroups = productGroupClient.findAllMappedByPath();

        long count = 0;
        for (var article : sumUpBook.articles()) {
            count += 1;

            LOGGER.info("[{}/{}] Creating product {}", count, sumUpBook.articles().size(), article.itemName());

            var categoryName = CATEGORIES_MAPPING.getOrDefault(article.category(), article.category());
            var productGroup = requireNonNull(productGroups.get(categoryName));

            if (article.variants().size() == 1) {
                var product = productMapper.mapArticleToProduct(article, productGroup);
                productClient.save(product);
                continue;
            }

            // Workaround: Since scanning barcodes of variations doesn't work, create a product group named after the article and create
            // separate products for each variant

            var articleGroup = productGroups.get(article.itemName());
            if (articleGroup == null) {
                articleGroup = productMapper.mapArticleToProductGroup(article, productGroup);
                productGroupClient.save(articleGroup);
                productGroups.put(article.itemName(), articleGroup);
            }

            productMapper
                    .mapVariantsToProducts(article, articleGroup)
                    .forEach(productClient::save);
        }
    }

    @Test
    void prepareCategoriesAndImportSumUp() throws IOException {
        prepareCategories();
        importSumUp();
    }

    @Test
    void countWolle() throws IOException {
        var sumUpBook = loadSumUpBook();
        var collected = sumUpBook.articles().stream()
                .filter(it -> it.category().equals("MyBoshi") || it.category().equals("Gründl"))
                .count();
        System.out.println(collected);
    }

    @Test
    void findProductTypeForVariation() {
        var apiClient = application.ready2orderApiClient();
        var productClient = new ProductClient(apiClient);

        var products = productClient.findAll();
        LOGGER.info("Typ ist {}", products.get(0).getTypeId());
    }

    @Test
    void removeItemNumberFromProduct() {
        var apiClient = application.ready2orderApiClient();
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
        var bookPath = Path.of("/home/lordjaxom/Downloads/2025-05-23_14-41-23_items-export_MDS2FTSP.csv");
        try (var reader = Files.newBufferedReader(bookPath)) {
            return SumUpArticleBook.loadBook(reader);
        }
    }

    private boolean verifySumUpBook(SumUpArticleBook book) {
        long errors = 0;
        errors += checkSumUpBookForDuplicates(book, "SKU", ArticleVariant::sku);
        errors += checkSumUpBookForDuplicates(book, "Barcode", ArticleVariant::barcode);
        // errors += checkSumUpBookForMissingBarcodes(book);
        errors += checkSumUpBookForMissingCategories(book);
        checkSumUpBookForNegativeInventory(book);

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
                .filter(it -> !CATEGORIES_BY_PATH.containsKey(it))
                .toList();
        missing.forEach(it -> LOGGER.error("Missing category {}", it));
        return missing.size();
    }

    private long checkSumUpBookForMissingBarcodes(SumUpArticleBook book) {
        return book.articles().stream()
                .mapToLong(it -> {
                    var missing = it.variants().stream()
                            .filter(variant -> !StringUtils.hasLength(variant.barcode()))
                            .toList();
                    missing.forEach(variant -> LOGGER.error("Missing barcode for {} {}", it.itemName(), variant.variations()));
                    return missing.size();
                })
                .sum();
    }

    private long checkSumUpBookForNegativeInventory(SumUpArticleBook book) {
        var negative = book.articles().stream()
                .flatMap(article -> article.variants().stream()
                        .map(variant -> new ArticleVariant(article, variant)))
                .filter(it -> it.variant.quantity() < 0)
                .toList();
        negative.forEach(it -> LOGGER.error("Negative quantity {} in {} {}", it.variant.quantity(), it.article.itemName(),
                it.variant.variations()));
        return negative.size();
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

    private record Category(String name, @Nullable String parent) {

        public String path(List<Category> categories) {
            return Stream.of(parentPath(categories), name)
                    .filter(Objects::nonNull)
                    .collect(joining("/"));
        }

        public @Nullable String parentPath(List<Category> categories) {
            return Optional.ofNullable(parent)
                    .flatMap(it -> categories.stream()
                            .filter(category -> category.name.equals(it))
                            .map(category -> category.path(categories))
                            .findFirst()
                    )
                    .orElse(null);
        }
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
                    .collect(joining(" "));
        }
    }
}
