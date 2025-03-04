package org.open4goods.services.googlesearch.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.open4goods.services.googlesearch.config.GoogleSearchConfig;

import org.open4goods.services.googlesearch.dto.GoogleSearchRequest;
import org.open4goods.services.googlesearch.dto.GoogleSearchResponse;
import org.open4goods.services.googlesearch.dto.GoogleSearchResult;
import org.open4goods.services.googlesearch.exception.GoogleSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service for interacting with the Google Custom Search API.
 * <p>
 * It externalizes its configuration via {@link GoogleSearchProperties}, increments actuator metrics for each search,
 * and implements a health check based on proper configuration and recent API call outcomes.
 */
@Service
public class GoogleSearchService implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(GoogleSearchService.class);

    private final HttpClient httpClient;
    private final Gson gson;
    private final GoogleSearchConfig properties;
    private final MeterRegistry meterRegistry;
    
    // Volatile variable to hold the last error message if a non-200 response was received.
    private volatile String lastErrorMessage = null;

    /**
     * Constructs a new GoogleSearchService.
     *
     * @param properties    the Google search configuration properties
     * @param meterRegistry the actuator meter registry for metrics
     */
    public GoogleSearchService(GoogleSearchConfig properties, MeterRegistry meterRegistry) {
        // Create an HttpClient with a connection timeout.
        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofSeconds(10))
                                    .build();
        this.gson = new Gson();
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Executes a search against the Google Custom Search API.
     *
     * @param request a {@link GoogleSearchRequest} containing the query and desired number of results
     * @return a {@link GoogleSearchResponse} containing the search results
     * @throws IOException          if an I/O error occurs during the HTTP call
     * @throws InterruptedException if the HTTP request is interrupted
     * @throws GoogleSearchException if the API responds with an error or the response cannot be parsed
     */
    public GoogleSearchResponse search(GoogleSearchRequest request) throws IOException, InterruptedException {
        // Increment the actuator metric for the number of searches performed.
        meterRegistry.counter("google.search.count").increment();

        // Validate input query (constructor of GoogleSearchRequest already does basic validation)
        final String encodedQuery = URLEncoder.encode(request.getQuery(), Charset.defaultCharset());
        
        // Build the API URL using externalized configuration.
        final String url = String.format("%s?q=%s&key=%s&cx=%s&num=%d",
                properties.getSearchUrl(),
                encodedQuery,
                properties.getApiKey(),
                properties.getCx(),
                request.getNumResults());
        
        // Prepare a safe version of the URL for logging (masking the API key)
        final String safeUrl = String.format("%s?q=%s&key=****&cx=%s&num=%d",
                properties.getSearchUrl(),
                encodedQuery,
                properties.getCx(),
                request.getNumResults());
        logger.debug("Executing search with URL: {}", safeUrl);

        // Build the HTTP request with a timeout.
        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        // Send the HTTP request and obtain the response.
        final HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Check the HTTP status code; if not 200, log and throw a custom exception.
        if (response.statusCode() != 200) {
            String errorMsg = "Error from Google Custom Search API: HTTP " + response.statusCode() + " - " + response.body();
            logger.error(errorMsg);
            lastErrorMessage = errorMsg;
            throw new GoogleSearchException(errorMsg);
        } else {
            // Clear any previous error if the current call is successful.
            lastErrorMessage = null;
        }

        logger.info("Search performed for query: '{}' with HTTP status: {}", request.getQuery(), response.statusCode());

        // Parse the JSON response into our DTO.
        return parseResponse(response.body());
    }

    /**
     * Parses the JSON response returned by the Google Custom Search API.
     *
     * @param jsonResponse the raw JSON response as a String
     * @return a {@link GoogleSearchResponse} containing the parsed search results
     * @throws GoogleSearchException if parsing fails or the expected JSON structure is missing
     */
    private GoogleSearchResponse parseResponse(String jsonResponse) {
        logger.debug("Parsing response JSON: {}", jsonResponse);
        try {
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray items = jsonObject.getAsJsonArray("items");

            final List<GoogleSearchResult> results = new ArrayList<>();
            if (items != null) {
                // Iterate over each item and extract title and link.
                items.forEach(item -> {
                    JsonObject obj = item.getAsJsonObject();
                    String title = obj.get("title").getAsString();
                    String link = obj.get("link").getAsString();
                    results.add(new GoogleSearchResult(title, link));
                });
            }
            return new GoogleSearchResponse(results);
        } catch (Exception e) {
            String errorMsg = "Failed to parse Google Custom Search API response";
            logger.error(errorMsg, e);
            throw new GoogleSearchException(errorMsg, e);
        }
    }

    /**
     * Health check implementation.
     * <p>
     * The service is considered healthy if the necessary configuration properties are set and the last search returned HTTP 200.
     *
     * @return a Health status indicating UP if properties are properly configured and no recent error was encountered; otherwise DOWN.
     */
    @Override
    public Health health() {
        if (properties.getApiKey() == null || properties.getApiKey().isEmpty() ||
            properties.getCx() == null || properties.getCx().isEmpty() ||
            properties.getSearchUrl() == null || properties.getSearchUrl().isEmpty()) {
            logger.error("Google Search properties are not properly configured.");
            return Health.down().withDetail("error", "Google Search properties are missing or invalid").build();
        }
        
        if (lastErrorMessage != null) {
            logger.error("Health check failed due to previous error: {}", lastErrorMessage);
            return Health.down().withDetail("error", lastErrorMessage).build();
        }
        
        logger.debug("Google Search properties are properly configured and no recent errors encountered.");
        return Health.up().build();
    }
}
