package org.hammer.wikihelp.cli;

import org.hammer.wikihelp.core.HttpTransport;
import org.hammer.wikihelp.core.PageCache;
import org.hammer.wikihelp.core.PageSelection;
import org.hammer.wikihelp.core.SourceConfiguration;
import org.hammer.wikihelp.core.SourceRegistry;
import org.hammer.wikihelp.core.WikiHelpConfiguration;
import org.hammer.wikihelp.core.WikiPage;
import org.hammer.wikihelp.core.WikiSource;
import org.hammer.wikihelp.renderer.eclipsehelp.EclipseHelpRenderer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WikiHelpCli {
    private WikiHelpCli() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        Path configFile = path(options, "config");
        Path cacheDirectory = path(options, "cache");
        Path outputDirectory = path(options, "output");
        Path antFile = path(options, "ant");
        Path tocFile = path(options, "toc");
        Path cssFile = path(options, "css");

        WikiHelpConfiguration configuration = WikiHelpConfiguration.load(configFile);
        String configuredMode = options.get("mode");
        if (configuredMode == null || configuredMode.isBlank()) {
            configuredMode = System.getProperty("wikihelp.mode");
        }
        WikiHelpConfiguration.Mode mode = configuredMode == null || configuredMode.isBlank()
                ? configuration.mode()
                : WikiHelpConfiguration.Mode.valueOf(configuredMode.toUpperCase());

        SourceRegistry registry = SourceRegistry.load();
        HttpTransport transport = new HttpTransport();
        PageCache cache = new PageCache();
        List<WikiPage> pages = new ArrayList<>();

        for (SourceConfiguration sourceConfiguration : configuration.sources()) {
            WikiSource source = registry.require(sourceConfiguration.type());
            PageSelection selection = PageSelection.from(sourceConfiguration);
            List<WikiPage> sourcePages;

            if (!source.remote()) {
                sourcePages = source.fetch(sourceConfiguration, selection, transport);
                cache.store(cacheDirectory, sourceConfiguration.id(), sourcePages);
                System.out.printf("WikiHelp: %s loaded %d local page(s)%n",
                        sourceConfiguration.id(), sourcePages.size());
            } else if (mode == WikiHelpConfiguration.Mode.ONLINE) {
                sourcePages = source.fetch(sourceConfiguration, selection, transport);
                cache.store(cacheDirectory, sourceConfiguration.id(), sourcePages);
                System.out.printf("WikiHelp: %s downloaded %d page(s)%n",
                        sourceConfiguration.id(), sourcePages.size());
            } else if (cache.exists(cacheDirectory, sourceConfiguration.id())) {
                sourcePages = cache.load(cacheDirectory, sourceConfiguration.id()).stream()
                        .filter(selection::matches)
                        .toList();
                System.out.printf("WikiHelp: %s used %d cached page(s)%n",
                        sourceConfiguration.id(), sourcePages.size());
            } else if (mode == WikiHelpConfiguration.Mode.CACHED) {
                sourcePages = source.fetch(sourceConfiguration, selection, transport);
                cache.store(cacheDirectory, sourceConfiguration.id(), sourcePages);
                System.out.printf("WikiHelp: %s populated cache with %d page(s)%n",
                        sourceConfiguration.id(), sourcePages.size());
            } else {
                throw new IllegalStateException(
                        "No cache for remote source '" + sourceConfiguration.id()
                                + "' while wikihelp.mode=offline. Run with --mode online once.");
            }
            pages.addAll(sourcePages);
        }

        if (pages.isEmpty()) {
            throw new IllegalStateException("No pages matched the configured selectors");
        }

        EclipseHelpRenderer.RenderResult result = new EclipseHelpRenderer().prepare(
                configuration.title(), pages, outputDirectory, antFile, tocFile, cssFile);
        System.out.printf("WikiHelp: prepared Eclipse Help for %d page(s)%n", result.pageCount());
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--") || index + 1 >= args.length) {
                throw usage("Expected --name value but got " + argument);
            }
            result.put(argument.substring(2), args[++index]);
        }
        for (String required : List.of("config", "cache", "output", "ant", "toc", "css")) {
            if (!result.containsKey(required)) throw usage("Missing --" + required);
        }
        return result;
    }

    private static Path path(Map<String, String> options, String name) {
        return Path.of(options.get(name)).toAbsolutePath().normalize();
    }

    private static IllegalArgumentException usage(String message) {
        return new IllegalArgumentException(message + System.lineSeparator()
                + "Usage: WikiHelpCli --config FILE --cache DIR --output DIR "
                + "--ant FILE --toc FILE --css FILE [--mode offline|cached|online]");
    }
}
