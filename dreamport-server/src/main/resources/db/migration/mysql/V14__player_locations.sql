-- 玩家个人位置:家(Essentials userdata 采集)+ 上次死亡(插件死亡监听采集)
-- 网页地图「我的位置」数据源,GET /api/user/locations 仅返回本人数据(JWT);多服按 username 最后写入为准
CREATE TABLE dp_player_locations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(32)  NOT NULL,                -- 账号名(与 JWT 主体一致)
    uuid       VARCHAR(36)  NULL,
    homes      MEDIUMTEXT   NULL,                    -- JSON 数组:[{name,world,x,y,z}]
    last_death TEXT         NULL,                    -- JSON:{world,x,y,z,diedAt}
    updated_at BIGINT       NOT NULL,
    UNIQUE KEY uk_player_locations (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
