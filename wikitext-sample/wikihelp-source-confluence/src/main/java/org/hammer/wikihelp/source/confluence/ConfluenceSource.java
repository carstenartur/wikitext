package org.hammer.wikihelp.source.confluence;

import org.hammer.wikihelp.core.AttachmentRequest;
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
import java.util.stream.Collectors;

public final class ConfluenceSource implements WikiSource {
    @Override
    public String type() {
        return "confluence";
    }

    @Override
    public List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception {
        String baseUrl = configuration.required("baseUrl").replaceAll("/+$", "");
        String apiPath = configuration.get("apiPath", "/wiki/rest/api").replaceAll("/+$", "");
        Map<String, String> headers = authentication(configuration);
        int maxPages = configuration.integer("maxPages", 500);
        String cql = cql(configuration, selection);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("cql", cql);
        parameters.put("expand", "body.export_view,metadata.labels,version,space");
        parameters.put("limit", "25");

        URI next = HttpTransport.withQuery(baseUrl + apiPath + "/content/search", parameters);
        List<WikiPage> result = new ArrayList<>();

        while (next != null && result.size() < maxPages) {
            Map<String, Object> root = Json.object(transport.getJson(next, headers));
            for (Object value : Json.array(root, "results")) {
                Map<String, Object> item = Json.object(value);
                WikiPage page = page(configuration, baseUrl, item);
                if (selection.matches(page)) result.add(page);
                if (result.size() >= maxPages) break;
            }
            String link = Json.string(Json.object(root, "_links").get("next"));
            next = link == null || link.isBlank() ? null : URI.create(baseUrl).resolve(link);
        }
        return List.copyOf(result);
    }

    @Override
    public Map<String, String> attachmentHeaders(
            SourceConfiguration configuration,
            WikiPage page,
            AttachmentRequest request) {
        return authentication(configuration);
    }

    private Map<String, String> authentication(SourceConfiguration configuration) {
        String token = configuration.environment("tokenEnvironment");
        String user = configuration.environment("userEnvironment");
        if (user != null && !user.isBlank()) return HttpTransport.basicHeader(user, token);
        return HttpTransport.bearerHeader(token);
    }

    private String cql(SourceConfiguration configuration, PageSelection selection) {
        List<String> clauses = new ArrayList<>();
        clauses.add("type=page");
        String space = configuration.get("space");
        if (space != null && !space.isBlank()) clauses.add("space=" + quote(space));

        List<String> tags = configuration.list("include.tags");
        if (!tags.isEmpty()) {
            String operator = selection.tagMode() == PageSelection.TagMode.ALL ? " AND " : " OR ";
            clauses.add(tags.stream()
                    .map(tag -> "label=" + quote(tag))
                    .collect(Collectors.joining(operator, "(", ")")));
        }
        return String.join(" AND ", clauses);
    }

    private WikiPage page(
            SourceConfiguration configuration,
            String baseUrl,
            Map<String, Object> item) {
        String id = Json.string(item.get("id"));
        String title = Json.string(item.get("title"));
        String html = Json.string(
                Json.object(Json.object(item, "body"), "export_view").get("value"));
        Set<String> tags = labels(item);
        Map<String, Object> links = Json.object(item, "_links");
        String webUi = Json.string(links.get("webui"));
        Map<String, Object> version = Json.object(item, "version");

        return new WikiPage(
                configuration.id(),
                id,
                title,
                id,
                configuration.get("language", "en"),
                MarkupFormat.HTML,
                html == null ? "" : html,
                TagSupport.combine(tags, configuration.list("tags")),
                webUi == null ? URI.create(baseUrl) : URI.create(baseUrl).resolve(webUi),
                Long.toString(Json.longValue(version.get("number"), 0)));
    }

    private Set<String> labels(Map<String, Object> item) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Map<String, Object> metadata = Json.object(item, "metadata");
        Map<String, Object> labels = Json.object(metadata, "labels");
        for (Object value : Json.array(labels, "results")) {
            String name = Json.string(Json.object(value).get("name"));
            if (name != null) result.add(TagSupport.normalize(name));
        }
        return Set.copyOf(result);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
