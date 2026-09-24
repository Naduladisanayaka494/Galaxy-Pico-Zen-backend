package com.knox.galaxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded images on this server's own disk, and returns the URL the
 * browser loads them back from.
 *
 * <p>This replaces the S3 upload path. A file written to
 * {@code <storage-dir>/products/image_x.png} is served at
 * {@code <base-url>/public/storage/products/image_x.png} — by nginx straight off
 * disk in production, and by {@link com.knox.galaxy.config.StorageWebConfig}'s
 * resource handler otherwise, so local dev needs nothing in front of the app.
 *
 * <p>The storage directory has to outlive a deploy: it is a path on the host (or
 * a mounted volume under Docker), never a directory inside the jar or the build
 * output. Replacing galaxy.jar must not take the images with it.
 *
 * <p>Values already stored in the database — including the old
 * {@code https://<bucket>.s3.<region>.amazonaws.com/...} URLs — are passed
 * through untouched, so existing product images keep resolving for as long as
 * that bucket stays up. Only newly uploaded images land here.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** URL path the storage directory is exposed under. Mirrored in nginx and SecurityConfig. */
    public static final String PUBLIC_PATH = "/public/storage";

    /** Folder used when none is given — the original product-image behaviour. */
    private static final String DEFAULT_FOLDER = "products";

    /**
     * Media types accepted, and the extension each is written with.
     *
     * <p>An allow-list rather than "whatever the data URI claims", for two
     * reasons. These files are now served from the app's own origin, so a stored
     * SVG (or an HTML document mislabelled as an image) becomes stored XSS
     * against every logged-in user of the tenant — with a cross-origin bucket
     * that was contained. And the declared type ends up in the filename, so an
     * unchecked one is a path-traversal string as much as it is a content type.
     */
    private static final Map<String, String> ALLOWED_TYPES;
    static {
        Map<String, String> types = new HashMap<>();
        types.put("image/png", ".png");
        types.put("image/jpeg", ".jpg");
        types.put("image/jpg", ".jpg");
        types.put("image/webp", ".webp");
        types.put("image/gif", ".gif");
        types.put("image/bmp", ".bmp");
        types.put("image/avif", ".avif");
        ALLOWED_TYPES = Collections.unmodifiableMap(types);
    }

    @Value("${galaxy.storage.dir:storage}")
    private String storageDir;

    @Value("${galaxy.storage.base-url:}")
    private String baseUrl;

    @Value("${galaxy.storage.max-file-size:10485760}")
    private long maxFileSize;

    /** Absolute, normalised storage root. Resolved once at boot. */
    private Path root;

    @PostConstruct
    public void init() {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        this.baseUrl = stripTrailingSlashes(baseUrl);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            // Fail the boot rather than the first upload. An unwritable storage
            // directory is a one-line misconfiguration, but discovered lazily it
            // surfaces as a 500 on product save long after the deploy went out,
            // with nothing in the startup log pointing at the cause.
            throw new IllegalStateException("Cannot create storage directory " + root + ": " + e.getMessage(), e);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Storage directory " + root + " is not writable by this process");
        }
        log.info("File storage ready at {} — serving on {}{}", root, baseUrl, PUBLIC_PATH);
    }

    /** Absolute path uploads are written under. Read by the resource handler. */
    public Path getRoot() {
        return root;
    }

    /**
     * If the input is a base64 data URI, writes it to the products folder and
     * returns its public URL. Any other input — an http(s) URL already stored, a
     * blank, a null — is returned unchanged.
     */
    public String uploadIfBase64(String imageInput) {
        return uploadIfBase64(imageInput, DEFAULT_FOLDER);
    }

    /**
     * As {@link #uploadIfBase64(String)}, but stores the file under {@code folder/}
     * instead of {@code products/} — so non-product images (business logos, and
     * later customer or user avatars) don't end up in the product namespace.
     */
    public String uploadIfBase64(String imageInput, String folder) {
        if (imageInput == null || !imageInput.startsWith("data:")) {
            return imageInput;
        }

        // Format is: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
        int commaIdx = imageInput.indexOf(',');
        if (commaIdx == -1) {
            return imageInput;
        }

        String metadataPart = imageInput.substring(0, commaIdx);
        String base64Payload = imageInput.substring(commaIdx + 1);

        if (!metadataPart.toLowerCase(Locale.ROOT).contains(";base64")) {
            // data:image/svg+xml,<svg .../> and friends carry their payload as
            // percent-encoded text rather than base64. The uploader never
            // produces one, and decoding it as base64 would only throw below.
            throw new IllegalArgumentException("Only base64-encoded data URIs are accepted");
        }

        String contentType = mediaType(metadataPart);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported image type: " + contentType
                    + ". Allowed: " + String.join(", ", ALLOWED_TYPES.keySet()));
        }

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64Payload.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Image payload is not valid base64", e);
        }
        if (decodedBytes.length > maxFileSize) {
            throw new IllegalArgumentException("Image is " + (decodedBytes.length / 1024) + "KB, over the "
                    + (maxFileSize / 1024) + "KB limit");
        }

        String safeFolder = sanitizeFolder(folder);
        String fileName = "image_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + extension;
        Path target = root.resolve(safeFolder).resolve(fileName);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, decodedBytes);
        } catch (IOException e) {
            log.error("Failed to write upload to {}", target, e);
            throw new RuntimeException("Failed to store image: " + e.getMessage(), e);
        }

        String url = baseUrl + PUBLIC_PATH + "/" + safeFolder + "/" + fileName;
        log.info("Stored {} bytes at {} — serving as {}", decodedBytes.length, target, url);
        return url;
    }

    /** "data:image/png;base64" -> "image/png". */
    private String mediaType(String metadataPart) {
        String withoutScheme = metadataPart.substring("data:".length());
        int semicolon = withoutScheme.indexOf(';');
        String type = semicolon == -1 ? withoutScheme : withoutScheme.substring(0, semicolon);
        return type.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Folder names come from call sites in this codebase, not from requests —
     * but they are concatenated into a filesystem path, so they are filtered
     * anyway rather than trusted on the strength of who calls the method today.
     */
    private String sanitizeFolder(String folder) {
        if (folder == null) {
            return DEFAULT_FOLDER;
        }
        String cleaned = folder.trim().replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isEmpty() ? DEFAULT_FOLDER : cleaned;
    }

    private String stripTrailingSlashes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
