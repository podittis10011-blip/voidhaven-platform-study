# VH-06 手动复现运行手册

> 重要：本手册里的请求矩阵是**从源码推导的预期结果**，不是实测结果。
> 2026-09-19 本轮 Docker 未运行，没有任何 HTTP 实测。你要做的是把这些预期逐条变成带 `requestId` 的实测记录。
> 每填完一行，就可以在 `outputs/voidhaven/2026-09-14-sprint/09-evidence-ledger.md` 里把对应的 `RUNTIME_CURRENT_SOURCE` 从 `PENDING` 改成 `PARTIAL` 或 `DONE`。

## 0. 准备

### 0.1 启动本地隔离栈

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform'

# 单栈即可：VH-06 涉及的文件全部已在 HEAD 中，不需要两个版本对照
docker compose --env-file .env -f infra/compose/compose.local.yml -p voidhaven-vh06 up --build -d
```

栈地址由 `.env` 的 `VOIDHAVEN_HTTP_PORT` 决定：默认 `18080`；想和 VH-03 的 `18082` 并存时改成 `18082`。

连通性确认：

```powershell
curl.exe -s -i http://localhost:18080/api/v1/public/capabilities | Select-Object -First 12
```

能拿回 `200` 和 `{"data":{...,"communityGateEnabled":...}}` 就说明栈是活的。

### 0.2 账号、凭据与安全边界

| 项目 | 值 |
|---|---|
| 管理员账号 | `local-admin`（角色 `ADMIN,SUPER_ADMIN`），口令取 `.env` 的 `VOIDHAVEN_BOOTSTRAP_ADMIN_PASSWORD` |
| 合作方账号 | `local-partner`（角色 `PARTNER`） |
| 公开访客 | 不需要账号——这正是 VH-06 要验证的场景 |
| 数据库 | `docker exec -it voidhaven-vh06-mysql-1 mysql -uvoidhaven_app -p voidhaven` |

**安全边界**：查询码、邀请码、grant、真实联系方式都属于凭据或私密数据。截图、笔记、博客、简历里只能出现合成值，且要打码。

### 0.3 两个环境配置（决定你能做哪些实验）

`.env` 目前是「门禁可用、联系表单不可用」的状态：

| 变量 | 当前值 | 影响 |
|---|---|---|
| `VOIDHAVEN_COMMUNITY_GATE_ENABLED` | `true` | 门禁开启 |
| `VOIDHAVEN_PRIVATE_CONTACT_ENABLED` | `false` | 需要联系方式的投稿/合作申请返回 `503 PRIVATE_CONTACT_DISABLED` |

注意 `infra/compose/compose.local.yml` 用的是 `${VAR:-default}`：**空值会被默认值填上**。所以密钥环与 HMAC 密钥在默认 `.env` 下其实有值（`test-local-v1` 等），门禁能正常工作；而 `VOIDHAVEN_PRIVATE_CONTACT_ENABLED=false` 是非空值，不会被默认值覆盖。

| 配置 | 动作 | 能做哪些组 |
|---|---|---|
| A（默认） | 什么都不改 | C 组（门禁）、D 组（消费）、A5/A7、B 组部分 |
| B（覆盖写路径） | 在 `.env` 里把 `VOIDHAVEN_PRIVATE_CONTACT_ENABLED=true` | A 组全部、E3（后台看联系方式） |
| C（复现 F-04） | 把 `VOIDHAVEN_PRIVATE_VALUE_CURRENT_KEY_ID` 设成一个**不存在的 keyId**（例如 `bogus-key`） | 验证门禁与密钥环的耦合 |

改完 `.env` 需要重建容器：`docker compose --env-file .env -f infra/compose/compose.local.yml -p voidhaven-vh06 up -d --force-recreate api`。

### 0.4 脚本骨架

```powershell
$b='http://localhost:18080/api/v1'

# 公开写：只需要 Idempotency-Key，不需要 cookie/CSRF
$key=[guid]::NewGuid().ToString()
Invoke-WebRequest -Uri "$b/public/submissions" -Method POST -SkipHttpErrorCheck `
  -ContentType 'application/json' -Headers @{'Idempotency-Key'=$key} `
  -Body '{"submissionType":"FEEDBACK","title":"合成标题","message":"合成内容","linkUrl":null,"imageDescription":null,"contact":null}'

# 后台写：需要会话 + CSRF 掩码令牌（见 VH-03 手册第 1 节）
$s=New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -Uri "$b/auth/session" -Method POST -WebSession $s -ContentType 'application/json' `
  -Body '{"loginId":"local-admin","password":"<本地口令>"}' -SkipHttpErrorCheck | Out-Null
$csrf=(Invoke-WebRequest -Uri "$b/auth/csrf" -WebSession $s -SkipHttpErrorCheck).Content | ConvertFrom-Json
```

