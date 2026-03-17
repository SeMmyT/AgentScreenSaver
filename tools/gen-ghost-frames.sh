#!/usr/bin/env bash
# gen-ghost-frames.sh — Generate animation frame variants for ghost mascot
set -euo pipefail

API_KEY="${GEMINI_API_KEY:-AIzaSyALn7rKnPPHMtlwPcRyO8-KFgU-czQIF5s}"
MODEL="gemini-3.1-flash-image-preview"
URL="https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${API_KEY}"
OUT_DIR="/home/semmy/codeprojects/CCReStatus/assets/ghost-gen"

generate() {
    local name="$1"
    local prompt="$2"
    local outfile="$OUT_DIR/${name}.png"

    echo "Generating: $name..."
    response=$(curl -s "$URL" \
        -H "Content-Type: application/json" \
        -d "$(python3 -c "
import json
prompt = $(python3 -c "import json; print(json.dumps('''$prompt'''))")
print(json.dumps({
    'contents': [{'parts': [{'text': prompt}]}],
    'generationConfig': {'responseModalities': ['IMAGE', 'TEXT']}
}))
")" 2>&1)

    echo "$response" | python3 -c "
import json, sys, base64
data = json.load(sys.stdin)
if 'error' in data:
    print(f'  ERROR: {data[\"error\"].get(\"message\",\"?\")[:100]}')
    sys.exit(1)
parts = data.get('candidates',[{}])[0].get('content',{}).get('parts',[])
for p in parts:
    if 'inlineData' in p:
        img = base64.b64decode(p['inlineData']['data'])
        with open('$outfile','wb') as f:
            f.write(img)
        print(f'  OK ({len(img)} bytes)')
        break
else:
    print('  No image')
"
}

STYLE="A cute ghost character mascot for a coding/developer screensaver app. Classic sheet-ghost shape, simple, friendly, cartoon-style. White/cream body. Pure black background (#0D0D0D). Centered in frame. 512x512 pixels. Pixel art aesthetic with clean edges. No text, no watermarks. MUST look like the same character as the other frames - consistent shape, size, and position."

# Frame 2 variants for animated states
generate "idle_f2" "${STYLE} The ghost is calm, floating gently. Eyes are closed (sleeping/blinking). Mouth is a small dash. Slightly lower position than normal — just settled down a tiny bit. Peaceful."

generate "thinking_f2" "${STYLE} The ghost is thinking hard. One eye squinting, the other looking up. A small lightbulb or question mark floating to the left (was on the right before). Slight tilt to the other side."

generate "thinking_f3" "${STYLE} The ghost is thinking intensely. Both eyes wide, spiral marks above head. Slightly tilted forward. Deep concentration."

generate "tool_call_f2" "${STYLE} The ghost is actively working — holding a tiny screwdriver. Eyes focused, determined. Motion lines on the other side. Arms in a different work position."

generate "awaiting_input_f2" "${STYLE} The ghost is looking at the viewer with big eyes, slight head tilt to the other side. The exclamation mark is now on the left. Bouncy, slightly higher position."

echo "Done!"
