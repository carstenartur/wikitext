package org.hammer.wikihelp.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SourceConfiguration {
    private final String id;
    private final String type;
    private final Path baseDirectory;
    private final Map<String, String> properties;

    public SourceConfiguration(String id, String type, Path baseDirectory, Map<String, String> properties) {
        this.id = require(id, "id");
        this.type = require(type, "type");
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
        this.properties = Map.copyOf(new LinkedHashMap<>(properties));
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public Path baseDirectory() {
        return baseDirectory;
    }

    public String get(String name) {
        return properties.get(name);
    }

    public String get(String name, String defaultValue) {
        String value = properties.get(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public String required(String name) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property wikihelp.source." + id + "." + name);
        }
        return value.trim();
    }

    public List<String> list(String name) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    public int integer(String name, int defaultValue) {
        String value = get(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
    }

    public boolean bool(String name, boolean defaultValue) {
        String value = get(name);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public Path resolve(String name) {
        Path path = Path.of(required(name));
        return path.isAbsolute() ? path.normalize() : baseDirectory.resolve(path).normalize();
    }

    public String environment(String propertyName) {
        String variable = get(propertyName);
        if (variable == null || variable.isBlank()) {
            return null;
        }
        return System.getenv(variable.trim());
    }

    public Map<String, String> properties() {
        return properties;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
