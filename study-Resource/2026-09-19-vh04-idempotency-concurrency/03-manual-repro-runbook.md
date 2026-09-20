# VH-04 手动复现运行手册

| 项 | 值 |
|---|---|
| 代码版本 | HEAD `2295a41`（2026-09-19），工作树干净 |
| 本轮状态 | AI 已在一次性栈上跑完 A～F 六组请求，栈已回收 |
| 你可以用的栈 | 自己的 compose 栈（推荐），或按 1.2 起一套一次性栈 |
| 数据边界 | 只用合成账号与合成内容；不要用真实账号、真实联系方式或生产凭据 |

## 1. 前置

### 1.1 路径 A（推荐）：用你自己的 compose 栈

1. `infra/compose/compose.local.yml` 里 API 已经写死 `SPRING_PROFILES_ACTIVE: local`，**这是 VH-04 复现的前提**——只有 `local` profile 才会执行 `LocalDataInitializer`，帮你建出 `local-admin`、`local-partner` 和三条合成内容。
2. compose 只把 nginx 发布到 `${VOIDHAVEN_HTTP_PORT}`（VH-03 里你用过 18080 / 18082）。请求要**走 nginx**，不要直连容器内部的 8081。
3. 与 VH-04 直接相关的默认值：

| 变量 | 默认 | 对 VH-04 的意义 |
|---|---|---|
| `VOIDHAVEN_PUBLIC_WRITE_RATE_LIMIT` | 8 | 公开写入额度；做限流实验时调成 2 更快看到 429 |
| `VOIDHAVEN_PUBLIC_WRITE_RATE_LIMIT_WINDOW_SECONDS` | 3600 | 窗口长度 |
| `VOIDHAVEN_AUTH_LOGIN_FAILURE_LIMIT` | 5 | 登录名维度的失败上限 |
| `VOIDHAVEN_IDEMPOTENCY_IN_PROGRESS_SECONDS` | 86400 | 在途占位存活时长 |

4. `local` profile 会把登录**网络**额度抬到 200（`application-local.yml`），所以本机很容易触发「登录名维度」的 429，很难触发「网络维度」的 429。

### 1.2 路径 B：起一套一次性栈（本轮 AI 的方式）

本机**没有 `docker` CLI**，但 Docker 引擎可通过命名管道访问，于是：

- **不能用** `docker compose up`；
- **可以用** Testcontainers 起 MySQL（`mvnw verify` 里的 `DistributedSafetyIT` 就是这么跑的）；
- API 直接用仓库打包出的 fat jar 启动。

完整步骤与最小示例见 `05-runtime-notes.md`。

### 1.3 三个必须先确认的事实

1. `curl` 访问受保护接口时**必须**同时用 `-b cookies.txt -c cookies.txt`。只读不写会漏掉 `/auth/csrf` 下发的 `XSRF-TOKEN`，下一步写请求必然 403（本轮踩过一次）。
2. CSRF 令牌要用 `GET /api/v1/auth/csrf` 返回的 **96 字符掩码值**放进 `X-XSRF-TOKEN`，不能用 cookie 里的 36 字符裸值。
3. 公开接口不需要登录，**但需要 `Idempotency-Key`**；管理员接口需要 会话 + CSRF + 幂等键（改内容还要 `If-Match`）。

## 2. 请求速查

| 目的 | 方法与路径 | 必需头 |
|---|---|---|
| 健康检查 | `GET /healthz` | — |
| 公开投稿 | `POST /api/v1/public/submissions` | `Idempotency-Key` |
| 合作申请 | `POST /api/v1/public/partner-applications` | `Idempotency-Key` |
| 投稿进度查询 | `POST /api/v1/public/submissions/status-query` | — |
| 登录 | `POST /api/v1/auth/session` | — |
| 取 CSRF | `GET /api/v1/auth/csrf` | Cookie |
| 建内容 | `POST /api/v1/admin/content-items` | Cookie + CSRF + `Idempotency-Key` |
| 改内容 | `PATCH /api/v1/admin/content-items/{id}` | Cookie + CSRF + `Idempotency-Key` + `If-Match` |
| 审核决定 | `POST /api/v1/admin/reviews/{id}/decision` | Cookie + CSRF + `Idempotency-Key` + `If-Match` |

CSRF 头名从 `GET /auth/csrf` 的响应里读（当前是 `X-XSRF-TOKEN`），不要写死。

## 3. 本轮已实测矩阵（AI 对照基线，不是你的掌握证据）

环境：一次性 MySQL 8.4.11 容器 + `api-0.1.0-SNAPSHOT.jar`（profile `local`），`http://localhost:18084`。

### 3.1 A 组：幂等（公开投稿，走 `executeSingleDelivery`）

请求体 `B1`：

```json
{"submissionType":"FEEDBACK","title":"VH-04 idempotency probe","message":"local synthetic probe A1","contact":"test-contact-0001"}
```

