package com.pawtrail.review.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.review.application.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 관리자 전용 입구입니다.
//
// 권한 검사는 여기서 하지 않습니다.
// 공통 모듈의 보안 설정이 /api/v1/admin/** 를 ADMIN 에게만 열어 두었고,
// 게이트웨이도 두 번째 마디로 어느 서비스인지를 갈라 보냅니다.
//
// 경로를 사용자 쪽과 나눈 이유는 권한이 다르기 때문입니다.
// 같은 경로에 역할만 달리 보면 규칙이 코드 안으로 숨고, 게이트웨이와 보안 설정이
// 그것을 알 수 없게 됩니다.
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    // 신고를 승인하기 전에 그 후기를 내리는 자리입니다.
    // 지우는 동작은 사용자 삭제와 같습니다.
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<CommonApiResponse<Void>> delete(
        @PathVariable UUID reviewId,
        @CurrentUser CustomUserPrincipal principal
    ) {
        reviewService.deleteByAdmin(principal.accountId(), reviewId);

        return ResponseEntity.ok(CommonApiResponse.success(null));
    }
}
