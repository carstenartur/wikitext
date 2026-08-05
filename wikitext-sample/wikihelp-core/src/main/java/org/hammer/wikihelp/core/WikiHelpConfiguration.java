package org.hammer.wikihelp.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public record WikiHelpConfiguration(
        String title,
        Mode mode,
        List<SourceConfiguration> sources) {

    public enum Mode { OFFLINE, CACHED, ONLINE }

    public WikiHelpConfiguration {
        title = title == null || title.isBlank() ? "Wiki Help" : title;
        mode = mode == null ? Mode.OFFLINE : mode;
        sources = List.copyOf(sources);
    }

    public static WikiHelpConfiguration load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String title = properties.getProperty("wikihelp.title", "Wiki Help").trim();
        Mode mode = Mode.valueOf(properties.getProperty("wikihelp.mode", "offline").trim().toUpperCase());
        String sourceList = properties.getProperty("wikihelp.sources", "");
        Path baseDirectory = file.toAbsolutePath().normalize().getParent();

        List<SourceConfiguration> sources = new ArrayList<>();
        for (String rawId : sourceList.split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) continue;
            String prefix = "wikihelp.source." + id + ".";
            Map<String, String> sourceProperties = new LinkedHashMap<>();
            for (String name : properties.stringPropertyNames()) {
                if (name.startsWith(prefix)) {
                    sourceProperties.put(name.substring(prefix.length()), properties.getProperty(name).trim());
                }
            }
            String type = sourceProperties.remove("type");
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Missing " + prefix + "type");
            }
            sources.add(new SourceConfiguration(id, type, baseDirectory, sourceProperties));
        }

        if (sources.isEmpty()) {
            throw new IllegalArgumentException("wikihelp.sources must contain at least one source id");
        }
        return new WikiHelpConfiguration(title, mode, sources);
    }
}
