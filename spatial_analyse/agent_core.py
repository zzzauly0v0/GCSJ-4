"""灾害出行风险研判 Agent — 原生 OpenAI 客户端 agentic loop。"""
import os
import json

from dotenv import load_dotenv
from openai import AsyncOpenAI

from spatial_analyse.tools.geo import resolve_place
from spatial_analyse.tools.gis_query import nearest_station, _fetch_eval_rows, summarize_disasters
from spatial_analyse.tools.knowledge import lookup_knowledge

load_dotenv(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"), override=True)

_MODEL_ID  = os.getenv("DASHSCOPE_MODEL_ID")
_API_KEY   = os.getenv("DASHSCOPE_API_KEY")
_BASE_URL  = "https://dashscope.aliyuncs.com/compatible-mode/v1"

_SYSTEM_PROMPT = (
    "你是「四川灾害出行风险研判助手」。\n\n"
    "## 核心任务\n"
    "用户告诉你目的地和出行时间后，调用工具查询数据，然后直接输出研判结论。"
    "不要输出思考过程，不要解释你在做什么，只输出面向游客的最终建议。\n\n"
    "## 工具调用顺序\n"
    "1. geo_locate(place) — 把地名解析为经纬度；若返回 null 则礼貌请用户补充更具体的地名；\n"
    "2. query_station_disasters(lon, lat) — 查就近站点历史灾害统计 (by_month / max_comp_level)；\n"
    "3. disaster_kb(topic) — 可选，仅在需要具体防范措施时调用。\n\n"
    "## 输出格式\n"
    "- 先一句话给出「X 月去 Y 整体风险：低/中/高」的判断；\n"
    "- 再列出该月历史上最常见 1-2 种灾害及发生频次（引用数据）；\n"
    "- 最后给 2-3 条可执行的注意事项。\n"
    "- 语气简洁务实，面向普通游客，全程用中文。"
)

# ── 工具 schema（OpenAI function calling 格式）──────────────────────────────

_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "geo_locate",
            "description": "将中文地名解析为经纬度坐标",
            "parameters": {
                "type": "object",
                "properties": {
                    "place": {"type": "string", "description": "地名，如「九寨沟」「峨眉山」"}
                },
                "required": ["place"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_station_disasters",
            "description": "查询给定经纬度就近气象站的历史灾害统计数据",
            "parameters": {
                "type": "object",
                "properties": {
                    "lon": {"type": "number", "description": "经度"},
                    "lat": {"type": "number", "description": "纬度"},
                },
                "required": ["lon", "lat"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "disaster_kb",
            "description": "查询特定灾种的科普与防范知识",
            "parameters": {
                "type": "object",
                "properties": {
                    "topic": {"type": "string", "description": "灾种关键词，如「泥石流」「暴雨」"}
                },
                "required": ["topic"],
            },
        },
    },
]

def _tool_geo_locate(place: str) -> str:
    return json.dumps(resolve_place(place), ensure_ascii=False)


def _tool_query_station_disasters(lon: float, lat: float) -> str:
    try:
        st = nearest_station(lon, lat)
        if not st:
            return json.dumps({"error": "附近无气象站数据"}, ensure_ascii=False)
        rows = _fetch_eval_rows(st["code"])
        return json.dumps({"station": st, "summary": summarize_disasters(rows)},
                          ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": f"查询失败: {e}"}, ensure_ascii=False)


_TOOL_MAP = {
    "geo_locate":             _tool_geo_locate,
    "query_station_disasters": _tool_query_station_disasters,
    "disaster_kb":            lookup_knowledge,
}

_TOOL_STATUS = {
    "geo_locate":             "正在定位地点",
    "query_station_disasters": "正在查询就近站点历史灾害",
    "disaster_kb":            "正在查询灾害科普",
}


# ── 工具执行 ────────────────────────────────────────────────────────────────

def _call_tool(name: str, arguments_json: str) -> str:
    try:
        args = json.loads(arguments_json)
        fn   = _TOOL_MAP.get(name)
        if fn is None:
            return f"未知工具: {name}"
        result = fn(**args)
        return json.dumps(result, ensure_ascii=False) if not isinstance(result, str) else result
    except Exception as e:
        return f"工具调用失败: {e}"


# ── thinking 过滤状态机 ──────────────────────────────────────────────────────

class _ThinkFilter:
    """流式过滤 <think>…</think> 块，返回应发给前端的净文本。"""

    def __init__(self):
        self._in   = False
        self._buf  = ""

    def feed(self, chunk: str) -> str:
        out = ""
        if self._in:
            self._buf += chunk
            if "</think>" in self._buf:
                after       = self._buf.split("</think>", 1)[1]
                self._in    = False
                self._buf   = ""
                out         = after
        else:
            if "<think>" in chunk:
                before, rest = chunk.split("<think>", 1)
                out        = before
                self._in   = True
                self._buf  = rest
                if "</think>" in self._buf:
                    after       = self._buf.split("</think>", 1)[1]
                    self._in    = False
                    self._buf   = ""
                    out        += after
            else:
                out = chunk
        return out


# ── 主入口 ──────────────────────────────────────────────────────────────────

async def stream_reply(messages: list):
    """输入对话历史 [{role, content}…]，逐个 yield 事件 dict。

    事件: {"type":"tool","name":str,"status":str}
          {"type":"token","text":str}
          {"type":"done"}
          {"type":"error","message":str}
    """
    if not _API_KEY or not _MODEL_ID:
        yield {"type": "error", "message": "AI 服务未配置 (缺少 DASHSCOPE_API_KEY/MODEL_ID)"}
        return

    try:
        client = AsyncOpenAI(api_key=_API_KEY, base_url=_BASE_URL)

        # 去掉前端欢迎语（第一条 assistant 消息），拼上 system prompt
        clean = [m for i, m in enumerate(messages)
                 if not (i == 0 and m.get("role") == "assistant")]
        loop_msgs = [{"role": "system", "content": _SYSTEM_PROMPT}] + clean

        for _ in range(6):  # 最多 6 轮工具调用，防止死循环
            stream = await client.chat.completions.create(
                model=_MODEL_ID,
                messages=loop_msgs,
                tools=_TOOLS,
                tool_choice="auto",
                stream=True,
            )

            # ── 收集本轮流式响应 ──
            content_buf   = ""
            tool_calls    = {}   # index → {id, name, arguments}
            finish_reason = None
            tf            = _ThinkFilter()

            async for chunk in stream:
                if not chunk.choices:
                    continue
                choice        = chunk.choices[0]
                finish_reason = choice.finish_reason or finish_reason
                delta         = choice.delta

                # 正文 token
                if delta.content:
                    clean_text = tf.feed(delta.content)
                    if clean_text:
                        content_buf += clean_text
                        yield {"type": "token", "text": clean_text}

                # 工具调用增量
                if delta.tool_calls:
                    for tc in delta.tool_calls:
                        idx = tc.index
                        if idx not in tool_calls:
                            tool_calls[idx] = {"id": "", "name": "", "arguments": ""}
                        if tc.id:
                            tool_calls[idx]["id"] = tc.id
                        if tc.function:
                            if tc.function.name:
                                tool_calls[idx]["name"] += tc.function.name
                            if tc.function.arguments:
                                tool_calls[idx]["arguments"] += tc.function.arguments

            # ── 判断是否继续循环 ──
            if finish_reason == "tool_calls" and tool_calls:
                # 把助手消息（含 tool_calls）追加进 messages
                tc_list = [
                    {"id": v["id"], "type": "function",
                     "function": {"name": v["name"], "arguments": v["arguments"]}}
                    for v in (tool_calls[k] for k in sorted(tool_calls))
                ]
                assistant_msg: dict = {"role": "assistant", "tool_calls": tc_list}
                if content_buf:
                    assistant_msg["content"] = content_buf
                loop_msgs.append(assistant_msg)

                # 执行工具，收集结果
                for tc in tc_list:
                    fn_name = tc["function"]["name"]
                    yield {"type": "tool", "name": fn_name,
                           "status": _TOOL_STATUS.get(fn_name, f"正在调用 {fn_name}")}
                    result = _call_tool(fn_name, tc["function"]["arguments"])
                    loop_msgs.append({
                        "role": "tool",
                        "tool_call_id": tc["id"],
                        "content": result,
                    })
                # 继续下一轮
            else:
                break   # 模型已给出最终回复，退出循环

        yield {"type": "done"}

    except Exception as e:
        yield {"type": "error", "message": f"AI 生成失败: {e}"}
