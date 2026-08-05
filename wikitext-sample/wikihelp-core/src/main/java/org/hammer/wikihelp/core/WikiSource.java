package org.hammer.wikihelp.core;

import java.util.List;
import java.util.Map;

public interface WikiSource {
    String type();

    default boolean remote() {
        return true;
    }

    List<WikiPage> fetch(
            SourceConfiguration configuration,
            PageSelection selection,
            HttpTransport transport) throws Exception;

    default List<AttachmentRequest> attachmentRequests(
            SourceConfiguration configuration,
            WikiPage page,
            HttpTransport transport) throws Exception {
        return AttachmentSupport.discover(page);
    }

    default Map<String, String> attachmentHeaders(
            SourceConfiguration configuration,
            WikiPage page,
            AttachmentRequest request) {
        return Map.of();
    }

    default WikiAttachment fetchAttachment(
            SourceConfiguration configuration,
            WikiPage page,
            AttachmentRequest request,
            HttpTransport transport,
            int maxBytes) throws Exception {
        return AttachmentSupport.download(
                request,
                transport,
                attachmentHeaders(configuration, page, request),
                maxBytes);
    }
}
