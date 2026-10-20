"""Embedding back-ends used by the offline pipeline.

Two embedders are supported, and the app uses whichever one built its assets
(recorded in careers.json -> embedding.kind):

* GeminiEmbedder  - Google Gemini embeddings API (default model
  `gemini-embedding-2`, 768 dims). Semantic, needs GEMINI_API_KEY and network.
* HashEmbedder    - "hash-tfidf": a deterministic, dependency-free lexical
  embedding (feature hashing of words, bigrams and lexicon expansions, weighted
  by per-bucket IDF). It exists so the APK can be built and used without any API
  key. The exact same algorithm is implemented in Kotlin
  (core/src/main/kotlin/.../HashEmbedder.kt); a parity fixture keeps them equal.
"""
from __future__ import annotations

import json
import math
import os
import time
from pathlib import Path

import numpy as np
import requests

# --------------------------------------------------------------------------- hash

FNV_OFFSET = 0x811C9DC5
FNV_PRIME = 0x01000193


def fnv1a32(s: str) -> int:
    h = FNV_OFFSET
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFF
    return h


def _normalise_chars(text: str) -> str:
    out = []
    for ch in text:
        if "A" <= ch <= "Z":
            ch = chr(ord(ch) + 32)
        out.append(ch if ("a" <= ch <= "z" or "0" <= ch <= "9" or ch in "+#") else " ")
    return "".join(out)


def stem(t: str) -> str:
    if len(t) > 4 and t.endswith("ies"):
        return t[:-3] + "y"
    if (len(t) > 3 and t.endswith("s") and not t.endswith(("ss", "us"))
            and not (t.endswith("is") and len(t) > 4)):  # keep analysis, redis; apis -> api
        return t[:-1]
    return t


class HashEmbedder:
    kind = "hash"
    model = "hash-tfidf-v1"

    def __init__(self, lexicon_path: Path, dims: int = 512):
        lex = json.loads(Path(lexicon_path).read_text(encoding="utf-8"))
        self.stopwords = set(lex["stopwords"])
        self.expansions = lex["expansions"]
        self.dims = dims
        self.idf = np.ones(dims, dtype=np.float64)

    def features(self, text: str) -> dict[str, float]:
        feats: dict[str, float] = {}
        prev = None
        for raw in _normalise_chars(text).split():
            if raw.strip("+#") == "" or raw in self.stopwords:
                prev = None
                continue
            tok = stem(raw)
            feats["w:" + tok] = feats.get("w:" + tok, 0.0) + 1.0
            if prev is not None:
                bg = "b:" + prev + "_" + tok
                feats[bg] = feats.get(bg, 0.0) + 1.0
            prev = tok
            exp = self.expansions.get(raw) or self.expansions.get(tok)
            if exp:
                for e in exp:
                    k = "w:" + stem(e)
                    feats[k] = feats.get(k, 0.0) + 0.5
        return feats

    def buckets(self, feats: dict[str, float]):
        for f, w in feats.items():
            h = fnv1a32(f)
            yield h % self.dims, (1.0 if (h >> 31) == 0 else -1.0), w

    def fit_idf(self, texts: list[str]) -> None:
        df = np.zeros(self.dims, dtype=np.float64)
        for t in texts:
            seen = {b for b, _, _ in self.buckets(self.features(t))}
            for b in seen:
                df[b] += 1
        n = len(texts)
        self.idf = np.log((n + 1.0) / (df + 1.0)) + 1.0

    def embed_one(self, text: str) -> np.ndarray:
        v = np.zeros(self.dims, dtype=np.float64)
        for b, sign, w in self.buckets(self.features(text)):
            v[b] += sign * math.log1p(w) * self.idf[b]
        n = np.linalg.norm(v)
        return v / n if n > 0 else v

    def embed(self, texts: list[str]) -> np.ndarray:
        return np.vstack([self.embed_one(t) for t in texts]) if texts else np.zeros((0, self.dims))

    def meta(self) -> dict:
        return {"kind": self.kind, "model": self.model, "dims": self.dims,
                "idf": [round(float(x), 6) for x in self.idf]}


# ------------------------------------------------------------------------- gemini

GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"


class GeminiEmbedder:
    kind = "gemini"

    def __init__(self, api_key: str, model: str = "gemini-embedding-2", dims: int = 768,
                 batch_size: int = 100, pause_s: float = 0.7):
        self.api_key = api_key
        self.model = model
        self.dims = dims
        self.batch_size = batch_size
        self.pause_s = pause_s

    def _batch(self, texts: list[str]) -> list[list[float]]:
        url = f"{GEMINI_BASE}/models/{self.model}:batchEmbedContents"
        body = {"requests": [{"model": f"models/{self.model}",
                              "content": {"parts": [{"text": t[:6000]}]},
                              "outputDimensionality": self.dims} for t in texts]}
        for attempt in range(6):
            r = requests.post(url, json=body, timeout=120,
                              headers={"x-goog-api-key": self.api_key})
            if r.status_code == 200:
                return [e["values"] for e in r.json()["embeddings"]]
            if r.status_code in (429, 500, 503):
                wait = 2 ** attempt * 5
                print(f"  Gemini {r.status_code}, retrying in {wait}s")
                time.sleep(wait)
                continue
            raise RuntimeError(f"Gemini embeddings failed: {r.status_code} {r.text[:500]}")
        raise RuntimeError("Gemini embeddings: too many retries")

    def embed(self, texts: list[str]) -> np.ndarray:
        out: list[list[float]] = []
        for i in range(0, len(texts), self.batch_size):
            out.extend(self._batch(texts[i:i + self.batch_size]))
            if (i // self.batch_size) % 10 == 0:
                print(f"  embedded {min(i + self.batch_size, len(texts))}/{len(texts)}")
            time.sleep(self.pause_s)
        m = np.asarray(out, dtype=np.float64)
        n = np.linalg.norm(m, axis=1, keepdims=True)
        return m / np.where(n == 0, 1, n)

    def meta(self) -> dict:
        return {"kind": self.kind, "model": self.model, "dims": self.dims}


def make_embedder(kind: str, lexicon_path: Path):
    if kind == "auto":
        kind = "gemini" if os.environ.get("GEMINI_API_KEY") else "hash"
    if kind == "gemini":
        key = os.environ.get("GEMINI_API_KEY")
        if not key:
            raise SystemExit("GEMINI_API_KEY is not set (use --embedder hash for the offline fallback)")
        return GeminiEmbedder(key, model=os.environ.get("GEMINI_EMBED_MODEL", "gemini-embedding-2"),
                              dims=int(os.environ.get("GEMINI_EMBED_DIMS", "768")))
    return HashEmbedder(lexicon_path)
