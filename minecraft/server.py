#!/usr/bin/env python3
"""Minecraft 1.16.5 web app server - serves client + data + save API."""
import json, os, sys, hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CLIENT = ROOT / "client"
DATA = ROOT / "data"
PORT = int(os.environ.get("PORT", "8000"))

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(CLIENT), **kw)
    def log_message(self, fmt, *args):
        sys.stderr.write("[mc] %s\n" % (fmt % args))
    def do_GET(self):
        if self.path.startswith("/data/"):
            p = DATA / self.path[6:].split("?")[0]
            if p.exists() and p.suffix == ".json":
                body = p.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
        if self.path == "/" or self.path == "/index.html":
            self.path = "/index.html"
        super().do_GET()
    def do_POST(self):
        if self.path == "/api/save":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length)
            try:
                data = json.loads(body)
                seed = str(data.get("seed", "0"))
                h = hashlib.sha256(seed.encode()).hexdigest()[:12]
                out = ROOT / "saves" / f"world_{h}.json"
                out.parent.mkdir(exist_ok=True)
                out.write_bytes(body)
                self._json({"ok": True, "file": str(out)})
            except Exception as e:
                self._json({"ok": False, "error": str(e)}, 400)
            return
        if self.path == "/api/health":
            self._json({"ok": True, "version": "1.16.5", "blocks": len(json.loads((DATA/"blocks.json").read_text())["blocks"])})
            return
        self.send_error(404)
    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    print(f"Minecraft 1.16.5 web app on http://0.0.0.0:{PORT}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
