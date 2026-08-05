package org.hammer.wikihelp.core;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AttachmentSupport {
    private static final Pattern HTML_ATTRIBUTE = Pattern.compile(
            "(?i)(src|poster|data|href)\\s*=\\s*(['\"])(.*?)\\2");
    private static final Pattern SRCSET_ATTRIBUTE = Pattern.compile(
            "(?i)srcset\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern CSS_URL = Pattern.compile(
            "(?i)url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile(
            "!?\\[[^\\]]*]\\((?:<)?([^\\s)>]+)(?:>)?(?:\\s+['\"][^'\"]*['\"])?\\)");
    private static final Pattern ASCIIDOC_IMAGE = Pattern.compile(
            "(?m)^\\s*(?:image::?|link:)([^\\[\\s]+)\\[");
    private static final Pattern MEDIAWIKI_FILE = Pattern.compile(
            "(?is)\\[\\[(?:File|Image)\\s*:\\s*([^|\\]]+)(?:\\|[^\\]]*)?]]");
    private static final Pattern CONTENT_DISPOSITION_FILE = Pattern.compile(
            "(?i)(?:^|;)\\s*filename\\*?=(?:UTF-8''|['\"])?([^;'\"]+)");
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            "apng", "avif", "bmp", "csv", "doc", "docx", "gif", "ico", "jpeg", "jpg",
            "json", "odg", "odp", "ods", "odt", "pdf", "png", "ppt", "pptx", "svg",
            "tar", "tif", "tiff", "txt", "webp", "xls", "xlsx", "xml", "zip");

    private AttachmentSupport() {
    }

    public static List<AttachmentRequest> discover(WikiPage page) {
        LinkedHashMap<String, AttachmentRequest> requests = new LinkedHashMap<>();
        String content = page.content() == null ? "" : page.content();

        if (page.format() == MarkupFormat.HTML) {
            Matcher attributes = HTML_ATTRIBUTE.matcher(content);
            while (attributes.find()) {
                String attribute = attributes.group(1).toLowerCase(Locale.ROOT);
                String reference = htmlDecode(attributes.group(3).trim());
                if (!"href".equals(attribute) || isAttachmentLink(reference)) {
                    add(requests, page, reference);
                }
            }
            Matcher srcsets = SRCSET_ATTRIBUTE.matcher(content);
            while (srcsets.find()) {
                for (String candidate : srcsets.group(2).split(",")) {
                    String reference = candidate.trim().split("\\s+", 2)[0];
                    add(requests, page, htmlDecode(reference));
                }
            }
            Matcher css = CSS_URL.matcher(content);
            while (css.find()) add(requests, page, htmlDecode(css.group(2).trim()));
        }

        if (page.format() == MarkupFormat.MARKDOWN) {
            Matcher links = MARKDOWN_LINK.matcher(content);
            while (links.find()) {
                String reference = links.group(1).trim();
                boolean image = links.group().startsWith("!");
                if (image || isAttachmentLink(reference)) add(requests, page, reference);
            }
        }

        if (page.format() == MarkupFormat.ASCIIDOC) {
            Matcher images = ASCIIDOC_IMAGE.matcher(content);
            while (images.find()) add(requests, page, images.group(1).trim());
        }

        if (page.format() == MarkupFormat.MEDIAWIKI
                && page.originalUri() != null
                && "file".equalsIgnoreCase(page.originalUri().getScheme())) {
            Path parent = Path.of(page.originalUri()).getParent();
            for (String fileName : mediaWikiFileNames(content)) {
                for (Path candidate : List.of(parent.resolve(fileName), parent.resolve("images").resolve(fileName))) {
                    if (Files.isRegularFile(candidate)) {
                        AttachmentRequest request = new AttachmentRequest(
                                "File:" + fileName,
                                candidate.toUri(),
                                fileName);
                        requests.putIfAbsent(key(request), request);
                        break;
                    }
                }
            }
        }
        return List.copyOf(requests.values());
    }

    public static List<String> mediaWikiFileNames(String content) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = MEDIAWIKI_FILE.matcher(content == null ? "" : content);
        while (matcher.find()) {
            String name = htmlDecode(matcher.group(1).trim());
            if (!name.isBlank()) result.add(name);
        }
        return List.copyOf(result);
    }

    public static WikiAttachment download(
            AttachmentRequest request,
            HttpTransport transport,
            Map<String, String> headers,
            int maxBytes) throws Exception {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        URI uri = request.uri();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("file".equals(scheme)) {
            Path path = Path.of(uri);
            long size = Files.size(path);
            if (size > maxBytes) {
                throw new IllegalArgumentException(
                        "Attachment exceeds limit of " + maxBytes + " bytes: " + uri);
            }
            String mediaType = Files.probeContentType(path);
            return new WikiAttachment(
                    request.reference(),
                    uri,
                    request.fileName(),
                    mediaType == null ? inferMediaType(request.fileName()) : mediaType,
                    Files.readAllBytes(path));
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported attachment URI scheme: " + uri);
        }

        HttpBinaryResponseData response = transport.getBytes(uri, headers, maxBytes);
        String contentType = response.firstHeader("Content-Type");
        if (contentType != null) contentType = contentType.replaceFirst(";.*$", "").trim();
        String fileName = contentDispositionFileName(response.firstHeader("Content-Disposition"));
        if (fileName == null || fileName.isBlank()) fileName = request.fileName();
        return new WikiAttachment(
                request.reference(),
                uri,
                fileName,
                contentType == null || contentType.isBlank() ? inferMediaType(fileName) : contentType,
                response.body());
    }

    public static boolean isLikelyBinary(Path path) {
        String extension = extension(path.getFileName().toString());
        if (ATTACHMENT_EXTENSIONS.contains(extension) && !Set.of("csv", "json", "txt", "xml").contains(extension)) {
            return true;
        }
        try {
            String type = Files.probeContentType(path);
            return type != null && (type.startsWith("image/")
                    || type.startsWith("audio/")
                    || type.startsWith("video/")
                    || type.startsWith("font/")
                    || type.equals("application/pdf")
                    || type.equals("application/zip")
                    || type.equals("application/octet-stream"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String safeFileName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        if (name.contains("\\")) name = name.substring(name.lastIndexOf('\\') + 1);
        name = htmlDecode(name);
        if (name.isBlank()) name = "attachment";
        name = name.replaceAll("[^\\p{Alnum}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (name.isBlank()) name = "attachment";
        if (name.length() > 120) {
            String extension = extension(name);
            int suffix = extension.isBlank() ? 0 : extension.length() + 1;
            name = name.substring(0, Math.max(1, 120 - suffix))
                    + (extension.isBlank() ? "" : "." + extension);
        }
        return name;
    }

    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "";
    }

    private static void add(
            Map<String, AttachmentRequest> requests,
            WikiPage page,
            String reference) {
        if (reference == null || reference.isBlank() || reference.startsWith("#")
                || reference.matches("(?i)^(?:data|javascript|mailto|tel):.*")) {
            return;
        }
        URI uri = resolve(page.originalUri(), reference);
        if (uri == null) return;
        AttachmentRequest request = new AttachmentRequest(reference, uri, fileName(uri));
        requests.putIfAbsent(key(request), request);
    }

    private static URI resolve(URI base, String reference) {
        try {
            URI candidate = URI.create(reference.replace(" ", "%20"));
            if (candidate.isAbsolute()) return candidate;
            if (base == null) return null;
            return base.resolve(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String key(AttachmentRequest request) {
        return request.reference() + "\u0000" + request.uri();
    }

    private static String fileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) return "attachment";
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Preserve the encoded form when it is malformed.
        }
        return safeFileName(name);
    }

    private static boolean isAttachmentLink(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("/download/attachments/")
                || lower.contains("/uploads/")
                || lower.contains("/attachments/")
                || lower.contains("download=")) {
            return true;
        }
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);
        return ATTACHMENT_EXTENSIONS.contains(extension(lower));
    }

    private static String inferMediaType(String fileName) {
        return switch (extension(fileName)) {
            case "apng" -> "image/apng";
            case "avif" -> "image/avif";
            case "bmp" -> "image/bmp";
            case "gif" -> "image/gif";
            case "ico" -> "image/x-icon";
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "tif", "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static String contentDispositionFileName(String header) {
        if (header == null || header.isBlank()) return null;
        Matcher matcher = CONTENT_DISPOSITION_FILE.matcher(header);
        if (!matcher.find()) return null;
        String value = matcher.group(1).trim();
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static String htmlDecode(String value) {
        return value == null ? "" : value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
