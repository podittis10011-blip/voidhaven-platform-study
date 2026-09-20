# VH-02 手动复现手册（含本轮实测结果）

## 0. 边界

- 目标环境：`http://localhost:18082`（Compose 项目 `voidhaven-lab`，行为等同当前工作树）。
- 只使用合成数据：`LEARN-20260918-VH02-01`、`LEARN-20260918-VH02-REC`、`LEARN-20260918-VH02-RJ`。
- 使用默认本地合成账号 `local-admin` / `change-me-before-first-local-run`，仅限隔离环境。
- 数据库只做 `SELECT`，不执行 `UPDATE`/`DELETE`，不执行 `docker compose down -v`。
- 不修改正式仓库文件。

## 1. 复现前必须先搞清楚的三件事

### 1.1 两个端口的归属

| 请求 | 18082 | 18080 |
|---|---|---|
| `GET /api/v1/public/resources?sort=popularity` | 400 `CATALOG_SORT_INVALID` | 200（参数被忽略） |
| `GET /api/v1/public/resources?filterKey=resourceType` | 400 `CONTENT_FILTER_INCOMPLETE` | 200（参数被忽略） |
| 后台列表返回的 ID | `26CYD6…` / `586HX9…` | `9E4TVH…` / `W4HRWG…` |
| 容器项目名（只读 SQL 确认） | `voidhaven-lab` | `voidhaven-repro-current` |

结论：18082 才是与当前工作树行为一致的那个。`09-evidence-ledger.md` 里把 `voidhaven-repro-current` 记作 `RUNTIME_CURRENT_SOURCE`，与本次实测相反，见 `README.md` §1。

### 1.2 错误响应体的结构

成功响应：

```json
{ "data": { "...": "..." }, "requestId": "..." }
```

失败响应（平铺，不在 `error` 下面）：

```json
{ "code": "CONTENT_VERSION_CONFLICT", "message": "送审内容已发生变化，请刷新后重新审核。",
  "requestId": "Q627PS2XBHD0Y203QJ7E6B5Z1F", "fieldErrors": {}, "retryable": false }
```

`requestId` 同时出现在响应头 `X-Request-Id`。`retryable` 由 `ApiExceptionHandler.java:75` 计算：429 与所有 5xx 为 `true`。

### 1.3 用能发原始请求头的客户端

本机 PowerShell 7.6.5 用 `Invoke-WebRequest -Headers @{'If-Match'='1'}` 会报 `The format of value '1' is invalid.`，因为它按 RFC 严格校验实体标签。本轮改用底层 `System.Net.Http.HttpClient` + `TryAddWithoutValidation`，也可以直接用 `curl.exe`。

## 2. 请求矩阵（37 条编号用例）

除 R39 在 18080 上做跨构建对照外，其余全部在 18082 实测。

### 2.1 成功主链

| # | 请求 | 结果 | 关键观察 |
|---|---|---|---|
| R01 | `POST /api/v1/admin/content-items`（资源草稿） | 200 | `id=586HX9RCQVKQ1ZBQDMVKG2PDAP`，`revision=1`，`DRAFT/NONE/HIDDEN/ACTIVE` |
| R04 | `POST /admin/content-items/{id}/submit-review` | 200 | `PENDING_REVIEW`，响应 `revision=1` |
| R09 | `POST /admin/reviews/{id}/decision`（`APPROVE`，`If-Match: 2`） | 200 | `APPROVED` |
| R11 | `POST /admin/content-items/{id}/publish` | 200 | `PUBLISHED/ACTIVE/PUBLIC`，响应 `revision=3`，重读为 `4` |
| R13 | `PATCH /admin/content-items/{id}`（`If-Match: 4`） | 200 | 回到 `DRAFT` 并新建候选修订；公开页不变 |
| R16 | 再 `PATCH` 把标题改成 `…-01-v2` | 200 | 仍是 `DRAFT`，公开页仍读旧标题 |
| R18 | 送审 → 批准 → 发布候选 | 200 ×3 | 公开页标题切换为 `LEARN-20260918-VH02-01-v2` |
| R19 | `POST /admin/content-items/{id}/hide` | 200 | `visibility=HIDDEN` |
| R20 | `POST /admin/content-items/{id}/show` | 200 | 回到 `PUBLIC` |
| R21 | `POST /admin/content-items/{id}/archive` | 200 | `ARCHIVED` + `HIDDEN` |
| R22 | `POST /admin/content-items/{id}/restore` | 200 | `APPROVED / NONE / HIDDEN / ACTIVE` |
| R24 | 恢复后重新 `publish` | 200 | 再次 `PUBLISHED`，公开页恢复 200 |
| R36 | 新内容 `REJECT` + 原因 | 200 | `REJECTED` |
| R37 | `POST /admin/content-items/{id}/reopen` | 200 | `CHANGES_REQUESTED` |

### 2.2 失败用例

