"""TechPath ML engine: train the career-path classifier and export it for the app.

This script reproduces the method in the SemProject_MLEngine notebook
(RoadmapSH1_Exploration.ipynb) and packages the result for on-device use:

1. Cleaning (same steps and order as the notebook)
   - drop rows with no title or description
   - drop exact duplicates, then duplicates of (career_path, title)
   - clean_text(): lowercase, markdown links -> label, remove URLs, @tags@,
     markdown symbols and other punctuation (keep + # . for c++, c#, .net)
   - drop descriptions with fewer than 10 words
   - keep career paths with at least 30 skills
   -> 10,204 skills across 86 career paths
2. Model: TF-IDF (English stop words, unigrams + bigrams, min_df=2,
   sublinear_tf) + multinomial Logistic Regression (C=30, max_iter=2000),
   validated with an 80/20 stratified split. `--grid` re-runs the notebook's
   5-fold grid search over C and n-gram range.
3. Retrieval: cosine similarity between the student's text and every skill on
   the predicted path (done on the phone).

Usage:
    pip install -r requirements.txt
    python train_model.py                       # finds ../../SemProject_MLEngine/roadmap_dataset.csv
    python train_model.py --csv path/to/roadmap_dataset.csv [--grid] [--fit-on train|all]

Outputs (app/src/main/assets/):
    model/model_meta.json   classes, hyper-parameters, intercepts, stop words, test metrics
    model/vocab.txt         TF-IDF vocabulary, one term per line, in feature-index order
    model/idf.bin           IDF weights, float32 little-endian [n_features]
    model/coef.bin          LR weights, float16 little-endian, [n_features][n_classes]
    model/skill_vectors.bin TF-IDF vector of every skill (nodes.json order), sparse:
                            uint32 count, then per skill: uint16 nnz, nnz x uint16 index, nnz x float16 value
    careers.json            one entry per career path (display name, description, ...)
    nodes.json              every skill: title, description, model text, resources, core flag
    opportunities.json      sample listings (unchanged, no vectors)
and core/src/test/resources/model_parity_fixture.json for the Kotlin parity test.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import ENGLISH_STOP_WORDS, TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, top_k_accuracy_score
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.pipeline import Pipeline

from path_descriptions import description, display_name

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RANDOM_STATE = 42
MIN_SKILLS = 30
BEST_C = 30
BEST_NGRAMS = (1, 2)

RES_COLS = ["courses", "articles", "official_docs", "videos", "opensource"]
RES_TYPE = {"courses": "course", "articles": "article", "official_docs": "docs",
            "videos": "video", "opensource": "opensource"}
RESOURCE_RE = re.compile(r"(.+?)\s*\((h?ttps?://\S+?)\)(?=\s*\|\s|\s*$)")
ROLE_PATHS = {"frontend", "backend", "full-stack", "devops", "devsecops", "data-analyst",
              "ai-engineer", "ai-data-scientist", "data-engineer", "android", "ios",
              "machine-learning", "mlops", "postgresql-dba", "blockchain", "qa",
              "software-architect", "cyber-security", "ux-design", "technical-writer",
              "game-developer", "server-side-game-developer", "product-manager",
              "engineering-manager", "devrel", "bi-analyst", "network-engineer",
              "forward-deployed-engineer", "ai-red-teaming", "ai-product-builder",
              "product-design"}
FUNDAMENTALS = re.compile(
    r"^(intro|introduction|basics|fundamentals|what is|what are|getting started|overview|"
    r"how .{1,30} works?|terminology|prerequisites|internet|learn the|learn a)\b|"
    r"\b(fundamentals|basics|introduction|core concepts)$", re.I)
MAX_CORE = 10

log: list[str] = []


def say(line: str = "") -> None:
    log.append(line)
    print(line)


# ----------------------------------------------------------------- notebook code

def clean_text(text: str) -> str:
    """Normalise one piece of text (identical to the notebook's clean_text)."""
    text = str(text).lower()                               # 1. lowercase
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)   # 2. markdown link [label](url) -> label
    text = re.sub(r"https?://\S+|www\.\S+", " ", text)      # 3. remove raw URLs
    text = re.sub(r"@\w+@", " ", text)                     # 4. remove roadmap.sh tags like @official@
    text = re.sub(r"[=#*_`>-]{2,}", " ", text)              # 5. remove markdown heading/rule symbols
    text = re.sub(r"[^a-z0-9+#.\s]", " ", text)             # 6. keep letters, digits and tech symbols
    text = re.sub(r"\s+", " ", text).strip()                # 7. collapse repeated spaces
    return text


