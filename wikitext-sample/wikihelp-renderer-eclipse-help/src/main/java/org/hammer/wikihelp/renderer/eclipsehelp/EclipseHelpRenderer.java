package org.hammer.wikihelp.renderer.eclipsehelp;

import org.hammer.wikihelp.core.MarkupFormat;
import org.hammer.wikihelp.core.WikiPage;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EclipseHelpRenderer {
    private static final Pattern URL_ATTRIBUTE = Pattern.compile(
            "(?i)(href|src)\\s*=\\s*(['\"])(.*?)\\2");
    private static final Pattern UNSAFE_BLOCK = Pattern.compile(
            "(?is)<(?:script|iframe|object|embed|form)\\b.*?</(?:script|iframe|object|embed|form)>");
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1");

    public RenderResult prepare(
            String title,
            List<WikiPage> inputPages,
            Path outputRoot,
            Path antFile,
            Path tocFile,
            Path cssFile) throws IOException {
        deleteRecursively(outputRoot);
        Files.createDirectories(outputRoot);
        Files.createDirectories(antFile.toAbsolutePath().normalize().getParent());

        List<RenderedPage> pages = new ArrayList<>();
        Set<String> languages = new LinkedHashSet<>();
        Map<Group, List<String>> wikiTextSources = new LinkedHashMap<>();

        for (WikiPage page : inputPages.stream()
                .sorted(Comparator.comparing(WikiPage::language)
                        .thenComparing(WikiPage::sourceId)
                        .thenComparing(WikiPage::title))
                .toList()) {
            String language = safeLanguage(page.language());
            languages.add(language);
            Path languageDirectory = outputRoot.resolve(language);
            Files.createDirectories(languageDirectory);

            String baseName = fileBase(page);
            String href = outputRoot.getFileName() + "/" + language + "/" + baseName + ".html";
            if (page.format() == MarkupFormat.HTML) {
                String body = sanitizeAndResolve(page.content(), page.originalUri());
                Files.writeString(
                        languageDirectory.resolve(baseName + ".html"),
                        htmlDocument(page.title(), body, page.originalUri()),
                        StandardCharsets.UTF_8);
            } else if (page.format().isWikiTextRenderable()) {
                String sourceName = baseName + "." + page.format().extension();
                Files.writeString(languageDirectory.resolve(sourceName), page.content(), StandardCharsets.UTF_8);
                wikiTextSources.computeIfAbsent(
                        new Group(language, page.format()), ignored -> new ArrayList<>()).add(sourceName);
            } else {
                throw new IllegalArgumentException(
                        "Format " + page.format() + " for " + page.title()
                                + " is not directly renderable. Configure the source with contentMode=rendered.");
            }
            pages.add(new RenderedPage(page.sourceId(), language, page.title(), href));
        }

        for (String language : languages) {
            Files.copy(cssFile, outputRoot.resolve(language).resolve("book.css"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Files.writeString(tocFile, tocXml(title, pages), StandardCharsets.UTF_8);
        Files.writeString(antFile, antXml(title, outputRoot, wikiTextSources), StandardCharsets.UTF_8);
        return new RenderResult(antFile, tocFile, pages.size());
    }

    private String antXml(
            String title,
            Path outputRoot,
            Map<Group, List<String>> groups) {
        String output = xml(outputRoot.toString().replace('\\', '/'));
        String helpRoot = xml(outputRoot.getFileName().toString());
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project name="wikihelp-render" default="render" basedir="..">
                    <path id="wikitext.tasks.classpath">
                        <fileset dir="lib">
                            <include name="org.eclipse.mylyn.wikitext.*core*.jar"/>
                        </fileset>
                    </path>
                    <taskdef classpathref="wikitext.tasks.classpath"
                             resource="org/eclipse/mylyn/wikitext/core/util/anttask/tasks.properties"/>
                    <target name="render">
                """);

        for (Map.Entry<Group, List<String>> entry : groups.entrySet()) {
            Group group = entry.getKey();
            String includes = entry.getValue().stream()
                    .sorted()
                    .map(this::xml)
                    .reduce((first, second) -> first + "," + second)
                    .orElse("");
            String directory = output + "/" + xml(group.language());
            xml.append("        <wikitext-to-eclipse-help markuplanguage=\"")
                    .append(xml(group.format().wikiTextLanguage()))
                    .append("\" navigationImages=\"false\" validate=\"true\" ")
                    .append("failonvalidationerror=\"true\" formatoutput=\"true\" ")
                    .append("defaultAbsoluteLinkTarget=\"mylyn_external\" helpPrefix=\"")
                    .append(helpRoot).append("/").append(xml(group.language()))
                    .append("\" title=\"")
                    .append(xml(title))
                    .append("\" overwrite=\"true\" xmlFilenameFormat=\".wikihelp-$1_toc.xml\">\n")
                    .append("            <fileset dir=\"").append(directory)
                    .append("\" includes=\"").append(includes).append("\"/>\n")
                    .append("            <stylesheet url=\"book.css\"/>\n")
                    .append("        </wikitext-to-eclipse-help>\n");
        }

        xml.append("        <delete>\n")
                .append("            <fileset dir=\"").append(output)
                .append("\" includes=\"**/*.mediawiki,**/*.confluence,**/*.textile,**/*.tracwiki,**/*.twiki,**/.wikihelp-*_toc.xml\"/>\n")
                .append("        </delete>\n")
                .append("    </target>\n")
                .append("</project>\n");
        return xml.toString();
    }

    private String tocXml(String title, List<RenderedPage> pages) {
        Map<String, Map<String, List<RenderedPage>>> grouped = new LinkedHashMap<>();
        for (RenderedPage page : pages) {
            grouped.computeIfAbsent(page.language(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(page.sourceId(), ignored -> new ArrayList<>())
                    .add(page);
        }

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<toc label=\"").append(xml(title)).append("\">\n");
        for (Map.Entry<String, Map<String, List<RenderedPage>>> language : grouped.entrySet()) {
            xml.append("  <topic label=\"").append(xml(languageLabel(language.getKey()))).append("\">\n");
            for (Map.Entry<String, List<RenderedPage>> source : language.getValue().entrySet()) {
                xml.append("    <topic label=\"").append(xml(source.getKey())).append("\">\n");
                for (RenderedPage page : source.getValue()) {
                    xml.append("      <topic label=\"").append(xml(page.title()))
                            .append("\" href=\"").append(xml(page.href())).append("\"/>\n");
                }
                xml.append("    </topic>\n");
            }
            xml.append("  </topic>\n");
        }
        return xml.append("</toc>\n").toString();
    }

    private String htmlDocument(String title, String body, URI source) {
        String sourceLink = source == null ? "" :
                "<p class=\"wikihelp-source\">Source: <a href=\"" + html(source.toString()) + "\">"
                        + html(source.toString()) + "</a></p>";
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <link rel="stylesheet" href="book.css">
                </head>
                <body>
                  <h1>%s</h1>
                  %s
                  %s
                </body>
                </html>
                """.formatted(html(title), html(title), body, sourceLink);
    }

    private String sanitizeAndResolve(String html, URI base) {
        String safe = UNSAFE_BLOCK.matcher(html == null ? "" : html).replaceAll("");
        safe = EVENT_HANDLER.matcher(safe).replaceAll("");
        Matcher matcher = URL_ATTRIBUTE.matcher(safe);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(3).trim();
            String replacement = value;
            if (base != null && !value.isBlank()
                    && !value.startsWith("#")
                    && !value.matches("(?i)^(?:https?|mailto|data):.*")) {
                replacement = base.resolve(value).toString();
            }
            if (replacement.matches("(?i)^javascript:.*")) replacement = "#";
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + "=" + matcher.group(2) + html(replacement) + matcher.group(2)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String fileBase(WikiPage page) {
        String base = page.title().toLowerCase()
                .replaceAll("[^\\p{Alnum}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "page";
        if (base.length() > 70) base = base.substring(0, 70);
        return base + "-" + hash(page.sourceId() + "\n" + page.remoteId()).substring(0, 10);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeLanguage(String language) {
        String result = language == null ? "en" : language.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return result.isBlank() ? "en" : result;
    }

    private String languageLabel(String language) {
        return switch (language) {
            case "de" -> "Deutsch";
            case "en" -> "English";
            default -> language;
        };
    }

    private String xml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String html(String value) {
        return xml(value).replace("'", "&#39;");
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    public record RenderResult(Path antFile, Path tocFile, int pageCount) {
    }

    private record Group(String language, MarkupFormat format) {
    }

    private record RenderedPage(String sourceId, String language, String title, String href) {
    }
}
