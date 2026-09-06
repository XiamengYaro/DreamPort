-- DreamPort v1.1.x 照片墙留言(首页时光照片墙,按时间线条目稳定键 photo_key 存取)

CREATE TABLE dp_photo_comment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    photo_key   VARCHAR(128) NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  BIGINT       NOT NULL,
    INDEX idx_photo_comment_key (photo_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
