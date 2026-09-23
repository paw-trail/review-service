package com.pawtrail.review.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    // 주소를 서명하는 쪽입니다. 파일은 이 서버를 거치지 않습니다.
    @Bean
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .build();
    }

    // 객체를 지우는 쪽입니다.
    //
    // 지우기는 서명된 주소로 할 수 없어 서버가 직접 부릅니다.
    // 사진을 바꾸거나 후기를 지울 때 옛 객체를 치우는 데만 씁니다.
    //
    // 인증 정보는 코드에 두지 않습니다.
    // DefaultCredentialsProvider 가 환경변수 AWS_ACCESS_KEY_ID ·
    // AWS_SECRET_ACCESS_KEY 를 읽습니다.
    @Bean
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.region()))
            .build();
    }
}
