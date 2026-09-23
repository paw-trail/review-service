CREATE TABLE place_review
(
  id                    uuid          PRIMARY KEY,
  place_id              uuid          NOT NULL,
  account_id            uuid          NOT NULL,
  pet_id                uuid          NOT NULL,

  visited_at            date          NOT NULL,

  rating                smallint      NOT NULL,
  facility_score        smallint      NOT NULL,
  rule_score            smallint      NOT NULL,
  mood_score            smallint      NOT NULL,

  content               varchar(1000) NOT NULL,
  photos                text[]        NOT NULL,
  tags                  text[]        NOT NULL,
  like_count             integer       NOT NULL DEFAULT 0,

  pet_breed_at_visit    varchar(40),
  pet_weight_at_visit   numeric(4,1),
  pet_size_at_visit     varchar(12),

  created_at            timestamp     NOT NULL,
  created_by            varchar(45)   NOT NULL,
  updated_at            timestamp     NOT NULL,
  updated_by            varchar(45)   NOT NULL,
  deleted_at            timestamp,
  deleted_by            varchar(45),

  CONSTRAINT chk_review_rating
    CHECK (rating BETWEEN 1 AND 5),

  CONSTRAINT chk_review_facility_score
    CHECK (facility_score BETWEEN 1 AND 5),

  CONSTRAINT chk_review_rule_score
    CHECK (rule_score BETWEEN 1 AND 5),

  CONSTRAINT chk_review_mood_score
    CHECK (mood_score BETWEEN 1 AND 5)
);

CREATE INDEX idx_review_place
  ON place_review(place_id, created_at DESC);

CREATE INDEX idx_review_account
  ON place_review(account_id);

CREATE TABLE review_like
(
  review_id  uuid      NOT NULL,
  account_id uuid      NOT NULL,
  created_at timestamp NOT NULL,

  PRIMARY KEY (review_id, account_id),

  CONSTRAINT fk_review_like_review
    FOREIGN KEY (review_id) REFERENCES place_review(id) ON DELETE CASCADE
);

CREATE FUNCTION sync_review_like_count()
RETURNS trigger AS
$$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE place_review
    SET like_count = like_count + 1
    WHERE id = NEW.review_id;

    RETURN NEW;
  END IF;

  UPDATE place_review
  SET like_count = like_count - 1
  WHERE id = OLD.review_id;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_review_like_count
AFTER INSERT OR DELETE ON review_like
FOR EACH ROW
EXECUTE FUNCTION sync_review_like_count();
