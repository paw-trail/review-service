package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.dto.UserSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ReviewDetailOutput(
    UUID reviewId,
    short rating,
    short facilityScore,
    short ruleScore,
    short moodScore,
    String content,
    List<String> photos,
    List<String> tags,
    int likeCount,
    boolean likedByMe,
    boolean isMine,
    boolean canDelete,
    LocalDate visitedAt,
    Author author,
    PetSummary petSummary
) {
    public static ReviewDetailOutput of(
        PlaceReview review,
        UserSummary author,
        boolean likedByMe,
        boolean isMine,
        boolean canDelete
    ) {
        return new ReviewDetailOutput(
            review.getId(),
            review.getRating(),
            review.getFacilityScore(),
            review.getRuleScore(),
            review.getMoodScore(),
            review.getContent(),
            List.copyOf(Arrays.asList(review.getPhotos())),
            List.copyOf(Arrays.asList(review.getTags())),
            review.getLikeCount(),
            likedByMe,
            isMine,
            canDelete,
            review.getVisitedAt(),
            new Author(author.nickname(), author.profileImageUrl()),
            new PetSummary(
                review.getPetBreedAtVisit(),
                review.getPetWeightAtVisit(),
                review.getPetSizeAtVisit()
            )
        );
    }

    public record Author(String nickname, String profileImageUrl) {
    }

    public record PetSummary(String breedName, BigDecimal weightKg, String breedSize) {
    }
}
