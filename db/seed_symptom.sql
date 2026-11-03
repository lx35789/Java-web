-- ============================================================
-- 症状判因规则库种子数据
-- 来源：docs/03-症状诊断规则库.md
-- 用途：把「叶子黄了怎么办」从“靠 LLM 猜”变成可解释、可测试的规则判因
-- 判因算法：同病因多条规则命中则权重累加，再按总分归一化成置信度
-- 执行： mysql -u root -p greenthumb < db/seed_symptom.sql
-- ============================================================

USE `greenthumb`;

-- ---------- 1. 病因字典 cause_info ----------
INSERT IGNORE INTO `cause_info`
  (cause_code, name_cn, category, description, action_plan, urgency)
VALUES
  ('OVERWATER', '浇水过多 / 烂根', 'WATER', '盆土长期过湿导致根系缺氧，进而腐烂。根一旦烂了，吸不上水，叶子反而表现得更像缺水。', '1. 立即停止浇水，把花盆移到通风处\n2. 检查盆底排水孔是否堵塞\n3. 手指插入土中 2-3cm，干了再浇，不要按固定天数浇\n4. 若土壤仍有异味或发黑，脱盆剪除烂根后换新土重栽', 'HIGH'),
  ('UNDERWATER', '浇水不足', 'WATER', '盆土长期偏干，植株无法获得足够水分，叶缘先干、整体萎软。', '1. 一次性浇透，直到盆底有水流出\n2. 若土壤已板结拒水，用浸盆法泡 20 分钟\n3. 之后固定“见干见湿”的节奏，别等叶子蔫了才浇', 'MEDIUM'),
  ('LOW_LIGHT', '光照不足', 'LIGHT', '光照强度不够时光合作用受限，植株会拉长茎节去够光（徒长），叶色变淡。', '1. 移到明亮的散射光处（离窗 1 米内，避免正午直射）\n2. 每 1-2 周转动花盆四分之一圈，让各面受光均匀\n3. 光照条件实在差时可补一盏植物生长灯，每天 8-10 小时', 'LOW'),
  ('TOO_MUCH_LIGHT', '光照过强 / 晒伤', 'LIGHT', '强光直射使叶绿素被破坏，叶面出现褪绿、发白或焦褐色斑块。', '1. 移到散射光处，避免正午直射\n2. 晒伤组织不可逆，只剪完全焦枯的叶片\n3. 换位置要循序渐进，先散光一周再逐步见光', 'LOW'),
  ('LOW_HUMIDITY', '空气过于干燥', 'ENV', '室内空调/暖气环境湿度常低于 30%，蒸腾过强导致叶尖焦枯、叶缘干卷。', '1. 叶尖焦枯部分剪掉，留 1-2mm 以免伤到健康组织\n2. 加湿器或“托盘 + 湿石”把湿度提到 50% 以上\n3. 远离空调出风口和暖气片', 'LOW'),
  ('COLD_DAMAGE', '低温冻伤', 'ENV', '多数室内绿植低于 10℃ 就停止生长，低于 5℃ 会细胞冻伤，表现为水渍状褐斑后转黑。', '1. 搬离窗边、阳台等夜间低温处\n2. 剪除已冻伤发黑的叶片\n3. 不要立刻放到暖气旁，温差过大会二次伤害', 'MEDIUM'),
  ('NUTRIENT_DEF', '缺肥（缺氮/缺铁）', 'NUTRIENT', '长期不施肥导致养分耗尽；叶脉仍绿而叶肉发黄是典型的缺铁表现。', '1. 生长期每 2-4 周施一次稀释的通用液肥（按说明再稀释一半更安全）\n2. 缺铁明显时用含铁微量元素肥\n3. 冬季和刚换盆一个月内不要施肥', 'LOW'),
  ('NUTRIENT_BURN', '肥害（施肥过量）', 'NUTRIENT', '肥料浓度过高使土壤盐分升高，根系被“烧”伤，叶尖和叶缘最先受害。', '1. 立即用大量清水缓慢浇灌盆土，把盐分冲出去（连浇 3-4 次）\n2. 暂停施肥至少一个月\n3. 若已严重萎蔫，脱盆冲洗根系后换新土', 'HIGH'),
  ('NORMAL_SENESCENCE', '正常代谢 / 老叶更新', 'NORMAL', '植物生长过程中底部少量老叶自然黄化脱落，属正常新陈代谢。', '无需处理。保持正常养护即可；只顺手摘掉完全黄透的叶片，减少病虫害藏身处。', 'LOW'),
  ('PEST', '虫害（红蜘蛛/蚧壳虫）', 'PEST', '红蜘蛛喜干热，叶背有细网丝和失绿小点；蚧壳虫是褐色小凸点，会分泌黏性蜜露。', '1. 先用湿布或棉签物理清除可见虫体\n2. 清水冲洗叶背（红蜘蛛怕湿，冲洗很有效）\n3. 虫量大时用阿维菌素或吡虫啉，间隔 7 天再用一次以覆盖虫卵\n4. 与其他植物隔离至确认无虫', 'MEDIUM'),
  ('ROOT_BOUND', '盆太小 / 根系拥挤', 'ENV', '根系长满花盆后吸收能力下降，浇水很快就干、植株却长不好。', '1. 脱盆检查：若根系绕盆成一圈就是根满\n2. 换比原来大一号的盆（别一次换太大，容易积水）\n3. 换盆时轻抖掉部分旧土，剪掉枯根', 'MEDIUM'),
  ('WATER_QUALITY', '水质问题（硬水/含氯）', 'WATER', '自来水中的氯和钙镁盐分会逐渐在土中累积，也会在叶尖沉积。', '1. 自来水接出来静置 24 小时再浇，让氯挥发\n2. 有条件用纯净水或雨水\n3. 每 6-12 个月用大量清水淋洗一次盆土，冲掉盐分', 'LOW'),
  ('DORMANCY', '正常休眠', 'NORMAL', '部分植物在冬季或高温期进入休眠，生长停滞、叶片少量发黄属正常现象。', '无需抢救。减少浇水频率、停止施肥，等气温回升后自然恢复生长。', 'LOW'),
  ('TRANSPLANT_SHOCK', '换盆 / 换环境应激', 'ENV', '换盆伤根或换了摆放位置后，植株需要 1-2 周适应期，期间可能落叶、萎蔫。', '1. 给 1-2 周缓苗期，放在阴凉通风处，避免直射光\n2. 期间不要施肥\n3. 保持盆土微润但不过湿，等新叶长出即恢复正常养护', 'LOW');

