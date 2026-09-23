package com.pawtrail.review.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.ReviewTagProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final StorageProperties STORAGE_PROPERTIES = new StorageProperties(
        "review-images",
        "ap-northeast-2",
        900,
        3_600,
        20_971_520
    );

    @Mock
    private PlaceReviewRepository reviewRepository;

    @Mock
    private ReviewLikeRepository reviewLikeRepository;

    @Mock
    private UserProvider userProvider;

    @Mock
    private ReviewTagProvider reviewTagProvider;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private StorageProvider storageProvider;

    private ReviewService reviewService;

    // 설정은 값 객체라 목으로 만들지 않고 실제 값을 넣습니다.
    // 상한을 넘었는지 보는 검사에서 그 값이 그대로 쓰여야 합니다.
    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
            reviewRepository,
            reviewLikeRepository,
            userProvider,
            reviewTagProvider,
            storageProvider,
            STORAGE_PROPERTIES,
            placeProvider
        );
    }

    @Test
    void returnsEmptyPageWithoutLookingUpPlacesWhenThereAreNoReviews() {
        UUID accountId = UUID.randomUUID();

        when(reviewRepository.findActiveByAccountId(
            accountId,
            ReviewSort.RECENT,
            0,
            20
        )).thenReturn(new ReviewPage<>(List.of(), 0, 20, 0, 0));

        PageResponse<MyReviewOutput> result = reviewService.findMine(
            accountId,
            "recent",
            0,
            20
        );

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.page().number());
        assertEquals(20, result.page().size());
        assertEquals(0, result.page().totalElements());
        assertEquals(0, result.page().totalPages());
        verifyNoInteractions(placeProvider);
    }

    @Test
    void returnsUploadUrlWithUnsignedFileUrl() {
        UUID accountId = UUID.randomUUID();
        String key = "reviews/" + accountId + "/generated-review.jpg";

        when(storageProvider.newPhotoKey(accountId, "review.jpg")).thenReturn(key);
        when(storageProvider.presignUpload(key, "image/jpeg", 1024L))
            .thenReturn("https://upload.example.com/signed");
        when(storageProvider.publicUrl(key))
            .thenReturn("https://review-images.s3.ap-northeast-2.amazonaws.com/" + key);

        UploadUrlOutput result = reviewService.createUploadUrl(
            accountId,
            "review.jpg",
            "image/jpeg",
            1024L
        );

        assertEquals("https://upload.example.com/signed", result.uploadUrl());
        assertEquals(
            "https://review-images.s3.ap-northeast-2.amazonaws.com/" + key,
            result.fileUrl()
        );
        assertEquals(900, result.expiresIn());
        verify(storageProvider).presignUpload(key, "image/jpeg", 1024L);
    }

    // 상한을 넘으면 주소를 아예 발급하지 않습니다.
    @Test
    void rejectsImageOverMaxBytesBeforeSigning() {
        UUID accountId = UUID.randomUUID();

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.createUploadUrl(
                accountId,
                "review.jpg",
                "image/jpeg",
                20_971_521L
            ));

        assertEquals(CommonErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verifyNoInteractions(storageProvider);
    }
}
