package com.pawtrail.review.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.exception.ReviewErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "place_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReview extends BaseEntity {

    private static final short MIN_SCORE = 1;
    private static final short MAX_SCORE = 5;
    private static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "pet_id", nullable = false)
    private UUID petId;

    @Column(name = "visited_at", nullable = false)
    private LocalDate visitedAt;

    @Column(nullable = false)
    private short rating;

    @Column(name = "facility_score", nullable = false)
    private short facilityScore;

    @Column(name = "rule_score", nullable = false)
    private short ruleScore;

    @Column(name = "mood_score", nullable = false)
    private short moodScore;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] photos = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private int likeCount;

    // 사진이 한 장이라도 있는지를 조회에서 거르려고 둔 읽기 전용 칸입니다.
    //
    // photos 는 text[] 한 칸이라 JPQL 로는 길이를 물을 수 없습니다.
    // @Formula 는 이 식을 그대로 SELECT 에 실어 주므로
    // 저장소가 photoCount > 0 조건으로 사진 있는 후기만 고를 수 있습니다.
    //
    // 표에는 없는 칸이라 저장·수정 대상이 아니며 ddl-auto: validate 도 보지 않습니다.
    @Formula("cardinality(photos)")
    private Integer photoCount;

    @Column(name = "pet_breed_at_visit", length = 40)
    private String petBreedAtVisit;

    @Column(name = "pet_weight_at_visit", precision = 4, scale = 1)
    private BigDecimal petWeightAtVisit;

    @Column(name = "pet_size_at_visit", length = 12)
    private String petSizeAtVisit;

    public static PlaceReview create(
        UUID placeId,
        UUID accountId,
        UUID petId,
        LocalDate visitedAt,
        short rating,
        short facilityScore,
        short ruleScore,
        short moodScore,
        String content,
        List<String> photos,
        List<String> tags,
        String petBreedAtVisit,
        BigDecimal petWeightAtVisit,
        String petSizeAtVisit
    ) {
        validateScores(rating, facilityScore, ruleScore, moodScore);
        validateContent(content);

        PlaceReview review = new PlaceReview();
        review.placeId = placeId;
        review.accountId = accountId;
        review.petId = petId;
        review.visitedAt = visitedAt;
        review.rating = rating;
        review.facilityScore = facilityScore;
        review.ruleScore = ruleScore;
        review.moodScore = moodScore;
        review.content = content;
        review.photos = toArray(photos);
        review.tags = toArray(tags);
        review.likeCount = 0;
        review.petBreedAtVisit = petBreedAtVisit;
        review.petWeightAtVisit = petWeightAtVisit;
        review.petSizeAtVisit = petSizeAtVisit;
        return review;
    }

    public void update(
        Short rating,
        Short facilityScore,
        Short ruleScore,
        Short moodScore,
        String content,
        List<String> photos,
        List<String> tags
    ) {
        short nextRating = rating == null ? this.rating : rating;
        short nextFacilityScore = facilityScore == null ? this.facilityScore : facilityScore;
        short nextRuleScore = ruleScore == null ? this.ruleScore : ruleScore;
        short nextMoodScore = moodScore == null ? this.moodScore : moodScore;
        String nextContent = content == null ? this.content : content;

        validateScores(nextRating, nextFacilityScore, nextRuleScore, nextMoodScore);
        validateContent(nextContent);

        this.rating = nextRating;
        this.facilityScore = nextFacilityScore;
        this.ruleScore = nextRuleScore;
        this.moodScore = nextMoodScore;
        this.content = nextContent;
        if (photos != null) {
            this.photos = toArray(photos);
        }
        if (tags != null) {
            this.tags = toArray(tags);
        }
    }

    public void replaceLikeCount(long likeCount) {
        this.likeCount = Math.toIntExact(likeCount);
    }

    public String[] getPhotos() {
        return photos.clone();
    }

    public String[] getTags() {
        return tags.clone();
    }

    private static void validateScores(short... scores) {
        for (short score : scores) {
            if (score < MIN_SCORE || score > MAX_SCORE) {
                throw new CustomException(ReviewErrorCode.INVALID_REVIEW_SCORE);
            }
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw new CustomException(ReviewErrorCode.INVALID_REVIEW_CONTENT);
        }
    }

    private static String[] toArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new String[0];
        }
        return values.toArray(String[]::new);
    }
}
