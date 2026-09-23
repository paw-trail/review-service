package com.pawtrail.review.application.service;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.input.ReviewCreateInput;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.ReviewCreatedOutput;
import com.pawtrail.review.application.dto.output.PlaceReviewListOutput;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
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
