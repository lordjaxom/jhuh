package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SyncProductsFixITCase {

    @Autowired
    private lateinit var syncProductRepository: SyncProductRepository


}