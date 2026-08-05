package org.hammer.wikihelp.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttachmentSynchronizer {
    public List<WikiPage> synchronize(
            WikiSource source,
            SourceConfiguration configuration,
            List<WikiPage> pages,
            HttpTransport transport) throws Exception {
        if (!configuration.bool("attachments.enabled", true)) return List.copyOf(pages);

        int maxBytes = configuration.integer("attachments.maxBytes", 20 * 1024 * 1024);
        int maxPerPage = configuration.integer("attachments.maxPerPage", 100);
        boolean failOnError = configuration.bool("attachments.failOnError", true);
        Map<URI, WikiAttachment> downloaded = new LinkedHashMap<>();
        List<WikiPage> result = new ArrayList<>();

        for (WikiPage page : pages) {
            LinkedHashMap<String, AttachmentRequest> requests = new LinkedHashMap<>();
            for (AttachmentRequest request : source.attachmentRequests(
                    configuration, page, transport)) {
                requests.putIfAbsent(request.reference() + "\u0000" + request.uri(), request);
                if (requests.size() >= maxPerPage) break;
            }

            List<WikiAttachment> attachments = new ArrayList<>(page.attachments());
            for (AttachmentRequest request : requests.values()) {
                try {
                    WikiAttachment attachment = downloaded.get(request.uri());
                    if (attachment == null) {
                        attachment = source.fetchAttachment(
                                configuration, page, request, transport, maxBytes);
                        downloaded.put(request.uri(), attachment);
                    } else {
                        attachment = attachment.referencedAs(request);
                    }
                    attachments.add(attachment);
                } catch (Exception exception) {
                    if (failOnError) {
                        throw new IllegalStateException(
                                "Cannot download attachment " + request.uri()
                                        + " for page " + page.title(), exception);
                    }
                    System.err.printf(
                            "WikiHelp: skipped attachment %s for %s: %s%n",
                            request.uri(), page.title(), exception.getMessage());
                }
            }
            result.add(page.withAttachments(attachments));
        }
        return List.copyOf(result);
    }
}
