package org.hammer.wikihelp.source.eclipsepedia;

import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.MarkupFormat;
import org.hammer.wikihelp.core.PageSelection;
import org.hammer.wikihelp.core.SourceConfiguration;
import org.hammer.wikihelp.core.SourceSupport;
import org.hammer.wikihelp.core.TagSupport;
import org.hammer.wikihelp.core.WikiPage;
import org.hammer.wikihelp.core.WikiSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the read-only Eclipsepedia HTML archive after its MediaWiki API was retired. */
public final class EclipsepediaArchiveSource implements WikiSource {
    private static final Pattern LINK =
            Pattern.compile("<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING =
            Pattern.compile("<h1\\b[^>]*id\\s*=\\s*['\"]firstHeading['\"][^>]*>(.*?)</h1>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String type() {
        return "eclipsepedia";
    }

    @Override
    public List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception {
        String baseUrl = configuration.get("baseUrl", "https://wiki.eclipse.org/").replaceAll("/+$", "") + "/";
        URI base = URI.create(baseUrl);
        int maxPages = configuration.integer("maxPages", 500);
        Map<URI, Set<String>> discovered = new LinkedHashMap<>();

        for (String page : configuration.list("include.pages")) {
            discovered.put(archiveUri(base, page), Set.of());
        }

        List<String> categories = new ArrayList<>(configuration.list("include.tags"));
        String configuredCategory = configuration.get("category");
        if (configuredCategory != null && !configuredCategory.isBlank()) categories.add(configuredCategory);

        String categoryUrl = configuration.get("categoryUrl");
        if (categoryUrl != null && !categoryUrl.isBlank()) {
            merge(discovered, categoryLinks(transport, URI.create(categoryUrl), base, null, maxPages));
        }
        for (String category : categories) {
            URI uri = archiveUri(base, "Category:" + category.replace(' ', '_'));
            merge(discovered, categoryLinks(transport, uri, base, category, maxPages));
        }

        List<WikiPage> pages = new ArrayList<>();
        for (Map.Entry<URI, Set<String>> entry : discovered.entrySet()) {
            if (pages.size() >= maxPages) break;
            URI uri = entry.getKey();
            String html = transport.get(uri, Map.of()).body();
            String title = title(html, uri);
            String content = content(html);
            WikiPage page = new WikiPage(
                    configuration.id(),
                    uri.getPath(),
                    title,
                    uri.getPath().replaceFirst("^/", ""),
                    configuration.get("language", "en"),
                    MarkupFormat.HTML,
                    content,
                    TagSupport.combine(entry.getValue(), configuration.list("tags")),
                    uri,
                    "");
            if (selection.matches(page)) pages.add(page);
        }
        return List.copyOf(pages);
    }

    private Map<URI, Set<String>> categoryLinks(
            HttpTransport transport,
            URI categoryUri,
            URI base,
            String category,
            int maxPages) throws Exception {
        String html = transport.get(categoryUri, Map.of()).body();
        String scope = section(html, "id=\"mw-pages\"", "class=\"printfooter\"");
        if (scope.isBlank()) scope = section(html, "id=\"mw-content-text\"", "class=\"printfooter\"");

        Map<URI, Set<String>> links = new LinkedHashMap<>();
        Matcher matcher = LINK.matcher(scope);
        while (matcher.find() && links.size() < maxPages) {
            URI resolved = categoryUri.resolve(matcher.group(1));
            if (!sameOrigin(base, resolved)) continue;
            String path = resolved.getPath();
            if (path == null || path.isBlank() || path.equals(categoryUri.getPath())) continue;
            if (path.contains("Special:") || path.contains("Category:") || path.contains("File:")
                    || path.contains("Help:") || path.contains("Talk:") || path.endsWith("/index.php")) continue;
            URI page = URI.create(resolved.getScheme() + "://" + resolved.getAuthority() + path);
            Set<String> tags = category == null || category.isBlank()
                    ? Set.of()
                    : Set.of(TagSupport.normalize(category));
            links.put(page, tags);
        }
        return links;
    }

    private void merge(Map<URI, Set<String>> target, Map<URI, Set<String>> additions) {
        additions.forEach((uri, tags) -> target.merge(uri, tags, TagSupport::combine));
    }

    private URI archiveUri(URI base, String page) {
        return base.resolve("./" + page.replace(' ', '_'));
    }

    private String title(String html, URI uri) {
        Matcher matcher = HEADING.matcher(html);
        if (matcher.find()) return SourceSupport.stripHtml(matcher.group(1)).trim();
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
    }

    private String content(String html) {
        String content = section(html, "id=\"mw-content-text\"", "class=\"printfooter\"");
        if (content.isBlank()) content = html;
        return content
                .replaceAll("(?is)<script\\b.*?</script>", "")
                .replaceAll("(?is)<style\\b.*?</style>", "")
                .replaceAll("(?is)<form\\b.*?</form>", "")
                .replaceAll("(?is)<div\\b[^>]*class=['\"][^'\"]*(?:mw-jump-link|toccolours)[^'\"]*['\"][^>]*>.*?</div>", "");
    }

    private String section(String html, String startMarker, String endMarker) {
        int start = html.indexOf(startMarker);
        if (start < 0) return "";
        start = html.lastIndexOf('<', start);
        int end = html.indexOf(endMarker, start);
        if (end < 0) return html.substring(start);
        end = html.lastIndexOf('<', end);
        return html.substring(start, Math.max(start, end));
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getAuthority().equalsIgnoreCase(second.getAuthority());
    }
}