## 1. 请求矩阵（预期值来自源码，实测列留给你）

### A 组：公开投稿写路径

| # | 请求 | 源码预期 | 依据 | 实测 |
|---|---|---|---|---|
| A1 | `POST /public/submissions`，**不带** `Idempotency-Key` | `400 IDEMPOTENCY_KEY_REQUIRED` | `ApiExceptionHandler.handleMissingHeader` | |
| A2 | 同上但 `title` 缺失 | `400 VALIDATION_FAILED`，`fieldErrors` 含 `title` | `MethodArgumentNotValidException` 分支 | |
| A3 | `linkUrl = "http://example.com/x"` | `400 SOURCE_URL_REQUIRED` | `ContentSourcePolicy` 要求 HTTPS | |
| A4 | `submissionType=COPYRIGHT` 且 `contact` 为空 | `400 CONTACT_REQUIRED` | `PublicCaseService` 第 40～42 行 | |
| A5 | `submissionType=FEEDBACK` 且 `contact` 为空（配置 A 下） | `201`，返回 `{id, queryCode}` | 无联系方式不触发 `requireEnabled()` | |
| A6 | 配置 B 下带 `contact` 的投稿 | `201`；`private_contact_records` 多一行，`access_code_hash` 是 64 位十六进制 | `storeContact` 第 100～106 行 | |
| A7 | 用 A6 的同一个键 + 同一个请求体重放 | `409 SENSITIVE_RESULT_NOT_REPLAYABLE` | `IdempotencyService` 第 49 行 | |
| A8 | 用 A6 的键 + 改一个字的请求体 | `409 IDEMPOTENCY_KEY_REUSED` | `IdempotencyService` 第 47 行 | |
| A9 | 同一来源连续第 9 次投稿 | `429 PUBLIC_WRITE_RATE_LIMITED` + `Retry-After: 900` | 限流 8 次/小时；`Retry-After` 见 F-02 | |

### B 组：状态查询

| # | 请求 | 源码预期 | 依据 | 实测 |
|---|---|---|---|---|
| B1 | 用 A5/A6 返回的 `id` + `queryCode` | `200`，`data` 只含 `id/status/publicResult/dueAt/createdAt/updatedAt` | `CaseStatus` 记录 | |
| B2 | 同一 `id` + 错误 `queryCode` | `404 CASE_QUERY_INVALID` | `PublicCaseService` 第 93 行 | |
| B3 | 不存在的 26 位 `id` + 任意合法长度 `queryCode` | `404 CASE_QUERY_INVALID`，响应体与 B2 **完全一致** | 第 90 行 | |
| B4 | `queryCode` 只有 10 位 | `400 VALIDATION_FAILED` | `CaseQuery` 的 `@Size(min=16)` | |
| B5 | 连续超过 60 次查询 | `429 PUBLIC_STATUS_QUERY_RATE_LIMITED` | 独立的查询桶 | |

### C 组：社群门禁

| # | 请求 | 源码预期 | 依据 | 实测 |
|---|---|---|---|---|
| C1 | `POST /public/community-gate/verify`，`inviteCode` 长度 8 但不存在 | `401 COMMUNITY_INVITE_INVALID` | `CommunityService` 第 77～81 行 | |
| C2 | 配置 C（keyId 不存在）下调用 verify | `503 PRIVATE_CONTACT_DISABLED`（**不是** `COMMUNITY_NOT_CONFIGURED`） | F-04 | |
| C3 | 用后台新签发的有效邀请码 | `200 {grant, expiresAt}`；grant 表新增一行，`use_count` +1 | 第 84～90 行 | |
| C4 | 把 `max_uses` 设为 1，用第二个客户端再兑换 | `401 COMMUNITY_INVITE_INVALID` | 第 75 行 `use_count < max_uses` | |
| C5 | 连续输错 9 次 | **预期应当**在第 9 次返回 `429 COMMUNITY_GATE_COOLDOWN`；若 F-01 成立则始终 `401` | 第 69～74 行 + F-01 | |

### D 组：消费门禁

| # | 请求 | 源码预期 | 依据 | 实测 |
|---|---|---|---|---|
| D1 | `GET /public/community/entries`（无头） | `200`，只含 `accessLevel=PUBLIC` 的条目 | `publicEntries` 第 99 行 | |
| D2 | 同上，`X-Community-Grant: bogus` | `401 COMMUNITY_GRANT_INVALID` | `requireGrant` | |
| D3 | 同上，带有效 grant | `200`，包含 `COMMUNITY_GATED` 条目且 `value` 已解密 | 第 105～107 行 | |
| D4 | `GET /public/community/media/{id}` 不带 grant | `400 REQUEST_HEADER_REQUIRED` | 该头 `required=true` | |
| D5 | 同上带有效 grant | `200`，`Content-Type: image/*`，`Cache-Control: no-store` | `PublicMediaController` 第 30～35 行 | |
| D6 | `GET /public/content/{id}/gated-actions` 带有效 grant，内容非 `OPEN` | `200` + 空数组（不是 403） | `ContentActionService` 第 79 行 | |
| D7 | `GET /public/participation/project-join` 带无效 grant | `401 COMMUNITY_GRANT_INVALID` | `ProjectJoinService` 复用 `requireGrant` | |

