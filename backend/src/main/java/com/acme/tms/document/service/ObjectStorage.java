package com.acme.tms.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * The only class that knows S3 exists.
 *
 * <p>Bytes never pass through the application: the client uploads to and downloads from object
 * storage directly using a short-lived signed URL. That keeps large files off the app's heap and
 * its bandwidth, which is the entire reason for the two-phase flow in 08 §14.
 */
@Service
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public ObjectStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * A URL the client may PUT to, once, before it expires.
     *
     * <p>The content type is signed into the URL, so a caller who declared a PDF cannot upload
     * something else without the signature failing. That is a useful first gate but not the last
     * one: the size is still whatever they send, which is why {@code attach} re-checks the object
     * that actually landed.
     */
    public String presignUpload(String objectKey, String mimeType) {
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(mimeType)
            .build();

        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.uploadUrlTtlSeconds()))
                .putObjectRequest(put)
                .build())
            .url()
            .toString();
    }

    /** Short-lived so a link that leaks out of a browser history stops working quickly. */
    public String presignDownload(String objectKey, String fileName) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            // Browsers otherwise render a PDF inline under our own origin's name.
            .responseContentDisposition("attachment; filename=\"" + sanitize(fileName) + "\"")
            .build();

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.downloadUrlTtlSeconds()))
                .getObjectRequest(get)
                .build())
            .url()
            .toString();
    }

    /** Empty when the client never completed the upload. */
    public Optional<StoredObject> describe(String objectKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            // MinIO answers 404 without the typed exception in some versions.
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /** Best-effort cleanup of an object we have decided not to keep. */
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
        } catch (RuntimeException exception) {
            // The document row is already gone or was never written; an orphaned object costs
            // storage, not correctness, and failing the caller's request over it would be worse.
            log.warn("Could not delete rejected object {}", objectKey, exception);
        }
    }

    /** Keeps quotes and newlines out of the Content-Disposition header. */
    private String sanitize(String fileName) {
        return fileName.replaceAll("[\"\\r\\n]", "_");
    }

    public record StoredObject(long sizeBytes, String mimeType) {
    }
}
