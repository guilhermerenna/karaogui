DRY_RUN=0 \
API_URL="http://$BACKEND_IP:8080/api/videos/local" \
PATH_PREFIX="" \
python3 - <<'EOF'
import json
import os
import sys
import urllib.error
import urllib.request
dry_run = os.environ.get("DRY_RUN", "1") != "0"
api_url = os.environ["API_URL"]
prefix = os.environ.get("PATH_PREFIX", "").lstrip("/")
if prefix and not prefix.endswith("/"):
    prefix += "/"
FILES = [
    "Alice Merton__No Roots__242.mp4",
    "BIGBANG (빅뱅)__Still Life (봄여름가을겨울)__187.mp4",
    "BIGBANG (빅뱅)__거짓말 (Lies)__229.mp4",
    "Backstreet Boys__I Want It That Way__220.mp4",
    "Bruno Mars__Just The Way You Are__233.mp4",
    "Carly Rae Jepsen__Call Me Maybe__214.mp4",
    "Crush (크러쉬)__Beautiful__228.mp4",
    "D-Hack & PATEKO (디핵, 파테코)__OHAYO MY NIGHT__190.mp4",
    "EPIK HIGH (에픽하이) feat. Yoong Jin__Love Love Love__231.mp4",
    "Ella Langley__Choosin' Texas__232.mp4",
    "Gotye feat. Kimbra__Somebody That I Used To Know__265.mp4",
    "Haddaway__What Is Love__283.mp4",
    "Hot Chocolate__You Sexy Thing__260.mp4",
    "Jorge Ben Jor__País Tropical__174.mp4",
    "Justin Bieber__DAISIES__179.mp4",
    "Lady Gaga & Bradley Cooper__Shallow__227.mp4",
    "LeeSSang (리쌍) feat. 정인__헤어지지 못하는 여자, 떠나가지 못하는 남자__285.mp4",
    "Lewis Capaldi__Someone You Loved__202.mp4",
    "Los Hermanos__Anna Júlia__220.mp4",
    "Maroon 5__She Will Be Loved__282.mp4",
    "Milky Chance__Stolen Dance__318.mp4",
    "Måneskin__Coraline__320.mp4",
    "Ne-Yo__Because Of You__238.mp4",
    "Ne-Yo__So Sick__230.mp4",
    "Netinho__Milla__197.mp4",
    "PSY (싸이)__Gangnam Style (강남 스타일)__226.mp4",
    "Pitbull feat. John Ryan__Fireball__256.mp4",
    "Queen__Bohemian Rhapsody__375.mp4",
    "ROSÉ & Bruno Mars__APT__191.mp4",
    "Radiohead__Creep__258.mp4",
    "Sade__Smooth Operator__329.mp4",
    "Shakira feat. Wyclef Jean__Hips Don't Lie__233.mp4",
    "Shakira__Estoy Aquí__242.mp4",
    "Smash Mouth__I'm a Believer__195.mp4",
    "System Of A Down__Aerials__236.mp4",
    "The Lumineers__Ho Hey__175.mp4",
    "Tim Maia__Não Quero Dinheiro__165.mp4",
    "Tribalistas__Já Sei Namorar__191.mp4",
    "Vance Joy__Riptide__226.mp4",
    "WOODZ (조승연)__Drowning__245.mp4",
    "a-ha__Take On Me__251.mp4",
    "sokodomo feat. Zion.T, 원슈타인__회전목마 (Merry-Go-Round)__250.mp4",
    "노라조 (Norazo)__슈퍼맨 (Superman)__198.mp4",
    "머쉬베놈, 미란이, 쿤디판다, 먼치맨 feat. JUSTHIS__VVS__346.mp4",
    "브라운아이즈 (Brown Eyes)__With Coffee__251.mp4",
    "이광조__즐거운 인생__222.mp4",
    "환희 (Hwanhee)__Tomorrow__331.mp4",
]
ok = failed = 0
for filename in FILES:
    stem = filename[:-4] if filename.endswith(".mp4") else filename
    artist, song_title, duration_str = stem.split("__")
    payload = {
        "videoUrl": f"{prefix}{filename}",
        "artist": artist,
        "songTitle": song_title,
        "durationSeconds": int(duration_str),
    }
    body = json.dumps(payload, ensure_ascii=False)
    if dry_run:
        print(f"DRY-RUN: {body}")
        ok += 1
        continue
    req = urllib.request.Request(
        api_url,
        data=body.encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"OK [{resp.status}]: {artist} - {song_title} ({duration_str}s)")
            ok += 1
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        print(f"FAIL [{e.code}]: {filename} -> {err_body}", file=sys.stderr)
        failed += 1
    except Exception as e:
        print(f"FAIL: {filename} -> {e}", file=sys.stderr)
        failed += 1
mode = "DRY-RUN" if dry_run else "DONE"
print(f"\n=== {mode}: {ok}/{len(FILES)} ok, {failed} failed ===")
if failed:
    sys.exit(1)
EOF
