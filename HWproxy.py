from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import httpx
import json

app = FastAPI()

# ========= 配置区 =========
MAAS_URL = "https://api.modelarts-maas.com/v2/chat/completions"
MAAS_API_KEY = "这里粘贴纯英文数字的华为MaaS key"

class OpenAIMsg(BaseModel):
    model: str
    messages: list
    stream: bool = False
    temperature: float = 0.7

@app.post("/v1/chat/completions")
async def chat(req: OpenAIMsg):
    headers = {
        "Authorization": f"Bearer {MAAS_API_KEY.strip()}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": req.model,
        "messages": req.messages,
        "stream": req.stream,
        "temperature": req.temperature
    }
    print("=====转发到华为MaaS的payload=====")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    try:
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(MAAS_URL, headers=headers, json=payload)
            print(f"华为返回status: {resp.status_code}")
            print(f"华为返回原始文本: {resp.text}")
            resp.raise_for_status()
            return resp.json()
    except httpx.HTTPStatusError as e:
        print(f"HTTPStatusError: {e.response.status_code}, body={e.response.text}")
        raise HTTPException(status_code=e.response.status_code, detail=e.response.text)
    except Exception as e:
        print(f"Exception: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("HWproxy:app", host="127.0.0.1", port=8000)
