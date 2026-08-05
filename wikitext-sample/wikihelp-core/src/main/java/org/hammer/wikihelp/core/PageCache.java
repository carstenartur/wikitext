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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PageCache {
    private static final String HEADER_V1 = "# wikihelp-cache-v1";
    private static final String HEADER_V2 = "# wikihelp-cache-v2";
    private static final String ATTACHMENT_HEADER = "# wikihelp-attachments-v1";

    public boolean exists(Path root, String sourceId) {
        return Files.isRegularFile(root.resolve(sourceId).resolve("manifest.tsv"));
    }

    public void store(Path root, String sourceId, List<WikiPage> pages) throws IOException {
        Path sourceDirectory = root.resolve(sourceId);
        deleteRecursively(sourceDirectory);
        Path pageDirectory = sourceDirectory.resolve("pages");
        Path assetDirectory = sourceDirectory.resolve("assets");
        Files.createDirectories(pageDirectory);
        Files.createDirectories(assetDirectory);

        List<WikiPage> sorted = pages.stream()
                .sorted(Comparator.comparing(WikiPage::title)
                        .thenComparing(WikiPage::path)
                        .thenComparing(WikiPage::remoteId))
                .toList();

        List<String> manifest = new ArrayList<>();
        List<String> attachments = new ArrayList<>();
        Map<String, String> assetNames = new LinkedHashMap<>();
        manifest.add(HEADER_V2);
        attachments.add(ATTACHMENT_HEADER);

        for (WikiPage page : sorted) {
            String pageFileName = fileName(page);
            Files.writeString(pageDirectory.resolve(pageFileName), page.content(), StandardCharsets.UTF_8);
            manifest.add(String.join("\t",
                    encode(pageFileName),
                    encode(page.sourceId()),
                    encode(page.remoteId()),
                    encode(page.title()),
                    encode(page.path()),
                    encode(page.language()),
                    encode(page.format().name()),
                    encode(page.tags().stream().sorted()
                            .collect(java.util.stream.Collectors.joining("\u001f"))),
                    encode(page.originalUri() == null ? "" : page.originalUri().toString()),
                    encode(page.revision())));

            for (WikiAttachment attachment : page.attachments().stream()
                    .sorted(Comparator.comparing(WikiAttachment::reference)
                            .thenComparing(item -> item.originalUri().toString())
                            .thenComparing(WikiAttachment::fileName))
                    .toList()) {
                String assetFileName = assetNames.computeIfAbsent(
                        attachment.sha256(), ignored -> assetFileName(attachment));
                Path target = secureResolve(assetDirectory, assetFileName);
                if (!Files.exists(target)) Files.write(target, attachment.content());
                attachments.add(String.join("\t",
                        encode(pageFileName),
                        encode(attachment.reference()),
                        encode(attachment.originalUri().toString()),
                        encode(attachment.fileName()),
                        encode(attachment.mediaType()),
                        encode(attachment.sha256()),
                        encode(assetFileName)));
            }
        }
        Files.write(sourceDirectory.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8);
        Files.write(sourceDirectory.resolve("attachments.tsv"), attachments, StandardCharsets.UTF_8);
    }

    public List<WikiPage> load(Path root, String sourceId) throws IOException {
        Path sourceDirectory = root.resolve(sourceId);
        List<String> lines = Files.readAllLines(
                sourceDirectory.resolve("manifest.tsv"), StandardCharsets.UTF_8);
        if (lines.isEmpty() || (!HEADER_V1.equals(lines.getFirst()) && !HEADER_V2.equals(lines.getFirst()))) {
            throw new IOException("Unsupported or corrupt WikiHelp cache for " + sourceId);
        }
        boolean versionTwo = HEADER_V2.equals(lines.getFirst());
        Map<String, List<WikiAttachment>> attachments = versionTwo
                ? loadAttachments(sourceDirectory)
                : Map.of();

        List<WikiPage> pages = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length != 10) {
                throw new IOException(
                        "Invalid cache manifest line " + (index + 1) + " for " + sourceId);
            }
            String pageFileName = decode(columns[0]);
            String content = Files.readString(
                    secureResolve(sourceDirectory.resolve("pages"), pageFileName),
                    StandardCharsets.UTF_8);
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
                    decode(columns[9]),
                    attachments.getOrDefault(pageFileName, List.of())));
        }
        return List.copyOf(pages);
    }

    private Map<String, List<WikiAttachment>> loadAttachments(Path sourceDirectory) throws IOException {
        Path manifest = sourceDirectory.resolve("attachments.tsv");
        if (!Files.isRegularFile(manifest)) return Map.of();

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !ATTACHMENT_HEADER.equals(lines.getFirst())) {
            throw new IOException("Unsupported or corrupt WikiHelp attachment cache");
        }

        Map<String, List<WikiAttachment>> result = new LinkedHashMap<>();
        Path assets = sourceDirectory.resolve("assets");
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length != 7) {
                throw new IOException("Invalid attachment manifest line " + (index + 1));
            }
            String pageFileName = decode(columns[0]);
            String checksum = decode(columns[5]);
            byte[] content = Files.readAllBytes(secureResolve(assets, decode(columns[6])));
            WikiAttachment attachment = new WikiAttachment(
                    decode(columns[1]),
                    URI.create(decode(columns[2])),
                    decode(columns[3]),
                    decode(columns[4]),
                    content);
            if (!checksum.equals(attachment.sha256())) {
                throw new IOException("Attachment checksum mismatch for " + attachment.originalUri());
            }
            result.computeIfAbsent(pageFileName, ignored -> new ArrayList<>()).add(attachment);
        }
        result.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(result);
    }

    private static String fileName(WikiPage page) {
        String base = page.title().toLowerCase()
                .replaceAll("[^\\p{Alnum}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "page";
        if (base.length() > 70) base = base.substring(0, 70);
        return base + "-" + sha256(page.remoteId()).substring(0, 10)
                + "." + page.format().extension();
    }

    private static String assetFileName(WikiAttachment attachment) {
        String extension = AttachmentSupport.extension(attachment.fileName());
        return attachment.sha256() + (extension.isBlank() ? "" : "." + extension);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
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

    private static Path secureResolve(Path directory, String fileName) throws IOException {
        Path base = directory.toAbsolutePath().normalize();
        Path result = base.resolve(fileName).normalize();
        if (!result.startsWith(base)) {
            throw new IOException("Cache path escapes its directory: " + fileName);
        }
        return result;
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
