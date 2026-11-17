# Performance: 40% target over ten rounds

This round targets the local DataAgent memory and ontology read paths. The
references are:

- [billion-context](https://github.com/ranxianglei/billion-context): avoid paying
  for the same context/retrieval work repeatedly; make compression and caching
  first-class;
- [NVIDIA Auto-Ontology](https://github.com/NVIDIA/auto-ontology): deterministic
  semantic graph reads, join paths and bounded traversal.

## Measurement

Local demo, H2, same four read endpoints:

```text
GET /api/evolution/memory/retrieval?probes=10
GET /api/evolution/memory/rounds-v2?count=10
GET /api/evolution/semantic/answerable-v2?q=CUSTOMERS
GET /api/evolution/semantic/rounds-v2?count=10
```

Each endpoint: 2 warmups + 8 measured requests. Numbers are median latency in
milliseconds on this machine.

| Endpoint | Before | After | Reduction |
| --- | ---: | ---: | ---: |
| memory retrieval | 23.3 | 19.3 | 17.2% |
| memory rounds-v2 | 27.5 | 18.4 | 33.1% |
| semantic answerable-v2 | 37.8 | 20.4 | 46.0% |
| semantic rounds-v2 | 61.1 | 17.3 | 71.7% |
| **aggregate median** | **149.7** | **75.4** | **49.6%** |

Aggregate is the sum of the four medians; it is the headline number for this
workload, not a promise for every endpoint.

## Ten rounds of the performance lab

`GET /api/evolution/perf?rounds=10` clears the embedding cache and the ontology
graph/result cache, takes one cold sample, then records ten consecutive rounds:

```json
{
  "rounds": 10,
  "operations": [
    {
      "operation": "memory.retrieval+compress",
      "coldMs": 7.6,
      "warmMedianMs": 0.1,
      "improvementPct": 98.5
    },
    {
      "operation": "ontology.answerable+paths",
      "coldMs": 33.1,
      "warmMedianMs": 1.6,
      "improvementPct": 95.2
    }
  ]
}
```

The warm-cache numbers are intentionally optimistic: they measure the steady
state where the same memory/ontology panel is opened repeatedly. The cold number
and the HTTP before/after table above are the conservative view.

## What changed

1. **Embedding LRU cache** (`EmbeddingService`): hashed vectors are deterministic
   for the same text, so repeated memory/retrieval/contradiction work reuses them.
2. **Ontology graph cache** (`OntologyEvolutionService.graph()`): builds the
   term adjacency map once per tenant/join-count window instead of per pair.
3. **Batch shortest paths** for answerability: one graph load serves every
   matched-term pair, replacing one repository query per pair.
4. **Result TTL caches** for read-only evolution panels: 1.5 seconds, cleared by
   citation repair and join inference, so writes still take effect promptly.
5. **Cache-key cleanup**: keys no longer perform a count query on every hit;
   tenant/query/round is enough for the short TTL.
6. **Composite join accumulation**: repeated same-pair predicates append `AND`
   and raise confidence instead of overwriting the first predicate.
7. **Ranked semantic paths**: average join confidence minus a per-hop penalty,
   replacing first-BFS-path behavior.
8. **Boundary-aware read path**: the graph cache is invalidated after observed
   join mining, so a newly inferred join is visible on the next read.
9. **Performance lab API** (`/api/evolution/perf`): explicit cold/warm sampling
   instead of a misleading single average.
10. **Regression checks**: `performanceLabRunsTenRounds` plus the existing
    memory/ontology/lineage tests.

## API

```http
GET /api/evolution/perf?rounds=10
GET /api/evolution/memory/retrieval?probes=10
GET /api/evolution/memory/rounds-v2?count=10
GET /api/evolution/semantic/answerable-v2?q=CUSTOMERS
GET /api/evolution/semantic/rounds-v2?count=10
GET /api/evolution/semantic/paths?from=&to=&limit=5
```

## Boundaries

- This is a local H2/demo benchmark, not a production load test.
- Cache TTL is 1.5s; first request after a write is cold.
- Warm-cache improvement is not the same as algorithmic complexity removal.
- `semantic.answerable` still depends on the existing OntologyService query;
  the gain is from serving matched-term pairs from one graph snapshot.
- Numbers should be re-baselined on the target database and JVM.
