package org.hammer.wikihelp.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CliIntegrationSelfTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("wikihelp-cli-test");
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Files.writeString(sources.resolve("Guide.mediawiki"),
                "= Guide =\n\nSelected content.\n\n[[Category:help]]\n",
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("book.css"), "body { font-family: sans-serif; }\n");
        Files.writeString(directory.resolve("wikihelp.properties"), """
                wikihelp.title=Test Help
                wikihelp.mode=offline
                wikihelp.sources=test
                wikihelp.source.test.type=local
                wikihelp.source.test.location=sources
                wikihelp.source.test.language=en
                wikihelp.source.test.include.tags=help
                wikihelp.source.test.include.pathGlobs=**/*.mediawiki
                """);

        WikiHelpCli.main(new String[] {
                "--config", directory.resolve("wikihelp.properties").toString(),
                "--cache", directory.resolve("cache").toString(),
                "--output", directory.resolve("nl").toString(),
                "--ant", directory.resolve("target/wikihelp-render.xml").toString(),
                "--toc", directory.resolve("wikihelp_toc.xml").toString(),
                "--css", directory.resolve("book.css").toString()
        });

        require(Files.isRegularFile(directory.resolve("wikihelp_toc.xml")), "TOC was not generated");
        require(Files.readString(directory.resolve("wikihelp_toc.xml")).contains("Guide"),
                "TOC does not contain the selected page");
        require(Files.isRegularFile(directory.resolve("target/wikihelp-render.xml")),
                "Ant render plan was not generated");
        require(Files.exists(directory.resolve("cache/test/manifest.tsv")), "Cache was not generated");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
