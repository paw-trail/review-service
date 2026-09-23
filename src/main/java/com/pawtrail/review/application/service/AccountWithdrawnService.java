package com.pawtrail.review.application.service;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 탈퇴한 계정의 흔적을 지웁니다.
//
// 사용자 삭제와 달리 행을 남기지 않습니다.
// 사용자 삭제는 "이 글을 내린다" 이지만 탈퇴는 "이 사람의 것을 지운다" 이고,
// 남겨 둘 근거가 되는 신고나 통계도 그 계정과 함께 사라지기 때문입니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountWithdrawnService {

    private final PlaceReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final StorageProvider storageProvider;

    // 사진 삭제를 트랜잭션 안에서 합니다.
    //
    // 다른 자리(수정 · 삭제)는 커밋 뒤에 지우고 실패하면 로그만 남깁니다.
    // 여기는 다릅니다. 지우겠다고 한 약속이라 조용히 실패하면 안 되고,
    // 실패가 재시도와 DLQ 에 닿아야 다시 시도할 길이 남습니다.
    //
    // 대신 사진 삭제가 실패하면 후기 삭제까지 함께 되돌아갑니다.
    // 다시 받은 메시지가 처음부터 하게 되므로 결과는 같습니다.
    @Transactional
    public void withdraw(UUID accountId) {
        List<PlaceReview> reviews = reviewRepository.findAllByAccountIdForUpdate(accountId);

        List<UUID> reviewIds = reviews.stream()
            .map(PlaceReview::getId)
            .toList();

        List<String> photoKeys = reviews.stream()
            .flatMap(review -> Arrays.stream(review.getPhotos()))
            .toList();

        // 그 사람의 후기에 남들이 누른 좋아요입니다.
        //
        // 표의 외래키가 ON DELETE CASCADE 라 후기를 지우면 따라 지워지지만,
        // 그때는 지워질 후기의 like_count 를 트리거가 고치려 들어 순서가 엉킵니다.
        // 먼저 지우고 나서 후기를 지웁니다.
        reviewLikeRepository.deleteAllByReviewIds(reviewIds);
        reviewRepository.hardDeleteAll(reviews);

        // 그 사람이 남의 후기에 누른 좋아요입니다.
        // 이 행들이 빠지면서 남의 후기 like_count 가 트리거로 줄어듭니다.
        reviewLikeRepository.deleteAllByAccountId(accountId);

        for (String key : photoKeys) {
            storageProvider.delete(key);
        }

        log.info(
            "탈퇴한 계정의 후기를 정리했습니다: accountId={}, review={}, photo={}",
            accountId,
            reviews.size(),
            photoKeys.size()
        );
    }
}
