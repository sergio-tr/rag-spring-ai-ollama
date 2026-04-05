#!/usr/bin/env python3
"""Rewrite classifier-service/coverage.xml <source> paths for SonarCloud.

SonarScanner uses the repository root as project base (see sonar-project.properties).
Coverage.py writes <source> as the absolute path to ./app (e.g. /home/runner/.../classifier-service/app)
or sometimes as ``app``. Sonar then cannot map Cobertura entries to indexed files under
``classifier-service/app``.

This script normalizes every <source> under <sources> to the repo-relative path
``classifier-service/app``. Class ``filename`` attributes are already relative to that
directory (e.g. ``config.py``, ``evaluation/evaluator.py``) and must not be rewritten.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TARGET_SOURCE = "classifier-service/app"


def normalize_source_text(text: str) -> str:
    t = (text or "").strip().replace("\\", "/")
    if not t:
        return TARGET_SOURCE
    if t == TARGET_SOURCE:
        return TARGET_SOURCE
    if t == "app":
        return TARGET_SOURCE
    marker = "classifier-service/app"
    if marker in t:
        return TARGET_SOURCE
    if "classifier-service" in t and t.rstrip("/").endswith("/app"):
        return TARGET_SOURCE
    return t


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    classifier_root = script_dir.parent
    coverage_path = classifier_root / "coverage.xml"
    if not coverage_path.is_file():
        print(
            f"patch_coverage_xml_for_sonar: skip, file not found: {coverage_path}",
            file=sys.stderr,
        )
        return 0

    tree = ET.parse(coverage_path)
    root_el = tree.getroot()
    sources_el = root_el.find("sources")
    if sources_el is not None:
        for source_el in sources_el.findall("source"):
            source_el.text = normalize_source_text(source_el.text or "")

    with coverage_path.open("wb") as f:
        tree.write(f, encoding="utf-8", xml_declaration=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
