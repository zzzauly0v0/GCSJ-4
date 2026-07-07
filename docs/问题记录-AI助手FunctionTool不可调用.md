# 问题记录：AI 灾害助手一直重复"请告诉我目的地"，不输出有效研判

**日期**：2026-07-02
**模块**：`spatial_analyse/agent_core.py`、`frontend/src/views/agent/agentView.vue`
**严重程度**：高（核心功能完全不可用）

> 同一个表象（助手重复"请告诉我目的地"）背后实际叠加了 **三个独立问题**：
> ① 后端工具不可调用（见下文一~六）；② 服务端口与前端代理不一致，跑的是旧代码（见七）；
> ③ 前端把用户刚输入的问题从 history 里误删（见八）。三个都修完才彻底恢复正常。

---

## 一、现象

用户提问「我 9 月想去九寨沟玩，安全吗？」，助手不调用工具查数据，只反复回复模板句：

> 您好！我是「四川灾害出行风险研判助手」。
> 请告诉我您的目的地（例如：九寨沟、四姑娘山）和计划出行的月份……

无论怎么改 system prompt、怎么换问法，输出都一样。

## 二、排查过程

1. **最初怀疑是 prompt 写得不够明确** → 重写 system prompt（明确工具调用顺序、输出格式）→ 无效。
2. **怀疑是 Qwen3 思考型模型把推理链混进正文** → 加 `enable_thinking: False` → 报错
   `OpenAIChatCompletionsModel.__init__() got an unexpected keyword argument 'extra_body'`
   （Agents SDK 的模型类构造函数不收该参数，需走 `ModelSettings.extra_body`）。
3. **怀疑是前端把欢迎语（assistant 消息）发进 history 开头** → 后端过滤首条 assistant 消息 → 仍无效。
4. **放弃 Agents SDK，改为原生 OpenAI 客户端手写 agentic loop**（stream + tool_calls 循环）→ 仍无效。
5. **在后端直接跑 `stream_reply` 打印全部事件**，终于看到真相：

   ```
   [EVENT] {"type": "tool", "name": "geo_locate", ...}   ← 连续 6 次
   [EVENT] {"type": "done"}
   ```

   模型其实**每轮都在调用 geo_locate**，但每次都拿到失败结果，重试 6 轮耗尽循环上限后，只能编一句"请补充目的地"兜底。

6. 单独测试工具函数：

   ```python
   from spatial_analyse.tools.geo import geo_locate
   geo_locate('九寨沟')
   # TypeError: 'FunctionTool' object is not callable
   ```

## 三、根本原因

`tools/geo.py`、`tools/gis_query.py`、`tools/knowledge.py` 里的工具函数都套了
OpenAI Agents SDK 的 `@function_tool` 装饰器——**装饰后它们不再是普通函数，而是
`FunctionTool` 对象，不可直接调用**。

切换到手写 agentic loop 后，`_TOOL_MAP` 仍映射这些被装饰的名字：

```python
_TOOL_MAP = {
    "geo_locate": geo_locate,   # ← FunctionTool 对象，调用即抛 TypeError
    ...
}
```

每次工具执行都抛 `TypeError`，被 `_call_tool` 的 try/except 吞掉，把
`"工具调用失败: 'FunctionTool' object is not callable"` 当作工具结果喂回模型。
模型看到失败就重试，直到循环耗尽。

**教训**：异常被静默捕获后当正常数据返回，把真实错误完全掩盖了——表象（模型重复要信息）
和根因（工具不可调用）之间隔了两层，导致前四步排查全部打偏。

## 四、修复

`agent_core.py` 中 `_TOOL_MAP` 不再引用被 `@function_tool` 装饰的对象，
改为直接绑定底层纯函数，并加薄包装保持 JSON 返回格式：

```python
from spatial_analyse.tools.geo import resolve_place
from spatial_analyse.tools.gis_query import nearest_station, _fetch_eval_rows, summarize_disasters
from spatial_analyse.tools.knowledge import lookup_knowledge

def _tool_geo_locate(place: str) -> str:
    return json.dumps(resolve_place(place), ensure_ascii=False)

def _tool_query_station_disasters(lon: float, lat: float) -> str:
    st = nearest_station(lon, lat)
    if not st:
        return json.dumps({"error": "附近无气象站数据"}, ensure_ascii=False)
    rows = _fetch_eval_rows(st["code"])
    return json.dumps({"station": st, "summary": summarize_disasters(rows)}, ensure_ascii=False)

_TOOL_MAP = {
    "geo_locate":              _tool_geo_locate,
    "query_station_disasters": _tool_query_station_disasters,
    "disaster_kb":             lookup_knowledge,
}
```