### E 组：后台闭环（需要会话 + CSRF + `Idempotency-Key`）

| # | 请求 | 源码预期 | 依据 | 实测 |
|---|---|---|---|---|
| E1 | `POST /admin/submissions/{id}/decision`，`decision=APPROVE` | `200 {id, state:"RESOLVED"}`；状态历史新增一行 | `PublicInputAdministrationService` | |
| E2 | 对同一条再发一次 decision | `409 SUBMISSION_NOT_ACTIONABLE`，且不写审计 | 同上 | |
| E3 | `POST /admin/submissions/{id}/contact`（配置 B） | `200 {id, contact:"<明文>"}`；`audit_logs` 出现 `PRIVATE_CONTACT_REVEALED` | `reveal` 方法 | |
| E4 | `POST /admin/partner-applications/{id}/approve` | `200 {partnerProfileId, loginId, activationToken}`；申请变 `ACTIVATION_PENDING`，新账号 `PENDING` | `PartnerAccountProvisioningService` 第 62～92 行 | |
| E5 | `POST /admin/community/invite-batches` | `201 {id, inviteCode, expiresAt, maxUses}`；邀请码只此一次返回 | `createInviteBatch` | |
| E6 | `POST /admin/community/invite-batches/{id}/revoke` 后再用旧 grant 读 D3 | 旧 grant 立刻 `401`；该批次的 grant 行被删除 | `revokeInviteBatch` | |
| E7 | `POST /admin/community/entries` 用 `PUBLIC + EMAIL` 但不带 `publicExposureConfirmed` | `400 COMMUNITY_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED` | `prepare` 第 238～240 行 | |

## 2. 需要你自己动手的七个实验

### E-01 幂等键的三种命运（写操作，10 分钟）

目的：把「同键同体 / 同键异体 / 一次性结果」三种语义各自实测一次。

1. 生成一个键，投一次 `FEEDBACK`（无联系方式），记录 `201` 与返回的 `id/queryCode`。
2. **原样**重放：记录状态码与 `code`。
3. **改一个字**再重放：记录状态码与 `code`。
4. 查库确认只产生了一条 `content_submissions`：

```sql
select id, state, due_at, version from content_submissions order by created_at desc limit 5;
select idempotency_key, request_hash, response_status, response_replayable
  from idempotency_records order by created_at desc limit 5;
```

要能回答：第 2 步为什么不返回原来那份 `queryCode`？

### E-02 邀请码的原子占额（写操作 + 并发，20 分钟）

目的：证明"判定写在 UPDATE 的 WHERE 里"能挡住并发超发。

1. 用超管建一个 `expiresInDays=1, maxUses=1` 的批次，记下 `inviteCode`。
2. 用两个并行的 PowerShell 作业同时兑换：

```powershell
$body = '{"inviteCode":"<一次性邀请码>"}'
1..2 | ForEach-Object -Parallel {
  (Invoke-WebRequest -Uri 'http://localhost:18080/api/v1/public/community-gate/verify' -Method POST `
    -ContentType 'application/json' -Body $using:body -SkipHttpErrorCheck).StatusCode
}
```

3. 期望：**恰好一个 200、一个 401**，库里 `use_count = 1`、`community_access_grants` 只有一行。
4. 把 `use_count` 手工改回 0 再重跑一次，确认结果可重复。

要能回答：如果把 `use_count < max_uses` 从 SQL 挪到 Java 判断，会出现什么后果？靠哪一层机制兜底？

### E-03 一次性结果不可回放（巩固 E-01，5 分钟）

对比 A7 与 `POST /admin/community/invite-batches` 的重放行为——后者同样用 `executeSingleDelivery`。把两者的响应 `code` 与库里 `response_replayable` 一起记录，说明"为什么管理员补发邀请码必须重新签发，而不是回放旧响应"。

### E-04 两个限流桶互不吃额度（写操作，15 分钟）

目的：验证公开写限流与状态查询限流是独立的。

1. 先用同一来源把**投稿额度**打到 `429`（第 9 次）。
2. 立刻用同一来源查一次状态：**应当仍是正常响应（200/404），不是 429**。
3. 反过来，临时把 `VOIDHAVEN_PUBLIC_STATUS_QUERY_RATE_LIMIT` 调成 `3` 重建 api，把查询额度打到 429，再发一次投稿，确认写额度不受影响。

这一步会真实占用 1 小时额度。恢复现场：

```sql
delete from public_write_rate_limit_events where expires_at > current_timestamp(6);
delete from public_status_query_rate_limit_events where expires_at > current_timestamp(6);
```

### E-05 限流主体到底是"谁"（只读，15 分钟）

目的：把 `ClientNetworkSubject` 的信任边界跑成事实。

1. 通过 nginx（`http://localhost:18080/api/...`）发请求，同时带伪造头 `X-Forwarded-For: 198.51.100.7`。
2. 快速把额度打满，**换一个伪造地址**再发一次，观察是否被算成不同主体。
3. 再直连 API 容器（不经过 nginx）发一次同样的伪造头，对比结果。

