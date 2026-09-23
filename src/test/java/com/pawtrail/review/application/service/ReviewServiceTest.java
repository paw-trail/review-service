package com.pawtrail.review.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.provider.PetProvider;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.ReviewTagProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import com.pawtrail.review.application.dto.input.ReviewCreateInput;
import com.pawtrail.review.application.dto.input.ReviewUpdateInput;
import com.pawtrail.review.application.support.AfterCommitExecutor;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.exception.ReviewErrorCode;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private PetProvider petProvider;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

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
            placeProvider,
            petProvider,
            afterCommitExecutor
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

    @Test
    void copiesPetSnapshotAndStoresPhotoKeysOnCreate() {
        UUID accountId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        String photoUrl = "https://review-images.s3.ap-northeast-2.amazonaws.com/reviews/"
            + accountId + "/photo.jpg";
        String photoKey = "reviews/" + accountId + "/photo.jpg";

        when(petProvider.findOwnedPet(accountId, petId)).thenReturn(Optional.of(
            new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
        ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함", "야외석 넓음"));
        when(storageProvider.extractOwnedKey(photoUrl, accountId))
            .thenReturn(Optional.of(photoKey));
        when(reviewRepository.save(any(PlaceReview.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        reviewService.create(accountId, placeId, new ReviewCreateInput(
            petId,
            LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요",
            List.of(photoUrl, photoUrl),
            List.of("주차 편함", "주차 편함")
        ));

        ArgumentCaptor<PlaceReview> captor = ArgumentCaptor.forClass(PlaceReview.class);
        verify(reviewRepository).save(captor.capture());

        PlaceReview saved = captor.getValue();
        assertEquals("골든리트리버", saved.getPetBreedAtVisit());
        assertEquals("LARGE", saved.getPetSizeAtVisit());
        assertEquals(0, new BigDecimal("28.5").compareTo(saved.getPetWeightAtVisit()));

        // 같은 값을 두 번 보내도 한 번만 남습니다.
        assertEquals(1, saved.getPhotos().length);
        assertEquals(photoKey, saved.getPhotos()[0]);
        assertEquals(1, saved.getTags().length);
    }

    @Test
    void rejectsCreateWhenPetIsNotOwned() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        when(petProvider.findOwnedPet(accountId, petId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                petId,
                LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 5, (short) 4,
                "좋았어요",
                List.of(),
                List.of()
            )));

        assertEquals(ReviewErrorCode.PET_NOT_OWNED, exception.getErrorCode());
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void rejectsTagThatIsNotInTheConfiguredList() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        when(petProvider.findOwnedPet(accountId, petId)).thenReturn(Optional.of(
            new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
        ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함"));

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                petId,
                LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 5, (short) 4,
                "좋았어요",
                List.of(),
                List.of("없는 태그")
            )));

        assertEquals(CommonErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void rejectsPhotoUrlThatIsNotOwned() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        String photoUrl = "https://review-images.s3.ap-northeast-2.amazonaws.com/reviews/"
            + UUID.randomUUID() + "/photo.jpg";

        when(petProvider.findOwnedPet(accountId, petId)).thenReturn(Optional.of(
            new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
        ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함"));
        when(storageProvider.extractOwnedKey(photoUrl, accountId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                petId,
                LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 5, (short) 4,
                "좋았어요",
                List.of(photoUrl),
                List.of("주차 편함")
            )));

        assertEquals(CommonErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verifyNoInteractions(reviewRepository);
    }

    // 사진을 바꾸면 빠진 키만 지웁니다. 남는 키는 건드리지 않습니다.
    @Test
    void deletesOnlyRemovedPhotoKeysOnUpdate() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        String keptKey = "reviews/" + accountId + "/kept.jpg";
        String removedKey = "reviews/" + accountId + "/removed.jpg";
        String keptUrl = "https://review-images.s3.ap-northeast-2.amazonaws.com/" + keptKey;

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), accountId, UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(keptKey, removedKey), List.of(),
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        );

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
        when(storageProvider.extractOwnedKey(keptUrl, accountId)).thenReturn(Optional.of(keptKey));
        runAfterCommitImmediately();

        reviewService.update(accountId, reviewId, new ReviewUpdateInput(
            (short) 3, null, null, null, null, List.of(keptUrl), null
        ));

        assertEquals((short) 3, review.getRating());
        assertArrayEquals(new String[] {keptKey}, review.getPhotos());
        verify(storageProvider).delete(removedKey);
    }

    @Test
    void rejectsUpdateOnSomeoneElsesReview() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of(),
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        );

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.of(review));

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.update(accountId, reviewId, new ReviewUpdateInput(
                (short) 3, null, null, null, null, null, null
            )));

        assertEquals(ReviewErrorCode.REVIEW_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void rejectsUpdateOnMissingReview() {
        UUID reviewId = UUID.randomUUID();

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.update(UUID.randomUUID(), reviewId, new ReviewUpdateInput(
                (short) 3, null, null, null, null, null, null
            )));

        assertEquals(ReviewErrorCode.REVIEW_NOT_FOUND, exception.getErrorCode());
    }

    // 지우면 좋아요 행과 사진이 함께 정리됩니다.
    @Test
    void marksReviewDeletedAndClearsLikesAndPhotos() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        String key = "reviews/" + accountId + "/photo.jpg";

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), accountId, UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(key), List.of(),
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        );

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
        runAfterCommitImmediately();

        reviewService.delete(accountId, reviewId);

        assertTrue(review.isDeleted());
        verify(reviewLikeRepository).deleteAllByReviewId(review.getId());
        verify(storageProvider).delete(key);
    }

    // 커밋 뒤 실행기는 목이라 그냥 두면 아무것도 실행되지 않습니다.
    // 넘긴 작업을 바로 돌려 지우기가 불렸는지 볼 수 있게 합니다.
    private void runAfterCommitImmediately() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(afterCommitExecutor).run(any(Runnable.class), any(String.class));
    }
}
