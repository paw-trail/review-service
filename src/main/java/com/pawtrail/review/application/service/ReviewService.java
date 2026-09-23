package com.pawtrail.review.application.service;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.PlaceReviewListOutput;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.ReviewTagProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final UserProvider userProvider;
    private final ReviewTagProvider reviewTagProvider;
    private final StorageProvider storageProvider;
    private final PlaceProvider placeProvider;

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
                    isMine || role == Role.ADMIN
                );
            })
            .toList();

        return PlaceReviewListOutput.of(content, reviews, summary);
    }

    public UploadUrlOutput createUploadUrl(UUID accountId, String fileName, String contentType) {
        return UploadUrlOutput.from(
            storageProvider.createReviewUpload(accountId, fileName, contentType)
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
                )
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
