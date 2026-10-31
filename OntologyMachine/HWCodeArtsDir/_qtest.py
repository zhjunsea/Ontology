# -*- coding: utf-8 -*-
import urllib.request, urllib.parse, json, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
EP = "http://localhost:8080/sparql"
P = ("PREFIX reading: <http://example.org/sizhubazi/reading/> "
     "PREFIX yongshen: <http://example.org/sizhubazi/yongshen/> "
     "PREFIX pattern: <http://example.org/sizhubazi/pattern/> "
     "PREFIX core: <http://example.org/sizhubazi/core/> "
     "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ")

def q(body):
    url = EP + "?" + urllib.parse.urlencode({"query": P + body})
    req = urllib.request.Request(url, headers={"Accept": "application/sparql-results+json"})
    d = json.load(urllib.request.urlopen(req))
    return d["results"]["bindings"]

tests = {
 "CategoryFortune": "SELECT ?label ?cat ?text ?src WHERE { ?c a reading:CategoryFortune ; rdfs:label ?label ; reading:readingCategory ?cat ; reading:readingText ?text ; reading:readingSource ?src . }",
 "TimePlaceFortune": "SELECT ?label ?text ?src WHERE { ?c a reading:TimePlaceFortune ; rdfs:label ?label ; reading:readingText ?text ; reading:readingSource ?src . }",
 "ProgenyPrediction": "SELECT ?label ?text ?src WHERE { ?c a reading:ProgenyPrediction ; rdfs:label ?label ; reading:readingText ?text ; reading:readingSource ?src . }",
 "DynamicStaticTheory": "SELECT ?label ?text ?src WHERE { ?c a reading:DynamicStaticTheory ; rdfs:label ?label ; reading:readingText ?text ; reading:readingSource ?src . }",
 "HeadCoveringTheory": "SELECT ?label ?text ?src WHERE { ?c a reading:HeadCoveringTheory ; rdfs:label ?label ; reading:readingText ?text ; reading:readingSource ?src . }",
 "BingYao": "SELECT ?label ?text WHERE { ?c a yongshen:BingYao ; rdfs:label ?label ; rdfs:comment ?text . }",
 "AssimilationPattern": "SELECT ?label ?name ?cls ?text ?src WHERE { ?c a pattern:AssimilationPattern ; rdfs:label ?label ; pattern:patternName ?name ; pattern:patternClass ?cls ; pattern:patternMeaning ?text ; pattern:patternSource ?src . }",
 "Pattern(catalog)": "SELECT ?label ?name ?cls ?text ?src WHERE { ?c a pattern:Pattern ; rdfs:label ?label ; pattern:patternName ?name ; pattern:patternClass ?cls ; pattern:patternMeaning ?text ; pattern:patternSource ?src . }",
}
for name, body in tests.items():
    try:
        b = q(body)
        print("%-22s rows=%d" % (name, len(b)))
        if b:
            x = b[0]
            print("      e.g.", {k: (v["value"][:36]) for k, v in x.items()})
    except Exception as e:
        print("%-22s ERROR %s" % (name, e))
