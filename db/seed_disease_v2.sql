-- ============================================================
-- 病害知识库 v2：新增 3 物种 8 个类别
--
-- 对应模型 v2（37 类 / 9 物种）。必须与 ai-service/models/labels.json 的 code 完全一致，
-- 否则后端查不到知识条目，只能返回病名、给不出处置建议。
--
-- 可重复执行（ON DUPLICATE KEY UPDATE）。
-- 导入：
--   mysql -uroot -p greenthumb --default-character-set=utf8mb4 -e "source db/seed_disease_v2.sql"
-- ============================================================

INSERT INTO disease_info
  (code, name_cn, name_en, category, pathogen, symptoms, causes, treatment, prevention, severity)
VALUES
-- ---------- 仙人掌 ----------
('Cactus_Healthy', '仙人掌·健康', 'Cactus Healthy', 'PHYSIOLOGICAL', NULL,
 '肉质茎饱满挺立，表皮颜色均匀（深绿、灰绿或蓝绿视品种而定），刺座完整无破损，新刺生长正常。未见软腐、木栓化斑块或白色棉絮状虫体。',
 '光照充足、浇水间歇干透、通风良好。',
 '无需处理。保持"干透浇透"的节奏，生长季每月一次稀薄肥即可。',
 '冬季控水（盆土保持干燥）；避免长期放在无直射光的室内，否则茎节会变细徒长。',
 'LOW'),

('Cactus_Dactylopius_Opuntia', '仙人掌·胭脂虫危害', 'Cactus Cochineal Scale (Dactylopius opuntiae)', 'PEST',
 '胭脂虫 Dactylopius opuntiae（半翅目，胭脂虫科）',
 '茎节表面出现白色棉絮状或蜡粉状附着物，揭开后有暗红色体液（这是胭脂虫的色素）；受害部位生长停滞、发黄，严重时茎节干瘪脱落。虫体常聚集在茎节边缘、刺座周围和叶状茎连接处。',
 '通风不良、夏季高温干燥时虫口增长快；新购入植株未隔离；蚂蚁保护虫体、驱赶天敌。',
 '1. 立即与其它多肉隔离（此虫靠爬行和风传播）
2. 用棉签蘸 75% 酒精逐个点涂虫体，蜡质被溶解释放后虫体会死亡
3. 虫量大时用矿物油（白油）或啶虫脒喷施，重点喷茎节背面和缝隙，7–10 天后补喷一次
4. 受害严重的茎节直接剪除并销毁，不要丢在花盆里',
 '新购入植株先隔离观察 2–3 周；保持通风；定期检查刺座和茎节缝隙；避免氮肥过量造成徒长。',
 'MEDIUM'),

-- ---------- 虎皮兰 ----------
('Snake_Plant_Healthy', '虎皮兰·健康', 'Snake Plant Healthy', 'PHYSIOLOGICAL', NULL,
 '叶片直立硬挺，表面斑纹（横带或纵向条纹）清晰，叶缘完整无焦枯，叶基与土壤接触处紧实无发软，新叶从根茎中心正常抽出。',
 '散射光或半阴环境；盆土完全干透后再浇；温度 18–30 ℃。',
 '无需处理。浇水保持"宁干勿湿"，冬季可一个月以上不浇。',
 '用透气颗粒土（加粗砂、珍珠岩）；盆底不积水；避免长期低于 10 ℃。',
 'LOW'),

('Snake_Plant_Anthracnose', '虎皮兰·炭疽病', 'Snake Plant Anthracnose', 'FUNGAL',
 '炭疽菌 Colletotrichum 属（常见 C. sansevieriae）',
 '叶片出现水渍状小点，迅速扩大为褐色至灰褐色的圆形或椭圆形凹陷斑，边缘深褐、中央灰白，后期病斑上生出黑色小点（分生孢子盘）并可能穿孔。多从叶尖、叶缘或伤口处开始，潮湿时病斑扩展快并可连片导致整叶枯死。',
 '高温高湿、叶面长时间积水；浇水溅到叶片；通风差；伤口未处理。',
 '1. 剪除病叶，剪口在健康组织一侧留 2–3 cm，剪刀每次酒精消毒
2. 停浇水 1–2 周，加强通风把环境湿度降下来
3. 避免叶面喷水，浇水直接浇到土面
4. 药剂可用苯醚甲环唑、咪鲜胺或代森锰锌，7–10 天一次，连施 2–3 次
5. 与健康植株隔离',
 '浇水不沾叶面；保持通风；避免盆土长期潮湿；修剪工具消毒；不要过度密植。',
 'HIGH'),

