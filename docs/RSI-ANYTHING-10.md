# RSI-Anything：世界模型预测引导的十轮演进

参考：

- [RSI-Anything: Recursive Self-Improvement with Predictive World Models](https://arxiv.org/html/2610.13912v1)

## 1. 论文映射

RSI-Anything 用**预测式世界模型**引导动作与学习数据的选择：策略先提出候选，世界模型预测每个候选的结果/rollout，用带不确定性惩罚的排名分数 `R_d(τ̂) = Σ w_j r_{τ,j} − Δ_d(τ̂)` 只执行预算内最优的候选；再用真实反馈做策略/工作流修订，并**有条件地**适配世界模型（仅当 `reliability=unreliable` 且数据充分且预算足够时触发）。

DataAgent 的映射：

| RSI-Anything | DataAgent |
| --- | --- |
| 世界模型 φ（预测 rollout 结果） | `RsiWorldModel`：线性 LMS 学习 genome→(quality, cost, latency) |
| 预测引导选择（只执行 top 候选） | 每代提出 `population × OVERSAMPLE` 子代，预测后按预测分数取 top `population` 才真实评测 |
| 不确定性惩罚 Δ_d | （简化）预测分数直接采用归一化 Q/C/L 加权，未加显式不确定项 |
| reliability ∈ {reliable, unreliable, insufficient} | `RsiWorldModel.reliability(mae, minSamples, threshold)`：样本不足→insufficient，MAE≤0.05→reliable，否则→unreliable |
| 条件世界模型适配 | 每代若 `!reliable` 则 `worldModel.fit(samples(selected))` 重拟合 |
| 递归保留 + 组件/兼容性检查 | 精英保留（每代最佳若退化则保留上一代全局最佳），best 单调不回退 |

## 2. 世界模型（RsiWorldModel）

- 8 维归一化特征：`[bias=1.0, topK/20, includeVerified, includeMemory, planFirst, maxRows/500, parallelism/8, selfCheck]`
- 3 个读出：quality(0..1)、cost、latency（cost/latency 在 /1000 空间学习，predict 时反归一化）
- 学习：归一化 LMS（delta rule），`lr = 0.5 · error · feature`，默认 learningRate 0.5
- 可靠性：`samplesSeen < minSamples(=2·population)` → `insufficient`；否则 `qualityMae ≤ 0.05` → `reliable`，否则 `unreliable`
- 持久化：`worldModelJson` 存 `rsi_generation`，跨代恢复（`fromJson`）

## 3. 每代流程

1. 种子种群评测 → `worldModel.fit(samples(seed))` 初始构造 → gen0 记为 `insufficient`
2. g≥1：`proposeAndSelect()` 提出 `population×2` 变异子代 → 世界模型预测每个 → 按预测标量分排序 → 取 top `population` 持久化
3. `evaluateAndScore(selected)` 真实 replay 评测
4. `predictionMae` = mean|预测 quality − 观测 quality|
5. `reliability(mae, minSamples, 0.05)` → 若非 reliable 则 `fit(samples(selected))`（条件适配）
6. 精英保留 + best 跟踪 + `saveGeneration(...)`

## 4. 实测结果（10 代，population=4）

`POST /api/rsi/runs {"generations":10,"populationSize":4}`，2026-11-20 本机 H2 demo 数据：

- 最佳候选：gen4 `g4-g3-g2-g1-seed-3-fast-path-2-5-1-0`，quality **0.855**、cost **8.16**、latency 19.0ms、scalarScore **0.9925**（genome：topK=5、verified+memory+self-check、parallelism=4）
- bestScore 曲线：`0.739 → 0.796 → 0.828 → 0.828 → 0.9925 →（gen5..10 保持 0.9925）`——精英保留使 best 单调不回退

每代 predictionMae 与可靠性：

| 代 | predictionMae | reliability |
| --- | --- | --- |
| 0 | — | insufficient |
| 1 | 0.615 | unreliable |
| 2 | 0.327 | unreliable |
| 3 | 0.471 | unreliable |
| 4 | 0.164 | unreliable |
| 5 | 0.192 | unreliable |
| 6 | 0.109 | unreliable |
| 7 | 0.154 | unreliable |
| 8 | 0.197 | unreliable |
| 9 | 0.085 | unreliable |
| 10 | 0.252 | unreliable |

结论：

- 预测引导选择有效：最佳候选在 gen4 被世界模型预测选中（predictedScore 0.793）并真实评测后确认为全局最佳。
- 世界模型随样本增多整体变准：MAE 从 gen1 的 0.615 降到 gen9 的 0.085。
- 但 MAE 始终 > 0.05 阈值，reliability 全程 `unreliable`，因此每代都触发条件重拟合——说明 genome→quality 的线性代理对 benchmark 噪声敏感，0.05 阈值偏严（对 4 种群、每代 4 样本的小数据量尤其如此）。
- 后续可加不确定性惩罚项 Δ_d（论文的 `λ_d·U(u)`）以抑制高方差点，而非仅线性 MAE。

## 5. API / 数据模型

```http
GET  /api/rsi/overview
POST /api/rsi/runs
GET  /api/rsi/runs/{id}
GET  /api/rsi/runs/{id}/pareto
```

新增字段：

- `rsi_candidate.predicted_score`（预测标量分）
- `rsi_generation.world_model_json` / `prediction_mae` / `reliability`
- 迁移：`V16__rsi_world_model.sql`

## 6. 边界

- 世界模型是**线性 LMS 代理**，不是论文的深度 JEPA 世界模型；对 genome→outcome 的映射是局部线性的。
- 评测仍是 SRE benchmark 历史指标回放，不是在线 LLM rollout。
- 演化对象仍是 harness 配置，不是模型权重/代码。
- 未实现显式不确定性惩罚与预算约束；0.05 阈值在小数据量下几乎不会判 `reliable`（见上表，可调）。
