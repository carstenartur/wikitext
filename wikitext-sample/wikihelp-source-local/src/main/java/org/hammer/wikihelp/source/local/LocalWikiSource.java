package org.hammer.wikihelp.source.local;

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
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
        } else {
            files = List.of(location);
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
