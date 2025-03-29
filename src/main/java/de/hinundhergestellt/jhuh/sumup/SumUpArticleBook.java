package de.hinundhergestellt.jhuh.sumup;

import com.opencsv.bean.CsvToBeanBuilder;
import de.hinundhergestellt.jhuh.sumup.csv.CsvRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public record SumUpArticleBook(List<SumUpArticle> articles) {

    public static SumUpArticleBook loadBook(Reader reader) {
        try (reader) {
            var csvToBean = new CsvToBeanBuilder<CsvRecord>(reader)
                    .withType(CsvRecord.class)
                    .build();

            var articles = new ArrayList<SumUpArticle>();
            SumUpArticle current = null;
            for (var record : csvToBean) {
                if (current == null || !current.loadVariant(record)) {
                    current = SumUpArticle.loadArticle(record);
                    articles.add(current);
                }
            }
            return new SumUpArticleBook(unmodifiableList(articles));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
