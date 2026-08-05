package org.hammer.wikihelp.core;

import java.nio.file.Path;
import java.util.Locale;

public enum MarkupFormat {
    MEDIAWIKI("mediawiki", "MediaWiki"),
    CONFLUENCE("confluence", "Confluence"),
    TEXTILE("textile", "Textile"),
    TRACWIKI("tracwiki", "TracWiki"),
    TWIKI("twiki", "TWiki"),
    MARKDOWN("md", null),
    ASCIIDOC("adoc", null),
    HTML("html", null),
    PLAIN_TEXT("txt", null);

    private final String extension;
    private final String wikiTextLanguage;

    MarkupFormat(String extension, String wikiTextLanguage) {
        this.extension = extension;
        this.wikiTextLanguage = wikiTextLanguage;
    }

    public String extension() {
        return extension;
    }

    public String wikiTextLanguage() {
        return wikiTextLanguage;
    }

    public boolean isWikiTextRenderable() {
        return wikiTextLanguage != null;
    }

    public static MarkupFormat fromName(String value) {
        if (value == null || value.isBlank()) {
            return PLAIN_TEXT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        return switch (normalized) {
            case "mediawiki", "wiki", "wikitext" -> MEDIAWIKI;
            case "confluence" -> CONFLUENCE;
            case "textile" -> TEXTILE;
            case "tracwiki", "trac" -> TRACWIKI;
            case "twiki" -> TWIKI;
            case "markdown", "md", "gfm", "commonmark" -> MARKDOWN;
            case "asciidoc", "adoc" -> ASCIIDOC;
            case "html", "xhtml", "rendered" -> HTML;
            case "text", "txt", "plain", "plaintext", "rdoc", "org" -> PLAIN_TEXT;
            default -> throw new IllegalArgumentException("Unsupported markup format: " + value);
        };
    }

    public static MarkupFormat fromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mediawiki") || name.endsWith(".wiki")) return MEDIAWIKI;
        if (name.endsWith(".confluence")) return CONFLUENCE;
        if (name.endsWith(".textile")) return TEXTILE;
        if (name.endsWith(".tracwiki")) return TRACWIKI;
        if (name.endsWith(".twiki")) return TWIKI;
        if (name.endsWith(".md") || name.endsWith(".markdown")) return MARKDOWN;
        if (name.endsWith(".adoc") || name.endsWith(".asciidoc")) return ASCIIDOC;
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml")) return HTML;
        return PLAIN_TEXT;
    }
}
