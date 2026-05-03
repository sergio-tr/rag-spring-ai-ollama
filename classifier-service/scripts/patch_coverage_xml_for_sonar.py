#!/usr/bin/env python3
"""Rewrite classifier-service/coverage.xml <source> paths for SonarCloud.

SonarScanner uses the repository root as project base (see sonar-project.properties).
Coverage.py emits mixed Cobertura filenames; we normalize them so ``<source>classifier-service</source>``
plus ``filename`` equals the repo path (``app/main.py``, ``app/evaluation/evaluator.py``, ``uvicorn_entry.py``).
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SOURCE_ROOT = "classifier-service"


def _rewrite_class_filenames(root_el: ET.Element) -> None:
    """Point every measured module under ``app/``; keep ``uvicorn_entry.py`` at classifier root."""

    for cls_el in root_el.iter("class"):
        fn = cls_el.get("filename")
        if not fn:
            continue
        if fn == "uvicorn_entry.py":
            continue
        if fn.startswith("app/"):
            continue
        if fn.endswith(".py"):
            cls_el.set("filename", f"app/{fn}")


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

    _rewrite_class_filenames(root_el)

    sources_el = root_el.find("sources")
    if sources_el is not None:
        sources_el.clear()
        ET.SubElement(sources_el, "source").text = SOURCE_ROOT

    with coverage_path.open("wb") as f:
        tree.write(f, encoding="utf-8", xml_declaration=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