-- ---------- 2. 判因规则 symptom_rule ----------
INSERT IGNORE INTO `symptom_rule`
  (symptom_code, cause_code, condition_json, weight, explain_tpl, priority, enabled)
VALUES
  ('YELLOW_LEAF', 'OVERWATER', '{"leaf_position":"OLD","watering_ratio":{"op":">=","value":1.5}}', 0.95, '黄叶从下部老叶开始，且浇水频率明显超出建议值——积水导致根系缺氧', 100, 1),
  ('YELLOW_LEAF', 'OVERWATER', '{"leaf_position":"OLD","pot_material":"CERAMIC"}', 0.30, '釉面陶瓷盆透气性差，水分不易蒸发', 99, 1),
  ('YELLOW_LEAF', 'OVERWATER', '{"leaf_position":"ANY","watering_ratio":{"op":">=","value":2.0}}', 0.40, '浇水频率远超建议值', 98, 1),
  ('YELLOW_LEAF', 'LOW_LIGHT', '{"leaf_position":"NEW","light_level":"LOW"}', 0.85, '新叶先黄，且摆放位置光照不足——光合作用跟不上', 97, 1),
  ('YELLOW_LEAF', 'LOW_LIGHT', '{"leaf_position":"UNIFORM","light_level":"LOW"}', 0.60, '全株均匀褪色，是典型的缺光表现', 96, 1),
  ('YELLOW_LEAF', 'NUTRIENT_DEF', '{"vein_pattern":"GREEN_VEIN","fertilizing":"NONE"}', 0.80, '叶脉仍绿而叶肉发黄，且长期未施肥——典型缺铁/缺氮', 95, 1),
  ('YELLOW_LEAF', 'NUTRIENT_BURN', '{"leaf_position":"NEW","fertilizing":"RECENT"}', 0.75, '新叶先黄且近期施过肥——可能是肥害烧根', 94, 1),
  ('YELLOW_LEAF', 'LOW_HUMIDITY', '{"leaf_position":"ANY","humidity":{"op":"<","value":30}}', 0.55, '空气湿度偏低，蒸腾过强导致叶色变差', 93, 1),
  ('YELLOW_LEAF', 'NORMAL_SENESCENCE', '{"leaf_position":"OLD","season":"AUTUMN","watering_ratio":{"op":"<","value":1.2}}', 0.55, '秋季底部少量老叶自然发黄脱落，属正常代谢', 92, 1),
  ('YELLOW_LEAF', 'NORMAL_SENESCENCE', '{"leaf_position":"OLD","watering_ratio":{"op":"<","value":1.0}}', 0.35, '仅底部少量老叶发黄、浇水正常，多为自然更新', 91, 1),
  ('YELLOW_LEAF', 'COLD_DAMAGE', '{"temp":{"op":"<","value":10}}', 0.70, '环境温度低于 10℃，可能是低温导致的黄化', 90, 1),
  ('YELLOW_LEAF', 'PEST', '{"leaf_position":"ANY"}', 0.20, '建议翻开叶背看看：有无细小网丝（红蜘蛛）或褐色小凸点（蚧壳虫）', 89, 1),
  ('YELLOW_LEAF', 'WATER_QUALITY', '{"leaf_position":"ANY","cause_hint":"tap_water"}', 0.25, '长期直接用自来水浇灌，氯与硬水盐分逐渐累积', 88, 1),
  ('LEAF_TIP_BURN', 'LOW_HUMIDITY', '{"humidity":{"op":"<","value":40}}', 0.90, '空气干燥是叶尖焦枯最常见的原因', 87, 1),
  ('LEAF_TIP_BURN', 'NUTRIENT_BURN', '{"fertilizing":"HEAVY"}', 0.70, '施肥浓度过高，盐分在叶尖积累', 86, 1),
  ('LEAF_TIP_BURN', 'OVERWATER', '{"watering_ratio":{"op":">=","value":1.5}}', 0.45, '浇水过频同样会导致叶尖焦枯（根系受损）', 85, 1),
  ('LEAF_TIP_BURN', 'WATER_QUALITY', '{"cause_hint":"tap_water"}', 0.50, '自来水中的氯和氟化物会在叶尖累积', 84, 1),
  ('LEAF_TIP_BURN', 'COLD_DAMAGE', '{"temp":{"op":"<","value":10}}', 0.40, '低温叠加干燥，共同造成叶尖损伤', 83, 1),
  ('LEAF_DROP', 'OVERWATER', '{"watering_ratio":{"op":">=","value":1.5}}', 0.80, '盆土长期过湿导致根系受损，植株保不住叶子', 82, 1),
  ('LEAF_DROP', 'TRANSPLANT_SHOCK', '{"cause_hint":"recent_move"}', 0.75, '刚换过盆或换过环境，植株处于缓苗期', 81, 1),
  ('LEAF_DROP', 'LOW_LIGHT', '{"light_level":"LOW"}', 0.60, '光照不足时植株会主动舍弃部分叶片以减少消耗', 80, 1),
  ('WILT', 'OVERWATER', '{"pot_material":"CERAMIC","watering_ratio":{"op":">=","value":1.2}}', 0.85, '透气性差的盆 + 浇水偏多，根系缺氧', 79, 1),
  ('WILT', 'UNDERWATER', '{"watering_ratio":{"op":"<=","value":0.5}}', 0.85, '浇水次数明显偏少，盆土已经干透', 78, 1),
  ('WILT', 'COLD_DAMAGE', '{"temp":{"op":"<","value":10}}', 0.55, '低温使根系吸水能力下降，表现为萎蔫', 77, 1),
  ('SPOT', 'OVERWATER', '{"humidity":{"op":">","value":70}}', 0.60, '湿度过高是生理性斑点与真菌感染的共同温床', 76, 1),
  ('SPOT', 'TOO_MUCH_LIGHT', '{"light_level":"DIRECT"}', 0.55, '强光直射可造成灼伤性斑点', 75, 1),
  ('SPOT', 'PEST', '{}', 0.45, '部分虫害（蓟马、红蜘蛛）也会造成点状失绿', 74, 1),
  ('LEGGY', 'LOW_LIGHT', '{"light_level":"LOW"}', 0.95, '光照不足时茎节拉长去够光，这是徒长最主要的原因', 73, 1),
  ('LEGGY', 'NUTRIENT_BURN', '{"fertilizing":"RECENT"}', 0.35, '氮肥偏多会加剧徒长', 72, 1),
  ('PALE', 'TOO_MUCH_LIGHT', '{"light_level":"DIRECT"}', 0.80, '强光直射使叶绿素被破坏，叶色发白发黄', 71, 1),
  ('PALE', 'NUTRIENT_DEF', '{"fertilizing":"NONE"}', 0.65, '长期未施肥，叶绿素合成原料不足', 70, 1),
  ('PALE', 'PEST', '{}', 0.40, '刺吸式害虫吸食汁液也会造成叶片整体褪色', 69, 1),
  ('BASE_ROT', 'OVERWATER', '{"watering_ratio":{"op":">=","value":1.0}}', 0.95, '茎基发黑发软是积水的直接后果，属于紧急情况', 68, 1),
  ('BASE_ROT', 'OVERWATER', '{"pot_material":"CERAMIC"}', 0.35, '盆体透气性差会显著加剧茎基腐烂', 67, 1),
  ('BASE_ROT', 'COLD_DAMAGE', '{"temp":{"op":"<","value":10}}', 0.30, '低温叠加潮湿，最容易发生茎基腐烂', 66, 1);

-- 校验：应当分别是 14 和 35
-- SELECT COUNT(*) FROM cause_info;
-- SELECT COUNT(*) FROM symptom_rule;
-- SELECT symptom_code, COUNT(*) FROM symptom_rule GROUP BY symptom_code;
