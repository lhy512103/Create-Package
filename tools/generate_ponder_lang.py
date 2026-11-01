#!/usr/bin/env python3
"""Update Ponder's normal-mode translations from the bundled bilingual scenes.

Run from any directory: python tools/generate_ponder_lang.py
Only scene-owned keys are replaced; block/item/UI/tag translations are preserved.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/createpackage"


def scene_translations(language):
    translations = {"gui.createpackage.ponder.key_unavailable":
                    "未安装或未绑定" if language == "zh_cn" else "Not installed or unbound"}
    prefixes = []
    for path in sorted((ASSETS / "ponderer").glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        namespace, scene_path = document["id"].split(":", 1)
        prefix = f"{namespace}.ponder.{scene_path}"
        prefixes.append(prefix)
        if not document.get("items"):
            continue
        for segment in document.get("scenes", []):
            if not segment.get("steps"):
                continue
            segment_id = segment.get("id", "")
            scene_prefix = prefix + ("_" + segment_id if segment_id.strip() else "")
            title = segment.get("title") or document.get("title", {})
            translations[scene_prefix + ".header"] = title.get(language) or title.get("en_us") or title.get("zh_cn", "")
            index = 1
            for step in segment["steps"]:
                if step.get("type", "").lower() != "text":
                    continue
                text = step.get("text", {})
                translations[f"{scene_prefix}.text_{index}"] = text.get(language) or text.get("en_us") or text.get("zh_cn", "")
                index += 1
    return translations, prefixes


def main():
    for language in ("en_us", "zh_cn"):
        path = ASSETS / "lang" / (language + ".json")
        existing = json.loads(path.read_text(encoding="utf-8"))
        generated, prefixes = scene_translations(language)
        # Remove old unsuffixed scene keys as well as keys for renamed/deleted segments.
        preserved = {
            key: value for key, value in existing.items()
            if not any(key.startswith(prefix + ".") or key.startswith(prefix + "_") for prefix in prefixes)
        }
        preserved.update(generated)
        path.write_text(json.dumps(preserved, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{language}: {len(generated)} Ponder title/text translations")


if __name__ == "__main__":
    main()
