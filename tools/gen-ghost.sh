#!/usr/bin/env bash
# gen-ghost.sh — Generate ghost mascot images for all states via Gemini
set -euo pipefail

API_KEY="${GEMINI_API_KEY:-AIzaSyALn7rKnPPHMtlwPcRyO8-KFgU-czQIF5s}"
MODEL="gemini-3.1-flash-image-preview"
URL="https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${API_KEY}"
OUT_DIR="${1:-/home/semmy/codeprojects/CCReStatus/assets/ghost-gen}"

mkdir -p "$OUT_DIR"

generate() {
    local state="$1"
    local prompt="$2"
    local outfile="$OUT_DIR/${state}.png"

    echo "Generating: $state..."
    curl -s "$URL" \
        -H "Content-Type: application/json" \
        -d "$(python3 -c "
import json
prompt = '''$prompt'''
print(json.dumps({
    'contents': [{'parts': [{'text': prompt}]}],
    'generationConfig': {'responseModalities': ['IMAGE', 'TEXT']}
}))
")" 2>&1 | python3 -c "
import json, sys, base64
data = json.load(sys.stdin)
if 'error' in data:
    print(f'  ERROR ($state):', data['error'].get('message','?')[:100])
    sys.exit(1)
parts = data.get('candidates',[{}])[0].get('content',{}).get('parts',[])
for p in parts:
    if 'inlineData' in p:
        img = base64.b64decode(p['inlineData']['data'])
        with open('$outfile','wb') as f:
            f.write(img)
        print(f'  Saved $outfile ({len(img)} bytes)')
        break
else:
    print(f'  No image in response for $state')
    sys.exit(1)
"
}

STYLE="A cute ghost character mascot for a coding/developer screensaver app. The ghost is a classic sheet-ghost shape, simple, friendly, cartoon-style. White/cream body with a slight glow. Pure black background (#0D0D0D). Centered in frame. 512x512 pixels. Pixel art aesthetic with clean edges. No text, no watermarks."

# Generate each state
generate "idle" "${STYLE} The ghost is calm, relaxed, floating gently. Eyes are simple dots, mouth is a small 'o'. Peaceful expression. Subtle floating pose."

generate "thinking" "${STYLE} The ghost is concentrating hard. One eye squinting, swirl/spiral marks near head suggesting deep thought. Slight tilt. Thinking pose."

generate "tool_call" "${STYLE} The ghost is actively working — holding a tiny wrench or hammer. Eyes determined, focused. Small motion lines suggesting activity. Busy working pose."

generate "awaiting_input" "${STYLE} The ghost is looking at the viewer expectantly with big wide eyes and an exclamation mark floating nearby. Bouncy, alert pose. Wants attention."

generate "error" "${STYLE} The ghost looks dizzy/confused with X eyes and a wavy mouth. Small stars or error symbols floating around its head. Distressed pose."

generate "complete" "${STYLE} The ghost is happy and satisfied with closed happy eyes (^ ^) and a big smile. Small sparkles or checkmark nearby. Celebratory pose."

echo "Done! Generated images in $OUT_DIR"
