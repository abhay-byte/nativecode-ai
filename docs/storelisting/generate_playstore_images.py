import os
import urllib.request
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT_DIR = "/home/abhaybyte/repos/termux-lib/docs/images"
SCREENSHOTS_DIR = "/home/abhaybyte/repos/termux-lib/docs/screenshots/v1"
os.makedirs(OUT_DIR, exist_ok=True)

# Download font
FONT_URL = "https://github.com/googlefonts/SpaceGrotesk/raw/main/fonts/ttf/SpaceGrotesk-Bold.ttf"
FONT_PATH = "/tmp/SpaceGrotesk-Bold.ttf"
if not os.path.exists(FONT_PATH):
    try:
        urllib.request.urlretrieve(FONT_URL, FONT_PATH)
    except:
        pass

def get_font(size):
    try:
        return ImageFont.truetype(FONT_PATH, size)
    except:
        return ImageFont.load_default()

WIDTH, HEIGHT = 1080, 1920
BG_COLOR = "#131313"
ACCENT_COLOR = "#3DDC84"
TEXT_COLOR = "#FAFAFA"

def create_base():
    img = Image.new("RGB", (WIDTH, HEIGHT), BG_COLOR)
    # Add subtle grid texture
    draw = ImageDraw.Draw(img)
    for x in range(0, WIDTH, 60):
        draw.line([(x, 0), (x, HEIGHT)], fill="#1E1E1E", width=1)
    for y in range(0, HEIGHT, 60):
        draw.line([(0, y), (WIDTH, y)], fill="#1E1E1E", width=1)
    return img

def add_text(img, title, subtitle="", y_pos=150):
    draw = ImageDraw.Draw(img)
    font_large = get_font(80)
    font_medium = get_font(50)
    
    # Title
    try:
        bbox = draw.textbbox((0, 0), title, font=font_large)
        tw = bbox[2] - bbox[0]
    except:
        tw = len(title) * 40
    draw.text(((WIDTH - tw) // 2, y_pos), title, font=font_large, fill=ACCENT_COLOR)
    
    # Subtitle
    if subtitle:
        try:
            bbox = draw.textbbox((0, 0), subtitle, font=font_medium)
            sw = bbox[2] - bbox[0]
        except:
            sw = len(subtitle) * 25
        draw.text(((WIDTH - sw) // 2, y_pos + 120), subtitle, font=font_medium, fill=TEXT_COLOR)

def paste_screenshot(base, ss_name, position, scale=0.6, rotate=0, blur=0):
    ss_path = os.path.join(SCREENSHOTS_DIR, ss_name)
    if not os.path.exists(ss_path):
        return
    try:
        ss = Image.open(ss_path).convert("RGBA")
        if blur > 0:
            ss = ss.filter(ImageFilter.GaussianBlur(blur))
        
        # Resize
        w, h = ss.size
        new_w, new_h = int(w * scale), int(h * scale)
        ss = ss.resize((new_w, new_h), Image.Resampling.LANCZOS)
        
        # Rotate
        if rotate != 0:
            ss = ss.rotate(rotate, expand=True, fillcolor=(0,0,0,0))
            
        # Paste
        base.paste(ss, position, ss)
    except Exception as e:
        print(f"Error pasting {ss_name}: {e}")

# 1. The Hook
img1 = create_base()
paste_screenshot(img1, "home.png", (100, 400), scale=0.8, rotate=-10, blur=5)
paste_screenshot(img1, "terminal.png", (400, 800), scale=0.7, rotate=15, blur=3)
add_text(img1, "Native Code", "Your Pocket IDE", 800)
img1.save(os.path.join(OUT_DIR, "01_hero.png"))

# 2. AI Tools Run on Device
img2 = create_base()
add_text(img2, "AI Tools on Device", "Opencode, AGY, Codex, Claude", 150)
paste_screenshot(img2, "terminal_opencode.png", (50, 400), scale=0.55, rotate=-5)
paste_screenshot(img2, "terminal_agy.png", (450, 600), scale=0.55, rotate=5)
paste_screenshot(img2, "terminal_codex.png", (50, 1000), scale=0.55, rotate=-2)
paste_screenshot(img2, "terminal_claude_code.png", (450, 1200), scale=0.55, rotate=8)
img2.save(os.path.join(OUT_DIR, "02_ai_tools.png"))

# 3. Project Management
img3 = create_base()
add_text(img3, "Manage Complete Projects", "Workspace, Git, Settings", 150)
paste_screenshot(img3, "project_workspace.png", (200, 350), scale=0.6)
paste_screenshot(img3, "project_directory.png", (100, 800), scale=0.6, rotate=-5)
paste_screenshot(img3, "git_diff.png", (300, 1200), scale=0.6, rotate=5)
img3.save(os.path.join(OUT_DIR, "03_projects.png"))

# 4. Debian Shell + AI
img4 = create_base()
add_text(img4, "Debian Shell + AI", "Total Terminal Control", 150)
paste_screenshot(img4, "terminal.png", (150, 350), scale=0.65)
paste_screenshot(img4, "terminal_agy.png", (150, 950), scale=0.65)
img4.save(os.path.join(OUT_DIR, "04_debian.png"))

# 5. Agentic Developer
img5 = create_base()
add_text(img5, "Agentic Developer", "Autonomous Coding", 150)
paste_screenshot(img5, "workspace_agent.png", (150, 400), scale=0.6)
paste_screenshot(img5, "terminal_opencode.png", (150, 1000), scale=0.6)
img5.save(os.path.join(OUT_DIR, "05_agentic.png"))

# 6. Project Creator
img6 = create_base()
add_text(img6, "Project Creator", "For Entrepreneurs & Hustlers", 150)
paste_screenshot(img6, "all_projects.png", (150, 350), scale=0.6)
paste_screenshot(img6, "marketplace.png", (150, 950), scale=0.6)
img6.save(os.path.join(OUT_DIR, "06_creator.png"))

# 7. Vibe Code on Phone
img7 = create_base()
add_text(img7, "Vibe Code on Phone", "Easy To Use Pocket AI", 150)
paste_screenshot(img7, "home.png", (200, 400), scale=0.7)
paste_screenshot(img7, "repairs.png", (200, 1100), scale=0.7)
img7.save(os.path.join(OUT_DIR, "07_vibecode.png"))

# 8. Ship Faster
img8 = create_base()
add_text(img8, "Ship Faster.", "Build Anywhere.", 150)
paste_screenshot(img8, "xfce4_display.png", (150, 400), scale=0.6)
paste_screenshot(img8, "project_config.png", (150, 1000), scale=0.6)
img8.save(os.path.join(OUT_DIR, "08_ship.png"))

print("Store listing images generated at", OUT_DIR)