| # | 请求 | 期望 | 实测 |
|---|---|---|---|
| A1 | `key=K1` + `B1` | 201 | **201**，返回 `{id, queryCode, requestId}` |
| A2 | `key=K1` + `B1` 完全重放 | 409 | **409** `SENSITIVE_RESULT_NOT_REPLAYABLE` |
| A3 | `key=K1` + `B2`（改了 message） | 409 | **409** `IDEMPOTENCY_KEY_REUSED` |
| A4 | 不带 `Idempotency-Key` | 400 | **400** `IDEMPOTENCY_KEY_REQUIRED` |

数据库佐证：

```text
idempotency_key  response_status  response_replayable  request_hash(前 12 位)  response_body
vh04-key-K1      200              0                    ff3c7e85a7ad            {"state": "DELIVERED_NO_REPLAY"}
content_submissions: 5 行（A1 + 4 条限流实验），A2/A3/A4 都没有产生业务行
```

读法：`response_replayable = 0` 表示不可回放。记录被存下来了，但重放请求拿到的是 409，而不是当时的 `queryCode`——一次性秘密不回放。

### 3.2 B 组：额度被失败的请求一起消耗

继续向同一路径发 5 条**不同 key、不同 body** 的投稿：

| # | 结果 |
|---|---|
| B1～B4 | **201**（累计 8 次成功，达到上限） |
| B5 | **429** `PUBLIC_WRITE_RATE_LIMITED`，响应头 `Retry-After: 900` |

数据库佐证：

```text
public_write_rate_limit_events: 8 行，全部属于同一个 subject_hash（HMAC 后的网络标识）
content_submissions: 仍然只有 5 行
```

**这是本组最有价值的结论。** A1～A4 里只有 A1 真正创建了数据，但四次请求各消耗 1 个额度；第 9 次请求（B5）因此直接 429，业务代码根本没执行。执行顺序是：

```text
PublicWriteRateLimitInterceptor.preHandle → 参数解析 → 幂等判断 → 业务事务
        （占额度）                          （400 缺键） （409 复用）
```

### 3.3 C 组：乐观并发与幂等重放（管理员 PATCH）

前置：登录 `local-admin` → 取 CSRF → 对一条合成 RESOURCE 内容（`revision = 1`）做 `PATCH`。

| # | 请求 | 期望 | 实测 |
|---|---|---|---|
| C1 | `If-Match: 1` + `key=P1` | 200 | **200**，`revision: 2`，`workflowStatus: DRAFT`，`publicationStatus: ACTIVE` |
| C2 | `If-Match: 1`（已过期）+ `key=P2` | 409 | **409** `CONTENT_VERSION_CONFLICT` |
| C3 | `key=P1` + **同一 body** 重放 | 200（回放） | **200**，与 C1 完全相同的 JSON |
| C4 | `key=P1` + 改了标题 | 409 | **409** `IDEMPOTENCY_KEY_REUSED` |

数据库佐证（判断「C3 是否重新执行」的关键）：

```text
idempotency_records: 只有 vh04-patch-P1（200 / replayable=1 / 存的是 Managed JSON）
                     vh04-patch-P2 不存在 —— 失败的请求释放了占位
audit_logs:          CONTENT_REVISION_CREATED = 1，CONTENT_DRAFT_SAVED = 0
content_revisions:   2 行（revision_number = 1 原始、2 候选）
content_items:       workflow_status = DRAFT, publication_status = ACTIVE, version = 2
```

三条结论：

1. **C3 没有重新执行**——审计表里 `CONTENT_REVISION_CREATED` 仍然只有 1 条。
2. **失败请求不污染幂等键**——P2 在表里不存在，用户可以用同一个键重试。
3. **公开版本没有中断**——内容进入 DRAFT 候选态，`publicationStatus` 仍是 `ACTIVE`（呼应 VH-02 的双指针）。

### 3.4 D 组：在途占位与过期自愈

先手工把一条已完成的记录改成「在途」：

```sql
update idempotency_records
   set response_status = 102, response_body = json_object('state','IN_PROGRESS')
 where idempotency_key = 'vh04-inprogress-XZ';
```

| # | 请求 | 期望 | 实测 |
|---|---|---|---|
| D1 | 用被占用的 key 重发原请求 | 409 | **409** `REQUEST_IN_PROGRESS` |
| D2 | 把该行 `expires_at` 改到过去，再用同一个 key 重发 | 201 | **201**，返回了一个**新的 `id`** |

读法：`IdempotencyReservationService.reserve` 第一句就在删过期的 102 行（第 32～33 行），所以「进程崩了、占位没释放」会在过期后自愈，而不是永久锁死。

### 3.5 E 组：真并发（两个请求带同一个 `If-Match`）

