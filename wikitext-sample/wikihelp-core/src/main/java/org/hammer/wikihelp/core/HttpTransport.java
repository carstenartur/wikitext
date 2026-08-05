package org.hammer.wikihelp.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class HttpTransport {
    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;

    public HttpTransport() {
        this(Duration.ofSeconds(45), "wikitext-wikihelp/1.1");
    }

    public HttpTransport(Duration timeout, String userAgent) {
        this.timeout = timeout;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public HttpResponseData get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                .header("User-Agent", userAgent)
                .GET();
        headers.forEach(request::header);

        HttpResponse<String> response = client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponseData result = new HttpResponseData(
                response.statusCode(),
                response.body(),
                response.headers().map());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
            if (body.length() > 400) body = body.substring(0, 400) + "...";
            throw new IOException("HTTP " + response.statusCode() + " for " + uri + ": " + body);
        }
        return result;
    }

    public HttpBinaryResponseData getBytes(
            URI uri,
            Map<String, String> headers,
            int maxBytes) throws IOException, InterruptedException {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "*/*")
                .header("User-Agent", userAgent)
                .GET();
        headers.forEach(request::header);

        HttpResponse<InputStream> response = client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > maxBytes) {
                throw new IOException(
                        "Response exceeds attachment limit of " + maxBytes + " bytes for " + uri);
            }
            byte[] bytes = readLimited(body, maxBytes);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
                if (message.length() > 400) message = message.substring(0, 400) + "...";
                throw new IOException(
                        "HTTP " + response.statusCode() + " for " + uri + ": " + message);
            }
            return new HttpBinaryResponseData(
                    response.statusCode(), bytes, response.headers().map());
        }
    }

    public Object getJson(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        return Json.parse(get(uri, headers).body());
    }

    public static URI withQuery(String base, Map<String, ?> parameters) {
        URI uri = URI.create(base);
        StringJoiner query = new StringJoiner("&");
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            query.add(uri.getRawQuery());
        }
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            if (entry.getValue() == null) continue;
            query.add(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
        }
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    query.length() == 0 ? null : query.toString(),
                    uri.getFragment());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot construct URI from " + base, exception);
        }
    }

    public static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    public static Map<String, String> bearerHeader(String token) {
        if (token == null || token.isBlank()) return Map.of();
        return Map.of("Authorization", "Bearer " + token.trim());
    }

    public static Map<String, String> basicHeader(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of();
        }
        String credentials = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return Map.of("Authorization", "Basic " + credentials);
    }

    public static Map<String, String> mergeHeaders(Map<String, String> first, Map<String, String> second) {
        Map<String, String> result = new LinkedHashMap<>(first);
        result.putAll(second);
        return Map.copyOf(result);
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response exceeds attachment limit of " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
