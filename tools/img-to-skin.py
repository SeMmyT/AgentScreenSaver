#!/usr/bin/env python3
"""Convert dithered images to a full Skin JSON for the CCReStatus app.

Outputs a JSON file with:
- mascot.grid: first idle frame (base grid)
- mascot.color_map: unified color map
- mascot.state_frames: per-state animation frame grids
- palette: app theme colors
- ascii_frames: placeholder ASCII art
"""

import json
from pathlib import Path
from PIL import Image

DITHERED_DIR = Path("/home/semmy/codeprojects/CCReStatus/assets/ghost-dithered")
OUT_PATH = Path("/home/semmy/codeprojects/CCReStatus/assets/ghost-skin.json")

# State mapping: state value -> list of frame filenames (without _48.png)
STATES = {
    "idle": ["idle", "idle_f2"],
    "thinking": ["thinking", "thinking_f2", "thinking_f3"],
    "tool_call": ["tool_call", "tool_call_f2"],
    "awaiting_input": ["awaiting_input", "awaiting_input_f2"],
    "error": ["error"],
    "complete": ["complete"],
}

# Ghost skin ASCII frames (kept from existing GhostSkin.kt)
ASCII_FRAMES = {
    "idle": [
        "   .-.\n  (o o)\n  | O |\n  /| |\\\n (_| |_)",
        "   .-.\n  (- -)\n  | O |\n  /| |\\\n (_| |_)",
    ],
    "thinking": [
        "   .-.\n  (o ~)  ?\n  | = |\n  /| |\\\n (_| |_)",
        "   .-.\n  (~ o) ?\n  | = |\n  /| |\\\n (_| |_)",
        "   .-.  ?\n  (o o)\n  | = |\n  /| |\\\n (_| |_)",
    ],
    "tool_call": [
        "   .-.\n  (o o)\n  | = |\\>\n  /| |\\\n (_| |_)",
        "   .-.\n  (o o)\n  | = |/>\n  /| |\\\n (_| |_)",
    ],
    "awaiting_input": [
        "   .-.\n  (O O)  !\n  | _ |\n  /| |\\\n (_| |_)",
        "   .-.\n  (O O) !\n  | _ |\n  /| |\\\n (_| |_)",
    ],
    "error": [
        "   .-.\n  (x x)\n  | ~ |\n  /| |\\\n (_| |_)",
    ],
    "complete": [
        "   .-.\n  (^ ^)\n  | v |\n  /| |\\\n (_| |_)",
    ],
}


def load_grid(path: Path, all_colors: dict, next_idx: list, unified_map: dict):
    """Load an image and convert to grid with shared color map."""
    img = Image.open(path).convert("RGBA")
    w, h = img.size

    # Detect background from corners
    corners = [img.getpixel((0, 0)), img.getpixel((w - 1, 0)),
               img.getpixel((0, h - 1)), img.getpixel((w - 1, h - 1))]
    bg_color = max(set(corners), key=corners.count)

    grid = []
    for y in range(h):
        row = []
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))

            # Transparent or near-black = 0
            if a < 128 or (r < 20 and g < 20 and b < 20):
                row.append(0)
                continue

            # Background color = 0
            if (abs(r - bg_color[0]) < 15 and
                abs(g - bg_color[1]) < 15 and
                abs(b - bg_color[2]) < 15):
                row.append(0)
                continue

            key = (r, g, b)
            if key not in all_colors:
                idx = next_idx[0]
                all_colors[key] = idx
                # Store as signed int (Java int) for Android Color()
                argb = (0xFF << 24) | (r << 16) | (g << 8) | b
                if argb >= 0x80000000:
                    argb -= 0x100000000
                unified_map[str(idx)] = str(argb)
                next_idx[0] += 1

            row.append(all_colors[key])
        grid.append(row)

    return grid


def main():
    all_colors: dict[tuple, int] = {}
    unified_map: dict[str, str] = {}
    next_idx = [1]  # mutable counter

    state_frames: dict[str, list] = {}
    base_grid = None

    for state, frame_names in STATES.items():
        grids = []
        for fname in frame_names:
            path = DITHERED_DIR / f"{fname}_48.png"
            if not path.exists():
                print(f"  SKIP {fname}_48.png")
                continue
            grid = load_grid(path, all_colors, next_idx, unified_map)
            grids.append(grid)
            print(f"  {fname}: loaded")

        if grids:
            state_frames[state] = grids
            # Use first idle frame as base grid
            if state == "idle" and base_grid is None:
                base_grid = grids[0]

    if base_grid is None:
        base_grid = [[0] * 48 for _ in range(48)]

    # Build skin JSON
    skin = {
        "id": "dithered-ghost",
        "name": "Dithered Ghost",
        "description": "AI-generated ghost with ordered dithering",
        "author": "Gemini + ImageMagick",
        "schema_version": 1,
        "content_version": 1,
        "is_premium": False,
        "mascot": {
            "grid": base_grid,
            "color_map": unified_map,
            "state_frames": state_frames,
        },
        "palette": {
            "accent": str(0xFFD97757 - 0x100000000),
            "accent_deep": str(0xFFBD5D3A - 0x100000000),
            "background": str(0xFF141413 - 0x100000000),
            "text_primary": str(0xFFFAF9F5 - 0x100000000),
            "text_secondary": str(0xFFB0AEA5 - 0x100000000),
            "text_tertiary": str(0xFFE8E6DC - 0x100000000),
        },
        "ascii_frames": ASCII_FRAMES,
    }

    with open(OUT_PATH, "w") as f:
        json.dump(skin, f)  # No indent — file is large

    size_mb = OUT_PATH.stat().st_size / 1024 / 1024
    print(f"\nSkin saved to {OUT_PATH} ({size_mb:.1f} MB)")
    print(f"Unique colors: {len(unified_map)}")
    print(f"States: {list(state_frames.keys())}")
    print(f"Grid size: {len(base_grid)}x{len(base_grid[0])}")


if __name__ == "__main__":
    main()
