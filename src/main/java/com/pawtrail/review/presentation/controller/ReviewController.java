package com.pawtrail.review.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.review.application.dto.output.MyReviewOutput;
import com.pawtrail.review.application.dto.output.ReviewCreatedOutput;
import com.pawtrail.review.application.dto.output.PlaceReviewListOutput;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.application.service.ReviewService;
import com.pawtrail.review.presentation.request.CreateReviewRequest;
import com.pawtrail.review.presentation.request.UpdateReviewRequest;
import com.pawtrail.review.presentation.request.UploadUrlRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    // 한 번에 받을 수 있는 최대 개수입니다.
    //
    // 목록을 그리려면 장소 이름이나 작성자 이름을 다른 서비스에서 받아 와야 하는데
    // 그쪽 조회 API 가 한 번에 100개까지만 받습니다.
    // 이 값을 그보다 크게 두면 목록 한 쪽을 채우는 데 호출이 여러 번 필요해집니다.
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;

    @GetMapping("/places/{placeId}/reviews")
    public CommonApiResponse<PlaceReviewListOutput> findByPlace(
        @PathVariable UUID placeId,
        @CurrentUser CustomUserPrincipal principal,
        @RequestParam(defaultValue = "recent") String sort,
        @RequestParam(defaultValue = "false") boolean photoOnly,
        @RequestParam(defaultValue = "0") @PositiveOrZero int page,
        @RequestParam(defaultValue = "20") @Positive @Max(MAX_PAGE_SIZE) int size
    ) {
        return CommonApiResponse.success(reviewService.findByPlace(
            principal.accountId(),
            principal.role(),
            placeId,
            sort,
            photoOnly,
            page,
            size
        ));
    }

    @PostMapping("/places/{placeId}/reviews")
    public ResponseEntity<CommonApiResponse<ReviewCreatedOutput>> create(
        @PathVariable UUID placeId,
        @CurrentUser CustomUserPrincipal principal,
        @Valid @RequestBody CreateReviewRequest request
    ) {
        ReviewCreatedOutput output = reviewService.create(
            principal.accountId(),
            placeId,
            request.toInput()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonApiResponse.success(output));
    }

    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<CommonApiResponse<Void>> update(
        @PathVariable UUID reviewId,
        @CurrentUser CustomUserPrincipal principal,
        @Valid @RequestBody UpdateReviewRequest request
    ) {
        reviewService.update(principal.accountId(), reviewId, request.toInput());

        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<CommonApiResponse<Void>> delete(
        @PathVariable UUID reviewId,
        @CurrentUser CustomUserPrincipal principal
    ) {
        reviewService.delete(principal.accountId(), reviewId);

        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    @GetMapping("/reviews/me")
    public CommonApiResponse<PageResponse<MyReviewOutput>> findMine(
        @CurrentUser CustomUserPrincipal principal,
        @RequestParam(defaultValue = "recent") String sort,
        @RequestParam(defaultValue = "0") @PositiveOrZero int page,
        @RequestParam(defaultValue = "20") @Positive @Max(MAX_PAGE_SIZE) int size
    ) {
        return CommonApiResponse.success(reviewService.findMine(
            principal.accountId(),
            sort,
            page,
            size
        ));
    }

    @PostMapping("/reviews/{reviewId}/like")
    public ResponseEntity<CommonApiResponse<Void>> like(
        @PathVariable UUID reviewId,
        @CurrentUser CustomUserPrincipal principal
    ) {
        reviewService.like(principal.accountId(), reviewId);

        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    @DeleteMapping("/reviews/{reviewId}/like")
    public ResponseEntity<CommonApiResponse<Void>> unlike(
        @PathVariable UUID reviewId,
        @CurrentUser CustomUserPrincipal principal
    ) {
        reviewService.unlike(principal.accountId(), reviewId);

        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    @GetMapping("/reviews/tags")
    public CommonApiResponse<List<String>> findTags() {
        return CommonApiResponse.success(reviewService.findTags());
    }

    @PostMapping("/reviews/upload-url")
    public CommonApiResponse<UploadUrlOutput> createUploadUrl(
        @CurrentUser CustomUserPrincipal principal,
        @Valid @RequestBody UploadUrlRequest request
    ) {
        return CommonApiResponse.success(reviewService.createUploadUrl(
            principal.accountId(),
            request.fileName(),
            request.contentType(),
            request.contentLength()
        ));
    }
}
