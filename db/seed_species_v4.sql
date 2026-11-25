-- GreenThumb plant species seed v4: 蝴蝶兰（兰花）
-- 通用 44 类模型里没有兰花，病害走专用模型 specialized/orchid/；
-- 物种识别本模型不覆盖，故 model_class_index 留 NULL、provider 走 PLANTNET。
-- Import:
--   mysql -uroot -p greenthumb --default-character-set=utf8mb4 -e "source db/seed_species_v4.sql"

USE greenthumb;

INSERT INTO plant_species
  (id, common_name, common_name_en, scientific_name, aliases, family, emoji,
   difficulty, base_water_interval_days, water_amount_ml, light_requirement,
   humidity_requirement, temp_min_c, temp_max_c, fertilizer_interval_days,
   fertilizer_type, soil_type, growth_rate, max_height_cm, toxicity, toxic_to,
   model_class_index, recognition_provider, description, care_tips,
   season_coefficients, source)
VALUES
  (16, '蝴蝶兰', 'Phalaenopsis', 'Phalaenopsis aphrodite',
   '["兰花","蝶兰","洋兰"]', '兰科', '🌸', 'MEDIUM',
   7, 150, 'BRIGHT_INDIRECT', 'HIGH', 18.0, 28.0, 21,
   '兰花专用肥（薄肥勤施）', '水苔 / 松树皮 + 少量陶粒', 'SLOW', 50,
   'NONE', '[]', NULL, 'PLANTNET',
   '最常见的年宵花，花期长但怕积水闷根。附生习性，根系需要透气，用树皮或水苔而不是普通营养土。',
   '["浇透后一定倒掉托盘积水，根泡水 2 天就会烂","叶心不能积水，浇水后用餐巾纸吸干，否则心腐一夜之间毁株","喜明亮散射光，正午直射会日灼，长期阴暗则不开花","花谢后从花梗第 2-3 节剪断，有机会二次开花"]',
   '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":1.0,"WINTER":1.3}', 'MANUAL')
ON DUPLICATE KEY UPDATE
  common_name = VALUES(common_name),
  common_name_en = VALUES(common_name_en),
  aliases = VALUES(aliases),
  family = VALUES(family),
  emoji = VALUES(emoji),
  description = VALUES(description),
  care_tips = VALUES(care_tips);
