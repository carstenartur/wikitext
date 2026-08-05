package org.hammer.wikihelp.core;

import java.util.List;

public interface WikiSource {
    String type();

    default boolean remote() {
        return true;
    }

    List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception;
}
