package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewPet;
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
    List<PetSummary> pets
) {
    // photoUrls 는 서명된 보기 주소입니다. 표에는 키가 들어 있습니다.
    //
    // pets 는 sort_order 순으로 들어옵니다.
    // 이 화면의 배지는 견종과 크기만 쓰므로 체중은 담지 않습니다.
    public static MyReviewOutput of(
        PlaceReview review,
        PlaceSummary place,
        List<String> photoUrls,
        List<ReviewPet> pets
    ) {
        return new MyReviewOutput(
            review.getId(),
            review.getPlaceId(),
            place.name(),
            review.getRating(),
            review.getContent(),
            List.copyOf(photoUrls),
            List.copyOf(Arrays.asList(review.getTags())),
            review.getLikeCount(),
            review.getVisitedAt(),
            pets.stream()
                .map(pet -> new PetSummary(pet.getBreedName(), pet.getBreedSize()))
                .toList()
        );
    }

    public record PetSummary(String breedName, String breedSize) {
    }
}
