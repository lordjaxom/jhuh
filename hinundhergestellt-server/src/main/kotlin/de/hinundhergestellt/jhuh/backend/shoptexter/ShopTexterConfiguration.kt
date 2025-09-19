package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ShopTexterConfiguration {

    @Bean
    fun shopTexterChatClient(builder: ChatClient.Builder) =
        builder
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(OpenAiApi.ChatModel.GPT_5)
                    .temperature(1.0)
                    .build()
            )
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    @Bean(defaultCandidate = false)
    fun shopTexterJsonMapper() =
        jsonMapper { configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true) }
}

