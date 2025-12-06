package com.example.policyimpact.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ExplanationService (Groq)
 *
 * - Uses Groq's OpenAI-compatible endpoint: https://api.groq.com/openai/v1
 * - Falls back to deterministic local explanation when explanation.fallback.local=true
 * - Retries on 429/5xx and caches only successful results
 */
@Service
public class ExplanationService {

    private final WebClient webClient;

    /**
     * Provide a Groq API key via property `groq.api.key` or fallback to `openai.api.key`.
     * Recommended: set env var GROQ_API_KEY and map it in application.properties:
     * groq.api.key=${GROQ_API_KEY}
     */
    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${openai.api.key:}")
    private String openaiApiKeyFallback;

    // dev toggle - short-circuit to deterministic rule-based explanation
    @Value("${explanation.fallback.local:false}")
    private boolean useLocalFallback;

    // Simple in-memory cache entry
    private static class CacheEntry {
        final String value;
        final Instant expiresAt;
        CacheEntry(String value, Instant expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // retry/backoff settings
    private final int maxRetries = 3;
    private final long initialBackoffMs = 1000L;
    private final Duration successCacheTtl = Duration.ofMinutes(5);

    // default Groq-compatible model (change if you have access to other models)
    private final String groqModel = "llama-3.1-8b-instant";

    public ExplanationService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .build();
    }

    /**
     * Main explain method. If useLocalFallback==true, returns deterministic local text immediately.
     */
    public String explain(
            Map<String, Object> changes,
            Map<String, Object> premiumImpact,
            Map<String, Object> riskImpact,
            Map<String, Object> billing
    ) {
        // Short-circuit for dev fallback
        if (useLocalFallback) {
            System.err.println("ExplanationService: useLocalFallback=true -> returning local deterministic explanation (no Groq call).");
            return localExplain(changes, premiumImpact, riskImpact, billing);
        }

        String apiKey = chooseApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No Groq API key configured (groq.api.key or openai.api.key).");
        }

        String cacheKey = buildCacheKey(changes, premiumImpact, riskImpact, billing);
        CacheEntry e = cache.get(cacheKey);
        if (e != null && Instant.now().isBefore(e.expiresAt)) {
            return e.value + " (cached)";
        } else if (e != null) {
            cache.remove(cacheKey);
        }

        double premiumDelta = extractDouble(premiumImpact, "premiumDelta", extractDouble(premiumImpact, "delta", 0.0));
        double riskDelta = extractDouble(riskImpact, "riskDelta", extractDouble(riskImpact, "delta", 0.0));

        String prompt = String.format(
                "You are an insurance policy impact explainability agent. Provide a concise (2-3 sentences) plain-language explanation for the following:\n\n"
                        + "Detected changes: %s\n"
                        + "Premium delta (USD): %.2f\n"
                        + "Risk delta (score): %.2f\n"
                        + "Billing details: %s\n\n"
                        + "Explain the main drivers and give one friendly suggestion for the customer.",
                safeToString(changes), premiumDelta, riskDelta, safeToString(billing)
        );

        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", new Object[] {
                        Map.of("role", "system", "content", "You generate concise customer-facing explanations for insurance policy changes."),
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", 300
        );

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ClientResponse clientResponse = webClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .exchange()
                        .block(Duration.ofSeconds(20));

                if (clientResponse == null) {
                    throw new RuntimeException("No response from Groq.");
                }

                int status = clientResponse.statusCode().value();
                HttpHeaders headers = clientResponse.headers().asHttpHeaders();

                // Read body text for logging/parsing
                String bodyText = null;
                try {
                    bodyText = clientResponse.bodyToMono(String.class).block(Duration.ofSeconds(10));
                } catch (Exception ignored) {}

                System.err.printf("Groq response status=%d body=%s%n", status, bodyText == null ? "<null>" : bodyText);

                if (status >= 200 && status < 300) {
                    Map<String, Object> responseBody = null;
                    try {
                        responseBody = clientResponse.bodyToMono(Map.class).block(Duration.ofSeconds(10));
                    } catch (Exception ignored) {}

                    String explanation = extractExplanationFromResponse(responseBody);
                    if ((explanation == null || explanation.isBlank()) && bodyText != null) {
                        explanation = bodyText;
                    }

                    if (explanation == null || explanation.isBlank()) {
                        throw new RuntimeException("Groq returned empty explanation.");
                    }

                    // cache and return
                    cache.put(cacheKey, new CacheEntry(explanation, Instant.now().plus(successCacheTtl)));
                    return explanation;
                }

