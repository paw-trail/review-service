package com.pawtrail.review.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 후기 사진을 두는 S3 설정입니다.
//
// 값은 config 저장소의 review-service.yml 에 있고 액세스 키는 여기 없습니다.
// 키는 환경변수 AWS_ACCESS_KEY_ID · AWS_SECRET_ACCESS_KEY 로 들어옵니다.
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(

    @NotBlank String bucket,

    @NotBlank String region,

    // 올리기 주소의 유효 시간입니다.
    // 서명 방식이 7일까지만 허용하므로 그 값을 상한으로 둡니다.
    @Positive
    @Max(604800)
    long uploadExpiresSeconds,

    // 보기 주소의 유효 시간입니다.
    //
    // 후기 목록을 열 때마다 새로 서명하므로 길 필요가 없습니다.
    // 짧게 두면 주소가 밖으로 새어도 금방 쓸모없어집니다.
    @Positive
    @Max(604800)
    long downloadExpiresSeconds,

    // 올릴 수 있는 이미지 한 장의 최대 크기(바이트)입니다.
    //
    // 이 값을 넘으면 주소를 아예 발급하지 않으므로 S3 로 요청이 가지도 않습니다.
    @Positive
    long maxImageBytes
) {
}
