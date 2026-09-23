package com.pawtrail.review.domain.provider;

import java.util.Optional;
import java.util.UUID;

// 후기 사진을 다루는 계약입니다.
//
// 파일은 이 서버를 거치지 않습니다.
// 서명된 주소를 발급해 주고 브라우저가 S3 로 직접 올리고 직접 받습니다.
//
// 저장하는 값은 주소가 아니라 키입니다.
// 서명된 주소는 유효 시간이 지나면 못 쓰게 되므로 그대로 저장하면 사진이 깨집니다.
// 보여 줄 때마다 키로 새 주소를 서명합니다.
public interface StorageProvider {

    // 사진 하나가 들어갈 자리를 정합니다.
    //
    // 계정마다 자리가 갈리고(reviews/{accountId}/) 파일 이름 앞에 무작위 값이 붙습니다.
    // 같은 이름으로 여러 장을 올려도 서로 덮어쓰지 않습니다.
    String newPhotoKey(UUID accountId, String fileName);

    // 키를 가리키는 서명 없는 주소입니다.
    //
    // 올리기 주소를 발급할 때 함께 돌려주며, 작성 요청이 이 주소를 그대로 보냅니다.
    // 서버는 이 주소에서 키를 뽑아 저장합니다.
    String publicUrl(String key);

    // 주소에서 그 계정의 키를 뽑습니다.
    //
    // 우리 버킷의 주소가 아니거나, 남의 자리이거나, 서명이 붙어 있으면 비어 있는 값을 줍니다.
    // 이 검사가 없으면 남의 사진을 자기 후기에 붙일 수 있고,
    // 나중에 그 키로 객체를 지우는 코드가 생기면 남의 파일을 지우는 도구가 됩니다.
    Optional<String> extractOwnedKey(String url, UUID accountId);

    // 올리기 주소를 서명합니다. 형식과 크기가 서명에 들어갑니다.
    String presignUpload(String key, String contentType, long contentLength);

    // 보기 주소를 서명합니다.
    String presignDownload(String key);
}
