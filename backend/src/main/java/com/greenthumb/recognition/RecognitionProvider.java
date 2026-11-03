package com.greenthumb.recognition;

import java.util.List;

/**
 * 识别供应商抽象。
 * <p>
 * 设计要点（见 docs/05-识别服务接入方案.md）：
 *   ① 每个供应商自己声明 isConfigured()，没配 key 的直接跳过，不留半死状态
 *   ② recognize 出错就抛异常，由 RecognitionOrchestrator 兜住并降级到下一家
 *   ③ 新增供应商（如 Kindwise）只要实现这个接口并注册成 Bean 即可
 */
public interface RecognitionProvider {

    /** 供应商标识，会写进 recognition_record.provider 并返回给前端 */
    String name();

    /** 是否已配置（key 是否就位）。未配置的不会进入调用链 */
    boolean isConfigured();

    /**
     * 执行识别。
     *
     * @param imageBytes 图片原始字节
     * @param filename   原始文件名（部分 API 需要用它判断 MIME）
     * @return 按置信度降序的候选列表；识别不出内容时返回空列表（不是异常）
     */
    List<RecognitionCandidate> recognize(byte[] imageBytes, String filename) throws Exception;
}
