package de.hinundhergestellt.jhuh.vendors.sumup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;

class SumUpArticleBookTest {

    @Test
    void loads() throws IOException {
        try (var reader = new InputStreamReader(SumUpArticleBook.class
                .getResourceAsStream("/2025-03-28_14-35-02_items-export_MDS2FTSP.csv"))) {
            var sumUpArticleBook = SumUpArticleBook.loadBook(reader);
            sumUpArticleBook.articles().forEach(article -> {
               System.out.println(article);
            });
        }
    }

}