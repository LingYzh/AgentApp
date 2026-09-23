"""Loopback-only deterministic SSE source for emulator streaming QA; no model/key required."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import time

PARAGRAPH = "这是固定节奏的流式验收文本，用来比较正文更新与底部跟随滚动。包含 **强调**、`inline code` 和普通文字。\n\n"
TEXT = "# 流式验收\n\n" + "".join(f"## 段落 {i}\n\n" + PARAGRAPH * 3 for i in range(1, 21))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        try:
            for start in range(0, len(TEXT), 48):
                event = {"choices": [{"index": 0, "delta": {"content": TEXT[start:start + 48]}, "finish_reason": None}]}
                self.wfile.write(("data: " + json.dumps(event, ensure_ascii=False) + "\n\n").encode())
                self.wfile.flush()
                time.sleep(.12)
            self.wfile.write(b'data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n')
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 5582), Handler).serve_forever()
