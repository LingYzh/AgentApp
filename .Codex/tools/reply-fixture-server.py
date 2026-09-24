"""Emulator-only OpenAI fixture: two read-only tool rounds then a Markdown reply."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import time


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        messages = request.get("messages", [])
        start = next((i for i in range(len(messages) - 1, -1, -1)
                      if messages[i].get("role") == "user" and
                      "reply-qa" in str(messages[i].get("content", ""))), 0)
        results = [m for m in messages[start:] if m.get("role") == "tool"]
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.end_headers()

        def event(delta, finish=None):
            payload = {"choices": [{"index": 0, "delta": delta, "finish_reason": finish}]}
            self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode())
            self.wfile.flush()

        try:
            if len(results) < 2:
                index = len(results)
                event({"content": ["先查询当前会话权限。", "再确认最新权限，历史工具结果不代替实时状态。"][index]})
                time.sleep(6)
                event({"tool_calls": [{"index": 0, "id": f"state-{index}", "type": "function",
                                       "function": {"name": "get_session_state", "arguments": "{}"}}]})
                event({}, "tool_calls")
            else:
                text = "喵~主人下午好呀！Nya~❤\n\n普通语气保持同一基线。\n\nH~2~O、x^2^ 与 <sub>下标</sub>。\n\n**两轮工具调用已完成。**"
                for offset in range(0, len(text), 4):
                    event({"content": text[offset:offset + 4]})
                    time.sleep(.25)
                event({}, "stop")
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 5583), Handler).serve_forever()
