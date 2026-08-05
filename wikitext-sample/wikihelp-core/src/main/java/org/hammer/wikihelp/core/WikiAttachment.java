package org.hammer.wikihelp.core;

import java.net.URI;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public final class WikiAttachment {
    private final String reference;
    private final URI originalUri;
    private final String fileName;
    private final String mediaType;
    private final byte[] content;
    private final String sha256;

    public WikiAttachment(
            String reference,
            URI originalUri,
            String fileName,
            String mediaType,
            byte[] content) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        this.reference = reference;
        this.originalUri = Objects.requireNonNull(originalUri, "originalUri");
        this.fileName = AttachmentSupport.safeFileName(fileName);
        this.mediaType = mediaType == null || mediaType.isBlank()
                ? "application/octet-stream"
                : mediaType.trim();
        this.content = Objects.requireNonNull(content, "content").clone();
        this.sha256 = sha256(this.content);
    }

    public String reference() {
        return reference;
    }

    public URI originalUri() {
        return originalUri;
    }

    public String fileName() {
        return fileName;
    }

    public String mediaType() {
        return mediaType;
    }

    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }

    public String sha256() {
        return sha256;
    }

    public WikiAttachment referencedAs(AttachmentRequest request) {
        return new WikiAttachment(
                request.reference(),
                request.uri(),
                request.fileName(),
                mediaType,
                content);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof WikiAttachment other)) return false;
        return reference.equals(other.reference)
                && originalUri.equals(other.originalUri)
                && fileName.equals(other.fileName)
                && mediaType.equals(other.mediaType)
                && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(reference, originalUri, fileName, mediaType);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "WikiAttachment[reference=" + reference
                + ", originalUri=" + originalUri
                + ", fileName=" + fileName
                + ", mediaType=" + mediaType
                + ", size=" + content.length
                + ", sha256=" + sha256 + "]";
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
