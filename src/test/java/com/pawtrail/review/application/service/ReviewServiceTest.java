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
import com.pawtrail.review.domain.model.ReviewPet;
import com.pawtrail.review.domain.exception.ReviewErrorCode;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.ReviewPetRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private ReviewPetRepository reviewPetRepository;

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
            reviewPetRepository,
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

    // 고른 순서가 sort_order 가 되고 그것이 화면의 배지 순서가 됩니다.
    @Test
    void copiesEachPetSnapshotInTheGivenOrderOnCreate() {
        UUID accountId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID firstPet = UUID.randomUUID();
        UUID secondPet = UUID.randomUUID();
        String photoUrl = "https://review-images.s3.ap-northeast-2.amazonaws.com/reviews/"
            + accountId + "/photo.jpg";
        String photoKey = "reviews/" + accountId + "/photo.jpg";

        when(petProvider.findOwnedPets(accountId, List.of(firstPet, secondPet)))
            .thenReturn(Map.of(
                firstPet, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE"),
                secondPet, new PetSnapshot("말티즈", new BigDecimal("3.2"), "SMALL")
            ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함", "야외석 넓음"));
        when(storageProvider.extractOwnedKey(photoUrl, accountId))
            .thenReturn(Optional.of(photoKey));
        when(reviewRepository.save(any(PlaceReview.class))).thenAnswer(saveWithGeneratedId());

        reviewService.create(accountId, placeId, new ReviewCreateInput(
            List.of(firstPet, secondPet),
            LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요",
            List.of(photoUrl, photoUrl),
            List.of("주차 편함", "주차 편함")
        ));

        ArgumentCaptor<PlaceReview> reviewCaptor = ArgumentCaptor.forClass(PlaceReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());

        PlaceReview saved = reviewCaptor.getValue();

        // 같은 값을 두 번 보내도 한 번만 남습니다.
        assertEquals(1, saved.getPhotos().length);
        assertEquals(photoKey, saved.getPhotos()[0]);
        assertEquals(1, saved.getTags().length);

        List<ReviewPet> pets = capturedPets();

        assertEquals(2, pets.size());
        assertEquals(firstPet, pets.get(0).getPetId());
        assertEquals((short) 0, pets.get(0).getSortOrder());
        assertEquals("골든리트리버", pets.get(0).getBreedName());
        assertEquals("LARGE", pets.get(0).getBreedSize());
        assertEquals(0, new BigDecimal("28.5").compareTo(pets.get(0).getWeightKg()));
        assertEquals(secondPet, pets.get(1).getPetId());
        assertEquals((short) 1, pets.get(1).getSortOrder());
        assertEquals("말티즈", pets.get(1).getBreedName());
        assertEquals(saved.getId(), pets.get(0).getReviewId());
    }

    // 같은 아이를 두 번 골라 보내도 한 줄만 남습니다.
    // 기본 키가 막기는 하지만 그 충돌은 커밋 시점에 터져 500 이 됩니다.
    @Test
    void keepsOnlyOneRowWhenTheSamePetIsSentTwice() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        // 태그를 안 보내므로 추천 태그 목록은 묻지 않습니다. 그래서 여기서 세우지 않습니다.
        when(petProvider.findOwnedPets(accountId, List.of(petId)))
            .thenReturn(Map.of(petId, new PetSnapshot("말티즈", new BigDecimal("3.2"), "SMALL")));
        when(reviewRepository.save(any(PlaceReview.class))).thenAnswer(saveWithGeneratedId());

        reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
            List.of(petId, petId),
            LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요",
            List.of(),
            List.of()
        ));

        List<ReviewPet> pets = capturedPets();

        assertEquals(1, pets.size());
        assertEquals((short) 0, pets.get(0).getSortOrder());
    }

    // 한 마리라도 본인의 아이가 아니면 전부 실패시킵니다.
    // 남의 아이만 빼고 저장하면 사용자는 고른 대로 저장된 줄 알게 됩니다.
    @Test
    void rejectsCreateWhenAnyPetIsNotOwned() {
        UUID accountId = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();

        when(petProvider.findOwnedPets(accountId, List.of(mine, someoneElses)))
            .thenReturn(Map.of(
                mine, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
            ));

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                List.of(mine, someoneElses),
                LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 5, (short) 4,
                "좋았어요",
                List.of(),
                List.of()
            )));

        assertEquals(ReviewErrorCode.PET_NOT_OWNED, exception.getErrorCode());
        verifyNoInteractions(reviewRepository);
        verifyNoInteractions(reviewPetRepository);
    }

    @Test
    void rejectsTagThatIsNotInTheConfiguredList() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        when(petProvider.findOwnedPets(accountId, List.of(petId)))
            .thenReturn(Map.of(
                petId, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
            ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함"));

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                List.of(petId),
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

        when(petProvider.findOwnedPets(accountId, List.of(petId)))
            .thenReturn(Map.of(
                petId, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
            ));
        when(reviewTagProvider.findAll()).thenReturn(List.of("주차 편함"));
        when(storageProvider.extractOwnedKey(photoUrl, accountId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () ->
            reviewService.create(accountId, UUID.randomUUID(), new ReviewCreateInput(
                List.of(petId),
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
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(keptKey, removedKey), List.of()
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

    // 수정은 반려동물을 건드리지 않습니다.
    // 뒤늦게 더한 아이의 스냅샷은 방문 당시가 아니라 오늘 값이 됩니다.
    @Test
    void leavesPetsUntouchedOnUpdate() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        );

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.of(review));

        reviewService.update(accountId, reviewId, new ReviewUpdateInput(
            (short) 3, null, null, null, null, null, null
        ));

        assertEquals((short) 3, review.getRating());
        verifyNoInteractions(reviewPetRepository);
        verifyNoInteractions(petProvider);
    }

    @Test
    void rejectsUpdateOnSomeoneElsesReview() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
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
    // review_pet 은 행을 남기는 소프트 삭제라 그대로 둡니다.
    @Test
    void marksReviewDeletedAndClearsLikesAndPhotos() {
        UUID accountId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        String key = "reviews/" + accountId + "/photo.jpg";

        PlaceReview review = PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(key), List.of()
        );

        when(reviewRepository.findActiveByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
        runAfterCommitImmediately();

        reviewService.delete(accountId, reviewId);

        assertTrue(review.isDeleted());
        verify(reviewLikeRepository).deleteAllByReviewId(review.getId());
        verify(storageProvider).delete(key);
        verifyNoInteractions(reviewPetRepository);
    }

    // 저장한 자식 행을 꺼냅니다.
    @SuppressWarnings("unchecked")
    private List<ReviewPet> capturedPets() {
        ArgumentCaptor<List<ReviewPet>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewPetRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // 실제 저장소는 저장하면서 식별자를 채웁니다.
    // 목은 그대로 돌려주기만 해 식별자가 비어 있으므로, 자식 행이 가리킬 값을 여기서 넣습니다.
    private org.mockito.stubbing.Answer<PlaceReview> saveWithGeneratedId() {
        return invocation -> {
            PlaceReview review = invocation.getArgument(0);
            ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
            return review;
        };
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