def make_tfidf(ngrams=BEST_NGRAMS) -> TfidfVectorizer:
    return TfidfVectorizer(stop_words="english", ngram_range=ngrams, min_df=2, sublinear_tf=True)


def load_and_clean(csv: Path) -> pd.DataFrame:
    raw = pd.read_csv(csv)
    say(f"raw rows                         {len(raw):>6}  ({raw.career_path.nunique()} paths)")
    df = raw.dropna(subset=["title", "description"])
    say(f"drop missing title/description   {len(df):>6}")
    df = df.drop_duplicates()
    df = df.drop_duplicates(subset=["career_path", "title"])
    say(f"drop duplicate (path, title)     {len(df):>6}")
    df = df.copy()
    df["text"] = (df["title"] + ". " + df["description"]).apply(clean_text)
    df["desc_words"] = df["description"].apply(clean_text).str.split().str.len()
    df = df[df["desc_words"] >= 10]
    say(f"drop descriptions < 10 words     {len(df):>6}")
    counts = df["career_path"].value_counts()
    small = counts[counts < MIN_SKILLS]
    df = df[df["career_path"].isin(counts[counts >= MIN_SKILLS].index)].reset_index(drop=True)
    say(f"drop paths with < {MIN_SKILLS} skills      {len(df):>6}  ({df.career_path.nunique()} paths; removed {sorted(small.index)})")
    return df


# ------------------------------------------------------------------- app helpers

