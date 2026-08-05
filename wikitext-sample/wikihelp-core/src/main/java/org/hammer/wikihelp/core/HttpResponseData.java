package org.hammer.wikihelp.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record HttpResponseData(int status, String body, Map<String, List<String>> headers) {
    public HttpResponseData {
        headers = Map.copyOf(headers);
    }

    public Optional<String> firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
