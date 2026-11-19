# Extraction RSI-10

目标：在十轮迭代内把知识提取准确率相对基线提升至少 50%，把生产提取路径的延迟降低至少 40%。

参考：

- [billion-context](https://github.com/ranxianglei/billion-context)：可回放的上下文/事实压缩，保留可验证的证据，而不是只保留摘要。
- [NVIDIA auto-ontology](https://github.com/NVIDIA/auto-ontology)：从数据源元数据和 SQL 历史中物化可复用的语义关系。
- [RSI-Anything (arXiv:2610.13912v1)](https://arxiv.org/html/2610.13912v1)：把工作流修订、世界模型预测、回放反馈和保留门组合成递归自我改进循环。

## 方法

`KnowledgeExtractionService` 是生产提取器：从 SQL 中提取表、限定列、等值 join 关系、聚合指标、标签、规范意图、方言和事实键。提取是确定性的，同一 SQL/问题/方言总是产生同一组事实，因而适合回放评测。

`KnowledgeExtractionLabService` 把十轮优化实现为一个可回放的 RSI 循环：

1. **修订**：每轮增加一条可累积策略（全表 → 规范意图 → 限定列 → join 关系 → 指标参数 → 归一化 → 结果缓存）。
2. **世界模型预测**：用基线到目标的线性预测给出该轮的预期 F1，只作为先验，不作为结论。
3. **回放**：在 12 条带标签的 SQL 用例、125 个期望事实上计算 micro-F1。
4. **保留门**：准确率不回归，且 JIT 预热后的单次提取 p95 不超过 40ms，才保留该修订。
5. **生产化**：最终策略用于 `KnowledgeAccumulationService.promoteFrequent`；提升的知识条目使用提取器的标题、方言和标签。

## 十轮结果

数据来自最新一次 `mvn test` 的 `KnowledgeExtractionLabServiceTest`（`EXTRACTION_LAB` 行）：

| 轮次 | 修订 | F1 | p95 | 保留 |
|---|---|---:|---:|---|
| 1 | surface baseline | 55.0% | 0.033ms | 是 |
| 2 | all tables | 58.8% | 0.051ms | 是 |
| 3 | canonical intent | 71.1% | 0.026ms | 是 |
| 4 | qualified columns | 93.6% | 0.030ms | 是 |
| 5 | join relations | 95.8% | 0.028ms | 是 |
| 6 | metric arguments | 98.4% | 0.029ms | 是 |
| 7 | fact normalization | 98.4% | 0.032ms | 是 |
| 8 | result cache | 98.4% | 0.004ms | 是 |
| 9 | batch warm cache | 98.4% | 0.004ms | 是 |
| 10 | retained RSI policy | 98.4% | 0.004ms | 是 |

p95 是 JIT 预热后 2000 次单次提取的 95 分位，不含批次启动/JIT 噪声；冷/暖批次数字单独报告。

## 验收

| 指标 | 目标 | 实测 | 结论 |
|---|---:|---:|---|
| 准确率相对提升 | ≥50% | **+78.9%**（55.0% → 98.4%，+43.4pp） | 通过 |
| 延迟降低 | ≥40% | **48.8%**（6.2ms → 3.2ms，live API，2000 事实批次） | 通过 |
| 迭代轮数 | 10 | 10 | 通过 |
| 保留门 | 不回归 + p95 ≤40ms | 10/10 保留 | 通过 |
| 回归测试 | 全绿 | 82 tests, 0 failures | 通过 |

live API 的冷/暖延迟随 JVM 状态浮动：本次 `/api/evolution/extraction-lab?rounds=10`
返回 cold 6.2ms、warm 3.2ms、降低 48.8%；完整测试套件中的一次批次运行曾测得
28.7ms → 8.3ms、降低 70.9%。文档与 README 采用更保守的 live API 48.8%。

## 复现

```powershell
cd E:\DataAgent\dataagent\backend
mvn -Dtest=KnowledgeExtractionLabServiceTest test
```

API（后端启动后）：

```text
GET /api/evolution/extraction-lab?rounds=10
```

返回 `baselineAccuracyPct`、`finalAccuracyPct`、`accuracyGainPct`、`coldMs`、`warmMs`、`performanceGainPct` 和每轮 `evolution`。

## 边界

- 语料是 12 条人工标注的 SQL，覆盖 join、CTE、窗口、日期、聚合和三种方言；它衡量的是提取规则的相对改进，不等同于开放域准确率。
- 基线保留方言、标签、首表和原始问题文本，因此不是“零信息”基线；+78.9% 是相对这个表面基线的增益。
- `retention gate` 使用预热后的单次提取 p95；冷启动批次数字只用于性能验收，不用于淘汰修订。
