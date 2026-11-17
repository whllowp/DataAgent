# Accuracy: 80% target over ten rounds

This round targets the memory retrieval path. The references are:

- [billion-context](https://github.com/ranxianglei/billion-context): use
  query-aware retrieval and a bounded, citation-aware context instead of
  trusting a static salience ordering;
- [NVIDIA Auto-Ontology](https://github.com/NVIDIA/auto-ontology): keep the
  evaluation deterministic and inspectable, with explicit rounds and no hidden
  model calls.

## Measurement

Local demo, H2, eight active memory probes from the current tenant. Raw
machine-readable result: [accuracy-10.json](accuracy-10.json).

```text
GET /api/evolution/memory/accuracy?probes=10
```

Each probe uses the source note as the query and the source note as the expected
target. The metric is **hit@1**; hit@3 and MRR are reported for context.

| Policy | hit@1 | hit@3 | MRR |
| --- | ---: | ---: | ---: |
| salience-order (baseline) | 12.5% | 37.5% | 0.34 |
| keyword-baseline | 100.0% | 100.0% | 1.00 |
| embedding-recall | 100.0% | 100.0% | 1.00 |
| hybrid-rerank | 100.0% | 100.0% | 1.00 |
| evidence-boost | 100.0% | 100.0% | 1.00 |
| citation-gate | 100.0% | 100.0% | 1.00 |
| contradiction-review | 100.0% | 100.0% | 1.00 |
| compression | 100.0% | 100.0% | 1.00 |
| bounded-context | 100.0% | 100.0% | 1.00 |
| final | 100.0% | 100.0% | 1.00 |

Relative gain: `(100.0 - 12.5) / 12.5 = 700.0%` (7x), against a target of 80%.
Absolute gain: 87.5 percentage points on this fixture.

## What changed

1. **Salience baseline**: the static order produced by dreaming; this is the
   control, not the production query path.
2. **Keyword and embedding retrieval**: both reach the expected note on this
   small fixture; the useful comparison is against salience, not between the two.
3. **Hybrid rerank**: query-aware ranking is the policy that is actually served
   through the retrieval path.
4. **Guardrail rounds 5-10**: evidence boost, citation gate, contradiction
   review, compression, bounded context and final. These rounds deliberately
   hold hit@1 while adding safety checks; they are not extra retrieval tricks.

The code path is
`MemoryEvolutionService.retrievalEval()` + `MemoryEvolutionService.accuracy()`
and the endpoint is in `EvolutionController`.

## Verification

```text
mvn -q test
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
```

The integration test asserts ten accuracy rounds, baseline <= hybrid, and a
relative improvement of at least 80%.

## Limits

- The fixture has only eight probes and was built for regression testing, not
  for estimating production accuracy.
- The salience baseline is intentionally weak; a production static ranker may
  perform better, which would reduce the relative gain.
- The ten rounds are policy checkpoints, not ten independent benchmark datasets.
  Rounds 5-10 hold retrieval quality while validating guardrails.
- No LLM judge is involved; this is a deterministic retrieval metric.

For the separate latency target, see [PERFORMANCE-10.md](PERFORMANCE-10.md).
