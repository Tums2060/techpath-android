"""TechPath offline pipeline: roadmap_dataset.csv -> app assets.

This is the one-off "training" step of the RAG system. Nothing is trained or
fine-tuned: we clean the curated roadmap knowledge base and precompute one
embedding per node and per career path, so the app can do cosine
nearest-neighbour retrieval on-device.

Usage:
    export GEMINI_API_KEY=...            # optional; without it the hash fallback is used
    python build_assets.py --csv ../path/to/roadmap_dataset.csv

Outputs (written to app/src/main/assets/ by default):
    careers.json        embedding metadata + one entry per career path
    nodes.json          every cleaned node with resources and its vector
    opportunities.json  the sample listings with requirement vectors
    pipeline_report.txt row counts per step and asset sizes
"""
from __future__ import annotations

import argparse
import base64
import json
import re
import shutil
import sys
import unicodedata
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd

from embedders import GeminiEmbedder, HashEmbedder, make_embedder
from path_descriptions import description, display_name

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RES_COLS = ["courses", "articles", "official_docs", "videos", "opensource"]
RES_TYPE = {"courses": "course", "articles": "article", "official_docs": "docs",
            "videos": "video", "opensource": "opensource"}
ALIAS_PATHS = {"r-programming": "r"}  # identical node sets; keep one copy
ROLE_PATHS = {"frontend", "backend", "full-stack", "devops", "devsecops", "data-analyst",
              "ai-engineer", "ai-data-scientist", "data-engineer", "android", "ios",
              "machine-learning", "mlops", "postgresql-dba", "blockchain", "qa",
              "software-architect", "cyber-security", "ux-design", "technical-writer",
              "game-developer", "server-side-game-developer", "product-manager",
              "engineering-manager", "devrel", "bi-analyst", "network-engineer",
              "forward-deployed-engineer", "ai-red-teaming", "ai-product-builder",
              "product-design", "backend-beginner", "frontend-beginner", "devops-beginner"}
FUNDAMENTALS = re.compile(
    r"^(intro|introduction|basics|fundamentals|what is|what are|getting started|overview|"
    r"how .{1,30} works?|terminology|prerequisites|internet|learn the|learn a)\b|"
    r"\b(fundamentals|basics|introduction|core concepts)$", re.I)
MAX_CORE = 10
# Skill-gap threshold. Gemini compares a known skill with the full node vector
# (title + description); the lexical fallback compares it with the node title,
# because description words would swamp a one- or two-word skill.
KNOWN_THRESHOLD = {"gemini": 0.80, "hash": 0.60}
KNOWN_COMPARE = {"gemini": "node", "hash": "title"}
RESOURCE_RE = re.compile(r"(.+?)\s*\((h?ttps?://\S+?)\)(?=\s*\|\s|\s*$)")

log: list[str] = []


def step(name, before, after, note=""):
    line = f"{name:<44} {before:>6} -> {after:>6}  {note}"
    log.append(line)
    print(line)


def clean_text(s) -> str:
    if s is None or (isinstance(s, float) and np.isnan(s)):
        return ""
    s = unicodedata.normalize("NFC", str(s))
    s = re.sub(r"<[^>]+>", " ", s)                  # HTML tags
    s = re.sub(r"`([^`]*)`", r"\1", s)              # inline code ticks
    s = re.sub(r"\*\*|__|(?<!\w)\*(?!\s)", "", s)   # bold / italic markers
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)  # markdown links -> text
    s = re.sub(r"(Articles|Visit the following resources.*)\s*=*\s*$", "", s)
    return re.sub(r"\s+", " ", s).strip()


def parse_resources(cell, kind: str) -> list[dict]:
    """'Title (https://url) | Title (https://url)' -> [{title, url, type}].

    Titles may themselves contain ' | ' (e.g. 'Guide | Microsoft Learn'), so we
    match 'title (url)' units rather than splitting on the pipe."""
    if cell is None or (isinstance(cell, float) and np.isnan(cell)):
        return []
    out = []
    for m in RESOURCE_RE.finditer(str(cell).strip()):
        title = m.group(1).strip().lstrip("|").strip()
        url = m.group(2)
        if url.startswith("ttp"):
            url = "h" + url  # repair truncated scheme seen in the scrape
        out.append({"title": title or url, "url": url, "type": kind})
    return out