## 五、修复后验证

同样的问题端到端测试，全链路正常：

```
[tool] geo_locate            正在定位地点
[tool] query_station_disasters  正在查询就近站点历史灾害
[tool] disaster_kb           正在查询灾害科普

9 月去九寨沟整体风险：中
根据气象站历史数据，九寨沟地区在 9 月份平均有 34 天存在气象灾害记录……
（泥石流/坡面崩塌为主 + 3 条可执行注意事项）
```

## 六、经验总结

1. **工具执行失败不要静默吞掉**：`_call_tool` 把异常字符串当结果返回给模型，模型无法区分
   "数据查不到"和"代码坏了"。至少应在服务端 log 出完整 traceback。
2. **`@function_tool` 装饰器有侵入性**：被装饰的函数只能给 Agents SDK 用。若同一函数还要
   被手写 loop / 测试直接调用，应保持纯函数（`resolve_place` 等），装饰器只包一层薄壳。
3. **表象在模型端，根因常在工具端**：Agent 反复重试同一个工具、或退化成模板式追问，
   优先怀疑工具返回了错误/空结果，先在后端脱离前端单测 `stream_reply` 打印全部事件。
4. **本次无效的三次尝试也各有价值**：`enable_thinking` 需经 `ModelSettings.extra_body` 传递
   （思考型模型的 `<think>` 块已另有 `_ThinkFilter` 流式过滤兜底）；history 首条 assistant
   欢迎语确实不应发给模型（已保留过滤逻辑）。

---

## 七、后续问题 ①：修复后前端仍旧输出模板句 —— 端口错配跑的是旧代码

### 现象

后端代码修好、直测通过，但前端提问依然得到修复前一模一样的模板回复。

### 排查

- `frontend/vite.config.js` 中 `/ai` 代理目标为 `http://localhost:8062`（`.env.development` 无覆盖）；
- 但实际跑着的服务是用 `--port 8000` 启动的旧进程（且加载的是修复前的旧代码）；
- 8062 端口无人监听 → 有趣的是前端请求并未报错，而是打到了旧进程上，行为自然与修复前一致。

### 修复

杀掉 8000 旧进程，按文档端口重启：

```
uv run --project spatial_analyse uvicorn spatial_analyse.api_server:app --port 8062 --app-dir .
```

### 教训

改完代码 ≠ 生效。**验证修复必须打真实入口链路**（前端 → 代理 → 服务），
后端进程重启与端口一致性要作为验证清单的第一项。

---

## 八、后续问题 ②：第一轮提问总不被回答，第二轮才正常 —— 前端 history 把当前问题误删

### 现象

每次会话的**第一个问题**，助手都回复"请告诉我目的地和月份"；把同一个问题**再发一次**才能得到正常研判。
看起来像"顺序错了/慢一拍"。

### 排查

后端用与前端完全相同的 payload 连测 3 次，第一轮全部正常调工具出结果 → 问题在前端。

`agentView.vue` 构造 history 的代码：

```js
const history = messages.value
  .filter(m => m.content || m === assistant)   // ← 问题所在
  .slice(0, -1)
  .map(m => ({ role: m.role, content: m.content }))
  .filter(m => m.content)
```

本意：保留有内容的消息 + 刚 push 的空 assistant 占位，再用 `slice(0, -1)` 去掉占位。

实际：**Vue 3 响应式数组取出的元素是 Proxy，`m === assistant`（proxy vs 原始对象）恒为 false**。
于是空占位在 filter 阶段就被丢弃，`slice(0, -1)` 接着误删了最后一条真实消息——
**恰好是用户刚输入的问题**。第一轮发给后端的 history 只剩欢迎语，模型只能追问；
第二轮时上一个问题已留在历史里，所以"第二次问才回答"。

### 修复

不再依赖对象引用比较，先砍掉末尾占位再过滤空消息：

```js
const history = messages.value
  .slice(0, -1)          // 末尾一定是刚 push 的空 assistant 占位
  .filter(m => m.content)
  .map(m => ({ role: m.role, content: m.content }))
```

### 教训

1. **Vue 3 reactive 数组元素是 Proxy**，与原始对象做 `===` 比较恒为 false。
   要判断"是否刚 push 的那一项"，用位置（末尾）或 id 字段，不要用引用相等。
2. "第二次问才正常"这类**慢一拍**现象，优先怀疑发送侧把当前轮消息漏掉了：
   直接打印/抓包发给后端的实际 payload，一眼可见。
