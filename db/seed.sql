-- ============================================================
-- 植语助手 —— 种子数据
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 < db/seed.sql
-- 可重复执行（INSERT IGNORE + 固定主键）
-- ============================================================

USE greenthumb;

-- ------------------------------------------------------------
-- 植物种类库（14 种常见室内/阳台植物）
-- 1-10 为常见室内绿植；11-14 来自病害训练数据集，用于与模型覆盖物种对齐
-- ------------------------------------------------------------
INSERT IGNORE INTO `plant_species`
(id, common_name, common_name_en, scientific_name, aliases, family, emoji, difficulty,
 base_water_interval_days, water_amount_ml, light_requirement, humidity_requirement,
 temp_min_c, temp_max_c, fertilizer_interval_days, fertilizer_type, soil_type,
 growth_rate, max_height_cm, toxicity, toxic_to, model_class_index, recognition_provider,
 description, care_tips, season_coefficients, source)
VALUES
(1,'绿萝','Pothos','Epipremnum aureum','["魔鬼藤","黄金葛"]','天南星科','🌿','EASY',
 7,200,'MEDIUM','MEDIUM',15.0,30.0,30,'氮肥为主的观叶肥','疏松营养土',
 'FAST',200,'TOXIC','[{"target":"cat","level":"HIGH"},{"target":"dog","level":"MEDIUM"}]',33,'SELF_MODEL',
 '最常见的室内观叶植物，耐阴、好养、生长快，能适应较弱的光照环境。',
 '["避免盆内积水，这是绿萝死亡的头号原因","冬季减少浇水，等土干透再浇","汁液有微毒，接触后洗手，避免宠物啃食"]',
 '{"SPRING":1.0,"SUMMER":0.8,"AUTUMN":1.0,"WINTER":1.5}','MANUAL'),

(2,'龟背竹','Monstera','Monstera deliciosa','["蓬莱蕉"]','天南星科','🍃','EASY',
 10,300,'BRIGHT_INDIRECT','HIGH',15.0,30.0,30,'观叶肥','疏松营养土',
 'MEDIUM',250,'TOXIC','[{"target":"cat","level":"HIGH"},{"target":"dog","level":"HIGH"}]',30,'SELF_MODEL',
 '叶片有天然孔洞，喜明亮散射光和较高湿度，是典型的"颜值担当"。',
 '["喜欢高湿度，干燥时叶缘容易焦","忌强光直射，会晒伤叶片","叶片可用湿布擦拭除尘"]',
 '{"SPRING":1.0,"SUMMER":0.85,"AUTUMN":1.0,"WINTER":1.6}','MANUAL'),

(3,'虎皮兰','Snake Plant','Dracaena trifasciata','["虎尾兰","千岁兰"]','天门冬科','🪴','EASY',
 21,150,'LOW','LOW',12.0,32.0,60,'缓释肥','沙质排水土',
 'SLOW',120,'TOXIC','[{"target":"cat","level":"MEDIUM"},{"target":"dog","level":"MEDIUM"}]',40,'SELF_MODEL',
 '极度耐旱耐阴，几周不浇水也没事，是最适合"手残党"和忙人的植物。',
 '["宁干勿湿，浇水过多是唯一死因","冬季可整月不浇水","耐弱光，但明亮处斑纹更明显"]',
 '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":1.0,"WINTER":2.0}','MANUAL'),

(4,'发财树','Money Tree','Pachira aquatica','["马拉巴栗"]','锦葵科','🌳','MEDIUM',
 14,250,'MEDIUM','MEDIUM',15.0,30.0,45,'均衡复合肥','疏松营养土',
 'SLOW',200,'NONE','[]',NULL,'PLANTNET',
 '寓意招财，木质化的茎干能储水，因此非常怕涝。',
 '["极其怕积水，盆底不能有存水","落叶多为浇水过多或通风不良","光照不足会徒长、茎干变细"]',
 '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":1.0,"WINTER":1.8}','MANUAL'),

(5,'吊兰','Spider Plant','Chlorophytum comosum','["挂兰"]','天门冬科','🌱','EASY',
 8,180,'MEDIUM','MEDIUM',10.0,30.0,30,'观叶肥','通用营养土',
 'FAST',60,'NONE','[]',41,'SELF_MODEL',
 '会抽出小吊兰，净化空气能力强，非常容易繁殖。',
 '["叶尖焦枯最常见原因是自来水中的氟和氯","建议用静置过的水浇","长满盆后要及时分株"]',
 '{"SPRING":1.0,"SUMMER":0.8,"AUTUMN":1.0,"WINTER":1.5}','MANUAL'),