                // retry on 429 or 5xx
                if (status == 429 || (status >= 500 && status < 600)) {
                    String retryAfter = headers.getFirst("Retry-After");
                    long waitMs = computeBackoffMs(attempt);
                    if (retryAfter != null) {
                        try {
                            long ra = Long.parseLong(retryAfter.trim());
                            waitMs = Math.max(waitMs, ra * 1000L);
                        } catch (NumberFormatException ignored) {}
                    }

                    System.err.println("Groq responded " + status + ". Attempt " + attempt + " waiting " + waitMs + "ms before retry.");
                    if (attempt >= maxRetries) {
                        if (status == 429) {
                            return "Explanation temporarily unavailable due to request limits. Please try again in a few seconds.";
                        } else {
                            return "Unable to generate explanation at this time (server error).";
                        }
                    }

                    try { Thread.sleep(waitMs + jitterMs(200, 800)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return "Explanation interrupted; please try again."; }
                    continue;
                }

                // other client errors
                System.err.printf("Groq non-retriable status %d body=%s%n", status, bodyText);
                return "Unable to generate explanation due to request error: " + status;

            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                System.err.printf("ExplanationService attempt %d failed: %s%n", attempt, msg);

                if (attempt >= maxRetries) {
                    return "Explanation temporarily unavailable. Please try again in a few seconds.";
                }

                long backoff = computeBackoffMs(attempt);
                try { Thread.sleep(backoff + jitterMs(200, 800)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return "Explanation interrupted; please try again."; }
            }
        }
    }

    /**
     * Local deterministic fallback explanation (safe dev fallback).
     */
    private String localExplain(Map<String, Object> changes,
                                Map<String, Object> premiumImpact,
                                Map<String, Object> riskImpact,
                                Map<String, Object> billing) {
        StringBuilder sb = new StringBuilder();
        try {
            double pd = extractDouble(premiumImpact, "delta", extractDouble(premiumImpact, "premiumDelta", 0.0));
            double rd = extractDouble(riskImpact, "delta", extractDouble(riskImpact, "riskDelta", 0.0));

            if (pd > 0) { sb.append(String.format("Premium increased by $%.2f", pd)); }
            else if (pd < 0) { sb.append(String.format("Premium decreased by $%.2f", Math.abs(pd))); }
            else { sb.append("No material change in premium"); }

            if (rd > 0) { sb.append(String.format("; risk increased by %.2f points", rd)); }
            else if (rd < 0) { sb.append(String.format("; risk decreased by %.2f points", Math.abs(rd))); }
            else { sb.append("; risk unchanged"); }

            if (changes != null && changes.containsKey("coverages")) sb.append("; coverage limits changed");
            if (changes != null && changes.containsKey("ratedFactors")) sb.append("; rated factors changed (driver/vehicle/location)");
            sb.append(". Consider reviewing deductibles or limits to reduce costs.");
        } catch (Exception e) {
            sb.append("Change detected; please review the policy details.");
        }
        return sb.toString();
    }

    private String extractExplanationFromResponse(Map<String, Object> response) {
        if (response == null) return null;
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List)) return null;
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) return null;
        Object first = choices.get(0);
        if (!(first instanceof Map)) return null;
        Map<?, ?> firstMap = (Map<?, ?>) first;
        Object message = firstMap.get("message");
        if (message instanceof Map) {
            Object content = ((Map<?, ?>) message).get("content");
            if (content != null) return content.toString().trim();
        }
        Object text = firstMap.get("text");
        if (text != null) return text.toString().trim();
        return null;
    }

    private long computeBackoffMs(int attempt) {
        long base = initialBackoffMs * (1L << Math.max(0, attempt - 1));
        return Math.min(base, 10_000L);
    }

    private long jitterMs(int min, int max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private double extractDouble(Map<String, Object> map, String key, double fallback) {
        if (map == null) return fallback;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private String safeToString(Object o) {
        return Objects.toString(o, "{}");
    }

    private String buildCacheKey(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            sb.append("|").append(Objects.toString(p, ""));
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    public void clearCache() {
        cache.clear();
        System.err.println("ExplanationService cache cleared.");
    }

    private String chooseApiKey() {
        if (groqApiKey != null && !groqApiKey.isBlank()) return groqApiKey;
        if (openaiApiKeyFallback != null && !openaiApiKeyFallback.isBlank()) return openaiApiKeyFallback;
        return null;
    }
}