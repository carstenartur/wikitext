package org.hammer.wikihelp.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hammer.wikihelp.core.*;
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
    private static final byte[] PNG = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/media/api.php", RemoteSourceSelfTest::mediaWiki);
        server.createContext("/api/v4/projects", RemoteSourceSelfTest::gitLab);
        server.createContext("/wiki/rest/api/content/search", exchange -> json(exchange, """
                {"results":[{"id":"17","title":"Confluence Guide",
                  "body":{"export_view":{"value":"<p><img src=\\\"/wiki/download/attachments/17/confluence.png\\\"></p>"}},
                  "metadata":{"labels":{"results":[{"name":"help"}]}},
                  "version":{"number":3},"_links":{"webui":"/spaces/DOC/pages/17"}}],"_links":{}}
                """));
        server.createContext("/Category:Help", exchange -> html(exchange, """
                <html><body><div id="mw-pages"><a href="/Archive_Page">Archive Page</a></div>
                <div class="printfooter"></div></body></html>
                """));
        server.createContext("/Archive_Page", exchange -> html(exchange, """
                <html><body><h1 id="firstHeading">Archive Page</h1>
                <div id="mw-content-text"><p><img src="/archive.png"></p></div>
                <div class="printfooter"></div></body></html>
                """));
        for (String path : List.of("/media/pixel.png", "/uploads/gitlab.png",
                "/wiki/download/attachments/17/confluence.png", "/archive.png")) {
            server.createContext(path, exchange -> binary(exchange, "image/png", PNG));
        }
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            test(new MediaWikiSource(), config("mediawiki", Map.of(
                    "apiUrl", base + "/media/api.php", "include.tags", "help")), "MediaWiki Guide", MarkupFormat.MEDIAWIKI);
            test(new GitLabWikiSource(), config("gitlab", Map.of(
                    "apiBaseUrl", base + "/api/v4", "baseUrl", base,
                    "project", "group/project", "include.tags", "help")), "GitLab Guide", MarkupFormat.HTML);
            test(new ConfluenceSource(), config("confluence", Map.of(
                    "baseUrl", base, "space", "DOC", "include.tags", "help")), "Confluence Guide", MarkupFormat.HTML);
            test(new EclipsepediaArchiveSource(), config("eclipsepedia", Map.of(
                    "baseUrl", base, "include.tags", "Help")), "Archive Page", MarkupFormat.HTML);
        } finally { server.stop(0); }
    }

    private static void test(
            WikiSource source,
            SourceConfiguration config,
            String title,
            MarkupFormat expectedFormat) throws Exception {
        HttpTransport transport = new HttpTransport();
        List<WikiPage> pages = source.fetch(config, PageSelection.from(config), transport);
        pages = new AttachmentSynchronizer().synchronize(source, config, pages, transport);
        require(pages.size() == 1, source.type() + " page count " + pages.size());
        WikiPage page = pages.getFirst();
        require(title.equals(page.title()), source.type() + " title");
        require(expectedFormat == page.format(), source.type() + " format");
        require(page.tags().contains("help"), source.type() + " tag selection");
        require(page.attachments().size() == 1, source.type() + " attachments " + page.attachments());
        WikiAttachment attachment = page.attachments().getFirst();
        require(java.util.Arrays.equals(PNG, attachment.content()), source.type() + " bytes");
        require("image/png".equals(attachment.mediaType()), source.type() + " MIME " + attachment.mediaType());
    }

    private static SourceConfiguration config(String type, Map<String,String> properties) {
        return new SourceConfiguration(type, type, Path.of("."), properties);
    }

    private static void mediaWiki(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String base = "http://127.0.0.1:" + exchange.getLocalAddress().getPort();
        if (query != null && query.contains("list=categorymembers")) {
            json(exchange, "{\"query\":{\"categorymembers\":[{\"title\":\"MediaWiki Guide\"}]}}");
        } else if (query != null && query.contains("prop=imageinfo")) {
            json(exchange, "{\"query\":{\"pages\":[{\"pageid\":8,\"title\":\"File:pixel.png\",\"imageinfo\":[{\"url\":\"" + base + "/media/pixel.png\",\"mime\":\"image/png\"}]}]}}");
        } else {
            json(exchange, """
                    {"query":{"pages":[{"pageid":7,"title":"MediaWiki Guide",
                      "fullurl":"https://example.invalid/MediaWiki_Guide",
                      "categories":[{"title":"Category:Help"}],
                      "revisions":[{"revid":12,"slots":{"main":{"content":"= MediaWiki Guide =\\n\\n[[File:pixel.png]]\\n\\n[[Category:Help]]"}}}]}]}}
                    """);
        }
    }

    private static void gitLab(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        if (path.endsWith("/wikis/guide")) {
            json(exchange, "{\"title\":\"GitLab Guide\",\"slug\":\"guide\",\"format\":\"markdown\",\"content\":\"<p><img src=\\\"/uploads/gitlab.png\\\"></p>\",\"version\":\"4\"}");
        } else {
            json(exchange, "[{\"title\":\"GitLab Guide\",\"slug\":\"guide\",\"format\":\"markdown\",\"content\":\"---\\ntags: [help]\\n---\\n# GitLab Guide\"}]");
        }
    }

    private static void json(HttpExchange exchange, String body) throws IOException { respond(exchange, "application/json", body.getBytes(StandardCharsets.UTF_8)); }
    private static void html(HttpExchange exchange, String body) throws IOException { respond(exchange, "text/html; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8)); }
    private static void binary(HttpExchange exchange, String type, byte[] bytes) throws IOException { respond(exchange, type, bytes); }
    private static void respond(HttpExchange exchange, String contentType, byte[] bytes) throws IOException { exchange.getResponseHeaders().set("Content-Type", contentType); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
