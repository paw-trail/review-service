package com.pawtrail.review.application.service;

import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private PlaceReviewRepository reviewRepository;

    @Mock
    private ReviewLikeRepository reviewLikeRepository;

    @Mock
    private UserProvider userProvider;

    @Mock
    private PlaceProvider placeProvider;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void returnsEmptyPageWithoutLookingUpPlacesWhenThereAreNoReviews() {
        UUID accountId = UUID.randomUUID();
        when(reviewRepository.findActiveByAccountId(
            accountId,
            ReviewSort.RECENT,
            0,
            200
        )).thenReturn(new ReviewPage<>(List.of(), 0, 200, 0, 0));

        PageResponse<MyReviewOutput> result = reviewService.findMine(
            accountId,
            "recent",
            0,
            200
        );

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.page().number());
        assertEquals(200, result.page().size());
        assertEquals(0, result.page().totalElements());
        assertEquals(0, result.page().totalPages());
        verifyNoInteractions(placeProvider);
    }
}
