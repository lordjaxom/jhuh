package de.hinundhergestellt.jhuh.vendors.ready2order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.log.LogMessage;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class RateLimitEnforcingApiClient extends ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitEnforcingApiClient.class);

    private static final Duration RATE_LIMIT_DURATION = Duration.ofMinutes(1);
    private static final Duration RATE_LIMIT_BUFFER = Duration.ofSeconds(2);
    private static final int RATE_LIMIT_COUNT = 60;

    private static final DurationFormatter FORMATTER = new DurationFormatter(DurationFormat.Style.SIMPLE, DurationFormat.Unit.SECONDS);

    private final SortedSet<LocalDateTime> requestTimes = new TreeSet<>();

    public RateLimitEnforcingApiClient(RestTemplate restTemplate) {
        super(restTemplate);
    }

    @Override
    public <T> ResponseEntity<T> invokeAPI(String path, HttpMethod method, Map<String, Object> pathParams,
                                           MultiValueMap<String, String> queryParams, Object body, HttpHeaders headerParams,
                                           MultiValueMap<String, String> cookieParams, MultiValueMap<String, Object> formParams,
                                           List<MediaType> accept, MediaType contentType, String[] authNames,
                                           ParameterizedTypeReference<T> returnType) throws RestClientException {
        enforceRateLimit();
        return super.invokeAPI(path, method, pathParams, queryParams, body, headerParams, cookieParams, formParams, accept, contentType,
                authNames, returnType);
    }

    private synchronized void enforceRateLimit() {
        var now = LocalDateTime.now();
        requestTimes.headSet(now.minus(RATE_LIMIT_DURATION)).clear();
        if (requestTimes.size() < RATE_LIMIT_COUNT) {
            LOGGER.trace("Check rate limit, {} requests in {}", requestTimes.size(), log(RATE_LIMIT_DURATION));

            requestTimes.add(now);
            return;
        }

        var delay = RATE_LIMIT_DURATION
                .minus(Duration.between(requestTimes.first(), now))
                .plus(RATE_LIMIT_BUFFER);

        LOGGER.debug("Enforce rate limit of {} calls in {}, delaying {}", RATE_LIMIT_COUNT, log(RATE_LIMIT_DURATION), log(delay));

        sleep(delay);
        enforceRateLimit();
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted enforcing rate limit", e);
        }
    }

    private static LogMessage log(Duration duration) {
        return LogMessage.of(() -> FORMATTER.print(duration, Locale.getDefault()));
    }
}
