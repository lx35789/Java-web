-- GreenThumb plant species seed v3: add Tagetes for model v3.
-- Import:
--   mysql -uroot -p greenthumb --default-character-set=utf8mb4 -e "source db/seed_species_v3.sql"

USE greenthumb;

INSERT INTO plant_species
  (id, common_name, common_name_en, scientific_name, aliases, family, emoji,
   difficulty, base_water_interval_days, water_amount_ml, light_requirement,
   humidity_requirement, temp_min_c, temp_max_c, fertilizer_interval_days,
   fertilizer_type, soil_type, growth_rate, max_height_cm, toxicity, toxic_to,
   model_class_index, recognition_provider, description, care_tips,
   season_coefficients, source)
VALUES
  (15, '万寿菊', 'Marigold', 'Tagetes erecta',
   '["臭菊花","万寿灯"]', '菊科', '🌼', 'EASY',
   5, 250, 'DIRECT', 'MEDIUM', 10.0, 35.0, 30,
   '磷钾肥为主', '疏松排水良好的营养土', 'FAST', 80,
   'NONE', '[]', NULL, 'SELF_MODEL',
   '喜光耐热，花量大，但叶片病害和虫害需要早发现早处理。',
   '["每天保证充足直射光","浇水见干见湿，避免盆内积水","定期检查叶背是否有蚜虫或红蜘蛛"]',
   '{"SPRING":1.0,"SUMMER":0.85,"AUTUMN":1.0,"WINTER":1.8}', 'MANUAL')
ON DUPLICATE KEY UPDATE
  common_name = VALUES(common_name),
  common_name_en = VALUES(common_name_en),
  aliases = VALUES(aliases),
  family = VALUES(family),
  emoji = VALUES(emoji),
  description = VALUES(description),
  care_tips = VALUES(care_tips);
