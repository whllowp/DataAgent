# 安全、租户与 PII

## 1. 角色

| 角色 | 能力 |
| --- | --- |
| `READ` | 只读 API：元数据、历史、知识、语义、审计外的读取；不可执行 SQL |
| `EXECUTE` | READ + SQL 执行/计划/分析/对比、记忆/反馈等 Agent 写操作 |
| `ADMIN` | 全部能力：数据源管理、API Key、审计、语义同步、租户内管理 |

静态 `DATAAGENT_API_TOKEN` 等价于默认租户的 ADMIN，用于本地/兼容模式。

## 2. Scoped API Key

创建：

```http
POST /api/api-keys
{
  "name": "bi-reader",
  "role": "READ",
  "tenantId": "default",
  "datasourceIds": ["<datasource-uuid>"],
  "expiresAt": null
}
```

返回一次性明文：

```json
{
  "apiKey": { "id": "...", "keyPrefix": "a1b2c3d4e5f6", "...": "..." },
  "key": "da_a1b2c3d4e5f6_<secret>"
}
```

- 仅保存 `keyPrefix` 和 `SHA-256(secretKey + ':' + plaintext)`
- 明文只在创建响应出现一次
- `GET /api/auth/me` 查看当前 principal
- `GET /api/api-keys` / `DELETE /api/api-keys/{id}` 由 ADMIN 管理
- 撤销后立即失效

请求头：

```http
X-API-Key: da_xxx_yyy
```

## 3. 租户隔离

- `datasource.tenant_id`，V9 默认 `default`
- API Key 绑定 `tenant_id`
- `DatasourceService.list/getRequired` 同时校验租户和 ACL
- 非 ADMIN Key 只能访问 `api_key_datasource` 中显式授权的数据源
- 跨租户访问返回 403
- 后台任务/调度器使用 `system` principal（默认租户）

## 4. PII 分类与脱敏

`PiiService` 按列名/标签识别：

| 类型 | 匹配示例 | 脱敏 |
| --- | --- | --- |
| EMAIL | email、mail_addr | `a***@example.com` |
| PHONE | phone、mobile | `138****5678` |
| ID_CARD | id_card、passport、ssn | `110********1234` |
| BANK_CARD | bank_card、account_no、iban | `**** **** **** 1234` |
| PERSON_NAME | customer_name、contact_name | `张**` |
| ADDRESS | address、street、postal_code | 前 6 位 + `***` |

- 查询结果在进入响应/缓存前脱敏
- `QueryResult.piiMaskedColumns` 返回被脱敏列
- Studio 显示「PII 已脱敏」徽标
- `GET /api/pii/classify?column=customer_email` 查询分类
- 配置：

```yaml
dataagent:
  pii:
    enabled: true
    mask-enabled: true
```

注意：当前是列名规则 + 结果集脱敏，不是数据库列级动态掩码；字段分类应结合企业数据目录校准。

## 5. 安全边界

- SQL 只读网关仍是最外层硬约束
- 审计记录 actor、IP、UA、action、resource
- 生产必须替换 `DATAAGENT_API_TOKEN` 和 `DATAAGENT_SECRET_KEY`
- 当前租户隔离是应用层；数据库账号仍是数据源级共享账号
- 后续：字段级授权、行级安全、KMS/外部密钥、OIDC/SSO
