-- AI 养护顾问（docs/32）：会话表补「对话起点诊断摘要」
-- 新库直接跑 db/schema.sql 即可，不需要本文件；本文件只给已经建过表的环境补字段。
-- MySQL 没有 ADD COLUMN IF NOT EXISTS，重复执行会报 1060 Duplicate column name，忽略即可。

USE greenthumb;

ALTER TABLE qa_session
  ADD COLUMN diagnosis_context TEXT NULL COMMENT '本次对话的起点：诊断结论摘要'
  AFTER message_count;
