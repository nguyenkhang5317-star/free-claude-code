#!/usr/bin/env python3
"""
Minecraft 1.16.5 — Desktop App (Tkinter, KHÔNG phải web)
App native chạy trực tiếp bằng Python, không cần browser/server.
Chạy: python minecraft/app.py
Controls: WASD di chuyển, Space nhảy, chuột trái đào, chuột phải đặt,
1-9 hotbar, E túi đồ, F ăn, Esc menu.
"""
import tkinter as tk
from tkinter import messagebox
import math, random, json, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"

# --- load registries ---
def load_json(name):
    try:
        return json.loads((DATA / name).read_text(encoding="utf-8"))
    except Exception:
        return {}
BLOCKS = {b["id"]: b for b in load_json("blocks.json").get("blocks", [])}
ITEMS = {i["id"]: i for i in load_json("items.json").get("items", [])}
MOBS = {m["id"].replace("minecraft:", ""): m for m in load_json("mobs.json").get("mobs", [])}

# --- worldgen (deterministic, port từ world.js) ---
def hash_seed(s):
    h = 2166136261
    for ch in str(s):
        h ^= ord(ch); h = (h * 16777619) & 0xFFFFFFFF
    return h

def mulberry32(a):
    def f():
        nonlocal a
        a = (a + 0x6D2B79F5) & 0xFFFFFFFF
        t = a
        t = (t ^ (t >> 15)) * (t | 1) & 0xFFFFFFFF
        t ^= (t + ((t ^ (t >> 7)) * (t | 61) & 0xFFFFFFFF)) & 0xFFFFFFFF
        return ((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296
    return f

class Noise:
    def __init__(self, seed):
        rng = mulberry32(seed)
        perm = list(range(256))
        for i in range(255, 0, -1):
            j = int(rng() * (i + 1)); perm[i], perm[j] = perm[j], perm[i]
        self.p = perm * 2
    def fade(self, t): return t*t*t*(t*(t*6-15)+10)
    def grad(self, h, x, y, z):
        h &= 7
        return [x+y, x-y, -x+y, -x-y, x+z, x-z, -x+z, -x-z][h]
    def noise(self, x, y, z):
        X, Y, Z = math.floor(x) & 255, math.floor(y) & 255, math.floor(z) & 255
        x -= math.floor(x); y -= math.floor(y); z -= math.floor(z)
        u, v, w = self.fade(x), self.fade(y), self.fade(z)
        A = self.p[X] + Y; AA = self.p[A] + Z; AB = self.p[A+1] + Z
        B = self.p[X+1] + Y; BA = self.p[B] + Z; BB = self.p[B+1] + Z
        l = lambda a, b, t: a + t*(b-a)
        return l(l(l(self.grad(self.p[AA],x,y,z), self.grad(self.p[BA],x-1,y,z), u),
                   l(self.grad(self.p[AB],x,y-1,z), self.grad(self.p[BB],x-1,y-1,z), u), v),
                 l(l(self.grad(self.p[AA+1],x,y,z-1), self.grad(self.p[BA+1],x-1,y,z-1), u),
                   l(self.grad(self.p[AB+1],x,y-1,z-1), self.grad(self.p[BB+1],x-1,y-1,z-1), u), v), w)
    def fbm(self, x, y, z, oct=4):
        a, amp, f = 0, 0.5, 1
        for _ in range(oct):
            a += amp * self.noise(x*f, y*f, z*f); amp *= 0.5; f *= 2
        return a

SEA = 28
BLOCK_COLORS = {
    "minecraft:grass_block": "#5f9f35", "minecraft:dirt": "#866043", "minecraft:stone": "#7d7d7d",
    "minecraft:cobblestone": "#6e6e6e", "minecraft:oak_log": "#6b5232", "minecraft:oak_planks": "#a88852",
    "minecraft:sand": "#dbcfA3", "minecraft:glass": "#c8e6f0", "minecraft:oak_leaves": "#387626",
    "minecraft:bricks": "#966153", "minecraft:bedrock": "#555555", "minecraft:water": "#345fda",
    "minecraft:gold_ore": "#fcee4b", "minecraft:diamond_ore": "#5dece7", "minecraft:iron_ore": "#d8af93",
    "minecraft:coal_ore": "#232323", "minecraft:snow_block": "#f0fbfb", "minecraft:gravel": "#887e7e",
    "minecraft:torch": "#ffcc33", "minecraft:crafting_table": "#a88852", "minecraft:furnace": "#8a8a8a",
    "minecraft:tnt": "#e04030", "minecraft:pumpkin": "#e07b20", "minecraft:melon": "#7ac74f",
    "minecraft:white_wool": "#eeeeee", "minecraft:netherrack": "#703a3a", "minecraft:end_stone": "#dbdba4",
    "minecraft:ice": "#a0d8ef", "minecraft:obsidian": "#1a1030", "minecraft:glowstone": "#ffcc66",
}
def block_color(name): return BLOCK_COLORS.get(name, "#888888")

class World:
    def __init__(self, seed_str, dim="overworld"):
        self.seed_str = str(seed_str); self.seed = hash_seed(self.seed_str); self.dim = dim
        self.n1 = Noise(self.seed); self.n2 = Noise(self.seed ^ 0x9e3779b9); self.n3 = Noise(self.seed ^ 0x85ebca6b)
        self.blocks = {}  # (x,y,z) -> id string
        self._gen()
    def height_at(self, x, z):
        if self.dim == "nether": return 20 + int(self.n1.fbm(x*0.01, 0, z*0.01, 3) * 10)
        if self.dim == "end": return 30 + int(self.n1.fbm(x*0.02, 0, z*0.02, 2) * 4)
        base = self.n1.fbm(x*0.008, 0, z*0.008, 4) * 22
        hills = self.n2.fbm(x*0.03, 0, z*0.03, 3) * 6
        m = abs(self.n3.fbm(x*0.004, 0, z*0.004, 2)) * 18
        return max(4, int(SEA - 6 + base + hills + m))
    def _gen(self):
        rng = mulberry32(self.seed)
        R = 24  # generate 48x48 area around spawn
        for x in range(-R, R):
            for z in range(-R, R):
                h = self.height_at(x, z)
                for y in range(0, h+1):
                    if y == 0: bid = "minecraft:bedrock"
                    elif y < h-3: bid = "minecraft:stone"
                    elif y < h: bid = "minecraft:dirt"
                    else: bid = "minecraft:grass_block"
                    if bid == "minecraft:stone" and y < 40:
                        r = rng()
                        if y < 12 and r < 0.008: bid = "minecraft:diamond_ore"
                        elif y < 28 and r < 0.012: bid = "minecraft:iron_ore"
                        elif r < 0.02: bid = "minecraft:coal_ore"
                    if y > 2 and y < h-2 and bid != "minecraft:bedrock":
                        if self.n1.fbm(x*0.04, y*0.06, z*0.04, 2) > 0.62: continue  # cave
                    self.blocks[(x, y, z)] = bid
                if h < SEA and self.dim == "overworld":
                    for y in range(h+1, SEA+1): self.blocks[(x, y, z)] = "minecraft:water"
                # trees
                if self.dim == "overworld" and h > SEA and rng() < 0.02:
                    for dy in range(1, 5): self.blocks[(x, h+dy, z)] = "minecraft:oak_log"
                    for dx in (-1,0,1):
                        for dz in (-1,0,1):
                            for dy in (4,5):
                                if (x+dx, h+dy, z+dz) not in self.blocks:
                                    self.blocks[(x+dx, h+dy, z+dz)] = "minecraft:oak_leaves"
    def get(self, x, y, z): return self.blocks.get((x, y, z))
    def set(self, x, y, z, bid):
        if bid is None: self.blocks.pop((x, y, z), None)
        else: self.blocks[(x, y, z)] = bid
    def ground(self, x, z):
        for y in range(60, -1, -1):
            b = self.get(x, y, z)
            if b and b != "minecraft:water": return y
        return 0

class Game(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Minecraft 1.16.5 — Desktop App")
        self.geometry("960x640"); self.resizable(False, False)
        self.configure(bg="#1a1a1a")
        self.keys = set(); self.mouse = (0, 0)
        self.inventory = [None]*9; self.sel = 0
        self.hp = 20; self.hunger = 20; self.time = 0.3; self.day = 1; self.tick = 0
        self.px, self.pz, self.py, self.vy = 0, 0, 30, 0
        self.on_ground = False
        self.world = None
        self.build_menu()
        self.bind_all("<KeyPress>", lambda e: (self.keys.add(e.keysym.lower()), self.on_key(e)))
        self.bind_all("<KeyRelease>", lambda e: self.keys.discard(e.keysym.lower()))
        self.bind_all("<Button-1>", lambda e: self.mine(e))
        self.bind_all("<Button-3>", lambda e: self.place(e))
        self.bind_all("<Motion>", lambda e: setattr(self, "mouse", (e.x, e.y)))

    def build_menu(self):
        for w in self.winfo_children(): w.destroy()
        f = tk.Frame(self, bg="#5a8f3c"); f.pack(fill="both", expand=True)
        tk.Label(f, text="MINECRAFT", font=("Arial", 44, "bold"), bg="#5a8f3c", fg="white").pack(pady=(60, 0))
        tk.Label(f, text="Java Edition 1.16.5 — Desktop App (không phải web)", font=("Arial", 12), bg="#5a8f3c", fg="white").pack()
        tk.Label(f, text="Seed:", bg="#5a8f3c", fg="white").pack(pady=(20, 0))
        self.seed_e = tk.Entry(f, font=("Arial", 14)); self.seed_e.insert(0, "12345"); self.seed_e.pack()
        tk.Label(f, text="World type:", bg="#5a8f3c", fg="white").pack(pady=(8, 0))
        self.dim_v = tk.StringVar(value="overworld")
        tk.OptionMenu(f, self.dim_v, "overworld", "nether", "end").pack()
        tk.Button(f, text="▶  PLAY", font=("Arial", 20, "bold"), bg="#5cb85c", fg="white", relief="flat", width=14, height=2, command=self.start).pack(pady=20)
        tk.Label(f, text="WASD di chuyển · Space nhảy · Chuột trái đào · Chuột phải đặt · 1-9 hotbar · E túi đồ · F ăn", bg="#5a8f3c", fg="white", font=("Arial", 9)).pack()

    def start(self):
        seed = self.seed_e.get() or "12345"
        self.world = World(seed, self.dim_v.get())
        self.px, self.pz = 0, 0; self.py = self.world.ground(0, 0) + 1
        for w in self.winfo_children(): w.destroy()
        self.canvas = tk.Canvas(self, width=960, height=640, bg="#87ceeb", highlightthickness=0)
        self.canvas.pack()
        self.hud = tk.Label(self, bg="#222222", fg="white", font=("Arial", 10), anchor="w")
        self.hud.pack(fill="x")
        self.hotbar = tk.Frame(self, bg="#222222"); self.hotbar.pack(fill="x")
        self.render_hotbar()
        self.after(50, self.loop)

    def render_hotbar(self):
        for w in self.hotbar.winfo_children(): w.destroy()
        for i in range(9):
            s = self.inventory[i]
            bg = "#ffffff" if i == self.sel else "#444444"
            tk.Label(self.hotbar, text=(s[0].replace("minecraft:", "") + f" x{s[1]}") if s else str(i+1),
                     bg=bg, fg="black" if i == self.sel else "white", width=12, height=2, font=("Arial", 8)).pack(side="left", padx=2, pady=2)

    def on_key(self, e):
        if e.keysym.isdigit():
            n = int(e.keysym)
            if 1 <= n <= 9: self.sel = n-1; self.render_hotbar()
        if e.keysym.lower() == "f": self.eat()
        if e.keysym.lower() == "e": self.show_inv()
        if e.keysym == "Escape": self.build_menu()

    def eat(self):
        s = self.inventory[self.sel]
        if s and ITEMS.get(s[0], {}).get("food"):
            self.hunger = min(20, self.hunger + ITEMS[s[0]]["food"]); s[1] -= 1
            if s[1] <= 0: self.inventory[self.sel] = None
            self.render_hotbar()

    def show_inv(self):
        top = tk.Toplevel(self); top.title("Inventory"); top.geometry("400x300")
        for i, s in enumerate(self.inventory):
            tk.Label(top, text=f"{i+1}: {s[0] if s else 'empty'}", anchor="w").pack(fill="x")
        tk.Label(top, text="Craft: log -> planks (4), planks x2 -> stick (4)").pack(pady=8)
        tk.Button(top, text="Craft planks", command=lambda: self.craft("minecraft:oak_log", "minecraft:oak_planks", 4)).pack()
        tk.Button(top, text="Craft stick", command=lambda: self.craft("minecraft:oak_planks", "minecraft:stick", 4)).pack()

    def craft(self, src, dst, n):
        for i, s in enumerate(self.inventory):
            if s and s[0] == src and s[1] >= 1:
                s[1] -= 1
                if s[1] <= 0: self.inventory[i] = None
                self.add_item(dst, n); self.render_hotbar(); return
        messagebox.showinfo("Craft", "Không đủ nguyên liệu")

    def add_item(self, bid, n=1):
        for i, s in enumerate(self.inventory):
            if s and s[0] == bid and s[1] < 64: s[1] += n; return
        for i, s in enumerate(self.inventory):
            if s is None: self.inventory[i] = [bid, n]; return

    def screen_to_block(self, mx, my):
        # inverse isometric approx: find nearest block top face
        best, bd = None, 1e9
        for (x, y, z), bid in self.world.blocks.items():
            if abs(x - self.px) > 12 or abs(z - self.pz) > 12: continue
            sx, sy = self.iso(x, y, z)
            d = (sx-mx)**2 + (sy-my)**2
            if d < bd: bd, best = d, (x, y, z, bid)
        return best if bd < 2500 else None

    def mine(self, e):
        hit = self.screen_to_block(e.x, e.y)
        if hit:
            x, y, z, bid = hit
            self.world.set(x, y, z, None); self.add_item(bid, 1); self.render_hotbar()

    def place(self, e):
        hit = self.screen_to_block(e.x, e.y)
        s = self.inventory[self.sel]
        if hit and s:
            x, y, z, _ = hit
            if not self.world.get(x, y+1, z):
                self.world.set(x, y+1, z, s[0]); s[1] -= 1
                if s[1] <= 0: self.inventory[self.sel] = None
                self.render_hotbar()

    def iso(self, x, y, z):
        # isometric projection centered on player
        dx, dz = x - self.px, z - self.pz
        sx = 480 + (dx - dz) * 16
        sy = 320 + (dx + dz) * 8 - (y - self.py) * 16
        return sx, sy

    def loop(self):
        self.tick += 1
        # movement
        sp = 0.25
        dx = dz = 0
        if "w" in self.keys: dz -= sp
        if "s" in self.keys: dz += sp
        if "a" in self.keys: dx -= sp
        if "d" in self.keys: dx += sp
        self.px += dx; self.pz += dz
        # gravity
        self.vy -= 0.08; self.py += self.vy
        g = self.world.ground(int(self.px), int(self.pz)) + 1
        if self.py <= g:
            if self.vy < -0.6: self.hp -= int(-self.vy * 4)
            self.py = g; self.vy = 0; self.on_ground = True
        else: self.on_ground = False
        if "space" in self.keys and self.on_ground: self.vy = 0.42
        if self.hp <= 0:
            messagebox.showinfo("You died!", "Respawn...")
            self.hp = 20; self.py = self.world.ground(int(self.px), int(self.pz)) + 1
        # hunger
        if self.tick % 200 == 0 and self.hunger > 0: self.hunger -= 1
        if self.hunger <= 0: self.hp -= 1
        # day/night
        self.time = (self.time + 0.0005) % 1
        day_f = max(0, math.sin(self.time * math.pi * 2))
        bg = "#87ceeb" if day_f > 0.3 else "#1a1a3a"
        self.canvas.configure(bg=bg)
        self.draw()
        self.hud.config(text=f"Seed: {self.world.seed_str}  Day {self.day}  {int(self.time*24):02d}:00  HP {self.hp}/20  Hunger {self.hunger}/20  Pos {int(self.px)},{int(self.py)},{int(self.pz)}")
        self.after(50, self.loop)  # 20 TPS

    def draw(self):
        c = self.canvas; c.delete("all")
        # collect visible blocks sorted far->near
        vis = []
        for (x, y, z), bid in self.world.blocks.items():
            if abs(x - self.px) > 14 or abs(z - self.pz) > 14: continue
            vis.append((x + z, x, y, z, bid))
        vis.sort()
        for _, x, y, z, bid in vis:
            sx, sy = self.iso(x, y, z)
            col = block_color(bid)
            # top face (diamond) + sides
            c.create_polygon(sx, sy-8, sx+16, sy, sx, sy+8, sx-16, sy, fill=col, outline="#000000", width=1)
            c.create_polygon(sx-16, sy, sx, sy+8, sx, sy+24, sx-16, sy+16, fill=col, outline="#000000", width=1)
            c.create_polygon(sx+16, sy, sx, sy+8, sx, sy+24, sx+16, sy+16, fill=col, outline="#000000", width=1)
        # player
        px, py = self.iso(self.px, self.py, self.pz)
        c.create_rectangle(px-6, py-28, px+6, py, fill="#3a7d44", outline="black")
        c.create_rectangle(px-5, py-40, px+5, py-28, fill="#e8c39e", outline="black")
        c.create_text(480, 20, text="WASD di chuyển · Space nhảy · Click trái đào · Click phải đặt · E túi đồ", fill="white", font=("Arial", 10))

if __name__ == "__main__":
    Game().mainloop()
