package com.pawtrail.review.presentation.controller;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.review.application.dto.output.ReviewCountOutput;
import com.pawtrail.review.application.dto.output.ReviewPeriodOutput;
import com.pawtrail.review.application.dto.output.ReviewStatOutput;
import com.pawtrail.review.application.service.InternalReviewService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 다른 서비스가 부르는 입구입니다. 화면은 이 경로를 부르지 않습니다.
//
// 이 경로에는 로그인 검사가 걸려 있지 않습니다. 망 안에서만 닿기 때문입니다.
// 다만 그것이 소유권 검증 면제는 아닙니다.
// 남의 accountId 를 넣으면 남의 후기를 받아 갈 수 있으므로,
// 계정이 들어오는 경로는 게이트웨이가 실어 보낸 X-User-Id 와 대조합니다.
//
// 장소 평점은 계정이 들어가지 않고 장소 단위라 대조할 것이 없습니다.
// 검색이 재색인 배치로 부를 때는 사용자 요청이 아니라 헤더도 없습니다.
@Slf4j
@RestController
@RequestMapping("/internal/reviews")
@RequiredArgsConstructor
@Validated
public class InternalReviewController {

    // 한 번에 물어볼 수 있는 장소 수입니다.
    // 부르는 쪽(즐겨찾기 · 검색)이 이미 100개씩 나눠 부르고 있고,
    // place 의 조회 API 도 같은 값을 씁니다.
    private static final int MAX_PLACE_IDS = 100;

    private final InternalReviewService internalReviewService;

    @GetMapping("/count")
    public ResponseEntity<CommonApiResponse<ReviewCountOutput>> count(
        @CurrentUser CustomUserPrincipal principal,
        @RequestParam UUID accountId
    ) {
        requireSameAccount(principal, accountId);

        return ResponseEntity.ok(CommonApiResponse.success(
            internalReviewService.countByAccount(accountId)
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<CommonApiResponse<List<ReviewStatOutput>>> stats(
        @RequestParam
        @Size(max = MAX_PLACE_IDS, message = "한 번에 100개까지 조회할 수 있습니다")
        List<UUID> placeIds
    ) {
        return ResponseEntity.ok(CommonApiResponse.success(
            internalReviewService.findStatsByPlaceIds(placeIds)
        ));
    }

    @GetMapping
    public ResponseEntity<CommonApiResponse<List<ReviewPeriodOutput>>> findByPeriod(
        @CurrentUser CustomUserPrincipal principal,
        @RequestParam UUID accountId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        requireSameAccount(principal, accountId);

        return ResponseEntity.ok(CommonApiResponse.success(
            internalReviewService.findByAccountAndPeriod(accountId, from, to)
        ));
    }

    // 게이트웨이가 넣은 계정과 물어본 계정이 같은지 봅니다.
    //
    // 헤더가 아예 없으면 401 입니다.
    // 사용자 요청을 따라 들어온 호출이면 부르는 쪽이 그 헤더를 그대로 실어 보냅니다.
    private void requireSameAccount(CustomUserPrincipal principal, UUID accountId) {
        if (principal == null) {
            throw new CustomException(CommonErrorCode.AUTHENTICATION_FAILED);
        }

        if (!principal.accountId().equals(accountId)) {
            log.warn(
                "남의 계정을 물었습니다: caller={}, accountId={}",
                principal.accountId(),
                accountId
            );
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
