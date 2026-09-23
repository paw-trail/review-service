package com.pawtrail.review.infrastructure.provider.external;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3StorageProvider implements StorageProvider {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    // 사진이 들어가는 자리입니다. 계정마다 갈립니다.
    private static final String PHOTO_KEY_FORMAT = "reviews/%s/%s-%s";

    // 그 계정의 자리인지 볼 때 쓰는 접두사입니다.
    private static final String OWNED_PREFIX_FORMAT = "reviews/%s/";

    // 가상 호스팅 방식 주소입니다.
    // 버킷 이름이 호스트 앞에 붙는 형태이고 지금 S3 의 기본입니다.
    // 경로 방식(s3.리전.amazonaws.com/버킷/키)은 옛 방식이라 쓰지 않습니다.
    private static final String PUBLIC_URL_FORMAT = "https://%s.s3.%s.amazonaws.com/%s";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public String newPhotoKey(UUID accountId, String fileName) {
        return PHOTO_KEY_FORMAT.formatted(accountId, UUID.randomUUID(), safeFileName(fileName));
    }

    @Override
    public String publicUrl(String key) {
        return PUBLIC_URL_FORMAT.formatted(properties.bucket(), properties.region(), key);
    }

    @Override
    public Optional<String> extractOwnedKey(String url, UUID accountId) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }

        // 서명이 붙은 주소를 여기서 걸러 냅니다.
        // 발급한 fileUrl 에는 쿼리도 조각도 없습니다.
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return Optional.empty();
        }

        if (!"https".equals(uri.getScheme())) {
            return Optional.empty();
        }

        String expectedHost = "%s.s3.%s.amazonaws.com"
            .formatted(properties.bucket(), properties.region());
        if (!expectedHost.equals(uri.getHost())) {
            return Optional.empty();
        }

        // getPath 는 퍼센트 인코딩을 이미 푼 값입니다.
        // 인코딩된 채로 견주면 %2E%2E 같은 표기가 검사를 지나갑니다.
        String path = uri.getPath();
        if (path == null || path.length() < 2) {
            return Optional.empty();
        }
        String key = path.substring(1);

        String ownedPrefix = OWNED_PREFIX_FORMAT.formatted(accountId);
        if (!key.startsWith(ownedPrefix)) {
            return Optional.empty();
        }

        // 발급한 키는 계정 아래 파일 이름 하나뿐입니다.
        // 경로가 더 깊거나 거슬러 올라가는 표기가 섞였으면 우리가 만든 값이 아닙니다.
        String fileName = key.substring(ownedPrefix.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            return Optional.empty();
        }

        return Optional.of(key);
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength) {
        String normalizedContentType = normalizeContentType(contentType);

        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                // 서명에 들어가므로 브라우저는 이 타입으로만 올릴 수 있습니다.
                .contentType(normalizedContentType)
                // 이 값도 서명에 들어갑니다.
                // 다른 크기로 올리면 S3 가 거부하므로 클라이언트 검증에 기대지 않게 됩니다.
                //
                // 범위가 아니라 정확한 값입니다.
                // presigned PUT 에는 범위를 걸 수 없고 버킷 정책에도 크기 조건 키가 없습니다.
                // 프론트가 file.size 를 그대로 보내야 하며 한 바이트라도 다르면 403 이 납니다.
                .contentLength(contentLength)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.uploadExpiresSeconds()))
                .putObjectRequest(objectRequest)
                .build();

            return s3Presigner.presignPutObject(presignRequest).url().toExternalForm();
        } catch (SdkException exception) {
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }
    }

    @Override
    public String presignDownload(String key) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.downloadExpiresSeconds()))
                .getObjectRequest(objectRequest)
                .build();

            return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
        } catch (SdkException exception) {
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());

            log.info("객체를 지웠습니다: key={}", key);
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
            log.warn("허용하지 않는 형식입니다: contentType={}", normalized);
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
}
