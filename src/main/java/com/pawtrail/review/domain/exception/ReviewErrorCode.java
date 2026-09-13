package com.pawtrail.review.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "후기를 찾을 수 없습니다."),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 후기를 수정하거나 삭제할 권한이 없습니다."),
    PET_NOT_FOUND(HttpStatus.NOT_FOUND, "반려동물을 찾을 수 없습니다."),
    PET_NOT_OWNED(HttpStatus.FORBIDDEN, "본인의 반려동물로만 후기를 작성할 수 있습니다."),
    INVALID_REVIEW_SCORE(HttpStatus.BAD_REQUEST, "후기 점수는 1점부터 5점까지 입력할 수 있습니다."),
    INVALID_REVIEW_CONTENT(HttpStatus.BAD_REQUEST, "후기 내용은 1자 이상 1000자 이하로 입력해야 합니다."),
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요를 누른 후기입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
