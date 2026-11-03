# ============================================================
# 植语助手 · AI 服务（Python FastAPI）
#
# 职责边界：
#   Java 后端  —— 业务、规则引擎、事务、持久化
#   AI 服务    —— 一切图像/模型相关：病害分类、色彩分析、embedding
#
# 启动：
#   cd ai-service
#   pip install -r requirements.txt
#   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
#
# 健康检查：  http://localhost:8000/health
# 接口文档：  http://localhost:8000/docs
# ============================================================
