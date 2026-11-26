TOKEN="$TOKEN" \
API_URL="http://$BACKEND_IP:8080/api/videos" \
python3 - <<'EOF'
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
token = os.environ["TOKEN"]
api_url = os.environ["API_URL"]
query = os.environ.get("QUERY", "")
page = 0
total = 0
while True:
    params = urllib.parse.urlencode({"page": page, "q": query})
    req = urllib.request.Request(
        f"{api_url}?{params}",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        print(f"HTTP {e.code} on page {page}: {err}", file=sys.stderr)
        sys.exit(1)
    items = data.get("content", [])
    if not items:
        break
    for v in items:
        artist = v.get("artist") or "Unknown"
        song = v.get("songTitle") or v.get("videoName") or "Unknown"
        duration = v.get("durationSeconds")
        duration_str = str(duration) if duration is not None else "?"
        print(f"{artist} - {song} ({duration_str} s)", flush=True)
        total += 1
    if not data.get("hasMore"):
        break
    page += 1
print(f"\nTotal: {total} videos")
EOF
