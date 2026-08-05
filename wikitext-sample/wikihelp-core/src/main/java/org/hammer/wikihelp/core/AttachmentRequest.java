package org.hammer.wikihelp.core;

import java.net.URI;
import java.util.Objects;

public record AttachmentRequest(String reference, URI uri, String fileName) {
    public AttachmentRequest {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        uri = Objects.requireNonNull(uri, "uri");
        fileName = AttachmentSupport.safeFileName(fileName);
    }
}
