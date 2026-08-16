package com.knox.galaxy.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    private AmazonS3 s3Client;

    @PostConstruct
    public void init() {
        try {
            AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard().withRegion(region);
            if (accessKeyId != null && !accessKeyId.trim().isEmpty() &&
                    secretAccessKey != null && !secretAccessKey.trim().isEmpty()) {
                AWSCredentials credentials = new BasicAWSCredentials(accessKeyId.trim(), secretAccessKey.trim());
                builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
                log.info("Initialized AWS S3 Client with provided credentials in region: {}", region);
            } else {
                log.info("AWS Credentials not provided. Using default credential providers in region: {}", region);
            }
            this.s3Client = builder.build();
        } catch (Exception e) {
            log.error("Failed to initialize AWS S3 client", e);
        }
    }

    /** Key prefix used when no folder is given — the original product-image behaviour. */
    private static final String DEFAULT_FOLDER = "products";

    /**
     * If the input is a base64 encoded data URI, decodes it, uploads it to S3 products folder,
     * and returns the public S3 URL. Otherwise, returns the original input string.
     */
    public String uploadIfBase64(String imageInput) {
        return uploadIfBase64(imageInput, DEFAULT_FOLDER);
    }

    /**
     * As {@link #uploadIfBase64(String)}, but stores the object under {@code folder/}
     * instead of {@code products/} — so non-product images (business logos, and
     * later customer or user avatars) don't end up in the product namespace.
     */
    public String uploadIfBase64(String imageInput, String folder) {
        if (imageInput == null || !imageInput.startsWith("data:")) {
            return imageInput;
        }

        try {
            // Format is: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
            int commaIdx = imageInput.indexOf(',');
            if (commaIdx == -1) {
                return imageInput;
            }

            String metadataPart = imageInput.substring(0, commaIdx);
            String base64Payload = imageInput.substring(commaIdx + 1);

            // Extract content-type
            String contentType = "image/png"; // default fallback
            String extension = ".png";
            if (metadataPart.contains("image/")) {
                int start = metadataPart.indexOf("image/");
                int end = metadataPart.indexOf(";");
                if (end > start) {
                    contentType = metadataPart.substring(start, end);
                    extension = "." + contentType.substring("image/".length());
                }
            }

            byte[] decodedBytes = Base64.getDecoder().decode(base64Payload.trim());
            String fileName = folder + "/image_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + extension;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(decodedBytes.length);

            log.info("Uploading image to S3: {}/{}", bucketName, fileName);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedBytes)) {
                // Upload to S3
                s3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, metadata));
            }

            // Construct the S3 URL
            String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
            log.info("Successfully uploaded image to S3: {}", s3Url);
            return s3Url;

        } catch (Exception e) {
            log.error("Failed to upload base64 image to S3", e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }
}
