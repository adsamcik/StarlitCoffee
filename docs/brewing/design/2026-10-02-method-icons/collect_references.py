"""Collect public equipment photographs for the icon review, never app assets.

Scan writes candidate metadata; fetch downloads only explicitly selected URLs.
Run from this directory with the bundled or any standard Python 3 interpreter.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import html
import json
from pathlib import Path
import re
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "manifest.json"
HEADERS = {"User-Agent": "Mozilla/5.0"}


def request(url):
    if urllib.parse.urlparse(url).scheme != "https":
        raise ValueError("Only selected public HTTPS references are supported")
    return urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30)


def scan(item):
    try:
        with request(item["source_url"]) as response:
            source = response.read(3_000_000).decode("utf-8", "replace")
            resolved = response.url
        tags = re.findall(r"<meta\b[^>]*>", source, re.I)
        images = []
        for tag in tags:
            attrs = dict(re.findall(r'''([\w:-]+)=["']([^"']*)["']''', tag))
            if attrs.get("property", attrs.get("name")) in ("og:image", "og:image:secure_url", "twitter:image"):
                image = html.unescape(attrs.get("content", ""))
                image = urllib.parse.urljoin(resolved, image)
                if image.startswith("http://"):
                    image = "https://" + image[7:]
                if image and image not in images:
                    images.append(image)
        candidates = []
        for tag in re.findall(r"<img\b[^>]*>", source, re.I):
            attrs = dict(re.findall(r'''([\w:-]+)=["']([^"']*)["']''', tag))
            image = attrs.get("src", attrs.get("data-src", ""))
            if image and not image.startswith("data:"):
                candidates.append({"url": urllib.parse.urljoin(resolved, html.unescape(image)), "alt": html.unescape(attrs.get("alt", ""))})
        return {"id": item["id"], "resolved_source_url": resolved, "meta_images": images, "image_candidates": candidates[:90]}
    except Exception as error:
        return {"id": item["id"], "error": str(error)}


def fetch(item):
    url = item.get("reference_image_url")
    if not url:
        return {"id": item["id"], "error": "No selected reference image"}
    try:
        with request(url) as response:
            mime = response.headers.get_content_type()
            content = response.read(15_000_001)
            resolved = response.url
        extension = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp", "image/gif": ".gif"}.get(mime)
        if not extension or len(content) > 15_000_000:
            raise ValueError(f"Unsupported or oversized reference: {mime}, {len(content)} bytes")
        path = ROOT / "references" / (item["id"] + extension)
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(content)
        return {"id": item["id"], "reference_file": str(path.relative_to(ROOT)).replace("\\", "/"), "reference_sha256": hashlib.sha256(content).hexdigest(), "reference_bytes": len(content), "resolved_image_url": resolved, "reference_mime": mime}
    except Exception as error:
        return {"id": item["id"], "error": str(error)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("scan", "fetch"))
    parser.add_argument("ids", nargs="*")
    args = parser.parse_args()
    document = json.loads(MANIFEST.read_text(encoding="utf-8"))
    items = [item for item in document["methods"] if not args.ids or item["id"] in args.ids]
    with ThreadPoolExecutor(max_workers=5) as executor:
        results = list(executor.map(scan if args.mode == "scan" else fetch, items))
    if args.mode == "scan":
        candidates_path = ROOT / "reference-candidates.json"
        prior = json.loads(candidates_path.read_text(encoding="utf-8")) if candidates_path.exists() else []
        by_id = {result["id"]: result for result in prior}
        by_id.update({result["id"]: result for result in results})
        candidates_path.write_text(json.dumps(list(by_id.values()), indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        for result in results:
            print(json.dumps({key: value for key, value in result.items() if key != "image_candidates"}, ensure_ascii=False))
    else:
        by_id = {item["id"]: item for item in document["methods"]}
        for result in results:
            if "error" not in result:
                by_id[result["id"]].update(result)
            print(json.dumps(result, ensure_ascii=False))
        MANIFEST.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    if any("error" in result for result in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