def find_csv(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    for base in [Path.cwd(), ROOT, ROOT.parent]:
        hits = list(base.glob("**/roadmap_dataset.csv"))
        if hits:
            return hits[0]
    raise SystemExit("roadmap_dataset.csv not found; pass --csv")


def f16_b64(v: np.ndarray) -> str:
    return base64.b64encode(np.asarray(v, dtype="<f2").tobytes()).decode("ascii")


def unit(v: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(v)
    return v / n if n > 0 else v


def load_and_clean(csv: Path) -> pd.DataFrame:
    df = pd.read_csv(csv, dtype=str, keep_default_na=True)
    n0 = len(df); step("0 load raw", n0, n0, f"{df.career_path.nunique()} paths")
    for c in df.columns:
        df[c] = df[c].str.strip()
    n = len(df); df = df.drop_duplicates(); step("1 drop exact duplicate rows", n, len(df))
    n = len(df); df = df.dropna(subset=["node_id"]); step("2 drop missing node_id", n, len(df))
    n = len(df); df = df[df.topic_slug.fillna("") != "index"]; step("3 drop roadmap index pages", n, len(df))
    n = len(df); df = df.drop_duplicates(subset=["career_path", "node_id"], keep="first")
    step("4 drop duplicate (path,node_id)", n, len(df))
    n = len(df); df = df[~df.career_path.isin(ALIAS_PATHS)]
    step("5 drop alias paths", n, len(df), str(ALIAS_PATHS))
    df = df.copy()
    df["title"] = df["title"].map(clean_text)
    df["description"] = df["description"].map(clean_text)
    n = len(df); df = df[(df.title != "") & (df.description != "")]
    step("6 drop empty title/description", n, len(df))
    df["topic_slug"] = df["topic_slug"].fillna(
        df["title"].str.lower().str.replace(r"[^a-z0-9]+", "-", regex=True).str.strip("-"))
    step("7 parse resources", len(df), len(df))
    for c in RES_COLS:
        df[c] = df[c].map(lambda cell, k=RES_TYPE[c]: parse_resources(cell, k))
    return df.reset_index(drop=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv")
    ap.add_argument("--out", default=str(ROOT / "app" / "src" / "main" / "assets"))
    ap.add_argument("--embedder", choices=["auto", "gemini", "hash"], default="auto")
    args = ap.parse_args()

    csv = find_csv(args.csv)
    print(f"dataset: {csv}")
    df = load_and_clean(csv)
    lexicon = HERE / "data" / "hash_lexicon.json"
    emb = make_embedder(args.embedder, lexicon)
    print(f"embedder: {emb.kind} / {emb.model}")

    node_texts = (df.title + ": " + df.description).tolist()
    if isinstance(emb, HashEmbedder):
        emb.fit_idf(node_texts)
    vecs = emb.embed(node_texts)

    paths = sorted(df.career_path.unique())
    descs = [description(p) for p in paths]
    desc_vecs = emb.embed([f"{display_name(p)}: {d}" for p, d in zip(paths, descs)])

    # How many paths share each (normalised) title: topics such as Git, HTTP or
    # Databases that recur across many roadmaps are treated as fundamentals.
    norm_title = df.title.str.lower().str.replace(r"[^a-z0-9]+", " ", regex=True).str.strip()
    title_paths = norm_title.map(df.groupby(norm_title).career_path.nunique())

    careers, core_flags = [], np.zeros(len(df), dtype=bool)
    for i, p in enumerate(paths):
        idx = np.where(df.career_path.values == p)[0]
        centroid = unit(vecs[idx].mean(axis=0))
        # Core fundamentals = intro/basics-style titles first, then topics that
        # recur across roadmaps, then centrality (cosine to the path centroid).
        sims = vecs[idx] @ centroid
        score = [(1 if FUNDAMENTALS.search(df.title.iat[j]) else 0,
                  min(int(title_paths.iat[j]), 6), float(sims[k])) for k, j in enumerate(idx)]
        order = sorted(range(len(idx)), key=lambda k: score[k], reverse=True)
        core = [idx[k] for k in order[:min(MAX_CORE, max(3, len(idx) // 8))]]
        core_flags[core] = True
        careers.append({
            "id": p, "name": display_name(p),
            "type": "role" if p in ROLE_PATHS else "skill",
            "description": descs[i], "node_count": int(len(idx)),
            "vector": f16_b64(unit(centroid + desc_vecs[i])),
        })

    nodes = []
    for j, r in df.iterrows():
        res = [x for c in RES_COLS for x in r[c]]
        seen, uniq = set(), []
        for x in res:
            if x["url"] not in seen:
                seen.add(x["url"]); uniq.append(x)
        nodes.append({"id": r.node_id, "path": r.career_path, "slug": r.topic_slug,
                      "title": r.title, "description": r.description,
                      "core": bool(core_flags[j]), "resources": uniq,
                      "vector": f16_b64(vecs[j])})

    opp_src = json.loads((HERE / "data" / "opportunities_sample.json").read_text(encoding="utf-8"))
    opp_texts = [f"{o['title']}. Requirements: {', '.join(o['requirements'])}" for o in opp_src["listings"]]
    opp_vecs = emb.embed(opp_texts)
    for o, v in zip(opp_src["listings"], opp_vecs):
        o["vector"] = f16_b64(v)

    meta = emb.meta()
    meta["known_threshold"] = KNOWN_THRESHOLD[emb.kind]
    meta["known_compare"] = KNOWN_COMPARE[emb.kind]
    meta["encoding"] = "float16-le-base64"
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    dump = lambda name, obj: (out / name).write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    dump("careers.json", {"version": date.today().isoformat(), "embedding": meta, "careers": careers})
    dump("nodes.json", {"nodes": nodes})
    dump("opportunities.json", {"sample": True, "notice": opp_src["_comment"], "listings": opp_src["listings"]})
    shutil.copy(lexicon, out / "hash_lexicon.json")

    write_parity_fixture(lexicon, emb if isinstance(emb, HashEmbedder) else None)

    n_res = sum(len(n["resources"]) for n in nodes)
    zero_res = sum(1 for n in nodes if not n["resources"])
    log.append("")
    log.append(f"paths={len(careers)} nodes={len(nodes)} core={int(core_flags.sum())} resources={n_res} nodes_without_resources={zero_res}")
    log.append(f"embedding={meta['kind']}:{meta['model']} dims={meta['dims']}")
    for name in ["careers.json", "nodes.json", "opportunities.json", "hash_lexicon.json"]:
        log.append(f"asset {name:<20} {(out / name).stat().st_size / 1e6:8.2f} MB")
    total = sum((out / n).stat().st_size for n in ["careers.json", "nodes.json", "opportunities.json", "hash_lexicon.json"])
    log.append(f"asset total                {total / 1e6:8.2f} MB")
    (HERE / "pipeline_report.txt").write_text("\n".join(log) + "\n", encoding="utf-8")
    print("\n".join(log[-8:]))


def write_parity_fixture(lexicon: Path, fitted: HashEmbedder | None):
    """Vectors the Kotlin HashEmbedder must reproduce (core unit test)."""
    e = HashEmbedder(lexicon, dims=64)
    samples = ["I want to build apps for banks", "Kotlin: a modern language for Android",
               "C++ and C# are languages", "APIs, databases & authentication!",
               "Studies in cyber-security and hacking", ""]
    e.fit_idf(samples[:4])
    fixture = {"dims": 64, "idf": [float(x) for x in e.idf],
               "cases": [{"text": t, "vector": [float(x) for x in e.embed_one(t)]} for t in samples]}
    p = ROOT / "core" / "src" / "test" / "resources" / "hash_parity_fixture.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(fixture, indent=1), encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
