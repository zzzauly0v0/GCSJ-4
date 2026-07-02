"""AI 灾害助手 FastAPI 服务。

启动: uv run uvicorn spatial_analyse.api_server:app --port 8000
"""
import json

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from spatial_analyse.agent_core import stream_reply

app = FastAPI(title="AI 灾害助手")


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: list[ChatMessage]


@app.get("/ai/health")
async def health():
    return {"status": "ok"}


@app.post("/ai/chat")
async def chat(req: ChatRequest):
    messages = [{"role": m.role, "content": m.content} for m in req.messages]

    async def event_gen():
        try:
            async for ev in stream_reply(messages):
                yield f"data: {json.dumps(ev, ensure_ascii=False)}\n\n"
        except Exception as e:  # 兜底: 任何未捕获异常也以 error 帧收尾
            err = {"type": "error", "message": f"服务异常: {e}"}
            yield f"data: {json.dumps(err, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_gen(), media_type="text/event-stream")
