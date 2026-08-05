package org.hammer.wikihelp.core;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
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
        String revision,
        List<WikiAttachment> attachments) {

    public WikiPage(
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
        this(sourceId, remoteId, title, path, language, format, content, tags,
                originalUri, revision, List.of());
    }

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
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
    }

    public WikiPage withAttachments(List<WikiAttachment> value) {
        return new WikiPage(
                sourceId, remoteId, title, path, language, format, content, tags,
                originalUri, revision, value);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
