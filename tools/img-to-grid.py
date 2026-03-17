#!/usr/bin/env python3
"""Convert dithered 48x48 PNG images to skin grid JSON format.

Reads all *_48.png files from the dithered directory, extracts pixel data,
builds a unified color map, and outputs a skin JSON file.
"""

import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow", "-q"])
    from PIL import Image


def img_to_grid(path: str) -> tuple[list[list[int]], dict[int, str]]:
    """Convert an image to a grid of color indices + color map."""
    img = Image.open(path).convert("RGBA")
    w, h = img.size

    colors: dict[tuple, int] = {}
    color_map: dict[int, str] = {}
    grid: list[list[int]] = []
    next_idx = 1  # 0 = transparent

    # Background detection: sample corners
    corners = [img.getpixel((0, 0)), img.getpixel((w-1, 0)),
               img.getpixel((0, h-1)), img.getpixel((w-1, h-1))]
    bg_color = max(set(corners), key=corners.count)

    for y in range(h):
        row = []
        for x in range(w):
            px = img.getpixel((x, y))
            r, g, b, a = px

            # Treat near-black and background as transparent
            if a < 128 or (r < 20 and g < 20 and b < 20):
                row.append(0)
                continue

            # Also treat the detected background color as transparent
            if abs(r - bg_color[0]) < 15 and abs(g - bg_color[1]) < 15 and abs(b - bg_color[2]) < 15:
                row.append(0)
                continue

            key = (r, g, b)
            if key not in colors:
                colors[key] = next_idx
                # Store as ARGB long for Android Color(long)
                argb = 0xFF000000 | (r << 16) | (g << 8) | b
                color_map[next_idx] = f"0x{argb:08X}"
                next_idx += 1

            row.append(colors[key])
        grid.append(row)

    return grid, color_map


def main():
    dithered_dir = Path("/home/semmy/codeprojects/CCReStatus/assets/ghost-dithered")
    out_path = Path("/home/semmy/codeprojects/CCReStatus/assets/ghost-skin.json")

    # State mapping: base frames and animation frames
    states = {
        "IDLE": ["idle_48", "idle_f2_48"],
        "THINKING": ["thinking_48", "thinking_f2_48", "thinking_f3_48"],
        "TOOL_CALL": ["tool_call_48", "tool_call_f2_48"],
        "AWAITING_INPUT": ["awaiting_input_48", "awaiting_input_f2_48"],
        "ERROR": ["error_48"],
        "COMPLETE": ["complete_48"],
    }

    # Build unified color map across all images
    all_colors: dict[tuple, int] = {}
    unified_map: dict[int, str] = {}
    next_idx = 1

    all_grids: dict[str, list[list[int]]] = {}

    for state, frames in states.items():
        for frame_name in frames:
            path = dithered_dir / f"{frame_name}.png"
            if not path.exists():
                print(f"  SKIP {frame_name} (not found)")
                continue

            img = Image.open(path).convert("RGBA")
            w, h = img.size

            # Detect background from corners
            corners = [img.getpixel((0, 0)), img.getpixel((w-1, 0)),
                       img.getpixel((0, h-1)), img.getpixel((w-1, h-1))]
            bg_color = max(set(corners), key=corners.count)

            grid = []
            for y in range(h):
                row = []
                for x in range(w):
                    r, g, b, a = img.getpixel((x, y))

                    if a < 128 or (r < 20 and g < 20 and b < 20):
                        row.append(0)
                        continue

                    if (abs(r - bg_color[0]) < 15 and
                        abs(g - bg_color[1]) < 15 and
                        abs(b - bg_color[2]) < 15):
                        row.append(0)
                        continue

                    key = (r, g, b)
                    if key not in all_colors:
                        all_colors[key] = next_idx
                        argb = 0xFF000000 | (r << 16) | (g << 8) | b
                        unified_map[next_idx] = int(argb) if argb < 0x80000000 else int(argb) - 0x100000000
                        next_idx += 1

                    row.append(all_colors[key])
                grid.append(row)

            all_grids[frame_name] = grid
            print(f"  {frame_name}: {w}x{h}, {len(set(v for row in grid for v in row if v > 0))} colors used")

    # Build the skin JSON
    mascot_frames = {}
    for state, frames in states.items():
        state_grids = []
        for fn in frames:
            if fn in all_grids:
                state_grids.append(all_grids[fn])
        if state_grids:
            mascot_frames[state] = state_grids

    skin = {
        "id": "dithered-ghost",
        "name": "Dithered Ghost",
        "description": "AI-generated pixel ghost with ordered dithering",
        "author": "Gemini + ImageMagick",
        "version": 1,
        "mascot": {
            "gridSize": 48,
            "colorMap": {str(k): v for k, v in unified_map.items()},
            "frames": mascot_frames,
            "animation": {
                "breatheMin": 0.96,
                "breatheMax": 1.04,
                "wobbleOffset": 2.0,
                "bounceHeight": 6.0,
                "blinkIntervalMs": 3000
            }
        },
        "palette": {
            "accent": -2396652,
            "accentDeep": -4899756,
            "background": -14540782,
            "textPrimary": -1250054,
            "textSecondary": -8947849,
            "textTertiary": -2565888
        }
    }

    with open(out_path, "w") as f:
        json.dump(skin, f, indent=2)

    print(f"\nSkin JSON saved to {out_path}")
    print(f"Total unique colors: {len(unified_map)}")
    print(f"States with frames: {list(mascot_frames.keys())}")


if __name__ == "__main__":
    main()
