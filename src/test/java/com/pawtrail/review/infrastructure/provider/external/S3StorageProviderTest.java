package com.pawtrail.review.infrastructure.provider.external;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.dto.UploadTarget;
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
        900
    );

    @Test
    void createsAccountScopedPutAndGetPresignedUrls() throws Exception {
        UUID accountId = UUID.randomUUID();

        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            UploadTarget target = provider.createReviewUpload(
                accountId,
                "review photo.jpg",
                "image/jpeg"
            );

            URI uploadUri = URI.create(target.uploadUrl());
            URI fileUri = URI.create(target.fileUrl());
            String expectedKeyPrefix = "/reviews/" + accountId + "/";

            assertThat(uploadUri.getPath())
                .startsWith(expectedKeyPrefix)
                .endsWith("-review_photo.jpg");
            assertThat(fileUri.getPath()).isEqualTo(uploadUri.getPath());
            assertThat(uploadUri.getRawQuery())
                .contains("X-Amz-Signature")
                .contains("X-Amz-SignedHeaders=content-type%3Bhost");
            assertThat(fileUri.getRawQuery())
                .contains("X-Amz-Signature")
                .contains("X-Amz-SignedHeaders=host");
            assertThat(target.expiresIn()).isEqualTo(900);
        }
    }

    @Test
    void rejectsUnsupportedContentType() {
        try (S3Presigner presigner = presigner()) {
            S3StorageProvider provider = new S3StorageProvider(presigner, PROPERTIES);

            assertThatThrownBy(() -> provider.createReviewUpload(
                UUID.randomUUID(),
                "review.gif",
                "image/gif"
            ))
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
