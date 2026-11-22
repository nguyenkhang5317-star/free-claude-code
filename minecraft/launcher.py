#!/usr/bin/env python3
"""
Minecraft Launcher 1.16.5 — Desktop launcher (Tkinter)
Mô phỏng Minecraft Launcher chính thức: sidebar, Play, version, seed, world type.
Bấm Play -> khởi động server + mở game.
Chạy: python minecraft/launcher.py
"""
import tkinter as tk
from tkinter import ttk, messagebox
import subprocess, sys, os, time, webbrowser, threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app.py"

# Colors like official launcher (dark)
BG = "#1a1a1a"; SIDEBAR = "#2b2b2b"; CARD = "#3c3c3c"; GREEN = "#5cb85c"; GREEN_D = "#4a9a4a"; TEXT = "#ffffff"; MUTED = "#aaaaaa"; ACCENT = "#7ac74f"

class Launcher(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Minecraft Launcher 1.16.5")
        self.geometry("980x620")
        self.configure(bg=BG)
        self.server_proc = None
        self.build_ui()

    def build_ui(self):
        # Sidebar
        side = tk.Frame(self, bg=SIDEBAR, width=200)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)
        tk.Label(side, text="MINECRAFT", font=("Arial", 22, "bold"), bg=SIDEBAR, fg="white").pack(pady=(20,0))
        tk.Label(side, text="LAUNCHER", font=("Arial", 10), bg=SIDEBAR, fg=MUTED).pack()
        for label, cmd in [("▶  Play", self.show_play), ("📦  Installations", self.show_install), ("⚙  Settings", self.show_settings), ("📰  News", self.show_news)]:
            b = tk.Button(side, text=label, font=("Arial", 12), bg=SIDEBAR, fg="white", activebackground=CARD, relief="flat", anchor="w", command=cmd)
            b.pack(fill="x", padx=10, pady=6, ipady=8)
        tk.Label(side, text="v1.16.5\nWeb Edition", font=("Arial", 9), bg=SIDEBAR, fg=MUTED).pack(side="bottom", pady=10)

        # Main
        self.main = tk.Frame(self, bg=BG)
        self.main.pack(side="right", fill="both", expand=True)
        self.show_play()

    def clear_main(self):
        for w in self.main.winfo_children():
            w.destroy()

    def show_play(self):
        self.clear_main()
        # Banner
        banner = tk.Frame(self.main, bg="#5a8f3c", height=180)
        banner.pack(fill="x")
        tk.Label(banner, text="MINECRAFT", font=("Arial", 48, "bold"), bg="#5a8f3c", fg="white").pack(pady=(30,0))
        tk.Label(banner, text="Java Edition 1.16.5 — Web Edition", font=("Arial", 13), bg="#5a8f3c", fg="white").pack()
        # Controls card
        card = tk.Frame(self.main, bg=CARD, padx=20, pady=16)
        card.pack(fill="x", padx=20, pady=16)
        tk.Label(card, text="Launch options", font=("Arial", 13, "bold"), bg=CARD, fg="white").pack(anchor="w")
        row = tk.Frame(card, bg=CARD); row.pack(fill="x", pady=8)
        tk.Label(row, text="Version:", bg=CARD, fg="white", width=12, anchor="w").pack(side="left")
        self.version = ttk.Combobox(row, values=["1.16.5 (Web Edition)"], state="readonly"); self.version.set("1.16.5 (Web Edition)"); self.version.pack(side="left", fill="x", expand=True)
        row2 = tk.Frame(card, bg=CARD); row2.pack(fill="x", pady=4)
        tk.Label(row2, text="Seed:", bg=CARD, fg="white", width=12, anchor="w").pack(side="left")
        self.seed_var = tk.StringVar(value="12345")
        tk.Entry(row2, textvariable=self.seed_var, font=("Arial", 11)).pack(side="left", fill="x", expand=True)
        row3 = tk.Frame(card, bg=CARD); row3.pack(fill="x", pady=4)
        tk.Label(row3, text="World type:", bg=CARD, fg="white", width=12, anchor="w").pack(side="left")
        self.world_var = tk.StringVar(value="overworld")
        ttk.Combobox(row3, textvariable=self.world_var, values=["overworld","nether","end"], state="readonly").pack(side="left", fill="x", expand=True)
        # Play button
        self.play_btn = tk.Button(self.main, text="▶  PLAY", font=("Arial", 20, "bold"), bg=GREEN, fg="white", activebackground=GREEN_D, relief="flat", height=2, command=self.launch)
        self.play_btn.pack(pady=10)
        self.status = tk.Label(self.main, text="Ready. Press PLAY to launch.", bg=BG, fg=MUTED, font=("Arial", 10))
        self.status.pack()
        self.prog = ttk.Progressbar(self.main, length=400, mode="indeterminate")
        # News
        tk.Label(self.main, text="Latest news: 1.16.5 Web Edition — deterministic worldgen, 20 TPS, crafting & mobs.", bg=BG, fg=MUTED, font=("Arial", 9)).pack(pady=10)

    def show_install(self):
        self.clear_main()
        tk.Label(self.main, text="Installations", font=("Arial", 20, "bold"), bg=BG, fg="white").pack(pady=20)
        tk.Label(self.main, text="• 1.16.5 (Web Edition) — installed\n• Nether / End — chọn World type khi Play", bg=BG, fg=MUTED, font=("Arial", 11)).pack()

    def show_settings(self):
        self.clear_main()
        tk.Label(self.main, text="Settings", font=("Arial", 20, "bold"), bg=BG, fg="white").pack(pady=20)
        tk.Label(self.main, text="Render distance: 4 chunks\nFOV: 75\nGraphics: Fast\nServer port: 8000", bg=BG, fg=MUTED, font=("Arial", 11)).pack()
        tk.Button(self.main, text="Open game folder", bg=CARD, fg="white", relief="flat", command=lambda: os.startfile(str(ROOT)) if os.name=="nt" else webbrowser.open(str(ROOT))).pack(pady=10)

    def show_news(self):
        self.clear_main()
        tk.Label(self.main, text="News", font=("Arial", 20, "bold"), bg=BG, fg="white").pack(pady=20)
        tk.Label(self.main, text="Welcome to Minecraft 1.16.5 Web Edition!\n\n- Worldgen deterministic theo seed\n- Đào/đặt block, chế tạo, mob, ngày/đêm\n- Lệnh: /help /seed /give /tp /time", bg=BG, fg=MUTED, font=("Arial", 11)).pack()

    def launch(self):
        self.play_btn.config(state="disabled", text="LAUNCHING...")
        self.status.config(text="Starting server...")
        self.prog.pack(pady=6); self.prog.start(10)
        def run():
            try:
                env = os.environ.copy(); env["PORT"] = "8000"
                self.server_proc = subprocess.Popen([sys.executable, str(APP)], env=env)
                time.sleep(1.5)
                # open game with seed/dim via URL hash
                self.status.config(text="Game app started!")
                self.after(0, lambda: (self.prog.stop(), self.play_btn.config(state="normal", text="▶  PLAY")))
            except Exception as e:
                self.after(0, lambda: (messagebox.showerror("Launch failed", str(e)), self.prog.stop(), self.play_btn.config(state="normal", text="▶  PLAY")))
        threading.Thread(target=run, daemon=True).start()

    def on_close(self):
        if self.server_proc:
            self.server_proc.terminate()
        self.destroy()

if __name__ == "__main__":
    app = Launcher()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()
