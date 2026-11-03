-- ============================================================
-- 植语助手 GreenThumb AI — 数据库建表脚本
-- MySQL 8.0+ / utf8mb4
--
-- 相对原始方案的变化：
--   [新增] user_environment   规则引擎的环境输入源（原方案缺失）
--   [新增] disease_info       病害类别 → 处理建议
--   [新增] knowledge_chunk    RAG 的检索单元（原方案只有 article）
--   [新增] qa_message         qa_session 存不下多轮对话
--   [新增] recognition_result Top-3 识别结果规范化
--   [新增] symptom_rule       生理性症状判因规则（黄叶/焦边/落叶）
--   [新增] cause_info         症状病因字典
--   [新增] plant_observation  植物状态观测（时间序列 → 健康趋势曲线 + 计划自适应）
--   [修正] plant_species.model_class_index  植物库 50 种 ↔ 模型 47 类映射
--   [修正] care_task 唯一键  保证定时任务幂等
--   [修正] care_plan : care_task = 1 : N
--
-- 向量检索由 Redis Stack (RediSearch) 承担，
-- knowledge_chunk.embedding_id 保存向量库中的主键。
-- ============================================================

CREATE DATABASE IF NOT EXISTS greenthumb
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE greenthumb;

-- ------------------------------------------------------------
-- 1. 用户
-- ------------------------------------------------------------
CREATE TABLE `user` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username        VARCHAR(64)     NOT NULL                COMMENT '登录名',
  password_hash   VARCHAR(128)    NOT NULL                COMMENT 'BCrypt',
  nickname        VARCHAR(64)     DEFAULT NULL,
  avatar_url      VARCHAR(512)    DEFAULT NULL,
  email           VARCHAR(128)    DEFAULT NULL,
  phone           VARCHAR(32)     DEFAULT NULL,
  role            VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
  status          TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  last_login_at   DATETIME        DEFAULT NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_email (email),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ------------------------------------------------------------
-- 2. 用户环境（原方案缺失，规则引擎的输入源）
--    个性化养护公式：植物需水量 × 季节系数 × 光照系数 × 天气系数
--    - 季节系数：由 due_date 推导
--    - 天气系数：由 latitude/longitude 调 Open-Meteo 推导
--    - 光照系数：存在 user_plant.light_level（同一用户不同房间光照不同）
-- ------------------------------------------------------------
CREATE TABLE `user_environment` (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,
  city                  VARCHAR(64)     DEFAULT NULL      COMMENT '用户填写的城市名',
  latitude              DECIMAL(9,6)    DEFAULT NULL      COMMENT '城市名经 Geocoding 解析后缓存',
  longitude             DECIMAL(9,6)    DEFAULT NULL,
  timezone              VARCHAR(64)     DEFAULT 'Asia/Shanghai',
  climate_zone          VARCHAR(32)     DEFAULT NULL      COMMENT '热带/亚热带/温带/寒带',
  has_air_conditioner   TINYINT         NOT NULL DEFAULT 0 COMMENT '空调房 → 湿度低 → 浇水间隔缩短',
  has_heating           TINYINT         NOT NULL DEFAULT 0 COMMENT '暖气 → 冬季空气干燥',
  indoor_humidity_level VARCHAR(16)     DEFAULT 'MEDIUM'  COMMENT 'LOW / MEDIUM / HIGH 用户主观感受',
  weather_sync_enabled  TINYINT         NOT NULL DEFAULT 0,
  weather_synced_at     DATETIME        DEFAULT NULL,
  created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id),
  CONSTRAINT fk_env_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户环境（规则引擎输入）';

