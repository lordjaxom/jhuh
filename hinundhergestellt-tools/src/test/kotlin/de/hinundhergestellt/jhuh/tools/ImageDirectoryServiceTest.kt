package de.hinundhergestellt.jhuh.tools

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.io.path.Path
import kotlin.test.Test

@SpringBootTest
class ImageDirectoryServiceTest {

    @Autowired
    private lateinit var service : ImageDirectoryService

    @Test
    fun test() {
        println(service.listDirectoryEntries(Path("POLI-FLEX® IMAGE")))
        Thread.sleep(1_000_000)
    }
}