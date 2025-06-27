package de.hinundhergestellt.jhuh.vendors.ready2order.openapi

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DurationFormat
import org.springframework.format.datetime.standard.DurationFormatter
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import reactor.core.publisher.Mono.delay
import java.time.Duration
import java.time.LocalDateTime
import java.util.Collections.synchronizedNavigableSet
import java.util.Locale
import java.util.TreeSet

private val RATE_LIMIT_DURATION = Duration.ofMinutes(1)
private val RATE_LIMIT_BUFFER = Duration.ofSeconds(2)
private const val RATE_LIMIT_COUNT = 60

private val logger = KotlinLogging.logger {}

class RateLimitEnforcingFilter : (ClientRequest, ExchangeFunction) -> Mono<ClientResponse> {

    private val requestTimes = synchronizedNavigableSet(TreeSet<LocalDateTime>())

    override fun invoke(request: ClientRequest, next: ExchangeFunction) = defer { enforceRateLimit(next, request) }

    private fun enforceRateLimit(next: ExchangeFunction, request: ClientRequest): Mono<ClientResponse> {
        val now = LocalDateTime.now()
        synchronized(requestTimes) {
            requestTimes.headSet(now.minus(RATE_LIMIT_DURATION)).clear()
            if (requestTimes.size < RATE_LIMIT_COUNT) {
                logger.trace { "Check rate limit, ${requestTimes.size} requests in ${RATE_LIMIT_DURATION.toLogString()}" }
                requestTimes.add(now)
                return next.exchange(request)
            }
        }

        val delay: Duration = RATE_LIMIT_DURATION
            .minus(Duration.between(requestTimes.first(), now))
            .plus(RATE_LIMIT_BUFFER)

        logger.debug { "Enforce rate limit of $RATE_LIMIT_COUNT calls in ${RATE_LIMIT_DURATION.toLogString()}, delaying ${delay.toLogString()}" }

        return delay(delay).flatMap { invoke(request, next) }
    }
}

private val FORMATTER = DurationFormatter(DurationFormat.Style.SIMPLE, DurationFormat.Unit.SECONDS)

private fun Duration.toLogString() = FORMATTER.print(this, Locale.getDefault())
