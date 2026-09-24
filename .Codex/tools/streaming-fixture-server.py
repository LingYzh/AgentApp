"""Loopback-only deterministic SSE source for emulator streaming QA; no model/key required."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import re
import time

PARAGRAPH = "这是固定节奏的流式验收文本，用来比较正文更新与底部跟随滚动。包含 **强调**、`inline code` 和普通文字。\n\n"
TEXT = "# 流式验收\n\n" + "".join(f"## 段落 {i}\n\n" + PARAGRAPH * 3 for i in range(1, 21))
RATE_TEXT = "# 速度分档验收\n\n" + "".join(f"## 段落 {i}\n\n" + PARAGRAPH * 3 for i in range(1, 9))


def chunks(prompt):
    """Keep the original steady baseline; explicit QA prompts select pause/burst cases."""
    rate_match = re.search(r"(?:rate|pause)-(40|70|120|300)\b", prompt)
    if rate_match:
        # Approximate units: one non-ASCII code point or four ASCII characters. These
        # reproducible rates are NOT measurements from any provider's tokenizer.
        budget = int(rate_match[1]) / 10
        pieces = []
        start = 0
        accumulated = 0.0
        for index, char in enumerate(RATE_TEXT):
            accumulated += .25 if ord(char) < 128 else 1.0
            if accumulated >= budget:
                pieces.append(RATE_TEXT[start:index + 1])
                start = index + 1
                accumulated -= budget
        if start < len(RATE_TEXT):
            pieces.append(RATE_TEXT[start:])
        paused = False
        offset = 0
        while offset < len(pieces):
            if "pause-" in prompt and not paused and offset >= len(pieces) // 3:
                yield "", 2.0
                paused = True
                yield "".join(pieces[offset:offset + 5]), .5
                offset += 5
            else:
                yield pieces[offset], .1
                offset += 1
        return
    if "buffer-qa" not in prompt:
        for start in range(0, len(TEXT), 48):
            yield TEXT[start:start + 48], .12
        return
    opening = "# 延迟缓冲播放\n\n首段缓冲后连续播放。中文、English、e\u0301、👨‍👩‍👧‍👦、🇨🇳。\n\n"
    for start in range(0, len(opening), 6):
        yield opening[start:start + 6], .10
    yield "停顿前的文本应继续排空。\n\n", 2.0
    yield "## 停顿后恢复\n\n", .05
    for start in range(0, len(TEXT), 96):
        yield TEXT[start:start + 96], .24
    yield "\n```kotlin\nval buffered = true\n```\n\n[延迟引用][ref]\n\n", .4
    yield "[ref]: https://example.com\n\n**播放完成。**", .02


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        # AgentEngine may append an environment user message after the real prompt.
        prompt = next((str(message.get("content", ""))
                       for message in reversed(request.get("messages", []))
                       if message.get("role") == "user" and re.search(
                           r"(?:rate|pause)-(?:40|70|120|300)\b|buffer-qa", str(message.get("content", "")))), "")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        try:
            for chunk, delay in chunks(prompt):
                event = {"choices": [{"index": 0, "delta": {"content": chunk}, "finish_reason": None}]}
                self.wfile.write(("data: " + json.dumps(event, ensure_ascii=False) + "\n\n").encode())
                self.wfile.flush()
                time.sleep(delay)
            self.wfile.write(b'data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n')
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 5582), Handler).serve_forever()
