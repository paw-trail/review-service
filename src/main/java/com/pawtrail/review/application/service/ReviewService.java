package com.pawtrail.review.application.service;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.UserProvider;
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

}
