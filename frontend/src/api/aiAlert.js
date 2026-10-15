/**
 * AI 智能研判预警接口 (后端 /api/ai-alert/**)
 *
 * analyze — 对指定站点某风险日调用大模型生成研判 (摘要 / 建议等级 / 主导灾种 / 处置建议 / 依据)
 * adopt   — 采纳研判结果, 生成正式预警 (后端内部复用 Alert 创建 + 多渠道推送)
 *
 * 风险日候选来自现有 /disaster-eval/events, 无需额外接口。
 */
import request from '@/utils/request'

/** 生成 AI 研判。入参 { stationCode, obsDate }，obsDate 为 YYYY-MM-DD */
export const apiAiAnalyze = (data) => request.post('/ai-alert/analyze', data)

/** 采纳研判并发布预警。返回创建后的 AlertVO */
export const apiAiAdopt = (data) => request.post('/ai-alert/adopt', data)