(6,'芦荟','Aloe','Aloe vera','["库拉索芦荟"]','阿福花科','🌵','EASY',
 18,150,'BRIGHT_INDIRECT','LOW',10.0,32.0,60,'多肉专用肥','颗粒沙质土',
 'SLOW',60,'NONE','[]',NULL,'PLANTNET',
 '叶片肥厚能储水，耐旱怕涝，晒足太阳叶片更饱满。',
 '["宁干勿湿，积水必烂根","夏季避免正午暴晒","叶片发软多半是浇水过多"]',
 '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":1.0,"WINTER":2.0}','MANUAL'),

(7,'玉露','Haworthia','Haworthia cooperi','["宝草"]','阿福花科','🪷','MEDIUM',
 14,120,'BRIGHT_INDIRECT','MEDIUM',8.0,28.0,60,'多肉专用肥','颗粒土',
 'SLOW',15,'NONE','[]',NULL,'PLANTNET',
 '叶片晶莹剔透，喜欢明亮的散射光，怕强光直晒。',
 '["夏季高温会休眠，要严格控水","光照过强会发红、干瘪","用颗粒土，透气为第一要务"]',
 '{"SPRING":1.0,"SUMMER":1.6,"AUTUMN":1.0,"WINTER":1.8}','MANUAL'),

(8,'常春藤','Ivy','Hedera helix','["洋常春藤"]','五加科','🍀','MEDIUM',
 7,200,'MEDIUM','HIGH',5.0,25.0,30,'观叶肥','疏松营养土',
 'FAST',300,'TOXIC','[{"target":"cat","level":"MEDIUM"},{"target":"dog","level":"MEDIUM"}]',NULL,'PLANTNET',
 '藤蔓垂挂很好看，但最怕闷热干燥，夏天容易红蜘蛛。',
 '["喜欢凉爽湿润，怕高温闷热","空气干燥时勤喷水","定期检查叶背有没有红蜘蛛"]',
 '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":1.0,"WINTER":1.6}','MANUAL'),

(9,'仙人掌','Cactus','Opuntia microdasys','["黄毛掌"]','仙人掌科','🌵','EASY',
 30,100,'DIRECT','LOW',5.0,38.0,90,'多肉专用肥','沙质排水土',
 'SLOW',80,'NONE','[]',NULL,'PLANTNET',
 '最省心的植物之一，喜欢充足直射光，一个月浇一次都嫌多。',
 '["生长期也要等土完全干透再浇","冬季断水，保持 5℃ 以上","光照不足会变细长、失去形态"]',
 '{"SPRING":1.0,"SUMMER":1.0,"AUTUMN":1.2,"WINTER":3.0}','MANUAL'),

(10,'白掌','Peace Lily','Spathiphyllum wallisii','["一帆风顺"]','天南星科','🌸','MEDIUM',
 7,250,'MEDIUM','HIGH',15.0,30.0,30,'观叶肥','疏松营养土',
 'MEDIUM',100,'TOXIC','[{"target":"cat","level":"HIGH"},{"target":"dog","level":"HIGH"}]',NULL,'PLANTNET',
 '耐阴、开花好看，缺水时叶片会明显下垂——是最好的"会说话"的植物。',
 '["叶片下垂就是缺水信号，浇水后几小时会立起来","喜欢高湿度，可垫水盘增湿","忌强光直射，会晒焦叶片"]',
 '{"SPRING":1.0,"SUMMER":0.8,"AUTUMN":1.0,"WINTER":1.5}','MANUAL'),

-- 以下 4 种来自训练数据集 indoor-plant-disease-dataset（该数据集覆盖 6 物种 29 类），
-- 补进来是为了让「植物库」与「病害模型能识别的物种」对齐，
-- 否则用户给这些植物建档后再拍照，模型会硬猜一个错误结论。
-- model_class_index 统一留 NULL：新模型的 29 个类别是按「物种_病害」命名的，
-- 一个物种对应多个类别，单个整数索引表达不了，物种归属由 labels.json 的 species 字段决定。
(11,'月季','Rose','Rosa chinensis','["月月红","四季花"]','蔷薇科','🌹','MEDIUM',
 6,300,'DIRECT','MEDIUM',5.0,32.0,15,'磷钾肥为主，花期前追肥','疏松肥沃微酸性土',
 'FAST',100,'NONE','[]',NULL,'SELF_MODEL',
 '只要光照够就能月月开花，但也因此最容易得黑斑病，是出了名的"药罐子"。',
 '["每天至少 4-6 小时直射光，否则只长叶不开花","浇水只浇土面，叶面沾水极易诱发黑斑病","花后及时剪掉残花，能促下一茬花","通风不好必得白粉病，别贴墙角放"]',
 '{"SPRING":0.8,"SUMMER":0.9,"AUTUMN":0.9,"WINTER":1.8}','MANUAL'),

