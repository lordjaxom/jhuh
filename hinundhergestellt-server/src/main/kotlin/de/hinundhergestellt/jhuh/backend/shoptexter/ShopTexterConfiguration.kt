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
import javax.sql.DataSource

@Configuration
class ShopTexterConfiguration {

    @Bean
    fun shopTexterChatClient(builder: ChatClient.Builder, dataSource: DataSource) =
        builder
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(OpenAiApi.ChatModel.GPT_5)
                    .temperature(1.0)
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

