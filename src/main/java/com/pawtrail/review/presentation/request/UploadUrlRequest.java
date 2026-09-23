package com.pawtrail.review.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadUrlRequest(

    @NotBlank
    @Size(max = 255)
    String fileName,

    @NotBlank
    @Pattern(regexp = "image/(jpeg|png)")
    String contentType,

    // 올릴 파일의 크기(바이트)입니다.
    //
    // 이 값이 서명에 들어가므로 다른 크기로 올리면 S3 가 거부합니다.
    // 프론트는 file.size 를 그대로 보내야 하며 한 바이트라도 다르면 403 이 납니다.
    // 상한을 넘는지는 서비스가 봅니다.
    @NotNull
    @Positive
    Long contentLength
) {
}