(12,'姜黄','Turmeric','Curcuma longa','["郁金","黄姜"]','姜科','🌾','MEDIUM',
 5,250,'BRIGHT_INDIRECT','HIGH',15.0,35.0,30,'均衡复合肥','疏松保水微酸性土',
 'FAST',100,'NONE','[]',NULL,'SELF_MODEL',
 '热带根茎植物，叶片宽大像美人蕉；喜高温高湿，室内养最要注意的是空气湿度。',
 '["怕干不怕湿，但盆底绝不能积水","空气湿度低于 40% 叶缘就会焦","冬季地上部分枯萎是休眠，别扔，根茎还活着","光照不足叶片发黄变薄"]',
 '{"SPRING":1.0,"SUMMER":0.8,"AUTUMN":1.0,"WINTER":1.6}','MANUAL'),

(13,'扶桑','Hibiscus','Hibiscus rosa-sinensis','["朱槿","大红花"]','锦葵科','🌺','MEDIUM',
 4,350,'DIRECT','MEDIUM',12.0,35.0,15,'磷钾肥，花期前增施','疏松肥沃微酸性土',
 'FAST',150,'NONE','[]',NULL,'SELF_MODEL',
 '光照温度合适时几乎全年开花，是"开花机器"，但极度依赖直射光。',
 '["光照不足会大量掉花苞，这是最常见的问题","夏季几乎要天天浇水，一缺水花苞立刻掉","换位置或换盆后短期掉花苞属正常应激","冬季低于 10℃ 必须搬进室内"]',
 '{"SPRING":0.85,"SUMMER":0.8,"AUTUMN":0.95,"WINTER":1.6}','MANUAL'),

(14,'菊花','Chrysanthemum','Chrysanthemum morifolium','["秋菊","黄花"]','菊科','🌼','EASY',
 5,250,'BRIGHT_INDIRECT','MEDIUM',5.0,30.0,20,'磷钾肥为主','疏松肥沃排水土',
 'FAST',80,'TOXIC','[{"target":"cat","level":"MEDIUM"},{"target":"dog","level":"MEDIUM"}]',NULL,'SELF_MODEL',
 '短日照植物，秋季自然开花；盆栽菊花最怕积水和通风不良。',
 '["短日照才开花，晚上别放长期亮灯的房间","极怕涝，盆底积水很快根腐","通风不良易得斑枯病和白粉病","对猫狗有轻毒，注意别让宠物啃食"]',
 '{"SPRING":1.0,"SUMMER":0.9,"AUTUMN":0.85,"WINTER":1.5}','MANUAL');

-- ------------------------------------------------------------
-- 示例植物（demo 用户 3 盆 / admin 用户 1 盆）
-- ------------------------------------------------------------
INSERT IGNORE INTO `user_plant`
(id, user_id, species_id, nickname, location, light_level, pot_size, pot_material, soil_type,
 acquired_date, health_status, add_source, note, created_at)
VALUES
(101,1,1,'小绿','客厅','MEDIUM','MEDIUM','CERAMIC','通用营养土',
 '2026-05-20','WARNING','RECOGNITION','藤蔓已经垂到桌沿，下部有两片老叶发黄','2026-05-20 10:00:00'),
(102,1,2,'大叶','客厅','BRIGHT_INDIRECT','LARGE','TERRACOTTA','疏松营养土',
 '2026-04-10','HEALTHY','MANUAL','刚展开一片新叶，孔洞很明显','2026-04-10 15:20:00'),
(103,1,6,'胖胖','阳台','DIRECT','SMALL','TERRACOTTA','颗粒沙质土',
 '2026-03-02','HEALTHY','MANUAL',NULL,'2026-03-02 11:00:00'),
(104,2,3,'虎妞','卧室','LOW','MEDIUM','PLASTIC','沙质排水土',
 '2025-11-15','HEALTHY','MANUAL',NULL,'2025-11-15 09:30:00');