('Snake_Plant_Leaf_Withering', '虎皮兰·叶片枯萎', 'Snake Plant Leaf Withering', 'PHYSIOLOGICAL', NULL,
 '叶片自下而上或自叶尖开始变软、皱缩、颜色转黄褐并逐渐干枯；叶基处发软或出现水渍状褐变，拔出可见根系褐腐、须根脱落；与炭疽病的区别是：枯萎通常整片均匀失水，没有界限清晰的凹陷病斑和黑色小点。',
 '浇水过频或盆土长期不干导致烂根（最常见）；盆底积水；冬季低温加湿土；长期缺水也会造成叶尖干枯但叶基仍硬实。',
 '1. 脱盆检查根系：发黑发软、有异味的根全部剪掉
2. 剪口晾干 1–2 天（伤口结痂）后换新土重栽
3. 换透气颗粒土，盆要比根球略大，不要用过大盆
4. 重栽后 3–5 天不浇水，之后干透再浇
5. 若根茎中心已腐烂发软，切除腐部后基本无法挽救，建议取健康叶片扦插重新繁殖',
 '宁干勿湿，盆土彻底干透再浇；用带排水孔的花盆并倒掉托盘积水；冬季控水；避免低温（<10 ℃）时浇水。',
 'MEDIUM'),

-- ---------- 吊兰 ----------
('Spider_Plant_Healthy', '吊兰·健康', 'Spider Plant Healthy', 'PHYSIOLOGICAL', NULL,
 '叶片细长柔软、绿色均匀（斑叶品种条纹清晰），叶尖完整不焦，匍匐茎上的小植株饱满，根茎处紧实无褐变。',
 '明亮散射光；盆土保持微润但不积水；空气湿度 50% 以上。',
 '无需处理。生长季保持盆土微湿，每月一次薄肥。',
 '避免强光直射；用软水或晾置过的自来水浇（吊兰对氟和盐分敏感）；定期转盆使受光均匀。',
 'LOW'),

('Spider_Plant_Fungal_leaf_spot', '吊兰·真菌性叶斑病', 'Spider Plant Fungal Leaf Spot', 'FUNGAL',
 '常见为叶点霉 Phyllosticta、壳针孢 Septoria 或炭疽菌 Colletotrichum',
 '叶片出现褐色至黑褐色的小圆斑，直径 2–10 mm，边缘色深、中央渐灰白，多个病斑可融合成不规则大斑；后期斑上出现针尖状黑色小点。与叶尖坏死的区别：叶斑是散布在叶面中部的独立斑点，边界清晰，而叶尖坏死从叶尖开始向内延伸、边界模糊。',
 '叶面长期潮湿、通风不良；浇水从上方淋浇；环境湿度过高且温度偏低。',
 '1. 剪除病斑面积超过叶片 1/3 的叶子，其余可只剪掉病斑部分
2. 停止叶面喷水，改为浇土面
3. 加强通风，拉开植株间距
4. 药剂可用代森锰锌、苯醚甲环唑或百菌清，7–10 天一次，连施 2 次
5. 病叶不要留在盆土表面',
 '浇水避免沾湿叶面；保持通风；不要过度密植；发现病叶及时清除。',
 'MEDIUM'),

('Spider_Plant_Leaf_Tip_Necrosis', '吊兰·叶尖坏死', 'Spider Plant Leaf Tip Necrosis', 'PHYSIOLOGICAL', NULL,
 '叶尖先变为浅褐或焦褐色，逐渐向内延伸 1–5 cm，坏死与健康组织之间常有黄色过渡带；多个叶片同时出现、位置集中在叶尖。与真菌性叶斑的区别：病斑从叶尖起始、呈楔形向内，不是散布在叶面的独立圆斑。',
 '最常见原因是水质：自来水中氟化物、氯和可溶性盐积累在叶尖；其次是空气过于干燥、盆土盐分累积（施肥过量或长期不换土）、浇水不均（忽干忽湿）。',
 '1. 用剪刀沿坏死与健康组织交界处斜剪，剪成尖角形状，视觉上更自然（坏死组织无法恢复，只能剪掉）
2. 改用晾置 24 小时以上的自来水、纯净水或雨水浇灌
3. 每 2–3 个月用大量清水冲淋盆土一次，把积盐冲出（注意排水）
4. 提高空气湿度到 50% 以上，但要通风
5. 减少施肥频率和浓度
6. 每 1–2 年换土一次',
 '用低氟水源；避免施肥过量；保持稳定浇水节奏，不忽干忽湿；冬季远离暖气出风口。',
 'MEDIUM')

ON DUPLICATE KEY UPDATE
  name_cn   = VALUES(name_cn),
  name_en   = VALUES(name_en),
  category  = VALUES(category),
  pathogen  = VALUES(pathogen),
  symptoms  = VALUES(symptoms),
  causes    = VALUES(causes),
  treatment = VALUES(treatment),
  prevention= VALUES(prevention),
  severity  = VALUES(severity);

-- 校验：应为 37
SELECT COUNT(*) AS total_disease_info FROM disease_info;
SELECT code, name_cn, category, severity FROM disease_info WHERE code LIKE 'Cactus%'
   OR code LIKE 'Snake_Plant%' OR code LIKE 'Spider_Plant%' ORDER BY code;