-- ------------------------------------------------------------
-- 3. 植物种类库（50 种）
-- ------------------------------------------------------------
CREATE TABLE `plant_species` (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  common_name         VARCHAR(64)     NOT NULL            COMMENT '中文名 绿萝',
  common_name_en      VARCHAR(128)    DEFAULT NULL        COMMENT 'English  Pothos',
  scientific_name     VARCHAR(128)    DEFAULT NULL        COMMENT 'Epipremnum aureum',
  aliases             JSON            DEFAULT NULL        COMMENT '["魔鬼藤","黄金葛"]',
  family              VARCHAR(64)     DEFAULT NULL,
  emoji               VARCHAR(8)      DEFAULT NULL        COMMENT '列表卡片展示用 🌿',
  difficulty          VARCHAR(16)     DEFAULT 'EASY'      COMMENT 'EASY / MEDIUM / HARD',

  -- 养护规则（规则引擎的基准值）
  base_water_interval_days   INT      NOT NULL DEFAULT 7  COMMENT '基准浇水间隔(天)，再乘各系数',
  water_amount_ml            INT      DEFAULT NULL        COMMENT '单次建议水量',
  light_requirement          VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT 'FULL_SUN/BRIGHT_INDIRECT/MEDIUM/LOW',
  humidity_requirement       VARCHAR(16) DEFAULT 'MEDIUM',
  temp_min_c                 DECIMAL(4,1) DEFAULT NULL,
  temp_max_c                 DECIMAL(4,1) DEFAULT NULL,
  fertilizer_interval_days   INT      DEFAULT 30,
  fertilizer_type            VARCHAR(64) DEFAULT NULL      COMMENT '氮肥为主/磷钾肥/缓释肥',
  soil_type                  VARCHAR(128) DEFAULT NULL,
  growth_rate                VARCHAR(16) DEFAULT NULL,
  max_height_cm              INT      DEFAULT NULL,

  -- 安全属性
  toxicity                   VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/TOXIC',
  toxic_to                   JSON        DEFAULT NULL      COMMENT '[{"target":"cat","level":"HIGH"}]',

  -- 与识别模型的映射（关键：50 种植物库 ≠ 47 类模型）
  model_class_index          INT         DEFAULT NULL      COMMENT '模型输出类别索引；NULL=模型未覆盖，走 Pl@ntNet',
  recognition_provider       VARCHAR(16) DEFAULT 'SELF_MODEL' COMMENT 'SELF_MODEL / PLANTNET / NONE',

  image_url           VARCHAR(512)    DEFAULT NULL,
  description         TEXT            DEFAULT NULL,
  care_tips           JSON            DEFAULT NULL        COMMENT '["避免积水","冬季减少浇水"]',
  season_coefficients JSON            DEFAULT NULL        COMMENT '{"spring":1.0,"summer":0.7,"autumn":1.0,"winter":1.6} 间隔倍数',
  source              VARCHAR(32)     DEFAULT 'MANUAL'    COMMENT 'MANUAL / WIKIPEDIA / GBIF',
  source_url          VARCHAR(512)    DEFAULT NULL,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scientific_name (scientific_name),
  KEY idx_common_name (common_name),
  KEY idx_model_class (model_class_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植物种类库';

-- ------------------------------------------------------------
-- 4. 我的植物
--    位置 / 光照放在这里而非 user_environment：
--    同一用户客厅与卧室的光照完全不同
-- ------------------------------------------------------------
CREATE TABLE `user_plant` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  species_id      BIGINT UNSIGNED NOT NULL,
  nickname        VARCHAR(64)     DEFAULT NULL            COMMENT '用户起的名字 小绿',
  location        VARCHAR(64)     DEFAULT NULL            COMMENT '客厅/卧室/阳台',
  light_level     VARCHAR(16)     DEFAULT NULL            COMMENT 'DIRECT/BRIGHT_INDIRECT/MEDIUM/LOW',
  pot_size        VARCHAR(16)     DEFAULT NULL            COMMENT 'SMALL/MEDIUM/LARGE',
  pot_material    VARCHAR(16)     DEFAULT NULL            COMMENT '陶盆/塑料/陶瓷（影响失水速度）',
  soil_type       VARCHAR(64)     DEFAULT NULL,
  acquired_date   DATE            DEFAULT NULL,
  health_status   VARCHAR(16)     NOT NULL DEFAULT 'HEALTHY' COMMENT 'HEALTHY/WARNING/DISEASED/DEAD',
  cover_photo_id  BIGINT UNSIGNED DEFAULT NULL,
  add_source      VARCHAR(16)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/RECOGNITION/NATURAL_LANGUAGE',
  note            TEXT            DEFAULT NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, deleted),
  KEY idx_species (species_id),
  CONSTRAINT fk_up_user    FOREIGN KEY (user_id)    REFERENCES `user`(id)         ON DELETE CASCADE,
  CONSTRAINT fk_up_species FOREIGN KEY (species_id) REFERENCES `plant_species`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='我的植物';

-- ------------------------------------------------------------
-- 5. 照片
-- ------------------------------------------------------------
CREATE TABLE `plant_photo` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  user_plant_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '识别阶段可空，确认后再回填',
  url             VARCHAR(512)    NOT NULL,
  thumb_url       VARCHAR(512)    DEFAULT NULL,
  storage_type    VARCHAR(16)     NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL / MINIO',
  file_size       INT             DEFAULT NULL            COMMENT 'bytes',
  width           INT             DEFAULT NULL,
  height          INT             DEFAULT NULL,
  photo_type      VARCHAR(16)     NOT NULL DEFAULT 'WHOLE' COMMENT 'WHOLE/LEAF/DISEASE',
  taken_at        DATETIME        DEFAULT NULL            COMMENT '用于成长时间线排序',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_plant (user_plant_id, taken_at),
  KEY idx_user (user_id),
  CONSTRAINT fk_photo_user  FOREIGN KEY (user_id)       REFERENCES `user`(id)       ON DELETE CASCADE,
  CONSTRAINT fk_photo_plant FOREIGN KEY (user_plant_id) REFERENCES `user_plant`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植物照片';

-- ------------------------------------------------------------
-- 6. 识别记录 + Top-3 结果
-- ------------------------------------------------------------
CREATE TABLE `recognition_record` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  photo_id        BIGINT UNSIGNED NOT NULL,
  provider        VARCHAR(16)     NOT NULL DEFAULT 'SELF_MODEL' COMMENT 'SELF_MODEL / PLANTNET',
  model_version   VARCHAR(32)     DEFAULT NULL            COMMENT 'mobilenetv3-v1.0',
  top1_species_id BIGINT UNSIGNED DEFAULT NULL            COMMENT '冗余，便于统计查询',
  top1_confidence DECIMAL(5,4)    DEFAULT NULL,
  latency_ms      INT             DEFAULT NULL,
  confirmed       TINYINT         NOT NULL DEFAULT 0      COMMENT '用户是否采纳了结果',
  user_plant_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '采纳后生成的植物',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_top1 (top1_species_id),
  CONSTRAINT fk_rec_user  FOREIGN KEY (user_id)  REFERENCES `user`(id)        ON DELETE CASCADE,
  CONSTRAINT fk_rec_photo FOREIGN KEY (photo_id) REFERENCES `plant_photo`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='识别记录';

CREATE TABLE `recognition_result` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  record_id     BIGINT UNSIGNED NOT NULL,
  rank_no       TINYINT         NOT NULL                  COMMENT '1/2/3。rank 是 MySQL 8 保留字，用 rank_no',
  species_id    BIGINT UNSIGNED DEFAULT NULL              COMMENT '可空：API 返回的物种可能不在这 50 种内',
  raw_name      VARCHAR(128)    DEFAULT NULL              COMMENT '模型/API 原始输出名',
  confidence    DECIMAL(5,4)    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_record_rank (record_id, rank_no),
  CONSTRAINT fk_rr_record FOREIGN KEY (record_id) REFERENCES `recognition_record`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='识别结果 Top-3';

-- ------------------------------------------------------------
-- 7. 病害知识 + 病害记录
-- ------------------------------------------------------------
CREATE TABLE `disease_info` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code          VARCHAR(64)     NOT NULL                  COMMENT '模型类别码 aloe_rust',
  name_cn       VARCHAR(64)     NOT NULL,
  name_en       VARCHAR(128)    DEFAULT NULL,
  eppo_code     VARCHAR(16)     DEFAULT NULL              COMMENT 'Pl@ntNet 返回的 EPPO 编码',
  category      VARCHAR(16)     NOT NULL                  COMMENT 'FUNGAL/BACTERIAL/PEST/PHYSIOLOGICAL',
  pathogen      VARCHAR(128)    DEFAULT NULL,
  symptoms      TEXT            DEFAULT NULL,
  causes        TEXT            DEFAULT NULL,
  treatment     TEXT            DEFAULT NULL              COMMENT '处理建议（识别结果直接返回）',
  prevention    TEXT            DEFAULT NULL,
  severity      VARCHAR(16)     DEFAULT 'MEDIUM',
  applicable_species JSON       DEFAULT NULL              COMMENT '[1,7,23] 物种条件化模型用；NULL=通用',
  image_url     VARCHAR(512)    DEFAULT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害类别与处理建议';

CREATE TABLE `disease_record` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  user_plant_id   BIGINT UNSIGNED DEFAULT NULL,
  -- 注意：症状判因（生理性）可以没有照片，只有病害识别才一定有，所以这里必须允许 NULL
  photo_id        BIGINT UNSIGNED DEFAULT NULL,
  provider        VARCHAR(16)     NOT NULL DEFAULT 'SELF_MODEL',
  disease_info_id BIGINT UNSIGNED DEFAULT NULL,
  predicted_code  VARCHAR(64)     DEFAULT NULL,
  confidence      DECIMAL(5,4)    DEFAULT NULL,
  top_k           JSON            DEFAULT NULL            COMMENT '[{"code":"leaf_spot","p":0.21}]',
  -- 症状 vs 病害分流：模型判不了"黄叶"，走症状诊断
  diagnosis_type  VARCHAR(16)     NOT NULL DEFAULT 'DISEASE' COMMENT 'DISEASE(病理性) / SYMPTOM(生理性)',
  symptom_tags    JSON            DEFAULT NULL            COMMENT '["黄叶","叶尖焦枯"] 症状路径的输入',
  suggestion      TEXT            DEFAULT NULL            COMMENT 'LLM 润色后的自然语言建议',
  status          VARCHAR(16)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/TREATING/RESOLVED',
  resolved_at     DATETIME        DEFAULT NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, created_at),
  KEY idx_plant (user_plant_id),
  CONSTRAINT fk_dr_user  FOREIGN KEY (user_id)       REFERENCES `user`(id)        ON DELETE CASCADE,
  CONSTRAINT fk_dr_photo FOREIGN KEY (photo_id)      REFERENCES `plant_photo`(id)  ON DELETE CASCADE,
  CONSTRAINT fk_dr_info  FOREIGN KEY (disease_info_id) REFERENCES `disease_info`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害/症状诊断记录';

