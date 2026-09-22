package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record MyReviewOutput(
    UUID reviewId,
    UUID placeId,
    String placeName,
    short rating,
    String content,
    List<String> photos,
    List<String> tags,
    int likeCount,
    LocalDate visitedAt,
    PetSummary petSummary
) {
    public static MyReviewOutput of(PlaceReview review, PlaceSummary place) {
        return new MyReviewOutput(
            review.getId(),
            review.getPlaceId(),
            place.name(),
            review.getRating(),
            review.getContent(),
            List.copyOf(Arrays.asList(review.getPhotos())),
            List.copyOf(Arrays.asList(review.getTags())),
            review.getLikeCount(),
            review.getVisitedAt(),
            new PetSummary(review.getPetBreedAtVisit(), review.getPetSizeAtVisit())
        );
    }

    public record PetSummary(String breedName, String breedSize) {
    }
}
