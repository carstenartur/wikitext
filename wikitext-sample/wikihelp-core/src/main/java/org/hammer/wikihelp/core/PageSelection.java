package org.hammer.wikihelp.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PageSelection(
        Set<String> includeTags,
        Set<String> excludeTags,
        List<String> includeTitleGlobs,
        List<String> excludeTitleGlobs,
        List<String> includePathGlobs,
        List<String> excludePathGlobs,
        Set<String> includePages,
        TagMode tagMode) {

    public enum TagMode { ANY, ALL }

    public PageSelection {
        includeTags = normalized(includeTags);
        excludeTags = normalized(excludeTags);
        includeTitleGlobs = List.copyOf(includeTitleGlobs);
        excludeTitleGlobs = List.copyOf(excludeTitleGlobs);
        includePathGlobs = List.copyOf(includePathGlobs);
        excludePathGlobs = List.copyOf(excludePathGlobs);
        includePages = normalized(includePages);
        tagMode = tagMode == null ? TagMode.ANY : tagMode;
    }

    public static PageSelection from(SourceConfiguration config) {
        TagMode mode = "all".equalsIgnoreCase(config.get("tags.mode", "any")) ? TagMode.ALL : TagMode.ANY;
        return new PageSelection(
                new LinkedHashSet<>(config.list("include.tags")),
                new LinkedHashSet<>(config.list("exclude.tags")),
                config.list("include.titleGlobs"),
                config.list("exclude.titleGlobs"),
                config.list("include.pathGlobs"),
                config.list("exclude.pathGlobs"),
                new LinkedHashSet<>(config.list("include.pages")),
                mode);
    }

    public boolean matches(WikiPage page) {
        Set<String> pageTags = normalized(page.tags());

        if (!includeTags.isEmpty()) {
            boolean tagMatch = tagMode == TagMode.ALL
                    ? pageTags.containsAll(includeTags)
                    : includeTags.stream().anyMatch(pageTags::contains);
            if (!tagMatch) return false;
        }

        if (excludeTags.stream().anyMatch(pageTags::contains)) return false;
        if (!matchesAnyOrEmpty(includeTitleGlobs, page.title())) return false;
        if (matchesAny(excludeTitleGlobs, page.title())) return false;
        if (!matchesAnyOrEmpty(includePathGlobs, page.path())) return false;
        if (matchesAny(excludePathGlobs, page.path())) return false;

        if (!includePages.isEmpty()) {
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(normalize(page.remoteId()));
            candidates.add(normalize(page.title()));
            candidates.add(normalize(page.path()));
            if (includePages.stream().noneMatch(candidates::contains)) return false;
        }
        return true;
    }

    private static boolean matchesAnyOrEmpty(List<String> globs, String value) {
        return globs.isEmpty() || matchesAny(globs, value);
    }

    private static boolean matchesAny(List<String> globs, String value) {
        return globs.stream().anyMatch(glob -> Glob.matches(glob, value));
    }

    private static Set<String> normalized(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(normalize(value));
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
