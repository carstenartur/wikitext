package org.hammer.wikihelp.source.mediawiki;

import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.Json;
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

public final class MediaWikiSource implements WikiSource {
    @Override
    public String type() {
        return "mediawiki";
    }

    @Override
    public List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception {
        String apiUrl = configuration.required("apiUrl");
        Map<String, String> headers = SourceSupport.bearerAuthentication(configuration);
        int maxPages = configuration.integer("maxPages", 500);
        LinkedHashSet<String> titles = discoverTitles(configuration, selection, transport, apiUrl, headers, maxPages);
        List<WikiPage> pages = new ArrayList<>();
        boolean rendered = "rendered".equalsIgnoreCase(configuration.get("contentMode", "source"));

        for (String title : titles) {
            if (pages.size() >= maxPages) break;
            WikiPage page = rendered
                    ? fetchRendered(configuration, transport, apiUrl, headers, title)
                    : fetchSource(configuration, transport, apiUrl, headers, title);
            if (page != null && selection.matches(page)) pages.add(page);
        }
        return List.copyOf(pages);
    }

    private LinkedHashSet<String> discoverTitles(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport,
            String apiUrl,
            Map<String, String> headers,
            int maxPages) throws Exception {
        List<String> explicit = configuration.list("include.pages");
        List<String> tags = configuration.list("include.tags");

        LinkedHashSet<String> result = new LinkedHashSet<>(explicit);
        if (!tags.isEmpty()) {
            List<Set<String>> categorySets = new ArrayList<>();
            for (String tag : tags) {
                categorySets.add(categoryMembers(transport, apiUrl, headers, tag, maxPages));
            }
            if (selection.tagMode() == PageSelection.TagMode.ALL && !categorySets.isEmpty()) {
                LinkedHashSet<String> intersection = new LinkedHashSet<>(categorySets.getFirst());
                categorySets.stream().skip(1).forEach(intersection::retainAll);
                result.addAll(intersection);
            } else {
                categorySets.forEach(result::addAll);
            }
        }

        if (result.isEmpty()) {
            result.addAll(allPages(transport, apiUrl, headers, configuration.get("namespace", "0"), maxPages));
        }
        return result;
    }

    private Set<String> categoryMembers(
            HttpTransport transport,
            String apiUrl,
            Map<String, String> headers,
            String tag,
            int maxPages) throws Exception {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        String continuation = null;
        String category = tag.regionMatches(true, 0, "Category:", 0, 9) ? tag : "Category:" + tag;

        do {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("action", "query");
            query.put("format", "json");
            query.put("formatversion", "2");
            query.put("list", "categorymembers");
            query.put("cmtitle", category);
            query.put("cmtype", "page");
            query.put("cmlimit", "max");
            if (continuation != null) query.put("cmcontinue", continuation);

            Map<String, Object> root = Json.object(transport.getJson(HttpTransport.withQuery(apiUrl, query), headers));
            Map<String, Object> result = Json.object(root, "query");
            for (Object item : Json.array(result, "categorymembers")) {
                titles.add(Json.string(Json.object(item).get("title")));
                if (titles.size() >= maxPages) return titles;
            }
            continuation = Json.string(Json.object(root, "continue").get("cmcontinue"));
        } while (continuation != null && !continuation.isBlank());
        return titles;
    }

    private Set<String> allPages(
            HttpTransport transport,
            String apiUrl,
            Map<String, String> headers,
            String namespace,
            int maxPages) throws Exception {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        String continuation = null;
        do {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("action", "query");
            query.put("format", "json");
            query.put("formatversion", "2");
            query.put("list", "allpages");
            query.put("apnamespace", namespace);
            query.put("aplimit", "max");
            if (continuation != null) query.put("apcontinue", continuation);

            Map<String, Object> root = Json.object(transport.getJson(HttpTransport.withQuery(apiUrl, query), headers));
            for (Object item : Json.array(Json.object(root, "query"), "allpages")) {
                titles.add(Json.string(Json.object(item).get("title")));
                if (titles.size() >= maxPages) return titles;
            }
            continuation = Json.string(Json.object(root, "continue").get("apcontinue"));
        } while (continuation != null && !continuation.isBlank());
        return titles;
    }

