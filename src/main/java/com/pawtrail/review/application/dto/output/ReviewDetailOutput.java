package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewPet;
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
    List<PetSummary> pets
) {
    // photoUrls 는 서명된 보기 주소입니다.
    // 표에는 키가 들어 있고 유효 시간이 있는 주소는 저장하지 않으므로
    // 서비스가 그때그때 서명해 넘깁니다.
    //
    // pets 는 sort_order 순으로 들어옵니다. 작성할 때 고른 순서입니다.
    // 단수 칸(petSummary)을 함께 두지 않습니다.
    // 같은 사실이 두 곳에 있으면 한쪽만 고치는 사고가 나고,
    // 단수 칸은 "대표 한 마리" 라는 없던 개념을 만듭니다.
    public static ReviewDetailOutput of(
        PlaceReview review,
        UserSummary author,
        boolean likedByMe,
        boolean isMine,
        boolean canDelete,
        List<String> photoUrls,
        List<ReviewPet> pets
    ) {
        return new ReviewDetailOutput(
            review.getId(),
            review.getRating(),
            review.getFacilityScore(),
            review.getRuleScore(),
            review.getMoodScore(),
            review.getContent(),
            List.copyOf(photoUrls),
            List.copyOf(Arrays.asList(review.getTags())),
            review.getLikeCount(),
            likedByMe,
            isMine,
            canDelete,
            review.getVisitedAt(),
            new Author(author.nickname(), author.profileImageUrl()),
            pets.stream()
                .map(pet -> new PetSummary(
                    pet.getBreedName(),
                    pet.getWeightKg(),
                    pet.getBreedSize()
                ))
                .toList()
        );
    }

    public record Author(String nickname, String profileImageUrl) {
    }

    public record PetSummary(String breedName, BigDecimal weightKg, String breedSize) {
    }
}
