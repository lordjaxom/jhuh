package de.hinundhergestellt.jhuh.vendors.ready2order.openapi
//
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.springframework.core.ParameterizedTypeReference
//import org.springframework.format.annotation.DurationFormat
//import org.springframework.format.datetime.standard.DurationFormatter
//import org.springframework.http.HttpHeaders
//import org.springframework.http.HttpMethod
//import org.springframework.http.MediaType
//import org.springframework.http.ResponseEntity
//import org.springframework.util.MultiValueMap
//import org.springframework.web.client.RestTemplate
//import java.time.Duration
//import java.time.LocalDateTime
//import java.util.Locale
//import java.util.TreeSet
//
//private val RATE_LIMIT_DURATION = Duration.ofMinutes(1)
//private val RATE_LIMIT_BUFFER = Duration.ofSeconds(2)
//private const val RATE_LIMIT_COUNT = 60
//
//private val logger = KotlinLogging.logger {}
//
//class RateLimitEnforcingApiClient(restTemplate: RestTemplate) : ApiClient(restTemplate) {
//
//    private val requestTimes = TreeSet<LocalDateTime>()
//
//    override fun <T> invokeAPI(
//        path: String, method: HttpMethod, pathParams: MutableMap<String?, Any?>,
//        queryParams: MultiValueMap<String?, String?>, body: Any?, headerParams: HttpHeaders,
//        cookieParams: MultiValueMap<String?, String?>, formParams: MultiValueMap<String?, Any?>,
//        accept: MutableList<MediaType?>, contentType: MediaType, authNames: Array<String?>,
//        returnType: ParameterizedTypeReference<T?>
//    ): ResponseEntity<T> {
//        enforceRateLimit()
//        return super.invokeAPI<T>(
//            path, method, pathParams, queryParams, body, headerParams, cookieParams, formParams, accept, contentType,
//            authNames, returnType
//        )
//    }
//
//    @Synchronized
//    private fun enforceRateLimit() {
//        val now = LocalDateTime.now()
//        requestTimes.headSet(now.minus(RATE_LIMIT_DURATION)).clear()
//        if (requestTimes.size < RATE_LIMIT_COUNT) {
//            logger.trace { "Check rate limit, ${requestTimes.size} requests in ${RATE_LIMIT_DURATION.toLogString()}" }
//            requestTimes.add(now)
//            return
//        }
//
//        val delay: Duration = RATE_LIMIT_DURATION
//            .minus(Duration.between(requestTimes.first(), now))
//            .plus(RATE_LIMIT_BUFFER)
//
//        logger.debug { "Enforce rate limit of $RATE_LIMIT_COUNT calls in ${RATE_LIMIT_DURATION.toLogString()}, delaying ${delay.toLogString()}" }
//
//        Thread.sleep(delay)
//        enforceRateLimit()
//    }
//}
//
//private val FORMATTER = DurationFormatter(DurationFormat.Style.SIMPLE, DurationFormat.Unit.SECONDS)
//
//private fun Duration.toLogString() = FORMATTER.print(this, Locale.getDefault())
