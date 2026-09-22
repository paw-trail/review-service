package com.pawtrail.review.infrastructure.provider.external;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.dto.UploadTarget;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3StorageProvider implements StorageProvider {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final String REVIEW_PREFIX = "reviews/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public UploadTarget createReviewUpload(UUID accountId, String fileName, String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        String key = reviewPrefix(accountId) + UUID.randomUUID() + "-" + safeFileName(fileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(normalizedContentType)
                .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.uploadExpiresSeconds()))
                .putObjectRequest(putObjectRequest)
                .build();
            PresignedPutObjectRequest signed = s3Presigner.presignPutObject(presignRequest);

            return new UploadTarget(
                signed.url().toExternalForm(),
                objectUrl(key).toExternalForm(),
                properties.uploadExpiresSeconds()
            );
        } catch (SdkException exception) {
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        String leafName = fileName.replace('\\', '/');
        leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
        String safe = leafName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        return safe;
    }

    private String extractOwnedKey(UUID accountId, String fileUrl) {
        try {
            URI actual = URI.create(fileUrl);
            URI expected = objectUrl("validation-key").toURI();
            if (!"https".equalsIgnoreCase(actual.getScheme())
                || !expected.getHost().equalsIgnoreCase(actual.getHost())) {
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }

            String expectedSuffix = "validation-key";
            String expectedPathPrefix = expected.getRawPath().substring(
                0,
                expected.getRawPath().length() - expectedSuffix.length()
            );
            if (!actual.getRawPath().startsWith(expectedPathPrefix)) {
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }

            String key = actual.getRawPath().substring(expectedPathPrefix.length());
            if (!key.startsWith(reviewPrefix(accountId))) {
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED, exception);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED, exception);
        }
    }

    private URL objectUrl(String key) {
        return s3Client.utilities().getUrl(GetUrlRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .build());
    }

    private String reviewPrefix(UUID accountId) {
        return REVIEW_PREFIX + accountId + "/";
    }
}
