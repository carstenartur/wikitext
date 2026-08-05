package org.hammer.wikihelp.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.MarkupFormat;
import org.hammer.wikihelp.core.PageSelection;
import org.hammer.wikihelp.core.SourceConfiguration;
import org.hammer.wikihelp.core.WikiPage;
import org.hammer.wikihelp.core.WikiSource;
import org.hammer.wikihelp.source.confluence.ConfluenceSource;
import org.hammer.wikihelp.source.eclipsepedia.EclipsepediaArchiveSource;
import org.hammer.wikihelp.source.gitlab.GitLabWikiSource;
import org.hammer.wikihelp.source.mediawiki.MediaWikiSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RemoteSourceSelfTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/media/api.php", RemoteSourceSelfTest::mediaWiki);
        server.createContext("/api/v4/projects", RemoteSourceSelfTest::gitLab);
        server.createContext("/wiki/rest/api/content/search", exchange -> json(exchange, """
                {"results":[{"id":"17","title":"Confluence Guide",
                  "body":{"export_view":{"value":"<p>Confluence help</p>"}},
                  "metadata":{"labels":{"results":[{"name":"help"}]}},
                  "version":{"number":3},"_links":{"webui":"/spaces/DOC/pages/17"}}],
                 "_links":{}}
                """));
        server.createContext("/Category:Help", exchange -> html(exchange, """
                <html><body><div id="mw-pages"><a href="/Archive_Page">Archive Page</a></div>
                <div class="printfooter"></div></body></html>
                """));
        server.createContext("/Archive_Page", exchange -> html(exchange, """
                <html><body><h1 id="firstHeading">Archive Page</h1>
                <div id="mw-content-text"><p>Archived help content</p></div>
                <div class="printfooter"></div></body></html>
                """));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            test(new MediaWikiSource(), config("mediawiki", Map.of(
                    "apiUrl", base + "/media/api.php",
                    "include.tags", "help")), "MediaWiki Guide", MarkupFormat.MEDIAWIKI);
            test(new GitLabWikiSource(), config("gitlab", Map.of(
                    "apiBaseUrl", base + "/api/v4",
                    "baseUrl", base,
                    "project", "group/project",
                    "include.tags", "help")), "GitLab Guide", MarkupFormat.HTML);
            test(new ConfluenceSource(), config("confluence", Map.of(
                    "baseUrl", base,
                    "space", "DOC",
                    "include.tags", "help")), "Confluence Guide", MarkupFormat.HTML);
            test(new EclipsepediaArchiveSource(), config("eclipsepedia", Map.of(
                    "baseUrl", base,
                    "include.tags", "Help")), "Archive Page", MarkupFormat.HTML);
        } finally {
            server.stop(0);
        }
    }

    private static SourceConfiguration config(String type, Map<String, String> properties) {
        return new SourceConfiguration(type, type, Path.of("."), properties);
    }

    private static void test(
            WikiSource source,
            SourceConfiguration configuration,
            String expectedTitle,
            MarkupFormat expectedFormat) throws Exception {
        List<WikiPage> pages = source.fetch(configuration, PageSelection.from(configuration), new HttpTransport());
        require(pages.size() == 1, source.type() + " should return one selected page, got " + pages.size());
        WikiPage page = pages.getFirst();
        require(expectedTitle.equals(page.title()), source.type() + " title");
        require(expectedFormat == page.format(), source.type() + " format");
        require(page.tags().contains("help"), source.type() + " tag selection");
    }

    private static void mediaWiki(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null && query.contains("list=categorymembers")) {
            json(exchange, "{\"query\":{\"categorymembers\":[{\"title\":\"MediaWiki Guide\"}]}} ");
        } else {
            json(exchange, """
                    {"query":{"pages":[{"pageid":7,"title":"MediaWiki Guide",
                      "fullurl":"https://example.invalid/MediaWiki_Guide",
                      "categories":[{"title":"Category:Help"}],
                      "revisions":[{"revid":12,"slots":{"main":{"content":"= MediaWiki Guide =\\n\\n[[Category:Help]]"}}}]}]}}
                    """);
        }
    }

    private static void gitLab(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        if (path.endsWith("/wikis/guide")) {
            json(exchange, "{\"title\":\"GitLab Guide\",\"slug\":\"guide\",\"format\":\"markdown\",\"content\":\"<p>GitLab help</p>\",\"version\":\"4\"}");
        } else {
            json(exchange, "[{\"title\":\"GitLab Guide\",\"slug\":\"guide\",\"format\":\"markdown\",\"content\":\"---\\ntags: [help]\\n---\\n# GitLab Guide\"}]");
        }
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        respond(exchange, "application/json", body);
    }

    private static void html(HttpExchange exchange, String body) throws IOException {
        respond(exchange, "text/html; charset=UTF-8", body);
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
