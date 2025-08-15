package de.hinundhergestellt.jhuh

import org.springframework.boot.autoconfigure.SpringBootApplication
import java.nio.file.Path
import kotlin.io.path.Path

val projectDirectory: Path = Path(".").toAbsolutePath().parent.parent
val workDirectory: Path = projectDirectory.resolve("work")
val homeDirectory: Path = Path(System.getProperty("user.home"))

@SpringBootApplication
class TestApplication