-- ------------------------------------------------------------
-- 7b. 症状判因规则（生理性症状：黄叶/焦边/落叶…）
--     详见 docs/03-症状诊断规则库.md
--     设计要点：规则入库而非硬编码，便于管理员调权重
-- ------------------------------------------------------------
CREATE TABLE `symptom_rule` (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  symptom_code   VARCHAR(32)     NOT NULL               COMMENT 'YELLOW_LEAF / LEAF_TIP_BURN / ...',
  cause_code     VARCHAR(32)     NOT NULL               COMMENT 'OVERWATER / LOW_LIGHT / ...',
  condition_json JSON            NOT NULL               COMMENT '触发条件，多字段为 AND，如 {"leaf_position":"OLD","watering_ratio":{"op":">=","value":1.5}}',
  weight         DECIMAL(3,2)    NOT NULL               COMMENT '命中权重 0.00-1.00，同病因可累加',
  explain_tpl    VARCHAR(255)    DEFAULT NULL           COMMENT '可解释模板，支持 {ratio} 等占位',
  priority       INT             NOT NULL DEFAULT 0     COMMENT '同分排序',
  enabled        TINYINT         NOT NULL DEFAULT 1,
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_symptom (symptom_code, enabled),
  KEY idx_cause (cause_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='症状判因规则';

CREATE TABLE `cause_info` (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  cause_code   VARCHAR(32)     NOT NULL,
  name_cn      VARCHAR(64)     NOT NULL               COMMENT '浇水过多 / 烂根',
  category     VARCHAR(16)     NOT NULL               COMMENT 'WATER/LIGHT/NUTRIENT/ENV/PEST/NORMAL',
  description  TEXT            DEFAULT NULL,
  action_plan  TEXT            DEFAULT NULL           COMMENT '分步骤处理建议',
  urgency      VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW/MEDIUM/HIGH，前端按此配色',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cause_code (cause_code),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='症状病因字典';

-- ------------------------------------------------------------
-- 8. 养护计划（1）: 养护任务（N）
-- ------------------------------------------------------------
CREATE TABLE `care_plan` (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,
  user_plant_id         BIGINT UNSIGNED NOT NULL,
  plan_type             VARCHAR(16)     NOT NULL          COMMENT 'WATER/FERTILIZE/REPOT/PRUNE',
  season                VARCHAR(8)      DEFAULT NULL      COMMENT 'SPRING/SUMMER/AUTUMN/WINTER',
  base_interval_days    INT             NOT NULL          COMMENT '物种基准间隔',
  effective_interval_days INT           NOT NULL          COMMENT '乘完系数后的实际间隔',
  coefficient_detail    JSON            DEFAULT NULL      COMMENT '{"season":0.7,"light":1.2,"weather":0.9} 可解释性用',
  weather_snapshot      JSON            DEFAULT NULL      COMMENT '生成时的天气快照，便于复盘',
  start_date            DATE            NOT NULL,
  end_date              DATE            DEFAULT NULL,
  is_active             TINYINT         NOT NULL DEFAULT 1,
  generated_by          VARCHAR(16)     NOT NULL DEFAULT 'RULE' COMMENT 'RULE / LLM / MANUAL',
  llm_explain           TEXT            DEFAULT NULL      COMMENT '「本周建议浇水2次，因为空调房湿度低」',
  created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted               TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_plant_active (user_plant_id, plan_type, is_active),
  KEY idx_user (user_id),
  CONSTRAINT fk_cp_user  FOREIGN KEY (user_id)       REFERENCES `user`(id)       ON DELETE CASCADE,
  CONSTRAINT fk_cp_plant FOREIGN KEY (user_plant_id) REFERENCES `user_plant`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养护计划';

CREATE TABLE `care_task` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id         BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  user_plant_id   BIGINT UNSIGNED NOT NULL,
  task_type       VARCHAR(16)     NOT NULL                COMMENT 'WATER/FERTILIZE/REPOT/PRUNE',
  due_date        DATE            NOT NULL,
  status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/SKIPPED/OVERDUE',
  completed_at    DATETIME        DEFAULT NULL,
  care_log_id     BIGINT UNSIGNED DEFAULT NULL            COMMENT '标记完成后指向生成的日志',
  remind_at       DATETIME        DEFAULT NULL,
  reminded        TINYINT         NOT NULL DEFAULT 0,
  note            VARCHAR(255)    DEFAULT NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 定时任务幂等的关键：同计划同日期只允许一条
  UNIQUE KEY uk_plan_due (plan_id, due_date),
  KEY idx_user_due (user_id, due_date, status),
  KEY idx_remind (reminded, remind_at),
  CONSTRAINT fk_ct_plan  FOREIGN KEY (plan_id)       REFERENCES `care_plan`(id)  ON DELETE CASCADE,
  CONSTRAINT fk_ct_plant FOREIGN KEY (user_plant_id) REFERENCES `user_plant`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养护提醒任务';

-- ------------------------------------------------------------
-- 9. 养护日志
-- ------------------------------------------------------------
CREATE TABLE `care_log` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  user_plant_id   BIGINT UNSIGNED NOT NULL,
  task_id         BIGINT UNSIGNED DEFAULT NULL            COMMENT '计划外补录时为空',
  log_type        VARCHAR(16)     NOT NULL                COMMENT 'WATER/FERTILIZE/REPOT/PRUNE/PEST',
  amount          DECIMAL(8,2)    DEFAULT NULL            COMMENT '水量 ml / 肥量 g',
  unit            VARCHAR(16)     DEFAULT NULL,
  detail          VARCHAR(512)    DEFAULT NULL,
  photo_id        BIGINT UNSIGNED DEFAULT NULL,
  logged_at       DATETIME        NOT NULL                COMMENT '实际发生时间，统计看板按此聚合',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_plant_time (user_plant_id, logged_at),
  KEY idx_user_time (user_id, logged_at),
  KEY idx_task (task_id),
  CONSTRAINT fk_cl_user  FOREIGN KEY (user_id)       REFERENCES `user`(id)       ON DELETE CASCADE,
  CONSTRAINT fk_cl_plant FOREIGN KEY (user_plant_id) REFERENCES `user_plant`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养护日志';

-- ------------------------------------------------------------
-- 9b. 植物状态观测（时间序列）
--     记录「植物变成了什么样」，与 care_log（人做了什么）互补。
--     有了它才能：① 画健康趋势曲线 ② 让养护计划从开环变闭环
--     详见 docs/13-状态追踪与个性化验证.md
-- ------------------------------------------------------------
CREATE TABLE `plant_observation` (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_plant_id   BIGINT UNSIGNED NOT NULL,
  observed_at     DATETIME        NOT NULL              COMMENT '观测时刻',

  source          VARCHAR(24)     NOT NULL              COMMENT 'PHOTO/MANUAL/DIAGNOSIS/REMINDER',
  photo_id        BIGINT UNSIGNED DEFAULT NULL          COMMENT '若来自照片',
  diagnosis_id    BIGINT UNSIGNED DEFAULT NULL          COMMENT '若来自诊断记录',

  -- 用户主观指标
  leaf_score      TINYINT         DEFAULT NULL          COMMENT '1饱满 2一般 3发蔫 4很差',
  growth_score    TINYINT         DEFAULT NULL          COMMENT '1有新叶 2无变化 3有落叶',
  yellow_leaf     TINYINT         DEFAULT NULL          COMMENT '0无 1少 2中 3多',

  -- 客观指标：由照片经 HSV 色彩分割自动算出（python ai-service）
  green_ratio     DECIMAL(5,4)    DEFAULT NULL          COMMENT '绿色像素占比',
  yellow_ratio    DECIMAL(5,4)    DEFAULT NULL          COMMENT '黄色像素占比',
  brown_ratio     DECIMAL(5,4)    DEFAULT NULL          COMMENT '褐色/枯焦像素占比',

  health_index    TINYINT         NOT NULL              COMMENT '0-100 综合健康指数（画曲线用）',
  note            VARCHAR(255)    DEFAULT NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_plant_time (user_plant_id, observed_at),
  KEY idx_diagnosis (diagnosis_id),
  CONSTRAINT fk_po_plant FOREIGN KEY (user_plant_id) REFERENCES `user_plant`(id) ON DELETE CASCADE,
  CONSTRAINT fk_po_photo FOREIGN KEY (photo_id)      REFERENCES `plant_photo`(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植物状态观测（时间序列）';

-- ------------------------------------------------------------
-- 10. RAG 知识库：文章 + 分块
--     每条必须带 source/license，回答时标注来源
-- ------------------------------------------------------------
CREATE TABLE `knowledge_article` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title         VARCHAR(255)    NOT NULL,
  source        VARCHAR(32)     NOT NULL                  COMMENT 'WIKIPEDIA/MANUAL/PAPER',
  source_url    VARCHAR(512)    DEFAULT NULL,
  license       VARCHAR(64)     DEFAULT NULL              COMMENT 'CC BY-SA 4.0',
  attribution   VARCHAR(255)    DEFAULT NULL              COMMENT '署名要求',
  language      VARCHAR(8)      NOT NULL DEFAULT 'zh',
  category      VARCHAR(64)     DEFAULT NULL              COMMENT '浇水/光照/病害/施肥',
  tags          JSON            DEFAULT NULL,
  content       LONGTEXT        DEFAULT NULL              COMMENT '原文',
  content_hash  CHAR(64)        DEFAULT NULL              COMMENT '去重',
  chunk_count   INT             NOT NULL DEFAULT 0,
  embed_status  VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
  version       INT             NOT NULL DEFAULT 1,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_hash (content_hash),
  KEY idx_category (category),
  KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库文章';

CREATE TABLE `knowledge_chunk` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  article_id    BIGINT UNSIGNED NOT NULL,
  chunk_index   INT             NOT NULL,
  content       TEXT            NOT NULL,
  token_count   INT             DEFAULT NULL,
  species_id    BIGINT UNSIGNED DEFAULT NULL              COMMENT '若 chunk 针对特定植物，检索时可加权',
  embedding_id  VARCHAR(128)    DEFAULT NULL              COMMENT 'Redis 向量库中的 key',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_article_chunk (article_id, chunk_index),
  KEY idx_embedding (embedding_id),
  KEY idx_species (species_id),
  CONSTRAINT fk_kc_article FOREIGN KEY (article_id) REFERENCES `knowledge_article`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识分块';

-- ------------------------------------------------------------
-- 11. 问答：会话 + 消息（原方案 qa_session 一张表存不下多轮）
-- ------------------------------------------------------------
CREATE TABLE `qa_session` (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  user_plant_id BIGINT UNSIGNED DEFAULT NULL              COMMENT '针对某盆植物的追问，可注入其养护历史',
  title         VARCHAR(255)    DEFAULT NULL              COMMENT '首条提问截断',
  message_count INT             NOT NULL DEFAULT 0,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id, updated_at),
  CONSTRAINT fk_qs_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话';

CREATE TABLE `qa_message` (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id       BIGINT UNSIGNED NOT NULL,
  role             VARCHAR(16)     NOT NULL               COMMENT 'USER / ASSISTANT',
  content          TEXT            NOT NULL,
  retrieved_chunks JSON            DEFAULT NULL           COMMENT '[{"chunk_id":12,"score":0.87}] 可溯源/可评测',
  citations        JSON            DEFAULT NULL           COMMENT '回答中标注的来源',
  model            VARCHAR(64)     DEFAULT NULL,
  prompt_tokens    INT             DEFAULT NULL,
  completion_tokens INT            DEFAULT NULL,
  latency_ms       INT             DEFAULT NULL,
  feedback         TINYINT         DEFAULT NULL           COMMENT '1有用 -1无用 NULL未评',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id, created_at),
  CONSTRAINT fk_qm_session FOREIGN KEY (session_id) REFERENCES `qa_session`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答消息';

-- ============================================================
-- 关键查询示例
-- ============================================================

-- 提醒日历：某用户某月的所有任务
-- SELECT t.due_date, t.task_type, p.nickname, s.common_name
-- FROM care_task t
-- JOIN user_plant p ON p.id = t.user_plant_id
-- JOIN plant_species s ON s.id = p.species_id
-- WHERE t.user_id = ? AND t.due_date BETWEEN ? AND ?
-- ORDER BY t.due_date;

-- 统计看板：我的植物数量 / 浇水次数 / 健康分布
-- SELECT health_status, COUNT(*) FROM user_plant
-- WHERE user_id = ? AND deleted = 0 GROUP BY health_status;
-- SELECT COUNT(*) FROM care_log
-- WHERE user_id = ? AND log_type = 'WATER' AND logged_at >= ?;

-- 连续未完成提醒 → 触发重新生成计划
-- SELECT plan_id, COUNT(*) missed FROM care_task
-- WHERE status = 'OVERDUE' GROUP BY plan_id HAVING missed >= 3;

-- 模型覆盖率：植物库里有多少种能走自研模型
-- SELECT recognition_provider, COUNT(*) FROM plant_species
-- WHERE deleted = 0 GROUP BY recognition_provider;
