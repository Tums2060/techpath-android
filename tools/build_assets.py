#!/usr/bin/env python3
"""Build the app's bundled dataset assets from a local roadmapsh-datasets checkout.

Usage:  python3 tools/build_assets.py /path/to/roadmapsh-datasets

Reads out/path_taxonomy.csv, out/nodes_clean.csv and out/resources_clean.csv and writes
app/src/main/assets/paths.json plus one app/src/main/assets/paths/<career_path>.json per path.
Standard library only.
"""
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

ACRONYMS = {
    "ai": "AI", "ux": "UX", "qa": "QA", "ios": "iOS", "devops": "DevOps",
    "devsecops": "DevSecOps", "mlops": "MLOps", "bi": "BI", "dba": "DBA", "devrel": "DevRel",
}

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"


def path_name(slug):
    """ai-engineer -> 'AI Engineer'. Beginner variants get their own label in the app."""
    return " ".join(ACRONYMS.get(w, w.capitalize()) for w in slug.split("-"))


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def resolve_links(nodes, resources):
    """Return each node's links, in the same order as `nodes`.

    Links are keyed by node_id (this join reproduces n_resources exactly; the career_path column
    in resources_clean.csv only names one of the paths a shared node appears in). A node with no
    links of its own falls back to links for the same skill_id from other paths. Duplicate URLs
    are dropped.
    """
    by_node, by_skill = defaultdict(list), defaultdict(list)
    for r in resources:
        link = {"type": r["resource_type"], "title": r["title"], "url": r["url"]}
        by_node[r["node_id"]].append(link)
        by_skill[r["skill_id"]].append(link)

    result = []
    for n in nodes:
        links = by_node.get(n["node_id"]) or by_skill.get(n["skill_id"], [])
        seen, unique = set(), []
        for link in links:
            if link["url"] not in seen:
                seen.add(link["url"])
                unique.append(link)
        result.append(unique)
    return result


def main(dataset_dir):
    out = Path(dataset_dir) / "out"
    taxonomy = read_csv(out / "path_taxonomy.csv")
    nodes = read_csv(out / "nodes_clean.csv")
    resources = read_csv(out / "resources_clean.csv")

    nodes_by_path = defaultdict(list)
    for n, links in zip(nodes, resolve_links(nodes, resources)):  # file order is preserved
        nodes_by_path[n["career_path"]].append({
            "id": n["node_id"],
            "skillId": n["skill_id"],
            "title": n["title"],
            "description": n["description"],
            "links": links,
        })

    paths = []
    for t in taxonomy:
        slug = t["career_path"]
        parent = t["parent_path"] if t["parent_path"] != slug else None
        paths.append({
            "id": slug,
            "name": path_name(parent or slug),
            "type": t["path_type"],
            "parent": parent,
            "nodeCount": len(nodes_by_path[slug]),
        })

    (ASSETS / "paths").mkdir(parents=True, exist_ok=True)
    for old in (ASSETS / "paths").glob("*.json"):
        old.unlink()
    with open(ASSETS / "paths.json", "w", encoding="utf-8") as f:
        json.dump(paths, f, ensure_ascii=False, indent=1)
    for slug, path_nodes in nodes_by_path.items():
        with open(ASSETS / "paths" / f"{slug}.json", "w", encoding="utf-8") as f:
            json.dump({"id": slug, "nodes": path_nodes}, f, ensure_ascii=False, separators=(",", ":"))

    no_links = sum(1 for ns in nodes_by_path.values() for n in ns if not n["links"])
    print(f"paths={len(paths)} nodes={len(nodes)} links={len(resources)} nodes_without_links={no_links}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
