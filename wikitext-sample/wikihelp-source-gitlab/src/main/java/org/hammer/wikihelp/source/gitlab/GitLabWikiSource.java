package org.hammer.wikihelp.source.gitlab;

import org.hammer.wikihelp.core.HttpResponseData;
import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.Json;
import org.hammer.wikihelp.core.MarkupFormat;
import org.hammer.wikihelp.core.PageSelection;
import org.hammer.wikihelp.core.SourceConfiguration;
import org.hammer.wikihelp.core.SourceSupport;
import org.hammer.wikihelp.core.WikiPage;
import org.hammer.wikihelp.core.WikiSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GitLabWikiSource implements WikiSource {
    @Override
    public String type() {
        return "gitlab";
    }

    @Override
    public List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception {
        String baseUrl = configuration.get("baseUrl", "https://gitlab.com").replaceAll("/+$", "");
        String apiBase = configuration.get("apiBaseUrl", baseUrl + "/api/v4").replaceAll("/+$", "");
        String project = configuration.required("project");
        String projectId = HttpTransport.encodePathSegment(project);
        String token = configuration.environment("tokenEnvironment");
        Map<String, String> headers = token == null || token.isBlank()
                ? Map.of()
                : Map.of("PRIVATE-TOKEN", token);
        int maxPages = configuration.integer("maxPages", 500);
        boolean rendered = !"source".equalsIgnoreCase(configuration.get("contentMode", "rendered"));

        List<WikiPage> result = new ArrayList<>();
        for (int pageNumber = 1; result.size() < maxPages; pageNumber++) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("with_content", "1");
            query.put("per_page", "100");
            query.put("page", pageNumber);
            URI listUri = HttpTransport.withQuery(
                    apiBase + "/projects/" + projectId + "/wikis", query);
            HttpResponseData response = transport.get(listUri, headers);
            List<Object> entries = Json.array(Json.parse(response.body()));
            if (entries.isEmpty()) break;

            for (Object value : entries) {
                Map<String, Object> item = Json.object(value);
                String title = Json.string(item.get("title"));
                String slug = Json.string(item.get("slug"));
                String sourceContent = Json.string(item.get("content"));
                MarkupFormat sourceFormat = MarkupFormat.fromName(Json.string(item.get("format")));

                WikiPage sourcePage = new WikiPage(
                        configuration.id(),
                        slug,
                        title,
                        slug,
                        configuration.get("language", "en"),
                        sourceFormat,
                        sourceContent,
                        SourceSupport.tags(sourceFormat, sourceContent, configuration.list("tags")),
                        URI.create(baseUrl + "/" + project + "/-/wikis/" + slug),
                        "");
                if (!selection.matches(sourcePage)) continue;

                WikiPage page = rendered
                        ? renderedPage(configuration, transport, headers, apiBase, projectId, sourcePage)
                        : sourcePage;
                result.add(page);
                if (result.size() >= maxPages) break;
            }
            if (entries.size() < 100) break;
        }
        return List.copyOf(result);
    }

    private WikiPage renderedPage(
            SourceConfiguration configuration,
            HttpTransport transport,
            Map<String, String> headers,
            String apiBase,
            String projectId,
            WikiPage sourcePage) throws Exception {
        Map<String, Object> query = Map.of("render_html", "true");
        URI uri = HttpTransport.withQuery(
                apiBase + "/projects/" + projectId + "/wikis/"
                        + HttpTransport.encodePathSegment(sourcePage.remoteId()),
                query);
        Map<String, Object> item = Json.object(transport.getJson(uri, headers));
        String content = Json.string(item.get("content"));
        return new WikiPage(
                sourcePage.sourceId(),
                sourcePage.remoteId(),
                sourcePage.title(),
                sourcePage.path(),
                sourcePage.language(),
                MarkupFormat.HTML,
                content == null ? "" : content,
                sourcePage.tags(),
                sourcePage.originalUri(),
                Json.string(item.get("version")));
    }
}