    private WikiPage fetchSource(
            SourceConfiguration configuration,
            HttpTransport transport,
            String apiUrl,
            Map<String, String> headers,
            String title) throws Exception {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("action", "query");
        query.put("format", "json");
        query.put("formatversion", "2");
        query.put("redirects", "1");
        query.put("prop", "revisions|categories|info");
        query.put("inprop", "url");
        query.put("rvprop", "ids|timestamp|content");
        query.put("rvslots", "main");
        query.put("cllimit", "max");
        query.put("titles", title);

        Map<String, Object> root = Json.object(transport.getJson(HttpTransport.withQuery(apiUrl, query), headers));
        List<Object> pages = Json.array(Json.object(root, "query"), "pages");
        if (pages.isEmpty()) return null;
        Map<String, Object> page = Json.object(pages.getFirst());
        if (Boolean.TRUE.equals(page.get("missing"))) return null;

        Set<String> tags = categories(page);
        List<Object> revisions = Json.array(page, "revisions");
        if (revisions.isEmpty()) return null;
        Map<String, Object> revision = Json.object(revisions.getFirst());
        Map<String, Object> slots = Json.object(revision, "slots");
        Map<String, Object> main = Json.object(slots, "main");
        String content = Json.string(main.get("content"));
        if (content == null) content = Json.string(main.get("*"));
        if (content == null) content = "";

        String pageTitle = Json.string(page.get("title"));
        return new WikiPage(
                configuration.id(),
                Long.toString(Json.longValue(page.get("pageid"), pageTitle.hashCode())),
                pageTitle,
                pageTitle.replace(' ', '_'),
                configuration.get("language", "en"),
                MarkupFormat.MEDIAWIKI,
                content,
                TagSupport.combine(SourceSupport.tags(MarkupFormat.MEDIAWIKI, content, tags), tags),
                pageUri(configuration, apiUrl, pageTitle, Json.string(page.get("fullurl"))),
                Long.toString(Json.longValue(revision.get("revid"), 0)));
    }

    private WikiPage fetchRendered(
            SourceConfiguration configuration,
            HttpTransport transport,
            String apiUrl,
            Map<String, String> headers,
            String title) throws Exception {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("action", "parse");
        query.put("format", "json");
        query.put("formatversion", "2");
        query.put("page", title);
        query.put("prop", "text|displaytitle|categories|revid");

        Map<String, Object> root = Json.object(transport.getJson(HttpTransport.withQuery(apiUrl, query), headers));
        Map<String, Object> parsed = Json.object(root, "parse");
        String content = Json.string(parsed.get("text"));
        String displayTitle = SourceSupport.stripHtml(Json.string(parsed.get("displaytitle")));
        if (displayTitle.isBlank()) displayTitle = title;

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Object item : Json.array(parsed, "categories")) {
            Map<String, Object> category = Json.object(item);
            String value = Json.string(category.get("category"));
            if (value == null) value = Json.string(category.get("*"));
            if (value != null) tags.add(TagSupport.normalize(value));
        }

        return new WikiPage(
                configuration.id(),
                title,
                displayTitle,
                title.replace(' ', '_'),
                configuration.get("language", "en"),
                MarkupFormat.HTML,
                content == null ? "" : content,
                tags,
                pageUri(configuration, apiUrl, title, null),
                Long.toString(Json.longValue(parsed.get("revid"), 0)));
    }

    private Set<String> categories(Map<String, Object> page) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : Json.array(page, "categories")) {
            String title = Json.string(Json.object(item).get("title"));
            if (title != null) {
                result.add(TagSupport.normalize(title.replaceFirst("(?i)^Category:", "")));
            }
        }
        return Set.copyOf(result);
    }

    private URI pageUri(
            SourceConfiguration configuration,
            String apiUrl,
            String title,
            String fullUrl) {
        if (fullUrl != null && !fullUrl.isBlank()) return URI.create(fullUrl);
        String base = configuration.get("pageBaseUrl");
        if (base == null || base.isBlank()) {
            URI api = URI.create(apiUrl);
            String path = api.getPath().replaceFirst("/api\\.php$", "/wiki/");
            base = api.getScheme() + "://" + api.getAuthority() + path;
        }
        return SourceSupport.pageUri(base, title.replace(' ', '_'));
    }
}