| # | 场景 | 结果 | 错误码 | requestId（本轮） |
|---|---|---|---|---|
| R02 | 草稿直接发布 | 409 | `CONTENT_NOT_APPROVED` | `X1V9YG943CBVB7474WWKEFNYD7` |
| R03 | DRAFT 状态就审核 | 409 | `CONTENT_NOT_IN_REVIEW` | `R7NGYWS6FNDDXXAZSR75R125VH` |
| R05 | 旧 `If-Match: 0` 审核 | 409 | `CONTENT_VERSION_CONFLICT` | `XDH07409F5B6B5GSDV15TMVHEN` |
| R07 | `decision: "MAYBE"` | 400 | `VALIDATION_FAILED` | `C6G1FN2G3524YNZAYQTH705A51` |
| R23 | 恢复后直接 `show` | 409 | `CONTENT_NOT_SHOWABLE` | 无（未记录） |
| R27 | 招募无有效行动入口发布 | 409 | `RECRUITMENT_ACTION_REQUIRED` | `M9QK7HET7KJHB567NPENVQT68J` |
| R28 | `hide` 的 `reason` 为空白 | 400 | `VALIDATION_FAILED`（`fieldErrors.reason`） | 无（未记录） |
| R29 | `archive` 完全不带请求体 | 500 | `TEMPORARILY_UNAVAILABLE` | 无（未记录） |
| R30 | 对已公开内容再 `show` | 409 | `CONTENT_NOT_SHOWABLE` | 无（未记录） |
| R31 | 未归档却 `restore` | 409 | `CONTENT_NOT_RESTORABLE` | 无（未记录） |
| R32 | 已发布直接送审 | 409 | `CONTENT_NOT_SUBMITTABLE` | 无（未记录） |
| R33 | 已发布再 `publish` | 409 | `CONTENT_NOT_APPROVED` | 无（未记录） |
| R34 | `CHANGES_REQUESTED` 无原因（版本新鲜） | 400 | `REVIEW_REASON_REQUIRED` | `GPHS0NNW62TAHDKR515HS1D779` |
| R35 | `REJECT` 无原因 | 400 | `REVIEW_REASON_REQUIRED` | `T501ZYA1J4MX2PQ4SX795K2KGX` |
| R38 | 非拒绝态 `reopen` | 409 | `CONTENT_NOT_REOPENABLE` | `DHYNB1J4W27TZ6MG373VS7M114` |

两个容易踩错的细节：

1. R02/R03 这类「本该报参数错误」的用例，如果 `If-Match` 用了过期值，会先撞 `CONTENT_VERSION_CONFLICT`，把你想验证的错误码盖住。先 `GET /admin/content-items/{id}` 取最新 `revision`，再发失败用例。
2. R28 证明 `ACTION_REASON_REQUIRED`（`ContentManagementService.java:448`）从 `hide`/`archive`/`restore` 这三个入口到不了，因为它们的 DTO 上已有 `@NotBlank`，Bean Validation 先返回 `VALIDATION_FAILED`。而 R34/R35 能触发 `REVIEW_REASON_REQUIRED`，是因为 `ReviewDecision.reason` 只写了 `@Size`。

### 2.3 只读观察

| # | 请求 | 结果 |
|---|---|---|
| R12 | `GET /api/v1/public/resources/{id}`（已发布） | 200，返回已发布修订的标题 |
| R14/R17 | 候选修订处于 DRAFT / PENDING_REVIEW 时再查公开详情 | 200，仍是旧标题 |
| R19/R21/R22 之后 | 隐藏 / 归档 / 恢复未重新发布时查公开详情 | 404 `CONTENT_NOT_FOUND`（不是 403，也不是 410） |
| R08/R10 | `GET /api/v1/admin/content-items/{id}` | 返回权威 `revision`，并带 `reviews[]` 历史 |

### 2.4 幂等与跨构建对照

| # | 场景 | 结果 |
|---|---|---|
| R25 | 同一 `Idempotency-Key` + 同体重复 `publish` | 200，返回首次响应；实体 `version` 保持 `16 → 16`，没有第二次状态写入 |
| R26 | 同一键换成 `hide` 的请求体 | 409 `IDEMPOTENCY_KEY_REUSED` |
| R39 | 18080 上重复三条失败用例 | 与 18082 逐字一致（见 `README.md` §2） |

## 3. 数据库对照（只读 SELECT）

对 `voidhaven-lab`（18082）的 MySQL 执行下面四组查询：

