package com.pawtrail.review.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 후기 한 건에 함께 다녀온 반려동물 한 마리입니다.
 *
 * BaseEntity 를 상속하지 않습니다.
 * 후기에 딸려 만들어지고 후기와 함께 사라지는 표라
 * 누가 언제 만들었는지는 부모인 place_review 가 이미 들고 있습니다.
 *
 * 값을 고치는 메서드가 없습니다.
 * 수정에서 반려동물을 바꾸지 않기로 했으므로 이 행은 작성할 때 한 번 쓰고 끝입니다.
 * 잘못 골랐다면 후기를 지우고 다시 씁니다.
 *
 * PlaceReview 에 @OneToMany 로 매달지 않습니다.
 * place_facility 와 같은 방식이며, 목록 화면이 후기 스무 건을 그릴 때
 * 매달아 두면 아이를 찾는 조회가 스무 번 따로 나갑니다.
 * 저장소가 후기 id 목록으로 한 번에 받아 옵니다.
 */
@Entity
@Table(name = "review_pet")
@IdClass(ReviewPetId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPet {

    @Id
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Id
    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    // 작성 요청에서 고른 순서입니다. 0 부터 셉니다.
    // 화면이 배지를 이어 붙이고 모자라면 뒤를 줄이므로 먼저 고른 아이가 먼저 나와야 합니다.
    @Column(name = "sort_order", nullable = false, updatable = false)
    private short sortOrder;

    // 방문 당시의 값입니다. 셋 다 비어 있을 수 있습니다.
    @Column(name = "breed_name", length = 40, updatable = false)
    private String breedName;

    @Column(name = "weight_kg", precision = 4, scale = 1, updatable = false)
    private BigDecimal weightKg;

    @Column(name = "breed_size", length = 12, updatable = false)
    private String breedSize;

    private ReviewPet(
        UUID reviewId,
        UUID petId,
        short sortOrder,
        String breedName,
        BigDecimal weightKg,
        String breedSize
    ) {
        this.reviewId = reviewId;
        this.petId = petId;
        this.sortOrder = sortOrder;
        this.breedName = breedName;
        this.weightKg = weightKg;
        this.breedSize = breedSize;
    }

    public static ReviewPet create(
        UUID reviewId,
        UUID petId,
        short sortOrder,
        String breedName,
        BigDecimal weightKg,
        String breedSize
    ) {
        if (reviewId == null || petId == null) {
            throw new IllegalArgumentException("reviewId 와 petId 는 필수입니다.");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder 는 0 이상이어야 합니다.");
        }
        return new ReviewPet(reviewId, petId, sortOrder, breedName, weightKg, breedSize);
    }
}
