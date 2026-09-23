package com.pawtrail.review.domain.model;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * review_pet 의 복합 기본 키입니다.
 *
 * @IdClass 로 씁니다. @EmbeddedId 를 안 쓰는 이유는
 * 필드 접근이 한 겹 깊어져(pet.getId().getReviewId()) 다른 엔티티와 모양이 갈리기 때문입니다.
 * place 의 PlaceFacilityId 가 같은 근거로 @IdClass 를 썼습니다.
 *
 * Serializable 과 equals · hashCode 는 JPA 명세가 요구합니다.
 * 없으면 영속성 컨텍스트가 같은 키를 다른 것으로 보아 조회할 때마다 새 인스턴스가 생깁니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ReviewPetId implements Serializable {

    private UUID reviewId;

    private UUID petId;

    private ReviewPetId(UUID reviewId, UUID petId) {
        this.reviewId = reviewId;
        this.petId = petId;
    }

    public static ReviewPetId of(UUID reviewId, UUID petId) {
        if (reviewId == null || petId == null) {
            throw new IllegalArgumentException("reviewId 와 petId 는 필수입니다.");
        }
        return new ReviewPetId(reviewId, petId);
    }
}
