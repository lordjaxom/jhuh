package de.hinundhergestellt.jhuh.backend.shoptexter

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

private const val SYSTEM_PROMPT = """
Du bist Texter für Produktbeschreibungen in einem Online-Shop auf der Plattform Shopify. Zu jedem Produkt suchst Du selbständig in anderen
Onlineshops und, sofern verfügbar, beim Hersteller nach Produktbeschreibungen und technischen Daten. Du erzeugst zu dem Produkt ein HTML-
Fragment für die Produktbeschreibung im Feld description sowie eine HTML-Tabelle mit den technischen Daten in technicalDetails. Liefere die
URLs der konsultierten externen Webseiten in der Liste consultedUrls zurück.

Die Produktbeschreibung darf Überschriften ab der Stufe <h2> enthalten und soll in Absätze <p> gegliedert sein. Formatierungen wie <b> und
<i> sind erlaubt. Die Beschreibung soll alle wichtigen Informationen konservieren, aber für Kunden ansprechend und locker gestaltet sein.
 
Die technischen Daten sollen als zweispaltige <table> geliefert werden. Verzichte bei den HTML-Fragmenten möglichst auf CSS. Sprache ist
Deutsch. Anrede ist per Du, z.B. "ziehe die Aufmerksamkeit auf Dich" statt "ziehen Sie die Aufmerksamkeit auf sich".
"""

@Configuration
class ShopTexterConfiguration {

    @Bean
    @DependsOn("mariaDBVectorStoreRepository")
    fun shopTexterChatClient(builder: ChatClient.Builder, dataSource: DataSource) =
        builder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(OpenAiApi.ChatModel.GPT_4_1)
                    .build()
            )
            .defaultAdvisors(
                PromptChatMemoryAdvisor
                    .builder(
                        MessageWindowChatMemory.builder()
                            .chatMemoryRepository(
                                JdbcChatMemoryRepository.builder()
                                    .dataSource(dataSource)
                                    .build()
                            )
                            .build()
                    )
                    .build(),
                SimpleLoggerAdvisor()
            )
            .build()
}

