package org.hammer.wikihelp.core;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagSupport {
    private static final Pattern MEDIAWIKI_CATEGORY =
            Pattern.compile("\\[\\[\\s*Category\\s*:\\s*([^|\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_META =
            Pattern.compile("<meta\\s+[^>]*name\\s*=\\s*['\"]wikihelp-tags['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern FRONT_MATTER =
            Pattern.compile("(?m)^\\s*(?:wikihelp-)?tags\\s*:\\s*(.+)$");

    private TagSupport() {
    }

    public static Set<String> extract(MarkupFormat format, String content) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (content == null || content.isBlank()) return result;

        if (format == MarkupFormat.MEDIAWIKI) {
            Matcher matcher = MEDIAWIKI_CATEGORY.matcher(content);
            while (matcher.find()) add(result, matcher.group(1));
        }

        Matcher frontMatter = FRONT_MATTER.matcher(content);
        while (frontMatter.find()) {
            String value = frontMatter.group(1).trim();
            if (value.startsWith("[") && value.endsWith("]")) {
                value = value.substring(1, value.length() - 1);
            }
            for (String tag : value.split(",")) add(result, tag.replace("\"", "").replace("'", ""));
        }

        if (format == MarkupFormat.HTML) {
            Matcher htmlMeta = HTML_META.matcher(content);
            while (htmlMeta.find()) {
                for (String tag : htmlMeta.group(1).split(",")) add(result, tag);
            }
        }
        return Set.copyOf(result);
    }

    public static Set<String> combine(Set<String> first, Iterable<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        first.forEach(value -> add(result, value));
        second.forEach(value -> add(result, value));
        return Set.copyOf(result);
    }

    public static String normalize(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    private static void add(Set<String> result, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) result.add(normalized);
    }
}
