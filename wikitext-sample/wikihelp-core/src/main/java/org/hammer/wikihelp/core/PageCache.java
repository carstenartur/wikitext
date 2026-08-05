package org.hammer.wikihelp.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PageCache {
    private static final String HEADER = "# wikihelp-cache-v1";

    public boolean exists(Path root, String sourceId) {
        return Files.isRegularFile(root.resolve(sourceId).resolve("manifest.tsv"));
    }

    public void store(Path root, String sourceId, List<WikiPage> pages) throws IOException {
        Path sourceDirectory = root.resolve(sourceId);
        deleteRecursively(sourceDirectory);
        Path pageDirectory = sourceDirectory.resolve("pages");
        Files.createDirectories(pageDirectory);

        List<WikiPage> sorted = pages.stream()
                .sorted(Comparator.comparing(WikiPage::title)
                        .thenComparing(WikiPage::path)
                        .thenComparing(WikiPage::remoteId))
                .toList();

        List<String> manifest = new ArrayList<>();
        manifest.add(HEADER);
        for (WikiPage page : sorted) {
            String fileName = fileName(page);
            Files.writeString(pageDirectory.resolve(fileName), page.content(), StandardCharsets.UTF_8);
            manifest.add(String.join("\t",
                    encode(fileName),
                    encode(page.sourceId()),
                    encode(page.remoteId()),
                    encode(page.title()),
                    encode(page.path()),
                    encode(page.language()),
                    encode(page.format().name()),
                    encode(page.tags().stream().sorted().collect(java.util.stream.Collectors.joining("\u001f"))),
                    encode(page.originalUri() == null ? "" : page.originalUri().toString()),
                    encode(page.revision())));
        }
        Files.write(sourceDirectory.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8);
    }

    public List<WikiPage> load(Path root, String sourceId) throws IOException {
        Path sourceDirectory = root.resolve(sourceId);
        List<String> lines = Files.readAllLines(sourceDirectory.resolve("manifest.tsv"), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
            throw new IOException("Unsupported or corrupt WikiHelp cache for " + sourceId);
        }

        List<WikiPage> pages = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length != 10) {
                throw new IOException("Invalid cache manifest line " + (index + 1) + " for " + sourceId);
            }
            String fileName = decode(columns[0]);
            String content = Files.readString(sourceDirectory.resolve("pages").resolve(fileName), StandardCharsets.UTF_8);
            String uri = decode(columns[8]);
            Set<String> tags = new LinkedHashSet<>();
            String encodedTags = decode(columns[7]);
            if (!encodedTags.isBlank()) {
                for (String tag : encodedTags.split("\u001f")) tags.add(tag);
            }
            pages.add(new WikiPage(
                    decode(columns[1]),
                    decode(columns[2]),
                    decode(columns[3]),
                    decode(columns[4]),
                    decode(columns[5]),
                    MarkupFormat.valueOf(decode(columns[6])),
                    content,
                    tags,
                    uri.isBlank() ? null : URI.create(uri),
                    decode(columns[9])));
        }
        return List.copyOf(pages);
    }

    private static String fileName(WikiPage page) {
        String base = page.title().toLowerCase()
                .replaceAll("[^\\p{Alnum}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "page";
        if (base.length() > 70) base = base.substring(0, 70);
        return base + "-" + sha256(page.remoteId()).substring(0, 10) + "." + page.format().extension();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
