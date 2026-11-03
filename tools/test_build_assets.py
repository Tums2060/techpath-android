"""Tests for the link fallback in build_assets.py.  Run: python3 -m unittest discover tools"""
import unittest

from build_assets import path_name, resolve_links


def node(node_id, skill_id):
    return {"node_id": node_id, "skill_id": skill_id}


def res(node_id, skill_id, url):
    return {"node_id": node_id, "skill_id": skill_id, "resource_type": "articles", "title": url, "url": url}


class ResolveLinksTest(unittest.TestCase):
    def test_own_links_win(self):
        links = resolve_links([node("n1", "s1")], [res("n1", "s1", "a"), res("n2", "s1", "b")])
        self.assertEqual([l["url"] for l in links[0]], ["a"])

    def test_falls_back_to_same_skill_from_other_paths_deduplicated(self):
        resources = [res("n2", "s1", "a"), res("n3", "s1", "a"), res("n3", "s1", "b")]
        links = resolve_links([node("n1", "s1")], resources)
        self.assertEqual([l["url"] for l in links[0]], ["a", "b"])

    def test_no_links_anywhere_gives_empty_list(self):
        self.assertEqual(resolve_links([node("n1", "s9")], [res("n2", "s1", "a")]), [[]])


class PathNameTest(unittest.TestCase):
    def test_acronyms(self):
        self.assertEqual(path_name("ai-engineer"), "AI Engineer")
        self.assertEqual(path_name("ios"), "iOS")
        self.assertEqual(path_name("postgresql-dba"), "Postgresql DBA")


if __name__ == "__main__":
    unittest.main()
