package com.pawtrail.review.application.service;

import com.pawtrail.review.application.dto.output.ReviewCountOutput;
import com.pawtrail.review.application.dto.output.ReviewPeriodOutput;
import com.pawtrail.review.application.dto.output.ReviewStatOutput;
import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewPet;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewPetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 다른 서비스가 부르는 조회입니다.
//
// 공개 API 와 나눈 이유는 쓰는 쪽이 다르기 때문입니다.
// 이쪽은 화면이 아니라 서비스가 부르고, 봉투도 카드 모양이 아니라 필요한 칸만 담습니다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final ReviewPetRepository reviewPetRepository;

    // 마이페이지의 후기 수입니다.
    public ReviewCountOutput countByAccount(UUID accountId) {
        return new ReviewCountOutput(reviewRepository.countActiveByAccountId(accountId));
    }

    // 즐겨찾기 카드와 검색 색인이 쓰는 평점입니다.
    //
    // 후기가 없는 장소는 응답에서 빠집니다.
    // 0건을 0.0 으로 내보내면 "평점 0점" 과 구별되지 않아, 없으면 없는 대로 둡니다.
    public List<ReviewStatOutput> findStatsByPlaceIds(Collection<UUID> placeIds) {
        return reviewRepository.findActiveStatisticsByPlaceIds(placeIds).stream()
            .map(ReviewStatOutput::from)
            .toList();
    }

    // 하루 요약이 쓰는 기간 조회입니다.
    //
    // 방문일 기준이며 양끝을 포함합니다.
    // 부르는 쪽이 그 하루의 날짜를 from 과 to 에 같은 값으로 넣습니다.
    //
    // 견종은 후기마다 목록으로 나갑니다.
    // 그 하루에 여러 마리와 다녀왔을 수 있어 한 칸으로는 담기지 않습니다.
    public List<ReviewPeriodOutput> findByAccountAndPeriod(
        UUID accountId,
        LocalDate from,
        LocalDate to
    ) {
        List<PlaceReview> reviews = reviewRepository
            .findActiveByAccountIdAndVisitedAtBetween(accountId, from, to);

        Map<UUID, List<ReviewPet>> pets = reviewPetRepository.findByReviewIds(
            reviews.stream().map(PlaceReview::getId).toList()
        );

        return reviews.stream()
            .map(review -> ReviewPeriodOutput.from(
                review,
                pets.getOrDefault(review.getId(), List.of())
            ))
            .toList();
    }
}
