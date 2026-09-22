package com.pawtrail.review.application.service;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
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

    private final PlaceProvider placeProvider;

    public PageResponse<ReviewDetailOutput> findByPlace(
        UUID accountId,
        Role role,
        UUID placeId,
        int page,
        int size
    ) {
        ReviewPage<PlaceReview> reviews = reviewRepository.findActiveByPlaceId(
            placeId,
            page,
            size
        );
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

        return new PageResponse<>(
            content,
            new PageResponse.PageInfo(
                reviews.number(),
                reviews.size(),
                reviews.totalElements(),
                reviews.totalPages()
            )
        );
    }

    public PageResponse<MyReviewOutput> findMine(
        UUID accountId,
        String sortValue,
        int page,
        int size
    ) {
        ReviewSort sort = parseSort(sortValue);
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

    private ReviewSort parseSort(String value) {
        return switch (value) {
            case "recent" -> ReviewSort.RECENT;
            case "oldest" -> ReviewSort.OLDEST;
            case "rating_desc" -> ReviewSort.RATING_DESC;
            case "rating_asc" -> ReviewSort.RATING_ASC;
            default -> throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        };
    }


}
