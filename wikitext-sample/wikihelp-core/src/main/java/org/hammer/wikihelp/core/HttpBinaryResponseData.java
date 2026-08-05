package org.hammer.wikihelp.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record HttpBinaryResponseData(
        int statusCode,
        byte[] body,
        Map<String, List<String>> headers) {

    public HttpBinaryResponseData {
        body = Objects.requireNonNull(body, "body").clone();
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public String firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }
}
