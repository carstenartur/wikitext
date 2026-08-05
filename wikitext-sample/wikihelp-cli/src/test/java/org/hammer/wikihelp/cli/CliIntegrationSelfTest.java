package org.hammer.wikihelp.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CliIntegrationSelfTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("wikihelp-cli-test");
        Path sources = Files.createDirectories(directory.resolve("sources"));
        byte[] image = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};
        Files.write(sources.resolve("diagram.png"), image);
        Files.write(directory.resolve("outside.png"), new byte[] {9, 9, 9});
        Files.writeString(sources.resolve("Guide.mediawiki"),
                "= Guide =\n\nSelected content.\n\n[[File:diagram.png|Diagram]]\n"
                        + "[[File:../outside.png]]\n\n[[Category:help]]\n",
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("book.css"),
                "body { font-family: sans-serif; }\n");
        Files.writeString(directory.resolve("wikihelp.properties"), """
                wikihelp.title=Test Help
                wikihelp.mode=offline
                wikihelp.sources=test
                wikihelp.source.test.type=local
                wikihelp.source.test.location=sources
                wikihelp.source.test.language=en
                wikihelp.source.test.include.tags=help
                wikihelp.source.test.include.pathGlobs=**/*.mediawiki
                wikihelp.source.test.attachments.enabled=true
                """);

        WikiHelpCli.main(new String[] {
                "--config", directory.resolve("wikihelp.properties").toString(),
                "--cache", directory.resolve("cache").toString(),
                "--output", directory.resolve("nl").toString(),
                "--ant", directory.resolve("target/wikihelp-render.xml").toString(),
                "--toc", directory.resolve("wikihelp_toc.xml").toString(),
                "--css", directory.resolve("book.css").toString()
        });

        require(Files.isRegularFile(directory.resolve("wikihelp_toc.xml")),
                "TOC was not generated");
        require(Files.readString(directory.resolve("wikihelp_toc.xml")).contains("Guide"),
                "TOC does not contain the selected page");
        require(Files.isRegularFile(directory.resolve("target/wikihelp-render.xml")),
                "Ant render plan was not generated");
        require(Files.exists(directory.resolve("cache/test/manifest.tsv")),
                "Cache was not generated");
        require(Files.exists(directory.resolve("cache/test/attachments.tsv")),
                "Attachment manifest was not generated");
        try (var assets = Files.list(directory.resolve("cache/test/assets"))) {
            require(assets.count() == 1,
                    "only the in-tree attachment should be cached");
        }
        Path generatedSource;
        try (var files = Files.list(directory.resolve("nl/en"))) {
            generatedSource = files.filter(path -> path.toString().endsWith(".mediawiki"))
                    .findFirst().orElseThrow();
        }
        String rewritten = Files.readString(generatedSource);
        require(rewritten.contains("<img src=\"assets/"),
                "MediaWiki image was not localized: " + rewritten);
        require(rewritten.contains("[[File:../outside.png]]"),
                "out-of-tree reference should remain external/unresolved");
        try (var assets = Files.list(directory.resolve("nl/en/assets"))) {
            Path generatedAsset = assets.findFirst().orElseThrow();
            require(java.util.Arrays.equals(image, Files.readAllBytes(generatedAsset)),
                    "generated attachment bytes changed");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
