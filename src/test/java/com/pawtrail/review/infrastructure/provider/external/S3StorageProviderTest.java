package com.pawtrail.review.infrastructure.provider.external;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3StorageProviderTest {

    private static final StorageProperties PROPERTIES = new StorageProperties(
        "review-images",
        "ap-northeast-2",
        900,
        3_600,
        20_971_520
    );

    @Test
    void createsAccountScopedKeyAndUnsignedFileUrl() {
        UUID accountId = UUID.randomUUID();

        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String key = provider.newPhotoKey(accountId, "review photo.jpg");

            assertThat(key)
                .startsWith("reviews/" + accountId + "/")
                .endsWith("-review_photo.jpg");

            URI fileUri = URI.create(provider.publicUrl(key));

            assertThat(fileUri.getHost()).isEqualTo("review-images.s3.ap-northeast-2.amazonaws.com");
            assertThat(fileUri.getPath()).isEqualTo("/" + key);
            assertThat(fileUri.getRawQuery()).isNull();
        }
    }

    @Test
    void signsUploadWithContentTypeAndLength() {
        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String key = provider.newPhotoKey(UUID.randomUUID(), "review.jpg");
            URI uploadUri = URI.create(provider.presignUpload(key, "image/jpeg", 1024));

            assertThat(uploadUri.getPath()).isEqualTo("/" + key);
            assertThat(uploadUri.getRawQuery())
                .contains("X-Amz-Signature")
                .contains("content-length")
                .contains("content-type");
        }
    }

    @Test
    void signsDownloadWithDownloadLifetime() {
        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String key = provider.newPhotoKey(UUID.randomUUID(), "review.jpg");
            URI downloadUri = URI.create(provider.presignDownload(key));

            assertThat(downloadUri.getPath()).isEqualTo("/" + key);
            assertThat(downloadUri.getRawQuery())
                .contains("X-Amz-Signature")
                .contains("X-Amz-Expires=3600");
        }
    }

    @Test
    void extractsKeyFromOwnFileUrl() {
        UUID accountId = UUID.randomUUID();

        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String key = provider.newPhotoKey(accountId, "review.jpg");

            assertThat(provider.extractOwnedKey(provider.publicUrl(key), accountId))
                .contains(key);
        }
    }

    // 남의 자리, 서명이 붙은 주소, 다른 버킷은 모두 받지 않습니다.
    @Test
    void rejectsUrlsThatAreNotOwnUnsignedObject() {
        UUID accountId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String othersKey = provider.newPhotoKey(otherId, "review.jpg");
            String ownKey = provider.newPhotoKey(accountId, "review.jpg");

            assertThat(provider.extractOwnedKey(provider.publicUrl(othersKey), accountId))
                .isEmpty();
            assertThat(provider.extractOwnedKey(provider.presignDownload(ownKey), accountId))
                .isEmpty();
            assertThat(provider.extractOwnedKey(
                "https://other-bucket.s3.ap-northeast-2.amazonaws.com/" + ownKey,
                accountId
            )).isEmpty();
            assertThat(provider.extractOwnedKey("https://review-images.s3.ap-northeast-2."
                + "amazonaws.com/reviews/" + accountId + "/nested/photo.jpg", accountId))
                .isEmpty();
            assertThat(provider.extractOwnedKey(null, accountId)).isEmpty();
        }
    }

    @Test
    void rejectsUnsupportedContentType() {
        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            String key = provider.newPhotoKey(UUID.randomUUID(), "review.gif");

            assertThatThrownBy(() -> provider.presignUpload(key, "image/gif", 1024))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                    assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    void rejectsBlankFileName() {
        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            assertThatThrownBy(() -> provider.newPhotoKey(UUID.randomUUID(), " "))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                    assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.VALIDATION_FAILED));
        }
    }

    private S3Presigner presigner() {
        return S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-access-key", "test-secret-key")
            ))
            .build();
    }
}
