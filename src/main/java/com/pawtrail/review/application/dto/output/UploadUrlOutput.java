package com.pawtrail.review.application.dto.output;

// 사진을 올릴 주소를 알려 주는 응답입니다.
//
// uploadUrl  서명된 PUT 주소입니다. 브라우저가 여기로 파일을 직접 올립니다.
// fileUrl    서명이 붙지 않은 주소입니다. 작성 요청이 이 값을 그대로 보내면
//            서버가 키를 뽑아 저장하고, 보여 줄 때마다 새로 서명합니다.
// expiresIn  uploadUrl 이 살아 있는 시간(초)입니다.
public record UploadUrlOutput(
    String uploadUrl,
    String fileUrl,
    long expiresIn
) {
}
