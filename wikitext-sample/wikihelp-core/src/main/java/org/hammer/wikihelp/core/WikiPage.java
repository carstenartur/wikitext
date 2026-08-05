package org.hammer.wikihelp.core;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record WikiPage(
        String sourceId,
        String remoteId,
        String title,
        String path,
        String language,
        MarkupFormat format,
        String content,
        Set<String> tags,
        URI originalUri,
        String revision) {

    public WikiPage {
        sourceId = require(sourceId, "sourceId");
        remoteId = require(remoteId, "remoteId");
        title = require(title, "title");
        path = path == null || path.isBlank() ? remoteId : path;
        language = language == null || language.isBlank() ? "en" : language;
        format = Objects.requireNonNull(format, "format");
        content = Objects.requireNonNullElse(content, "");
        tags = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNullElse(tags, Set.of())));
        revision = Objects.requireNonNullElse(revision, "");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