要能回答：`local` profile 为什么打开 `trust-proxy-headers` 并信任 `10/8、172.16/12、192.168/16`？生产环境信任这三段意味着什么风险？

### E-06 失败事件是否被回滚（**F-01 的关键实验**，写操作，20 分钟）

目的：证实或推翻「社群门禁的失败计数永远不生效」。

```powershell
# ① 先看现有事件行，确认只是本地合成数据
#    select count(*) from community_gate_events;

# ② 连续用不存在的邀请码调用 9 次，逐次记录状态码与 code
1..9 | ForEach-Object {
  $r = Invoke-WebRequest -Uri 'http://localhost:18080/api/v1/public/community-gate/verify' -Method POST `
    -SkipHttpErrorCheck -ContentType 'application/json' -Body '{"inviteCode":"definitely-not-a-real-code"}'
  "{0}: {1} {2}" -f $_, $r.StatusCode, $r.Content
}

# ③ 立刻查库
#    select count(*), max(created_at) from community_gate_events where outcome = 'FAILURE';
```

两种结局的含义：

| 观察 | 结论 |
|---|---|
| 第 9 次是 `429 COMMUNITY_GATE_COOLDOWN`，FAILURE 行数为 8 或 9 | F-01 不成立，冷却逻辑工作正常 |
| 9 次全是 `401 COMMUNITY_INVITE_INVALID`，FAILURE 行数为 **0** | F-01 成立：失败事件被 `@Transactional` 回滚，冷却永不触发 |

如果证实，思考最小修法：把写入挪到 `REQUIRES_NEW` 的独立事务（对照 `RateLimitLockService`），还是在注解上加 `noRollbackFor`？两种方案的副作用分别是什么？

### E-07 撤销批次能否立刻失效（写操作，10 分钟）

1. 正常兑换一个 grant（D3 可用）。
2. 撤销该批次（E6），**立刻**用同一个 grant 再读 D3 与 D5。
3. 期望：两者都变 `401 COMMUNITY_GRANT_INVALID`，且 `community_access_grants` 里该批次的行已删除。
4. 追问自己：如果只把批次标记 `REVOKED`、不删 grant 行，`requireGrant` 的 JOIN 还能挡住吗？既然能挡住，为什么还要删一遍？

## 3. 记录模板

每条记录都按这个格式留档（放在你自己的学习记录里，不要只留在对话里）：

```yaml
case_id: VH-06-<场景>-<编号>
at: 2026-09-20T10:00:00+08:00
stack: 18080 / voidhaven-vh06
git: 2295a41
request: "POST /api/v1/public/submissions"
preconditions: "无 cookie；Idempotency-Key=<新生成>；密钥环已配置"
sent: ["Content-Type: application/json", "Idempotency-Key: <uuid>"]
expected: "201 CreatedCase"
observed_status: 201
observed_body: '{"data":{"id":"...","queryCode":"<打码>"},"requestId":"..."}'
observed_headers: ["X-Request-Id: ...", "Cache-Control: no-store"]
request_id: "..."
conclusion: "一次性结果只返回一次；库内只存 queryCode 的 SHA-256"
evidence_class: RUNTIME_CURRENT_SOURCE
notes: "本地合成数据；查询码未进入截图"
```

数据库侧建议同时记录三层事实：`content_submissions` 一行、`idempotency_records` 一行（`response_replayable=false`）、`public_case_status_history` 一行。

## 4. 清理与边界

- 学习期间可以保持栈运行；结束后停容器但**保留数据卷**，不要执行带 `-v` 的删除。
- E-02/E-04/E-06 会写入限流与门禁事件行；`RetentionService` 的夜间任务会清理过期行，也可以按上面的 SQL 手工清。
- E-04/E-06 会临时抬高限流额度，做完记得恢复 `.env` 或删行。
- E-01/E-04 产生的投稿记录都是合成数据；不要改成任何看起来像真实人的内容。
- 所有截图与笔记都要检查：不能出现查询码、邀请码、grant、cookie、CSRF 令牌、口令或真实联系方式。