```sql
select id, workflow_status, publication_status, visibility, record_state, version,
       current_revision_id, published_revision_id
from content_items
where id in ('586HX9RCQVKQ1ZBQDMVKG2PDAP','VC9E4J4SDX4CJADBQ46N1T4QD3');

select id, revision_number, title
from content_revisions
where content_item_id in ('586HX9RCQVKQ1ZBQDMVKG2PDAP','VC9E4J4SDX4CJADBQ46N1T4QD3')
order by content_item_id, revision_number;

select content_item_id, decision, reason, revision_id from review_records
where content_item_id in ('586HX9RCQVKQ1ZBQDMVKG2PDAP','VC9E4J4SDX4CJADBQ46N1T4QD3');

select action, count(*) from audit_logs
where object_id in ('586HX9RCQVKQ1ZBQDMVKG2PDAP','VC9E4J4SDX4CJADBQ46N1T4QD3')
group by action order by action;
```

实测输出（关键部分）：

```text
id                          workflow   publication visibility record  version current_revision           published_revision
586HX9RCQVKQ1ZBQDMVKG2PDAP  PUBLISHED  ACTIVE      PUBLIC     ACTIVE  16      FAPB880YNJ65NZCY3A4S80VARJ FAPB880YNJ65NZCY3A4S80VARJ
VC9E4J4SDX4CJADBQ46N1T4QD3  APPROVED   NONE        HIDDEN     ACTIVE  3       ZM7KGXP2J6WA5Y6CRK2WTCWWQ0 NULL

revision id                 number title
NX3120EJ0RW5GCABR87R2PQWEH  1      LEARN-20260918-VH02-01
FAPB880YNJ65NZCY3A4S80VARJ  2      LEARN-20260918-VH02-01-v2
ZM7KGXP2J6WA5Y6CRK2WTCWWQ0  1      LEARN-20260918-VH02-REC

decision  revision_id                 （reason 均为 NULL，批准可不写原因）
APPROVE   FAPB880YNJ65NZCY3A4S80VARJ  第 2 版
APPROVE   NX3120EJ0RW5GCABR87R2PQWEH  第 1 版

action                   count
CONTENT_REVISION_CREATED 1   只有第一次「编辑已发布内容」建了新修订行
CONTENT_DRAFT_SAVED      3   之后 3 次草稿保存都就地覆盖同一行
CONTENT_PUBLISHED        3
CONTENT_SUBMITTED        3
CONTENT_CREATED          2
REVIEW_APPROVE           3
CONTENT_HIDDEN / SHOWN / ARCHIVED / RESTORED  各 1
```

这张表就是「双修订」最硬的证据：

- 同一份内容有 2 条 `content_revisions`，标题分别是改前与改后。
- `current_revision_id == published_revision_id`，说明此刻草稿与线上是同一版（发布之后没有再编辑）。
- 发布失败的招募 `published_revision_id = NULL`，说明发布失败不会留下半成品指针。
- `CONTENT_REVISION_CREATED=1` 与 `CONTENT_DRAFT_SAVED=3` 精确对应 `revise` 的两条分支。

补充：`audit_logs` 表里存了 `before_summary` / `after_summary`，但 `/api/v1/admin/audit-logs` 返回的 `Entry` 不含这两个字段（`AuditQueryService.java:14-15`），要看前后值只能查库。

## 4. 本轮没能复现的部分

| 项 | 原因 | 替代证据 |
|---|---|---|
| 到期自动关闭 / 关闭三天后自动归档 | cron 默认 `0 10 1 * * *`（Asia/Shanghai），`application.yml` 没有暴露该配置 | `ContentLifecycleServiceTest`（Mockito + 固定 `Instant`） |
| 合作方发布配额 | 需要 `local-partner` 账号并调低配额 | 只读代码 `ContentManagementService.java:449-456` |
| 真实并发编辑撞唯一约束 | 需要两个并发会话 | 只读代码注释 `ContentManagementService.java:134-138` |
| 正文图片引用不一致 | 本轮未上传媒体 | 留给 VH-05，或自己补一次上传 |

## 5. 记录模板

```yaml
case_id: VH-02-<主题>-<序号>
title: <一句话说明你要证明什么>
runtime: http://localhost:18082
source_revision: HEAD be219ac + 61 staged changes
request:
  method: POST
  path: /api/v1/admin/content-items/{id}/publish
  headers: { Idempotency-Key: <uuid>, If-Match: "<n>" }
observed:
  status: 200
  code: null
  request_id: <响应头 X-Request-Id>
code_path: ContentManagementService.java:205-218 -> ContentItem.java:55
db_after:
  workflow_status: PUBLISHED
  publication_status: ACTIVE
  visibility: PUBLIC
  published_revision_id: <id>
test_mapping: 无单测覆盖（见 02-test-map.md §3）
not_proven: 并发、配额、真实媒体
```

## 6. 收尾检查

1. 重新记录 `git -C D:\Projects\voidhaven\voidhaven-platform status --short`，确认没有多出你之外的改动。
2. 本轮新增 3 条合成内容，保留在本地学习库即可；不执行 `docker compose down -v`。
3. 每个结果登记到 `09-evidence-ledger.md` 时，标明来自 18082 还是 18080。
4. 测试与复现结果不自动修改 `learning-state.yaml`。
