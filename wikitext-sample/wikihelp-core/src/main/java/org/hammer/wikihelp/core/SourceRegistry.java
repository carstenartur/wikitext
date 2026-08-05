package org.hammer.wikihelp.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.ServiceLoader;

public final class SourceRegistry {
    private final Map<String, WikiSource> sources = new LinkedHashMap<>();

    public static SourceRegistry load() {
        SourceRegistry registry = new SourceRegistry();
        ServiceLoader.load(WikiSource.class).forEach(registry::register);
        return registry;
    }

    public SourceRegistry register(WikiSource source) {
        WikiSource previous = sources.put(source.type().toLowerCase(Locale.ROOT), source);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate WikiSource type: " + source.type());
        }
        return this;
    }

    public WikiSource require(String type) {
        WikiSource source = sources.get(type.toLowerCase(Locale.ROOT));
        if (source == null) {
            throw new IllegalArgumentException(
                    "Unknown WikiSource type '" + type + "'. Available: " + String.join(", ", sources.keySet()));
        }
        return source;
    }
}
