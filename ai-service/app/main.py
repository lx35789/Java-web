"""
植语助手 AI 服务入口。

接口：
    GET  /health                      健康检查 + 模型状态
    GET  /v1/disease/labels           当前模型的类别列表
    POST /v1/disease/predict          病害分类（multipart: file）

启动：
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
"""
from __future__ import annotations

from typing import Optional

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .disease import classifier

app = FastAPI(
    title="GreenThumb AI Service",
    description="植语助手 · AI 服务（病害分类 / 后续扩展色彩分析、embedding）",
    version="0.1.0",
)


@app.get("/health")
def health() -> dict:
    """后端启动时会探这个接口，决定要不要走病害识别这条支路。"""
    return {
        "status": "UP",
        "diseaseModel": {
            "available": classifier.available,
            "reason": classifier.reason,
            "numClasses": classifier.num_classes,
            "meta": classifier.meta,
            # 实际覆盖的物种（英文 key，如 Cactus / Snake_Plant）。
            # 暴露出来的原因：前端曾把「支持哪些物种」硬编码成 6 种，
            # 模型扩到 9 种后没同步，导致仙人掌/虎皮兰/吊兰在界面上直接
            # 显示「暂无病害识别数据」并跳过，压根不调接口。
            # 让前端从模型本身读清单，就不会再漂移。
            "species": classifier.species_set,
        },
        # 物种专用模型清单：有专用模型的物种会走专用模型，准确率更高
        "specialized": classifier.describe()["specialized"],
    }


@app.get("/v1/disease/labels")
def labels() -> dict:
    return {"labels": classifier.labels, "meta": classifier.meta}


@app.post("/v1/disease/predict")
async def predict(file: UploadFile = File(...), top_k: int = 3, species: Optional[str] = None) -> JSONResponse:
    """
    病害分类。

    - species 传模型的物种 key（如 Aloe / Money_Plant）时，若有专用模型则走专用模型；
      没有专用模型自动退回通用模型，返回里 fallback=true。
    - 模型不可用时返回 available=false 而不是抛错 —— 让 Java 后端能优雅降级，
      而不是把「没训练模型」变成一个 500 错误丢给用户。
    """
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="空文件")

    result = classifier.predict(raw, top_k=top_k, species=species)
    return JSONResponse(result)


@app.post("/v1/species/predict")
async def predict_species(file: UploadFile = File(...), top_k: int = 3) -> JSONResponse:
    """
    物种识别。

    由病害模型按「物种」聚合概率间接实现（不需要单独训练物种分类器），
    固定走通用模型。只覆盖模型训练过的 6 个物种，调用方需按置信度决定是否采信。
    """
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="空文件")

    return JSONResponse(classifier.predict_species(raw, top_k=top_k))
