-- GreenThumb disease knowledge seed v3: 10 species / 44 classes
-- Sources: Mendeley Data DOI 10.17632/2vzp6ss7vg.1 (CC BY 4.0)
-- Import:
--   mysql -uroot -p greenthumb --default-character-set=utf8mb4 -e "source db/seed_disease_v3.sql"

USE greenthumb;

INSERT INTO disease_info
  (code, name_cn, name_en, category, pathogen, symptoms, causes,
   treatment, prevention, severity)
VALUES
('Rose_Nutrient_Deficiency', '月季·营养缺乏', 'Rose Nutrient Deficiency',
 'PHYSIOLOGICAL', NULL,
 '叶片整体或叶脉间失绿，常从老叶开始；缺铁时新叶黄化而叶脉仍绿。严重时叶缘焦枯、生长变慢。',
 '长期不施肥、盆土养分耗尽、土壤 pH 不适宜导致根系吸收受阻，或浇水过量造成烂根。',
 '1. 检查盆土干湿和根系，先排除积水烂根\n2. 生长期使用均衡水溶肥，按说明稀释后薄肥勤施\n3. 新叶黄化可补充含铁螯合肥\n4. 土壤偏碱时逐步调整到微酸性',
 '定期薄肥勤施；每 1-2 年换土；避免长期积水；根据新叶和老叶症状判断缺素类型。',
 'MEDIUM'),

('Rose_Fungal_Leaf_Spot', '月季·真菌性叶斑病', 'Rose Fungal Leaf Spot',
 'FUNGAL', 'Alternaria、Cercospora 等叶斑病原真菌',
 '叶片出现褐色、灰褐色或黑色斑点，边缘常有黄色晕圈；病斑可扩大并融合，严重时叶片卷曲、提前脱落。',
 '高温高湿、叶面长时间积水、通风差；浇水飞溅传播孢子；病叶留在盆面继续侵染。',
 '1. 剪除病斑严重的叶片并清理盆面落叶\n2. 停止叶面喷水，改为浇土壤\n3. 加强通风并拉开植株间距\n4. 病情扩展时使用代森锰锌或苯醚甲环唑等杀菌剂，按标签轮换使用',
 '保持叶面干燥；避免过度密植；发现病叶及时清除；新植株先隔离观察。',
 'MEDIUM'),

('Hibiscus_Bacterial_Leaf_Spot', '扶桑·细菌性叶斑病', 'Hibiscus Bacterial Leaf Spot',
 'BACTERIAL', 'Xanthomonas、Pseudomonas 等细菌',
 '叶片出现水渍状小点，随后扩大为褐色至黑褐色不规则斑，周围常有黄色晕圈；潮湿时病斑可呈油亮状。',
 '高温高湿、叶面伤口、雨水或喷水飞溅传播；工具和手部未消毒；植株过密。',
 '1. 立即剪除病叶并销毁，避免病叶接触健康植株\n2. 工具使用后用酒精消毒\n3. 停止叶面浇水并加强通风\n4. 可使用铜制剂抑制扩展，注意浓度避免药害',
 '避免叶面长期带水；工具消毒；新植株隔离观察；保持通风和适当株距。',
 'HIGH'),

('Hibiscus_Nutrient_Deficiency', '扶桑·营养缺乏', 'Hibiscus Nutrient Deficiency',
 'PHYSIOLOGICAL', NULL,
 '叶片褪绿、发黄或叶脉间失绿，常伴随新梢细弱、花芽减少；缺钾时叶缘可能出现焦枯。',
 '盆土养分不足、长期只浇清水、根系受损或土壤 pH 不合适导致养分吸收不良。',
 '1. 先检查土壤和根系，排除积水烂根\n2. 生长期每 1-2 周使用一次稀薄均衡肥\n3. 花芽期适当补充磷钾肥\n4. 新叶黄化时检查铁等微量元素供应',
 '规律施肥但不浓肥；每 1-2 年换土；保持适宜光照和通风；根据叶片位置判断缺素。',
 'MEDIUM'),

('Tagetes_Healthy', '万寿菊·健康', 'Marigold Healthy',
 'PHYSIOLOGICAL', NULL,
 '叶片颜色均匀、质地饱满，无异常斑点、焦边或虫咬缺口；新叶生长正常，茎秆挺立。',
 '光照充足、浇水规律、通风良好，土壤养分和湿度适中。',
 '无需处理。保持见干见湿的浇水节奏，生长期每月薄施一次复合肥。',
 '避免长期积水；保持充足光照；定期翻看叶背，早发现虫害和病斑。',
 'LOW'),

('Tagetes_Alternaria_Leaf_Blight', '万寿菊·链格孢叶枯病', 'Marigold Alternaria Leaf Blight',
 'FUNGAL', 'Alternaria 属真菌',
 '叶片出现褐色至深褐色坏死斑，可带有同心轮纹或黄色晕圈；病斑扩展后导致叶片局部枯死、卷曲和提前脱落。',
 '高温高湿、叶面长时间带水、通风差；病残体留在土面；植株过密。',
 '1. 剪除病叶并清理土面病残体\n2. 改用土壤浇水，避免叶面淋湿\n3. 加强通风和光照\n4. 病情持续时使用代森锰锌、苯醚甲环唑等杀菌剂，按标签交替使用',
 '选用健康种苗；保持叶面干燥；避免密植；及时清除病残体。',
 'MEDIUM'),

('Tagetes_Pest_Damage', '万寿菊·虫害', 'Marigold Pest Damage',
 'PEST', '蚜虫、红蜘蛛、蓟马等常见害虫',
 '叶片出现缺刻、孔洞、卷曲、失绿小点或黏性分泌物；翻看叶背可见虫体、卵或蛛丝。',
 '通风不良、环境干燥、新购植株未隔离；虫源从邻近植物传播。',
 '1. 先用清水冲洗或湿布擦除可见虫体\n2. 剪除虫害严重的叶片和嫩梢\n3. 红蜘蛛可提高湿度并加强通风，蚜虫和蓟马可使用相应杀虫剂\n4. 间隔 7 天复查并补处理一次，覆盖虫卵孵化期',
 '新植株隔离观察两周；定期检查叶背；保持通风；避免氮肥过量。',
 'MEDIUM')

ON DUPLICATE KEY UPDATE
  name_cn   = VALUES(name_cn),
  name_en   = VALUES(name_en),
  category  = VALUES(category),
  pathogen  = VALUES(pathogen),
  symptoms  = VALUES(symptoms),
  causes    = VALUES(causes),
  treatment = VALUES(treatment),
  prevention = VALUES(prevention),
  severity  = VALUES(severity);

SELECT COUNT(*) AS total_disease_info FROM disease_info;
SELECT code, name_cn, category, severity
FROM disease_info
WHERE code LIKE 'Tagetes%' OR code IN (
  'Rose_Nutrient_Deficiency',
  'Rose_Fungal_Leaf_Spot',
  'Hibiscus_Bacterial_Leaf_Spot',
  'Hibiscus_Nutrient_Deficiency'
)
ORDER BY code;
