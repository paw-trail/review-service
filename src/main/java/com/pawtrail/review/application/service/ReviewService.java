package com.pawtrail.review.application.service;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.input.ReviewCreateInput;
import com.pawtrail.review.application.dto.input.ReviewUpdateInput;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.ReviewCreatedOutput;
import com.pawtrail.review.application.dto.output.PlaceReviewListOutput;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
import com.pawtrail.review.application.support.AfterCommitExecutor;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.exception.ReviewErrorCode;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.PetProvider;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.ReviewTagProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewSummary;
import com.pawtrail.review.infrastructure.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final UserProvider userProvider;
    private final ReviewTagProvider reviewTagProvider;
    private final StorageProvider storageProvider;
    private final StorageProperties storageProperties;
    private final PlaceProvider placeProvider;
    private final PetProvider petProvider;
    private final AfterCommitExecutor afterCommitExecutor;

    public PlaceReviewListOutput findByPlace(
        UUID accountId,
        Role role,
        UUID placeId,
        String sortValue,
        boolean photoOnly,
        int page,
        int size
    ) {
        ReviewSort sort = parsePlaceSort(sortValue);

        ReviewPage<PlaceReview> reviews = reviewRepository.findActiveByPlaceId(
            placeId,
            sort,
            photoOnly,
            page,
            size
        );

        // 요약은 목록과 달리 사진만 보기와 쪽을 보지 않습니다.
        // 어떤 조건으로 보고 있든 장소의 평균은 같은 값이어야 합니다.
        ReviewSummary summary = reviewRepository.findActiveSummaryByPlaceId(placeId);

        List<UUID> reviewIds = reviews.content().stream()
            .map(PlaceReview::getId)
            .toList();

        Set<UUID> likedReviewIds = reviewLikeRepository.findLikedReviewIds(accountId, reviewIds);

        Set<UUID> authorIds = reviews.content().stream()
            .map(PlaceReview::getAccountId)
            .collect(Collectors.toUnmodifiableSet());

        Map<UUID, UserSummary> authors = userProvider.getUsers(authorIds);

        List<ReviewDetailOutput> content = reviews.content().stream()
            .map(review -> {
                boolean isMine = accountId.equals(review.getAccountId());
                UserSummary author = authors.getOrDefault(
                    review.getAccountId(),
                    UserSummary.unknown(review.getAccountId())
                );

                return ReviewDetailOutput.of(
                    review,
                    author,
                    likedReviewIds.contains(review.getId()),
                    isMine,
                    isMine || role == Role.ADMIN,
                    signPhotos(review)
                );
            })
            .toList();

        return PlaceReviewListOutput.of(content, reviews, summary);
    }

    // 후기를 씁니다.
    //
    // 반려동물 정보를 지금 복사해 둡니다.
    // 나중에 체중이나 크기가 바뀌어도 그때 다녀온 기록은 그대로 남아야 하고,
    // 지금 복사하지 못하면 영영 빈 칸이 되므로 못 받아 오면 작성을 실패시킵니다.
    //
    // 태그와 사진은 화면이 이미 거른 값이지만 여기서 한 번 더 봅니다.
    // 화면을 거치지 않고 부르는 쪽이 있을 수 있고, 사진 키는 남의 자리를 가리킬 수도 있습니다.
    @Transactional
    public ReviewCreatedOutput create(UUID accountId, UUID placeId, ReviewCreateInput input) {
        PetSnapshot pet = petProvider.findOwnedPet(accountId, input.petId())
            .orElseThrow(() -> {
                log.warn(
                    "본인의 반려동물이 아닙니다: accountId={}, petId={}",
                    accountId,
                    input.petId()
                );
                return new CustomException(ReviewErrorCode.PET_NOT_OWNED);
            });

        List<String> tags = toAllowedTags(input.tags());
        List<String> photoKeys = toOwnedPhotoKeys(accountId, input.photos());

        PlaceReview review = PlaceReview.create(
            placeId,
            accountId,
            input.petId(),
            input.visitedAt(),
            input.rating(),
            input.facilityScore(),
            input.ruleScore(),
            input.moodScore(),
            input.content(),
            photoKeys,
            tags,
            pet.breedName(),
            pet.weightKg(),
            pet.breedSize()
        );

        return new ReviewCreatedOutput(reviewRepository.save(review).getId());
    }

    // 후기를 고칩니다.
    //
    // 보낸 칸만 바꿉니다. 안 보낸 칸은 지금 값을 그대로 둡니다.
    // 사진과 태그는 빈 목록이 "비움" 이라 안 보낸 것과 뜻이 다릅니다.
    //
    // 방문일 · 반려동물 · 스냅샷은 바꾸지 않습니다.
    // 그 넷이 바뀌면 "그때 그 아이로 다녀온 기록" 이라는 글의 전제가 흔들립니다.
    //
    // 사진이 빠지면 그 객체를 커밋 뒤에 지웁니다.
    // 롤백이 났는데 객체만 사라지면 행이 가리키는 사진이 열리지 않기 때문입니다.
    @Transactional
    public void update(UUID accountId, UUID reviewId, ReviewUpdateInput input) {
        PlaceReview review = loadOwnReviewForUpdate(accountId, reviewId);

        List<String> previousKeys = List.of(review.getPhotos());

        List<String> nextKeys = input.photos() == null
            ? null
            : toOwnedPhotoKeys(accountId, input.photos());

        List<String> nextTags = input.tags() == null
            ? null
            : toAllowedTags(input.tags());

        review.update(
            input.rating(),
            input.facilityScore(),
            input.ruleScore(),
            input.moodScore(),
            input.content(),
            nextKeys,
            nextTags
        );

        if (nextKeys != null) {
            deleteAfterCommit(previousKeys.stream()
                .filter(key -> !nextKeys.contains(key))
                .toList());
        }
    }

    // 내 후기를 지웁니다.
    //
    // 행은 남기고 지운 시각만 적습니다.
    // 신고가 걸려 있거나 통계를 되짚어야 할 때 무엇이 있었는지가 남아야 하기 때문입니다.
    //
    // 좋아요는 남기지 않고 지웁니다.
    // 표의 외래키가 ON DELETE CASCADE 라 행을 지울 때만 따라 지워지는데,
    // 소프트 삭제는 행이 남으므로 여기서 직접 지워야 남의 "좋아요 누른 후기" 에서 사라집니다.
    @Transactional
    public void delete(UUID accountId, UUID reviewId) {
        PlaceReview review = loadOwnReviewForUpdate(accountId, reviewId);

        removeReview(review, accountId);
    }

    // 좋아요를 누릅니다.
    //
    // 여러 번 눌러도 같은 결과입니다.
    // 화면에서 두 번 눌리거나 요청이 재시도돼도 사용자에게는 "좋아요가 켜진 상태" 하나뿐이라,
    // 이미 눌렀다고 409 를 주면 화면이 실패로 보이게 됩니다.
    //
    // 있는지 보고 넣지 않습니다. 저장소가 한 번에 처리합니다.
    // 확인과 저장을 나누면 그 사이에 같은 사람의 두 번째 요청이 끼어들어 기본키 충돌이 나는데,
    // 그 충돌은 잡을 수 없는 자리(커밋 시점)에서 터져 한쪽이 500 을 받습니다.
    //
    // like_count 는 건드리지 않습니다. 행이 실제로 들어갔을 때만 표의 트리거가 올립니다.
    @Transactional
    public void like(UUID accountId, UUID reviewId) {
        requireActiveReview(reviewId);

        reviewLikeRepository.insertIfAbsent(reviewId, accountId);
    }

    // 좋아요를 취소합니다. 누르지 않은 상태에서 불러도 같은 결과입니다.
    @Transactional
    public void unlike(UUID accountId, UUID reviewId) {
        requireActiveReview(reviewId);

        reviewLikeRepository.delete(reviewId, accountId);
    }

    // 관리자가 후기를 지웁니다.
    //
    // 지우는 동작은 사용자 삭제와 같고 권한만 다릅니다.
    // 신고를 승인하기 전에 그 후기를 내리는 자리라 남의 글을 지울 수 있어야 합니다.
    // 누가 지웠는지는 deleted_by 에 남습니다.
    @Transactional
    public void deleteByAdmin(UUID adminAccountId, UUID reviewId) {
        PlaceReview review = reviewRepository.findActiveByIdForUpdate(reviewId)
            .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));

        log.info(
            "관리자가 후기를 지웁니다: adminAccountId={}, reviewId={}, authorId={}",
            adminAccountId,
            reviewId,
            review.getAccountId()
        );

        removeReview(review, adminAccountId);
    }

    // 사진을 올릴 주소를 발급합니다.
    //
    // 크기 상한을 여기서 봅니다.
    // 서명에 크기가 들어가 있어 S3 도 그 크기가 아니면 거부하지만,
    // 그것은 "요청한 크기와 다른 것" 을 막을 뿐 상한을 막지는 못합니다.
    // 형식과 이름은 요청 객체가 이미 걸렀습니다.
    //
    // 돌려주는 fileUrl 은 서명이 붙지 않은 주소입니다.
    // 작성 요청이 이 주소를 그대로 보내면 서버가 키를 뽑아 저장합니다.
    public UploadUrlOutput createUploadUrl(
        UUID accountId,
        String fileName,
        String contentType,
        long contentLength
    ) {
        if (contentLength > storageProperties.maxImageBytes()) {
            log.warn(
                "이미지가 상한을 넘었습니다: accountId={}, contentLength={}, max={}",
                accountId,
                contentLength,
                storageProperties.maxImageBytes()
            );
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        String key = storageProvider.newPhotoKey(accountId, fileName);

        return new UploadUrlOutput(
            storageProvider.presignUpload(key, contentType, contentLength),
            storageProvider.publicUrl(key),
            storageProperties.uploadExpiresSeconds()
        );
    }

    public PageResponse<MyReviewOutput> findMine(
        UUID accountId,
        String sortValue,
        int page,
        int size
    ) {
        ReviewSort sort = parseMySort(sortValue);

        ReviewPage<PlaceReview> reviews = reviewRepository.findActiveByAccountId(
            accountId,
            sort,
            page,
            size
        );

        List<PlaceReview> content = reviews.content();

        Set<UUID> placeIds = content.stream()
            .map(PlaceReview::getPlaceId)
            .collect(Collectors.toUnmodifiableSet());

        Map<UUID, PlaceSummary> places = placeIds.isEmpty()
            ? Map.of()
            : placeProvider.getPlaces(placeIds);

        List<MyReviewOutput> output = content.stream()
            .map(review -> MyReviewOutput.of(
                review,
                places.getOrDefault(
                    review.getPlaceId(),
                    PlaceSummary.unknown(review.getPlaceId())
                ),
                signPhotos(review)
            ))
            .toList();

        return new PageResponse<>(
            output,
            new PageResponse.PageInfo(
                reviews.number(),
                reviews.size(),
                reviews.totalElements(),
                reviews.totalPages()
            )
        );
    }

    // 살아 있는 후기인지만 봅니다.
    //
    // 좋아요는 남의 후기에도 누르므로 주인을 보지 않습니다.
    // 없거나 지운 후기는 404 이며, 지운 글에 좋아요가 붙으면 목록에서 셀 수 없는 수가 됩니다.
    private void requireActiveReview(UUID reviewId) {
        if (reviewRepository.findActiveById(reviewId).isEmpty()) {
            throw new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
    }

    // 내 후기를 잠그고 가져옵니다.
    //
    // 없거나 이미 지운 후기는 404 이고, 남의 후기는 403 입니다.
    // 둘을 가르는 이유는 남의 후기라도 그 자리에 무엇이 있다는 사실 자체는 공개이기 때문입니다.
    private PlaceReview loadOwnReviewForUpdate(UUID accountId, UUID reviewId) {
        PlaceReview review = reviewRepository.findActiveByIdForUpdate(reviewId)
            .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));

        if (!review.getAccountId().equals(accountId)) {
            log.warn(
                "남의 후기를 고치거나 지우려 했습니다: accountId={}, reviewId={}",
                accountId,
                reviewId
            );
            throw new CustomException(ReviewErrorCode.REVIEW_ACCESS_DENIED);
        }

        return review;
    }

    // 후기 하나를 지웁니다. 사용자 삭제와 관리자 삭제가 같은 자리를 씁니다.
    private void removeReview(PlaceReview review, UUID deletedBy) {
        List<String> keys = List.of(review.getPhotos());

        reviewLikeRepository.deleteAllByReviewId(review.getId());
        review.delete(deletedBy.toString());

        deleteAfterCommit(keys);
    }

    // 객체를 커밋 뒤에 하나씩 지웁니다.
    //
    // 하나씩 넘기는 이유는 실패를 잡아 삼키는 단위가 작업 하나이기 때문입니다.
    // 여러 개를 한 작업에 담으면 앞엣것이 실패했을 때 뒤엣것이 아예 실행되지 않습니다.
    private void deleteAfterCommit(List<String> keys) {
        for (String key : keys) {
            afterCommitExecutor.run(
                () -> storageProvider.delete(key),
                "후기 사진 삭제: key=" + key
            );
        }
    }

    // 설정에 있는 태그만 받습니다.
    //
    // 같은 태그를 여러 번 보내도 한 번만 남기고 보낸 순서를 지킵니다.
    private List<String> toAllowedTags(List<String> tags) {
        if (tags.isEmpty()) {
            return List.of();
        }

        Set<String> allowed = Set.copyOf(reviewTagProvider.findAll());
        Set<String> unique = new LinkedHashSet<>();

        for (String tag : tags) {
            if (!allowed.contains(tag)) {
                log.warn("추천 태그에 없는 값입니다: tag={}", tag);
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }
            unique.add(tag);
        }

        return List.copyOf(unique);
    }

    // 사진 주소를 키로 바꿉니다.
    //
    // 본인 자리(reviews/{accountId}/)의 서명 없는 주소만 받습니다.
    // 이 검사가 없으면 남의 사진을 자기 후기에 붙일 수 있고,
    // 나중에 후기를 지우면서 그 키의 객체까지 지우게 됩니다.
    private List<String> toOwnedPhotoKeys(UUID accountId, List<String> photos) {
        if (photos.isEmpty()) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (String photo : photos) {
            String key = storageProvider.extractOwnedKey(photo, accountId)
                .orElseThrow(() -> {
                    log.warn("본인 자리의 사진 주소가 아닙니다: accountId={}", accountId);
                    return new CustomException(CommonErrorCode.VALIDATION_FAILED);
                });
            unique.add(key);
        }

        return new ArrayList<>(unique);
    }

    // 표에 든 키를 보기 주소로 바꿉니다.
    //
    // 주소에는 유효 시간이 있어 저장해 두면 지난 뒤에 사진이 깨집니다.
    // 그래서 표에는 키만 넣고 내보낼 때마다 새로 서명합니다.
    private List<String> signPhotos(PlaceReview review) {
        return Arrays.stream(review.getPhotos())
            .map(storageProvider::presignDownload)
            .toList();
    }

    // 장소 상세에서 받는 정렬입니다.
    //
    // oldest 는 받지 않습니다.
    // 남의 장소 후기를 오래된 순으로 보는 화면이 없고,
    // 내 후기 쪽에만 있는 정렬이라 여기서 허용하면 쓰이지 않는 조합이 늘어납니다.
    private ReviewSort parsePlaceSort(String value) {
        return switch (value) {
            case "recent" -> ReviewSort.RECENT;
            case "rating_desc" -> ReviewSort.RATING_DESC;
            case "rating_asc" -> ReviewSort.RATING_ASC;
            default -> throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        };
    }

    // 내 후기에서 받는 정렬입니다. 네 가지 모두 화면에 있습니다.
    private ReviewSort parseMySort(String value) {
        return switch (value) {
            case "recent" -> ReviewSort.RECENT;
            case "oldest" -> ReviewSort.OLDEST;
            case "rating_desc" -> ReviewSort.RATING_DESC;
            case "rating_asc" -> ReviewSort.RATING_ASC;
            default -> throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        };
    }

    public List<String> findTags() {
        return reviewTagProvider.findAll();
    }
}
