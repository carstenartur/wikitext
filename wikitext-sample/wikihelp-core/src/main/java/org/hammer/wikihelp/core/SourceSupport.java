package org.hammer.wikihelp.core;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SourceSupport {
    private static final Pattern MEDIAWIKI_TITLE = Pattern.compile("(?m)^\\s*=\\s*([^=\\n]+?)\\s*=\\s*$");
    private static final Pattern MARKDOWN_TITLE = Pattern.compile("(?m)^\\s*#\\s+(.+?)\\s*$");
    private static final Pattern ASCIIDOC_TITLE = Pattern.compile("(?m)^\\s*=\\s+(.+?)\\s*$");
    private static final Pattern HTML_TITLE = Pattern.compile(
            "<(?:title|h1)\\b[^>]*>(.*?)</(?:title|h1)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SourceSupport() {
    }

    public static String title(MarkupFormat format, String content, Path fallback) {
        Pattern pattern = switch (format) {
            case MEDIAWIKI -> MEDIAWIKI_TITLE;
            case MARKDOWN -> MARKDOWN_TITLE;
            case ASCIIDOC -> ASCIIDOC_TITLE;
            case HTML -> HTML_TITLE;
            default -> null;
        };
        if (pattern != null) {
            Matcher matcher = pattern.matcher(content == null ? "" : content);
            if (matcher.find()) {
                return stripHtml(matcher.group(1)).trim();
            }
        }
        String name = fallback.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static Set<String> tags(MarkupFormat format, String content, Iterable<String> nativeTags) {
        LinkedHashSet<String> result = new LinkedHashSet<>(TagSupport.extract(format, content));
        nativeTags.forEach(tag -> {
            String normalized = TagSupport.normalize(tag);
            if (!normalized.isBlank()) result.add(normalized);
        });
        return Set.copyOf(result);
    }

    public static Map<String, String> bearerAuthentication(SourceConfiguration configuration) {
        return HttpTransport.bearerHeader(configuration.environment("tokenEnvironment"));
    }

    public static URI pageUri(String baseUrl, String pagePath) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(base).resolve("./" + pagePath);
    }

    public static String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ");
    }
}