def display_clean(s) -> str:
    """Light clean-up of the description shown in the app (not the model text)."""
    if s is None or (isinstance(s, float) and np.isnan(s)):
        return ""
    s = unicodedata.normalize("NFC", str(s))
    s = re.sub(r"<[^>]+>", " ", s)
    s = re.sub(r"`([^`]*)`", r"\1", s)
    s = re.sub(r"\*\*|__|(?<!\w)\*(?!\s)", "", s)
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    s = re.sub(r"https?://\S+", "", s)
    s = re.sub(r"@\w+@", "", s)
    s = re.sub(r"[=#]{3,}", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def parse_resources(cell, kind: str) -> list[dict]:
    """'Title (https://url) | Title (https://url)' -> [{title, url, type}]."""
    if cell is None or (isinstance(cell, float) and np.isnan(cell)):
        return []
    out = []
    for m in RESOURCE_RE.finditer(str(cell).strip()):
        title = m.group(1).strip().lstrip("|").strip()
        url = m.group(2)
        if url.startswith("ttp"):
            url = "h" + url
        out.append({"title": title or url, "url": url, "type": kind})
    return out


def find_csv(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    for cand in [ROOT.parent / "SemProject_MLEngine" / "roadmap_dataset.csv", ROOT / "roadmap_dataset.csv"]:
        if cand.exists():
            return cand
    for base in [Path.cwd(), ROOT, ROOT.parent]:
        hits = list(base.glob("**/roadmap_dataset.csv"))
        if hits:
            return hits[0]
    raise SystemExit("roadmap_dataset.csv not found; pass --csv")


# -------------------------------------------------------------------------- main

def evaluate(df: pd.DataFrame, grid: bool) -> tuple[dict, float, tuple]:
    X_train, X_test, y_train, y_test = train_test_split(
        df["text"], df["career_path"], test_size=0.20, stratify=df["career_path"], random_state=RANDOM_STATE)
    say(f"\ntrain {len(X_train)} rows, test {len(X_test)} rows")
    C, ngrams, cv_f1 = BEST_C, BEST_NGRAMS, None
    if grid:
        pipe = Pipeline([("tfidf", make_tfidf()), ("clf", LogisticRegression(max_iter=2000))])
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)
        t = time.time()
        gs = GridSearchCV(pipe, {"clf__C": [1, 10, 30], "tfidf__ngram_range": [(1, 1), (1, 2)]},
                          cv=cv, scoring="f1_macro", n_jobs=2)
        gs.fit(X_train, y_train)
        C, ngrams, cv_f1 = gs.best_params_["clf__C"], gs.best_params_["tfidf__ngram_range"], gs.best_score_
        say(f"grid search ({time.time() - t:.0f}s): best C={C} ngrams={ngrams} CV macro-F1={cv_f1:.3f}")
    else:
        say(f"using the notebook's best settings: C={C}, ngrams={ngrams} (pass --grid to re-run the search)")

    vec = make_tfidf(ngrams)
    clf = LogisticRegression(C=C, max_iter=2000)
    clf.fit(vec.fit_transform(X_train), y_train)
    proba = clf.predict_proba(vec.transform(X_test))
    pred = clf.classes_[proba.argmax(1)]
    metrics = {
        "test_accuracy": round(float(accuracy_score(y_test, pred)), 4),
        "test_macro_f1": round(float(f1_score(y_test, pred, average="macro")), 4),
        "test_top3_accuracy": round(float(top_k_accuracy_score(y_test, proba, k=3, labels=clf.classes_)), 4),
        "train_rows": int(len(X_train)), "test_rows": int(len(X_test)),
    }
    if cv_f1 is not None:
        metrics["cv_macro_f1"] = round(float(cv_f1), 4)
    say(f"test accuracy {metrics['test_accuracy']:.3f} | macro-F1 {metrics['test_macro_f1']:.3f} | "
        f"top-3 {metrics['test_top3_accuracy']:.3f}")
    return metrics, C, ngrams, (vec, clf, X_train)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv")
    ap.add_argument("--out", default=str(ROOT / "app" / "src" / "main" / "assets"))
    ap.add_argument("--grid", action="store_true", help="re-run the 5-fold grid search (slow, ~5 min)")
    ap.add_argument("--fit-on", choices=["all", "train"], default="all",
                    help="export a model refitted on all rows (default) or the 80%% training split")
    args = ap.parse_args()

    csv = find_csv(args.csv)
    say(f"dataset: {csv}")
    df = load_and_clean(csv)
    metrics, C, ngrams, (vec, clf, X_train) = evaluate(df, args.grid)

    if args.fit_on == "all":
        vec = make_tfidf(ngrams)
        clf = LogisticRegression(C=C, max_iter=2000)
        clf.fit(vec.fit_transform(df["text"]), df["career_path"])
        say(f"refitted on all {len(df)} rows for the app")
    classes = [str(c) for c in clf.classes_]
    vocab = sorted(vec.vocabulary_.items(), key=lambda kv: kv[1])
    assert all(i == j for j, (_, i) in enumerate(vocab))
    terms = [t for t, _ in vocab]
    assert not any("\n" in t for t in terms)

    out = Path(args.out)
    model_dir = out / "model"
    model_dir.mkdir(parents=True, exist_ok=True)
    (model_dir / "vocab.txt").write_text("\n".join(terms) + "\n", encoding="utf-8")
    vec.idf_.astype("<f4").tofile(model_dir / "idf.bin")
    clf.coef_.T.astype("<f2").tofile(model_dir / "coef.bin")  # [n_features][n_classes]
    meta = {
        "version": date.today().isoformat(),
        "model": "TF-IDF + Logistic Regression",
        "classes": classes,
        "n_features": len(terms),
        "intercept": [float(x) for x in clf.intercept_],
        "tfidf": {"stop_words": "english", "ngram_range": list(ngrams), "min_df": 2,
                  "sublinear_tf": True, "norm": "l2", "token_pattern": r"(?u)\b\w\w+\b"},
        "logistic_regression": {"C": C, "max_iter": 2000, "multi_class": "multinomial (softmax)"},
        "stop_words": sorted(ENGLISH_STOP_WORDS),
        "fit_on": args.fit_on,
        "rows": int(len(df)),
        "metrics": metrics,
    }
    (model_dir / "model_meta.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")

    # ---- knowledge base for the app ------------------------------------------------
    X_all = vec.transform(df["text"])
    norm_title = df.title.str.lower().str.replace(r"[^a-z0-9]+", " ", regex=True).str.strip()
    title_paths = norm_title.map(df.groupby(norm_title).career_path.nunique())
    paths = sorted(df.career_path.unique())
    core_flags = np.zeros(len(df), dtype=bool)
    careers = []
    for p in paths:
        idx = np.where(df.career_path.values == p)[0]
        centroid = np.asarray(X_all[idx].mean(axis=0)).ravel()
        sims = X_all[idx] @ centroid
        score = [(1 if FUNDAMENTALS.search(df.title.iat[j]) else 0,
                  min(int(title_paths.iat[j]), 6), float(sims[k])) for k, j in enumerate(idx)]
        order = sorted(range(len(idx)), key=lambda k: score[k], reverse=True)
        core_flags[[idx[k] for k in order[:min(MAX_CORE, max(3, len(idx) // 8))]]] = True
        careers.append({"id": p, "name": display_name(p), "type": "role" if p in ROLE_PATHS else "skill",
                        "description": description(p), "node_count": int(len(idx))})

    slugify = lambda t: re.sub(r"[^a-z0-9]+", "-", str(t).lower()).strip("-")
    nodes, used = [], set()
    for j, r in df.iterrows():
        nid = r.node_id if isinstance(r.node_id, str) and r.node_id.strip() else slugify(r.title)
        base, k = nid, 2
        while (r.career_path, nid) in used:
            nid = f"{base}-{k}"; k += 1
        used.add((r.career_path, nid))
        res, seen = [], set()
        for c in RES_COLS:
            for x in parse_resources(r[c], RES_TYPE[c]):
                if x["url"] not in seen:
                    seen.add(x["url"]); res.append(x)
        slug = r.topic_slug if isinstance(r.topic_slug, str) else slugify(r.title)
        nodes.append({"id": nid, "path": r.career_path, "slug": slug, "title": re.sub(r"\s+", " ", str(r.title)).strip(),
                      "description": display_clean(r.description),
                      "core": bool(core_flags[j]), "resources": res})

    dump = lambda name, obj: (out / name).write_text(
        json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    write_skill_vectors(X_all, model_dir / "skill_vectors.bin")
    dump("careers.json", {"version": date.today().isoformat(), "careers": careers})
    dump("nodes.json", {"nodes": nodes})
    opp = json.loads((HERE / "data" / "opportunities_sample.json").read_text(encoding="utf-8"))
    dump("opportunities.json", {"sample": True, "notice": opp["_comment"], "listings": opp["listings"]})

    write_parity_fixture(vec, clf, df)

    n_res = sum(len(n["resources"]) for n in nodes)
    say("")
    say(f"paths={len(careers)} skills={len(nodes)} core={int(core_flags.sum())} vocab={len(terms)} "
        f"resources={n_res} skills_without_resources={sum(1 for n in nodes if not n['resources'])}")
    files = ["careers.json", "nodes.json", "opportunities.json", "model/vocab.txt", "model/idf.bin",
             "model/coef.bin", "model/skill_vectors.bin", "model/model_meta.json"]
    for name in files:
        say(f"asset {name:<24} {(out / name).stat().st_size / 1e6:8.2f} MB")
    (HERE / "pipeline_report.txt").write_text("\n".join(log) + "\n", encoding="utf-8")


def write_skill_vectors(X, path: Path) -> None:
    """The notebook's `skill_vectors = tfidf.transform(final["text"])`, stored sparse."""
    assert X.shape[1] < 65536, "feature index must fit in uint16"
    X = X.tocsr(); X.sort_indices()
    with open(path, "wb") as f:
        f.write(np.uint32(X.shape[0]).astype("<u4").tobytes())
        for i in range(X.shape[0]):
            a, b = X.indptr[i], X.indptr[i + 1]
            f.write(np.uint16(b - a).astype("<u2").tobytes())
            f.write(X.indices[a:b].astype("<u2").tobytes())
            f.write(X.data[a:b].astype("<f2").tobytes())


def write_parity_fixture(vec: TfidfVectorizer, clf: LogisticRegression, df: pd.DataFrame):
    """Expected outputs the Kotlin engine must reproduce (core unit test)."""
    queries = ["I want to build mobile apps for Android phones",
               "I like protecting systems from hackers and analysing attacks",
               "I enjoy working with data pipelines and large databases",
               "I want to train machine learning models",
               "I want to be a quantitative programmer",
               "Check [the docs](https://x.io) for C++, C# & .NET -- @official@ SQL!!",
               "Café ÉLAN: naïve résumé builder, React/Node.js, AWS_LAMBDA"]
    cases = []
    for q in queries:
        cleaned = clean_text(q)
        x = vec.transform([cleaned])
        proba = clf.predict_proba(x)[0]
        top = np.argsort(proba)[::-1][:3]
        top_path = clf.classes_[top[0]]
        in_path = np.where(df.career_path.values == top_path)[0]
        sims = (vec.transform(df.text.values[in_path]) @ x.T).toarray().ravel()
        best = in_path[np.argsort(-sims, kind="stable")[:5]]
        cases.append({
            "text": q, "cleaned": cleaned,
            "tfidf": {str(int(i)): float(v) for i, v in zip(x.indices, x.data)},
            "top3": [{"path": str(clf.classes_[i]), "p": float(proba[i])} for i in top],
            "top5_skills": [str(df.title.iat[i]) for i in best],
        })
    p = ROOT / "core" / "src" / "test" / "resources" / "model_parity_fixture.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"cases": cases}, indent=1), encoding="utf-8")
    for c in cases[:5]:
        say(f'"{c["text"]}" -> ' + ", ".join(f'{t["path"]} {t["p"]:.2f}' for t in c["top3"])
            + f' | {", ".join(c["top5_skills"])}')


if __name__ == "__main__":
    sys.exit(main())
