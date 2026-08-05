package org.hammer.wikihelp.core;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        testJson();
        testSelection();
        testCache();
    }

    private static void testJson() {
        Map<String, Object> object = Json.object(Json.parse(
                "{\"name\":\"page\",\"count\":2,\"items\":[true,null,\"x\"]}"));
        require("page".equals(Json.string(object.get("name"))), "JSON string");
        require(Json.longValue(object.get("count"), -1) == 2, "JSON number");
        require(Json.array(object, "items").size() == 3, "JSON array");
    }

    private static void testSelection() {
        WikiPage page = new WikiPage(
                "source", "id", "Install Guide", "docs/install", "en",
                MarkupFormat.MARKDOWN, "# Install", Set.of("help", "public"),
                URI.create("https://example.invalid/docs/install"), "1");
        PageSelection selection = new PageSelection(
                Set.of("help"), Set.of("draft"), List.of("*Guide"),
                List.of(), List.of("docs/**"), List.of(), Set.of(), PageSelection.TagMode.ALL);
        require(selection.matches(page), "selector should match");
        require(Glob.matches("**/*.mediawiki", "Guide.mediawiki"), "double-star should include root files");

        WikiPage duplicateIdentifiers = new WikiPage(
                "source", "same", "same", "same", "en",
                MarkupFormat.PLAIN_TEXT, "content", Set.of(), null, "");
        PageSelection explicit = new PageSelection(
                Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of("same"), PageSelection.TagMode.ANY);
        require(explicit.matches(duplicateIdentifiers), "duplicate identifiers must not break selection");
    }

    private static void testCache() throws Exception {
        var directory = Files.createTempDirectory("wikihelp-cache-test");
        PageCache cache = new PageCache();
        WikiPage page = new WikiPage(
                "source", "id", "Title", "path", "de", MarkupFormat.MEDIAWIKI,
                "= Title =", Set.of("help"), URI.create("https://example.invalid/Title"), "42");
        cache.store(directory, "source", List.of(page));
        List<WikiPage> loaded = cache.load(directory, "source");
        require(loaded.size() == 1 && loaded.getFirst().equals(page), "cache round trip");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
