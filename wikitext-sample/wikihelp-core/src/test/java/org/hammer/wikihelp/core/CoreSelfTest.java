package org.hammer.wikihelp.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        testJson();
        testSelection();
        testAttachmentDiscovery();
        testCache();
        testLegacyCache();
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
        require(Glob.matches("**/*.mediawiki", "Guide.mediawiki"),
                "double-star should include root files");

        WikiPage duplicateIdentifiers = new WikiPage(
                "source", "same", "same", "same", "en",
                MarkupFormat.PLAIN_TEXT, "content", Set.of(), null, "");
        PageSelection explicit = new PageSelection(
                Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of("same"), PageSelection.TagMode.ANY);
        require(explicit.matches(duplicateIdentifiers),
                "duplicate identifiers must not break selection");
    }

    private static void testAttachmentDiscovery() {
        WikiPage page = new WikiPage(
                "source", "id", "Assets", "docs/assets", "en", MarkupFormat.HTML,
                """
                <img src="images/a.png" srcset="images/b.png 1x, images/c.png 2x">
                <a href="manual.pdf">Manual</a>
                <a href="other.html">Other page</a>
                <style>.logo { background: url('images/logo.svg'); }</style>
                """,
                Set.of(), URI.create("https://example.invalid/docs/assets"), "1");
        List<AttachmentRequest> requests = AttachmentSupport.discover(page);
        require(requests.size() == 5, "HTML attachment discovery: " + requests);
        require(requests.stream().anyMatch(item -> item.uri().toString().endsWith("images/c.png")),
                "srcset attachment");
        require(requests.stream().noneMatch(item -> item.uri().toString().endsWith("other.html")),
                "ordinary page links must not become attachments");
        require(AttachmentSupport.mediaWikiFileNames(
                "[[File:Diagram one.png|thumb]] [[Image:Second.svg]]")
                .equals(List.of("Diagram one.png", "Second.svg")),
                "MediaWiki file discovery");
    }

    private static void testCache() throws Exception {
        var directory = Files.createTempDirectory("wikihelp-cache-test");
        PageCache cache = new PageCache();
        byte[] image = new byte[] {1, 2, 3, 4};
        WikiAttachment first = new WikiAttachment(
                "images/a.png", URI.create("https://example.invalid/a.png"),
                "a.png", "image/png", image);
        WikiAttachment second = new WikiAttachment(
                "images/copy.png", URI.create("https://example.invalid/copy.png"),
                "copy.png", "image/png", image);
        WikiPage page = new WikiPage(
                "source", "id", "Title", "path", "de", MarkupFormat.MEDIAWIKI,
                "= Title =", Set.of("help"), URI.create("https://example.invalid/Title"),
                "42", List.of(first, second));
        cache.store(directory, "source", List.of(page));
        List<WikiPage> loaded = cache.load(directory, "source");
        require(loaded.size() == 1 && loaded.getFirst().equals(page),
                "cache round trip with attachments");
        try (var assets = Files.list(directory.resolve("source/assets"))) {
            require(assets.count() == 1, "identical attachment bytes must be stored once");
        }
        require(Files.readString(directory.resolve("source/manifest.tsv"))
                .startsWith("# wikihelp-cache-v2"), "cache version");
    }

    private static void testLegacyCache() throws Exception {
        var directory = Files.createTempDirectory("wikihelp-cache-v1-test");
        var source = Files.createDirectories(directory.resolve("legacy/pages"));
        Files.writeString(source.resolve("page.mediawiki"), "= Legacy =", StandardCharsets.UTF_8);
        String line = String.join("\t",
                encode("page.mediawiki"), encode("legacy"), encode("1"), encode("Legacy"),
                encode("Legacy"), encode("en"), encode("MEDIAWIKI"), encode("help"),
                encode("https://example.invalid/Legacy"), encode("1"));
        Files.writeString(directory.resolve("legacy/manifest.tsv"),
                "# wikihelp-cache-v1\n" + line + "\n", StandardCharsets.UTF_8);
        List<WikiPage> loaded = new PageCache().load(directory, "legacy");
        require(loaded.size() == 1 && loaded.getFirst().attachments().isEmpty(),
                "v1 cache compatibility");
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
