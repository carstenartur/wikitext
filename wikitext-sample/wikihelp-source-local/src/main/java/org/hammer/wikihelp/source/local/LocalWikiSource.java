package org.hammer.wikihelp.source.local;

import org.hammer.wikihelp.core.AttachmentSupport;
import org.hammer.wikihelp.core.AttachmentRequest;
import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.MarkupFormat;
import org.hammer.wikihelp.core.PageSelection;
import org.hammer.wikihelp.core.SourceConfiguration;
import org.hammer.wikihelp.core.SourceSupport;
import org.hammer.wikihelp.core.WikiPage;
import org.hammer.wikihelp.core.WikiSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.net.URI;

public final class LocalWikiSource implements WikiSource {
    @Override
    public String type() {
        return "local";
    }

    @Override
    public boolean remote() {
        return false;
    }

    @Override
    public List<AttachmentRequest> attachmentRequests(
            SourceConfiguration configuration,
            WikiPage page,
            HttpTransport transport) throws Exception {
        Path location = configuration.resolve("location").toAbsolutePath().normalize();
        Path root = Files.isDirectory(location) ? location : location.getParent();
        return WikiSource.super.attachmentRequests(configuration, page, transport).stream()
                .filter(request -> allowedLocalReference(root, request.uri()))
                .toList();
    }

    private boolean allowedLocalReference(Path root, URI uri) {
        if (!"file".equalsIgnoreCase(uri.getScheme())) return true;
        try {
            return Path.of(uri).toAbsolutePath().normalize().startsWith(root);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception {
        Path location = configuration.resolve("location");
        if (!Files.exists(location)) {
            throw new IllegalArgumentException("Local WikiHelp source does not exist: " + location);
        }

        List<Path> files;
        if (Files.isDirectory(location)) {
            try (var stream = Files.walk(location)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(file -> !AttachmentSupport.isLikelyBinary(file))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
        } else {
            files = AttachmentSupport.isLikelyBinary(location) ? List.of() : List.of(location);
        }

        String configuredFormat = configuration.get("format");
        String language = configuration.get("language", "en");
        List<String> configuredTags = configuration.list("tags");
        List<WikiPage> pages = new ArrayList<>();

        for (Path file : files) {
            Path relative = Files.isDirectory(location) ? location.relativize(file) : file.getFileName();
            MarkupFormat format = configuredFormat == null
                    ? MarkupFormat.fromPath(file)
                    : MarkupFormat.fromName(configuredFormat);
            String content = Files.readString(file, StandardCharsets.UTF_8);
            WikiPage page = new WikiPage(
                    configuration.id(),
                    relative.toString().replace('\\', '/'),
                    SourceSupport.title(format, content, file),
                    relative.toString().replace('\\', '/'),
                    language,
                    format,
                    content,
                    SourceSupport.tags(format, content, configuredTags),
                    file.toUri(),
                    Long.toString(Files.getLastModifiedTime(file).toMillis()));
            if (selection.matches(page)) pages.add(page);
        }
        return List.copyOf(pages);
    }
}
