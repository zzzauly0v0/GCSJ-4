"""组装灾害出行风险研判 Agent, 提供流式回复生成器。"""
import os

from dotenv import load_dotenv
from openai import AsyncOpenAI
from agents import Agent, Runner, OpenAIChatCompletionsModel

from spatial_analyse.tools.geo import geo_locate
from spatial_analyse.tools.gis_query import query_station_disasters
from spatial_analyse.tools.knowledge import disaster_kb

# 显式加载本包目录下的 .env, 不依赖启动服务时的工作目录
load_dotenv(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"), override=True)

_MODEL_ID = os.getenv("DASHSCOPE_MODEL_ID")
_API_KEY = os.getenv("DASHSCOPE_API_KEY")
_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

_SYSTEM_PROMPT = (
    "你是「四川灾害出行风险研判助手」。用户会告诉你想去的地点和时间, 你的任务是结合"
    "该地就近气象站的历史灾害数据, 给出针对性的出行风险研判与建议, 而不是泛泛的科普。\n"
    "工作流程 (自行判断调用哪些工具):\n"
    "1. 用 geo_locate 把地名解析为经纬度;\n"
    "2. 用 query_station_disasters 查该坐标就近站点的历史灾害统计 (含按月份分布);\n"
    "3. 如需科普/防范细节, 用 disaster_kb 查对应灾种;\n"
    "最后综合输出: 该地历史上哪些季节/月份、哪种灾害风险偏高 (引用 by_month 与"
    " max_comp_level 等数据), 结合用户出行月份给出是否适宜、注意事项。\n"
    "语气面向普通游客, 简洁、务实、给可执行建议。若地点无法定位, 礼貌请用户补充更"
    "具体的地名。用中文回答。"
)


def _make_model() -> OpenAIChatCompletionsModel:
    client = AsyncOpenAI(api_key=_API_KEY, base_url=_BASE_URL)
    return OpenAIChatCompletionsModel(model=_MODEL_ID, openai_client=client)


def build_agent() -> Agent:
    return Agent(
        name="灾害出行风险研判助手",
        instructions=_SYSTEM_PROMPT,
        model=_make_model(),
        tools=[geo_locate, query_station_disasters, disaster_kb],
    )


async def stream_reply(messages: list):
    """输入对话历史 [{role, content}...], 逐个 yield 事件 dict。

    事件: {"type":"tool","name":str,"status":str}
          {"type":"token","text":str}
          {"type":"done"}
          {"type":"error","message":str}
    """
    if not _API_KEY or not _MODEL_ID:
        yield {"type": "error", "message": "AI 服务未配置 (缺少 DASHSCOPE_API_KEY/MODEL_ID)"}
        return

    try:
        agent = build_agent()
        # Agents SDK 接受字符串或消息列表作为 input; 这里传对话历史列表
        result = Runner.run_streamed(agent, input=messages)
        async for event in result.stream_events():
            if event.type == "raw_response_event":
                data = getattr(event, "data", None)
                delta = getattr(data, "delta", None)
                if delta:
                    yield {"type": "token", "text": delta}
            elif event.type == "run_item_stream_event":
                item = getattr(event, "item", None)
                if item is not None and getattr(item, "type", "") == "tool_call_item":
                    raw = getattr(item, "raw_item", None)
                    tool_name = getattr(raw, "name", "工具")
                    status = {
                        "geo_locate": "正在定位地点",
                        "query_station_disasters": "正在查询就近站点历史灾害",
                        "disaster_kb": "正在查询灾害科普",
                    }.get(tool_name, f"正在调用 {tool_name}")
                    yield {"type": "tool", "name": tool_name, "status": status}
        yield {"type": "done"}
    except Exception as e:
        yield {"type": "error", "message": f"AI 生成失败: {e}"}
