-- 후기 한 건에 반려동물을 여러 마리 담기 위한 자식 표입니다.
--
-- 반려동물 한 마리는 값 하나가 아니라 칸 넷짜리 행입니다.
-- place_review 에 배열 넷을 나란히 두면 순서가 어긋나도 DB 가 잡아 주지 못하므로
-- place_facility · policy_evidence 와 같은 모양의 자식 표로 둡니다.

CREATE TABLE review_pet
(
  review_id   uuid          NOT NULL,
  pet_id      uuid          NOT NULL,

  -- 작성 요청의 petIds 순서입니다. 0 부터 셉니다.
  --
  -- 화면이 배지를 "골든리트리버 · 28.5kg, 말티즈 · 3.2kg" 처럼 이어 붙이고
  -- 자리가 모자라면 뒤를 "외 N마리" 로 줄이므로, 먼저 보이는 아이가
  -- 사용자가 먼저 고른 아이여야 합니다.
  --
  -- 수정에서 반려동물을 바꾸지 않으므로 이 값은 작성할 때 한 번 쓰고 끝입니다.
  sort_order  smallint      NOT NULL,

  -- 방문 당시의 값을 복사해 둡니다.
  -- 반려동물의 체중이나 크기는 나중에 바뀌는데, 후기는 "그때 그 아이로 다녀온 기록" 이라
  -- 지금 값으로 다시 그리면 글의 뜻이 달라집니다.
  --
  -- 세 칸 모두 NULL 을 받습니다. 견종을 정하지 않은 아이가 있고,
  -- V20 에서 옮겨 오는 행에도 빈 값이 들어 있습니다.
  breed_name  varchar(40),
  weight_kg   numeric(4,1),
  breed_size  varchar(12),

  -- 한 후기에 같은 아이가 두 번 들어가는 것을 기본 키가 막습니다.
  -- review_id 로 찾는 조회도 이 인덱스를 그대로 씁니다.
  PRIMARY KEY (review_id, pet_id),

  CONSTRAINT chk_review_pet_sort_order
    CHECK (sort_order >= 0),

  CONSTRAINT fk_review_pet_review
    FOREIGN KEY (review_id) REFERENCES place_review(id) ON DELETE CASCADE
);

-- 이미 쓰여 있는 후기를 한 줄씩 옮깁니다.
--
-- 지운 후기도 함께 옮깁니다.
-- 소프트 삭제라 행이 그대로 남아 있고, 신고를 되짚을 때 무엇이 있었는지가 보여야 합니다.
--
-- 옮기는 시점에는 후기마다 아이가 한 마리뿐이라 sort_order 는 모두 0 입니다.
INSERT INTO review_pet (review_id, pet_id, sort_order, breed_name, weight_kg, breed_size)
SELECT id,
       pet_id,
       0,
       pet_breed_at_visit,
       pet_weight_at_visit,
       pet_size_at_visit
FROM place_review;

-- 옮긴 칸은 부모 표에서 뺍니다.
-- 두 곳에 같은 사실을 두면 한쪽만 고치는 사고가 납니다.
ALTER TABLE place_review
  DROP COLUMN pet_id,
  DROP COLUMN pet_breed_at_visit,
  DROP COLUMN pet_weight_at_visit,
  DROP COLUMN pet_size_at_visit;