用 PowerShell `Start-Job` 同时发两个 `PATCH`：`If-Match: 2`、两个不同的幂等键、同一个请求体。

| 结果 | 观测 |
|---|---|
| 一个请求 | **200**，`revision: 3` |
| 另一个请求 | **409** `CONTENT_VERSION_CONFLICT` |
| 有没有 500 / 唯一键冲突 | **没有** |

数据库佐证：`audit_logs` 里 `CONTENT_DRAFT_SAVED = 1`；键 `vh04-conc-B` **不存在**。

若把 `revise` 的悲观锁去掉，会看到 500 或 `DATA_INTEGRITY_CONFLICT`（撞 `UNIQUE (content_item_id, revision_number)`）——这正是 `ContentManagementService` 第 134～139 行注释解释的失败模式。

### 3.6 F 组：登录失败限流

对一个**不存在的**登录名连打 6 次错误口令：

```text
attempt 1~5 => 401 AUTHENTICATION_FAILED
attempt 6   => 429 AUTH_RATE_LIMITED，retryable=true，Retry-After: 900
```

要点：前 5 次在「登录名」维度累积 `FAILURE`；第 6 次请求**在认证之前**就被 `AuthenticationRateLimiter.beginLogin` 拦下，不会再多一次口令比对开销。

## 4. 需要你自己动手的实验

每个实验做完，按第五节的模板登记：状态码 + 错误码 + `requestId` + 数据库行 + 一句话结论。

### E-01 并发同版本写入，复现「一成一败」

1. 登录后取 CSRF，读一条内容的 `revision`（记为 `<V>`）。
2. 同时发两个 `PATCH`，都带 `If-Match: <V>`，用两个**不同**的幂等键。
3. 预期：一个 200、一个 409 `CONTENT_VERSION_CONFLICT`。
4. 追加一步：改成**同一个**幂等键 + 同一个 body 再跑一次，两边应该都拿到同一个 200（第二次是回放），数据库里只多一条审计。

要能回答：第二次「两个都成功」，为什么不算并发写丢了数据？

### E-02 把限流额度调小，观察失败请求也占额度

1. compose 里把 `VOIDHAVEN_PUBLIC_WRITE_RATE_LIMIT` 改成 `2`，重启 api。
2. 依次发：同键重放、同键异体、缺键、新键新体。
3. 预期：前三个分别是 409 / 409 / 400，第四个是 **429**，且数据库里只有 1 条业务记录。

要能回答：这个行为在真实攻击场景下是优点还是风险？你会怎么改？

### E-03 在途幂等与过期自愈（最推荐）

1. 用 key `L1` 发一条公开投稿 → 201，记下返回的 `id`。
2. 用 SQL 把 `L1` 那行改成 `response_status = 102`。
3. 用 `L1` + 相同 body 重发 → 预期 409 `REQUEST_IN_PROGRESS`。
4. 把那行的 `expires_at` 改到过去，再重发 → 预期 201，且返回的 `id` **变了**。
5. 查 `content_submissions`，确认现在有 2 条记录。

要能回答：为什么「过期自愈」能做，而「直接删掉在途锁」不能做？

### E-04 敏感结果不可回放

1. 用 key `S1` 发一条带 `contact` 的公开投稿，记下 `queryCode`。
2. 用同一个 key 重发。
3. 预期：409 `SENSITIVE_RESULT_NOT_REPLAYABLE`；再用 `queryCode` 调 `status-query` 仍能查到进度。

要能回答：为什么这里不能像管理员接口那样「回放同一份响应」？

### E-05 清理与过期记录

1. 手工塞一条 `expires_at` 在过去的 `idempotency_records` 行（任意 key）。
2. 触发 `RetentionService`（本地没有对外端点时，可以直接跑它那段 `delete` SQL 做对照，并在记录里标注「未走应用路径」）。
3. 预期：过期行被删除；`retention_runs` 出现 `SUCCESS`，`detail_summary` 里 `idempotency=` 的计数包含你塞的那行。

要能回答：为什么清理任务自己也要 `GET_LOCK`？

## 5. 记录模板

```yaml
case_id: vh04-<组>-<序号>
date: 2026-09-19
source_head: 2295a41
runtime: <compose 项目名 / 一次性栈> @ <端口>
request:
  method: PATCH
  path: /api/v1/admin/content-items/<id>
  headers: [Idempotency-Key, If-Match, X-XSRF-TOKEN]
  body_summary: <脱敏摘要，不放真实数据>
observed:
  http_status: 409
  code: CONTENT_VERSION_CONFLICT
  request_id: <26 位>
database:
  table: idempotency_records
  row: <关键列的值>
conclusion: <一句话：这条证据证明了什么机制>
uncertain: <还不能确定的点>
```

`requestId` 是排障锚点：响应体里有，服务端日志里也有，用它把一次请求的所有日志串起来。
