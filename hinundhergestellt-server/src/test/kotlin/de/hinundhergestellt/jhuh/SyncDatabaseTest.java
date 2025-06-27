package de.hinundhergestellt.jhuh;

import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class SyncDatabaseTest {

    @Autowired
    private SyncCategoryRepository repository;

    @Test
    void testIt() {
        assertThat(repository.findAll()).isEmpty();
    }
}
