# VH-06 代码阅读路线（六站）

阅读版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`，工作树干净。
行号以 2026-09-19 的工作树为准；改动后会漂移，用方法名兜底。
VH-06 涉及的文件全部已在 HEAD 中，所以这份锚点对本地任何按 HEAD 构建的栈都成立。

## 0. 一句话链路

```text
【公开写】POST /api/v1/public/submissions   （Idempotency-Key 必填）
容器过滤器层（按 @Order 从小到大）
  → NoStoreResponseFilter       @Order(MIN_VALUE+10)  命中 /submissions → Cache-Control: no-store
  → springSecurityFilterChain   @Order(-100)          /public/** permitAll + CSRF 豁免 → 直接放行
  → RequestIdFilter             （未指定 @Order，默认最低优先级）  发 requestId、写 X-Request-Id
MVC 层
  → PublicWriteRateLimitInterceptor    按网络限流 8 次/小时（独立事务，业务回滚不影响计数）
  → PublicInputController              Bean Validation + 来源 URL 策略
  → PublicCaseService.createSubmission 联系方式规则 → 密钥环检查 → 幂等
      → insert content_submissions     只存 queryCode 的 SHA-256
      → insert private_contact_records（AES-256-GCM，AAD = SUBMISSION:<id>）
      → insert public_case_status_history
  → 201 { id, queryCode }              queryCode 只在此刻返回一次

【社群门禁】POST /api/v1/public/community-gate/verify
  → CommunityService.verify            失败计数 → 冷却 → 原子占用邀请码额度
      → update community_invite_batches ... use_count < max_uses
      → insert community_access_grants（存 grant 的 SHA-256，30 分钟）
  → 200 { grant, expiresAt }

【消费门禁】GET /api/v1/public/community/entries  Header: X-Community-Grant
  → CommunityService.publicEntries     有 grant → 校验后连受控条目一起投影
                                       无 grant → SQL 里只投影 access_level='PUBLIC'
```

一句话概括：**这条链路把「陌生人写进来的东西」和「受控信息发出去的东西」都变成了可核验的凭据问题**——进来要幂等键、出去要回执码和短期授权。

## 第 1 站：HTTP 入口与请求校验

### 1.1 `PublicInputController`（37 行，VH-06 的主入口）

文件：`apps/api/src/main/java/cn/voidhaven/api/web/PublicInputController.java`

| 行 | 内容 | 关键点 |
|---:|---|---|
| 12 | `@RestController @RequestMapping("/api/v1/public")` | 整个 VH-06 公开面都在 `/public` 前缀下，被 `SecurityConfig` 整体放行 + CSRF 豁免 |
| 16 | `SubmissionRequest(submissionType, title≤240, message≤5000, linkUrl≤2048, imageDescription≤1000, contact≤500)` | 投稿/反馈/版权三类共用一个请求体；`submissionType` 用枚举，非法值在反序列化阶段就被拒 |
| 17 | `PartnerApplicationRequest(displayName≤120, introduction≤5000, portfolioUrl≤2048, contact 必填≤500)` | 合作申请与投稿的最大差别：**联系方式是 Bean Validation 级别的必填**，不靠 Service 兜 |
| 18 | `CaseQuery(id 恰好 26 位, queryCode 16～256)` | 回执号的长度被写进契约；查询码长度下限 16 是为了挡住过短的猜测 |
| 19～24 | `POST /submissions`，`201` | 第 21 行先做来源校验（fail-fast），第 22 行才进 Service；`Idempotency-Key` 是**必填请求头**，缺失由 `MissingRequestHeaderException` 转成 `400 IDEMPOTENCY_KEY_REQUIRED` |
| 25～26 | `POST /submissions/status-query` | 用 POST 而不是 GET：查询码不进 URL、不进访问日志与浏览器历史 |
| 27～32 | `POST /partner-applications`，`201` | 与投稿同构，第 29 行同样先校验 `portfolioUrl` |
| 33～34 | `POST /partner-applications/status-query` | 同一套 `CaseQuery` |
| 35 | `requireSafeUrl` | 只对非空值调用 `ContentSourcePolicy.requireAllowed` |
| 36 | `requestId` | 从请求属性取，和 `ApiResponse` 一起回给前端 |

两个容易读漏的细节：

1. **投稿类里有条件必填**：`COPYRIGHT` 必须带联系方式，这条规则不在 Controller，而在 `PublicCaseService` 第 40～42 行——因为它是业务规则，可能随审核政策变化。
2. **投稿可以不带联系方式**：`FEEDBACK`/`CONTENT` 允许 `contact` 为空，此时**不会**触碰密钥环（`PublicCaseService` 第 43 行只对非空联系方式做 `requireEnabled()`）。这条分支有单元测试钉住。

### 1.2 `CommunityController`（37 行）

文件：`apps/api/src/main/java/cn/voidhaven/api/web/CommunityController.java`

| 行 | 内容 | 关键点 |
|---:|---|---|
| 15 | `VerifyRequest(inviteCode 8～256)` | 邀请码长度下限 8，挡住空值与极短猜测 |
| 24～27 | `POST /community-gate/verify` | 把 `networkSubject.resolve(request)` 一起交给 Service——限流键在 Controller 层解析，Service 只认「已解析的主体」 |
| 29～32 | `GET /community/entries` | `X-Community-Grant` 是**可选**头：不带就只拿公开条目，带了就必须有效，否则 401 |
| 34～36 | `ok(...)` | 统一封装 `ApiResponse` |

对比一下这两个端点：`verify` 是**写入**（消耗邀请码额度、写 grant、写事件），`entries` 是**只读**。前者在 `PublicWriteRateLimitInterceptor` 的白名单里吗？不在——它只用 `community_gate_events` 的失败计数做保护，且**成功不消耗**。这一点在第 4 站要能说清楚。

### 1.3 另外三个消费门禁的入口

| 位置 | 端点 | 授权要求 |
|---|---|---|
| `ProjectJoinController` 第 21～27 行 | `GET /participation/project-join` | `X-Community-Grant` 可选 |
| `PublicContentController` 第 26 行 | `GET /content/{id}/gated-actions` | `X-Community-Grant` **必填**；只对 `kind=RECRUITMENT` + `visibility=PUBLIC` + `record_state=ACTIVE` 的内容放行；`operational_status != OPEN` 时返回**空列表**而不是 403 |
| `PublicMediaController` 第 30～35 行 | `GET /community/media/{id}` | `X-Community-Grant` **必填**；先 `requireGrant` 再读对象存储；响应 `Cache-Control: no-store` |

### 1.4 能力开关与响应缓存

| 位置 | 内容 | 要点 |
|---|---|---|
| `ProductCapabilitiesController` 第 29～36 行 | `GET /public/capabilities` | 返回 `privateContactEnabled`、`communityGateEnabled`、`gatedRecruitmentEnabled`、`statusQueryEnabled`、`projectJoinEnabled`。注意第 33～34 行：`gatedRecruitmentEnabled` 直接复用 `communityGateEnabled`，`statusQueryEnabled` **写死为 `true`** |
| `NoStoreResponseFilter` 第 20～23 行 | 对 `path.contains("/community")`、`path.endsWith("/status-query")`、`endsWith("/gated-actions")`、`equals("/api/v1/public/submissions")`、`equals("/api/v1/public/partner-applications")` 加 `Cache-Control: no-store` | 用 `contains("/community")` 会把 `/api/v1/public/community/...` 全部覆盖，属于"宽了一点但方向正确"的写法 |

过滤器顺序要注意一个容易讲错的点：`NoStoreResponseFilter` 显式声明 `@Order(HIGHEST_PRECEDENCE + 10)`，所以它排在安全过滤链（`springSecurityFilterChain`，顺序 `-100`）**之前**；而 `RequestIdFilter` **没有指定 `@Order`**，默认落到最低优先级，也就是排在安全链**之后**、`DispatcherServlet` 之前。对 `/public/**` 这类放行路径没有差别（控制器里一定能读到 requestId 属性），但"被安全链拒绝的请求会不会带 `X-Request-Id`"这件事，需要你在真实栈上用一条 401/403 请求确认——VH-03 手册里对此有一处观察记录，值得对照复核。

读完自答：

1. `/public/capabilities` 把 `statusQueryEnabled` 写死为 `true`。这在什么情况下会说谎？前端据此隐藏/显示入口会造成什么体验问题？
2. `status-query` 为什么用 POST 而不是 `GET /submissions/{id}?code=...`？列出至少三条理由。
3. `gated-actions` 在内容不是 `OPEN` 时返回空列表、在内容不可见时返回 404。这两种"什么都没有"的区别是什么？面试里怎么说清？
4. `NoStoreResponseFilter` 用的是"路径包含 `/community`"。如果未来新增 `/api/v1/public/community-faq`，会有什么副作用？

## 第 2 站：写入路径的四个横切机制（本链路最重要的一站）

四个机制各自解决一个不同的问题，**不要混为一谈**：

| 机制 | 解决的问题 | 代码位置 | 默认参数 |
|---|---|---|---|
| 幂等键 | 同一意图的重复提交 | `IdempotencyService` 第 42～61 行 | 键 ≤128；记录保留 24 小时 |
| 网络限流 | 不同人/不同网络的高频滥用 | `PublicWriteRateLimitInterceptor` 第 14～19、33～38 行；`RateLimitLockService` 第 36～62 行 | 写 8 次/小时；查 60 次/小时 |
| 来源策略 | 公开内容里混入不可核验链接 | `ContentSourcePolicy.requireAllowed` | 仅 HTTPS，且排除 localhost/私网/保留域 |
| 联系方式加密 | 私密数据落库与按需解密 | `PrivateValueCipher`、`PublicCaseService.storeContact` 第 100～106 行 | AES-256-GCM，AAD 绑上下文；联系方式保留 180 天 |

### 2.1 幂等：`execute` 与 `executeSingleDelivery` 的区别

`IdempotencyService` 里有两套语义，VH-06 用的是第二套：

| | `execute`（第 21～36 行） | `executeSingleDelivery`（第 42～61 行） |
|---|---|---|
| 响应是否可回放 | 可（`response_replayable = true`，回放原响应体） | **不可**（库里只存 `{"state":"DELIVERED_NO_REPLAY"}`） |
| 同键同体重放 | 返回首次结果 | `409 SENSITIVE_RESULT_NOT_REPLAYABLE` |
| 同键异体 | `409 IDEMPOTENCY_KEY_REUSED` | 同左 |
| 首次请求进行中 | `409 REQUEST_IN_PROGRESS` | 同左 |
| 谁在用 | 后台管理写操作 | **公开投稿、合作申请、邀请码签发** |

为什么公开投稿要用第二套：返回值里带**一次性查询码**（等价于密码）。如果允许回放，任何拿到幂等键的人（例如共享设备的浏览器历史、日志里的键）都能再取一次查询码。代价是：用户重复提交时必须自己保存好第一次的结果，否则只能由管理员兜底——这是有意的安全换可用性。

### 2.2 限流：桶、键与事务边界

| 要点 | 位置 | 说明 |
|---|---|---|
| 只拦 POST，且只拦四个精确路径 | `PublicWriteRateLimitInterceptor` 第 14～19、34 行 | 用 `Set.contains(requestURI)` 精确匹配——比前缀匹配更不容易误伤，但**路径末尾多一个 `/` 就会绕过拦截** |
| 注册方式 | `WebConfiguration` 第 15～17 行 | `registry.addInterceptor(publicWriteRateLimit)`，作用于全部 MVC 请求 |
| 两个桶分开 | `PublicWriteRateLimiter` / `PublicStatusQueryRateLimiter` | 注释写得很直白：查状态不能吃掉投稿额度 |
| 键是 HMAC 后的网络地址 | `PublicWriteRateLimiter.subjectHash` 第 33～40 行 | 库里看不到原始 IP |
| 计数与判断在同一把行锁里 | `RateLimitLockService` 第 36～48 行 | `lockSubject` → `count(*)` → `insert`，且整个块跑在 `REQUIRES_NEW` 事务里（第 33 行），业务回滚**不会**退还额度 |
| 主体怎么解析 | `ClientNetworkSubject.resolve` | 默认只看 `remoteAddr`；只有直连地址在受信网段内、且 `X-Forwarded-For` 是**单个纯数字地址**时才采用转发值；多跳（含逗号）直接回落 |

把这条链路和 VH-03 的登录限流并排看，会得到一句很有用的总结：**限流必须活在业务事务之外，否则"请求失败"会把"已经发生过的滥用"一起回滚掉。**

### 2.3 来源策略：什么样的链接算"可核验"

`ContentSourcePolicy.isAllowed` 的判定顺序（读源码时需要能背出来）：

1. 非空；
2. 本地 fixture 开关打开且等于 `public-base-url + /local-fixtures/source` → 放行（**只用于本地合成数据**）；
3. 否则必须 `https`，且 host 非空、不是 `localhost`、`.invalid`、`.test`、`.example`、`.local`、`.internal`；
4. 不能带 userinfo（`https://user:pass@host`）；
5. 字面量 IP 必须是公网地址（排除回环、私网、链路本地、组播、`169.254/16`、CGNAT、文档保留段等）。

它同时被**写入时**和**读取时**使用：`PublicInputController` 第 21/29 行校验用户输入，`ContentService` 在投影公开内容时会再校验一次历史数据——"写入期校验 + 读取期 fail-closed"这个组合是值得单独记的答辩点。

### 2.4 联系方式：能开、能存、能看、能删

| 环节 | 位置 | 说明 |
|---|---|---|
| 能不能收 | `PrivateValueCipher.requireEnabled()` | 开关关闭 → `503 PRIVATE_CONTACT_DISABLED`；**在幂等预留之前**就抛，不产生垃圾记录 |
| 怎么存 | `PublicCaseService.storeContact` 第 100～106 行 | 密文 `v1:base64(nonce‖ciphertext)`，`keyId` 单独一列，AAD = `<SUBJECT_TYPE>:<id>`（防止把 A 的密文挪到 B 上） |
| 怎么读 | `PublicCaseAdministrationService.reveal` | 只取未过期的记录，解密后**必须**写一条 `PRIVATE_CONTACT_REVEALED` 审计才返回 |
| 什么时候没了 | `RetentionService` 第 42 行 | `delete from private_contact_records where delete_after < now()`；`delete_after = 创建时间 + 180 天`（`PublicCaseService` 第 27 行） |
| 后台列表怎么知道有联系方式 | `PublicInputAdministrationService` 的 `contact_available` 子查询 | 只暴露"有/没有"这个布尔值，不暴露内容 |

读完自答：

1. 幂等键的**请求体哈希**是怎么算的？为什么"同键异体"要返回 409，而不是覆盖？
2. 限流的 `REQUIRES_NEW` 事务带来什么代价？在"业务成功但限流记录提交失败"的极端情况下会发生什么？
3. `ContentSourcePolicy` 为什么要在**读取时**再校验一遍？只靠写入时校验会漏掉哪类数据？
4. 如果用 `AES-256-GCM` 但不加 AAD，你会失去什么保证？

## 第 3 站：回执号与查询码——"我提交了，但我没有账号"

这是 VH-06 最容易被面试官追问的设计点：**没有账号体系的人，怎么安全地查自己的进度？**

文件：`apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseService.java`（121 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 24 | `record CreatedCase(id, queryCode)` | 只在创建响应的**那一次**返回 |
| 25 | `record CaseStatus(id, status, publicResult, dueAt, createdAt, updatedAt)` | 查询返回的是**公开投影**：没有 title/message/link、没有联系方式、没有内部备注 |
| 56～57 | `Ids.ulid()` + `SecureTokens.randomUrlToken()` | 回执号是 26 位 Crockford Base32 随机串（不是真 ULID：`Ids.ulid()` 用 `SecureRandom` 逐字符生成，没有时间戳前缀）；查询码是 32 字节随机 → 43 字符 base64url |
| 58～60 | insert 时写入 `SecureTokens.sha256(queryCode)` | 明文查询码**不落库** |
| 88～98 | `status(table, id, queryCode)` | 三个判定：行不存在 → 404；查询码为空 → 404；SHA-256 不相等（`constantTimeEquals`）→ 404。**三种失败返回同一个错误** |
| 89 | `"select ... from " + table + " where id = ?"` | 表名拼接。当前两个调用点都是编译期常量（第 80、85 行），不接受外部输入，所以安全；但这是"看起来危险"的写法，面试被追问时要能说清边界 |
| 60、72 | `due_at = now + 7 天` | 公开事项默认 7 天处理期限，后台可改可清 |
| 78～86 | 两个 `@Transactional(readOnly = true)` 查询入口 | 只读事务，不给写路径增加压力 |

凭据强度对照（这张表值得自己算一遍）：

| 凭据 | 长度 | 熵 | 库内存储 | 有效期 | 泄露后果 |
|---|---|---|---|---|---|
| 回执号 `id` | 26 字符 | 约 122 bit | 明文（主键） | 永久 | 单独泄露无影响，必须配查询码 |
| 查询码 `queryCode` | 43 字符 base64url | 256 bit | SHA-256 | 无（永久） | 可查该事项的公开进度 |
| 邀请码 `inviteCode` | 43 字符 | 256 bit | HMAC-SHA256 | 批次有效期（≤90 天） | 可兑换 grant，且**消耗一次共享额度** |
| 短期授权 `grant` | 43 字符 | 256 bit | SHA-256 | 默认 30 分钟 | 可在有效期内读全部受控入口 |

读完自答：

1. 查询码错了、回执号不存在、回执号存在但查询码错——为什么三者都返回 404 而不是 401/403？如果区分开，攻击者能获得什么？
2. 查询码永不失效，是设计缺陷还是合理取舍？你会怎么加"过期"而不破坏用户体验？
3. 为什么查询接口要用 `constantTimeEquals`？这里的时序攻击现实威胁有多大？
4. 如果让你把这条链路改成"可以查历史列表"，哪些数据必须先脱敏？

## 第 4 站：社群门禁发券——原子占额与失败计数

文件：`apps/api/src/main/java/cn/voidhaven/api/service/CommunityService.java`（310 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 46～62 | 构造函数 | 三项 fail-closed 校验：`grantMinutes ∈ [1,120]`；**production profile 下开启门禁必须配非本地 HMAC 密钥**；HMAC 密钥为空时回落本地开发值 |
| 64～65 | `@Transactional verify(...)` | 整个发券过程一个事务 |
| 66 | `requireOperational()` | = `requireEnabled()` + `privateValues.requireKeys()`。**注意：门禁也因此依赖私密值密钥环**（见 README 的 F-04） |
| 68 | `subjectHash = HMAC(hmacSecret, networkSubject)` | 失败计数的主体是网络地址的 HMAC |
| 69～74 | 失败计数与冷却 | 24 小时窗口内 ≥8 次失败**且**最近一次失败在 30 分钟内 → `429 COMMUNITY_GATE_COOLDOWN` |
| 75～76 | `update ... set use_count = use_count + 1 where code_hmac = ? and state='ACTIVE' and valid_from <= now and expires_at > now and use_count < max_uses` | **一条 SQL 完成"校验 + 占额"**，把并发安全性交给数据库行锁，而不是先 SELECT 再 UPDATE |
| 77～81 | 占额失败分支 | 写一条 `'FAILURE'` 事件后抛 `401 COMMUNITY_INVITE_INVALID`（见 README 的 F-01：这条 insert 很可能被同一个事务回滚） |
| 82～83 | 再查一次 `batchId` | 因为 `code_hmac` 唯一，这一步没有竞态 |
| 84～87 | 生成 grant | 明文 grant 只回给调用方，库里存 SHA-256 |
| 88～89 | 写 `'SUCCESS'` 事件 | 成功也留痕，24 小时过期 |
| 116～122 | `requireGrant` | 同时要求 grant 未过期**且**批次仍 `ACTIVE` 且批次未过期——这让"撤销批次"能立刻让已发出的 grant 失效 |
| 186～196 | `createInviteBatch` | 默认 30 天 / 500 次；`expiresInDays ∈ [1,90]`、`maxUses ∈ [1,10000]` |
| 202～206 | `inviteBatches` | 列表里把"已过期但仍标记 ACTIVE"显示成 `EXPIRED`（**只改展示，不改库**） |
| 208～214 | `revokeInviteBatch` | `state='REVOKED'` + `delete from community_access_grants where invite_batch_id = ?`；两步都在同一事务 |

并发场景要能自己讲出来：

```text
两个访客同时用同一个 maxUses=1 的邀请码
  线程 A: update ... where use_count < max_uses  → 影响 1 行，拿到额度
  线程 B: 同一行的 UPDATE 被 InnoDB 行锁挡住 → A 提交后重读最新值 → use_count=1 不满足 < 1 → 影响 0 行 → 401
```

关键点：**判定条件写在 UPDATE 的 WHERE 里**，所以不需要额外的乐观锁或分布式锁。对照写法（先 `select use_count`，再判断，再 `update`）在并发下会超发。

读完自答：

1. 为什么邀请码要在库里存 HMAC 而不是 SHA-256？（提示：43 字符随机码其实不需要加盐；想想"如果有人能读到库"和"密钥轮换"两个角度）
2. DR-002 写的是"校内社群入口的基础问答"允许 8 次错误，而代码实现是"批次共享邀请码"。这是设计改变还是规范漂移？两种方案各自防住了什么？
3. 冷却判定的条件是"24 小时内失败 ≥8 **且** 最近一次在 30 分钟内"。如果某人在 24 小时里分散地失败了 8 次、最后一次在 1 小时前，会发生什么？这个设计合理吗？
4. `revokeInviteBatch` 为什么要显式删除 grant，而不是只把批次标记为 `REVOKED`？（提示：看 `requireGrant` 的 JOIN 条件，再想想为什么要"双保险"）

## 第 5 站：门禁消费——同一份数据，三种投影

| 读取路径 | 代码位置 | 无 grant 时 | 有 grant 时 |
|---|---|---|---|
| 社群入口列表 | `CommunityService.publicEntries`（94～114 行） | SQL 追加 `and access_level = 'PUBLIC'` | 不追加条件，并在映射时解密 `COMMUNITY_GATED` 的值 |
| 项目组入口列表 | `ProjectJoinService.publicEntries` | 同上 | 同上（复用 `community.requireGrant`） |
| 招募受控行动 | `ContentActionService.gatedActions` 第 75～81 行 | 端点要求头必填，所以进不来 | 读 `published_revision` 里 `COMMUNITY_GATED` 的行动，且内容必须 `OPEN` |
| 受控二维码 | `PublicMediaController.readCommunity` 第 30～35 行 | 端点要求头必填 | `requireGrant` → `media.loadCommunity(id)` |

三个必须能解释清楚的设计选择：

1. **投影差异在 SQL 里，不在 Java 里**：无 grant 时受控行根本不会被查出来，不存在"查出来再过滤"的泄露风险。
2. **受控值用不同的 URL**：公开二维码走 `/public/media/{id}`，受控二维码走 `/public/community/media/{id}`。前者由内容的公开可见性决定，后者由 grant 决定；两条路径的授权来源完全不同。
3. **`mediaUrl` 在响应里就已经分好了**（`CommunityService` 第 108～110 行）：前端不需要自己判断该用哪个地址，也就不会拼错。

读完自答：

1. 为什么 `publicEntries` 里"要不要包含受控条目"这件事要放在 SQL 拼接，而不是 `if (includeGated && access.equals("COMMUNITY_GATED"))` 这样的 Java 过滤？
2. `ProjectJoinService` 直接复用 `CommunityService.requireGrant`。这个复用的好处与耦合风险分别是什么？
3. 受控二维码的读取要经过两次检查（`requireGrant` + `media.loadCommunity`）。第二次检查在防什么？

## 第 6 站：后台闭环与数据落点

### 6.1 后台入口（都在 `/api/v1/admin`，URL 层要求 `ADMIN|SUPER_ADMIN`）

| 端点 | 位置 | 状态机 |
|---|---|---|
| `GET /admin/submissions`、`POST /submissions/{id}/decision` | `AdminController` 第 110、285 行 | 简化决策：`APPROVE → RESOLVED`、`REJECT → REJECTED`（`PublicInputAdministrationService.targetState`） |
| `POST /submissions/{id}/transition`、`POST /submissions/{id}/contact` | `AdminController` 第 293、302 行 | 完整状态机 + `If-Match` 版本 + 联系方式解密 |
| `GET /admin/partner-applications`、`POST /partner-applications/{id}/approve` | `AdminController` 第 97、252 行 | 批准 = 建 `PENDING` 账号 + 合作方档案 + 一次性激活凭据，申请进入 `ACTIVATION_PENDING` |
| `POST /partner-applications/{id}/decision` | `AdminController` 第 260 行 | **只接受 `REJECT`**，其它决定返回 400 |
| `POST /partner-applications/{id}/transition` | `AdminController` 第 268 行 | 完整状态机，与投稿同构 |
| `/admin/community/**`（入口与邀请码） | `CommunityAdminController` 第 20 行 | 类级 `@PreAuthorize("hasRole('SUPER_ADMIN')")` |

这就是 README 里 F-05 的来源：**合作申请同时存在"完整状态机"和"只批准/只拒绝"两套入口**，两者的状态白名单分别写在 `PublicCaseStates.PARTNER` 与 `PartnerAccountProvisioningService` 的 SQL 里。

### 6.2 状态机

```text
content_submissions :  RECEIVED → IN_PROGRESS → WAITING_FOR_REQUESTER → RESOLVED / REJECTED / CLOSED
partner_applications:  RECEIVED → IN_REVIEW → WAITING_FOR_APPLICANT → APPROVED → ACTIVATION_PENDING → ACTIVATED
                                                                   → REJECTED / CLOSED
community_invite_batches: ACTIVE → REVOKED（终态）；显示层额外派生 EXPIRED
community_entries      : active=true → active=false（终态，不可复活，只能新建）
```

历史别名：`PENDING_REVIEW` 在公开事项里被映射为 `RECEIVED`（`PublicCaseStates.normalize`），迁移 `V13` 也做过同样的数据回填——**公开事项的状态与内容审核的 `PENDING_REVIEW` 是两套独立概念**，这是 VH-02 和 VH-06 的分界。

### 6.3 表与迁移

| 表 | 迁移 | 关键结构 |
|---|---|---|
| `content_submissions` | `V1`/`V3`/`V13` | `access_code_hash CHAR(64) UNIQUE NULL`、`state`、`due_at`、`public_result`、`version`、`resolved_at` |
| `partner_applications` | `V1`/`V13`/`V17` | 同上 + `provisioned_account_id`（与账号一对一） |
| `public_case_status_history` | `V13` | `case_type` + `case_id` + `from_state` → `to_state` + `public_message` + `actor_account_id` |
| `private_contact_records` | `V13`（`V3` 起名） | `subject_type/subject_id/ciphertext/key_id/delete_after` |
| `community_entries` | `V14` → `V20` | `V20` 把单一 `entry_type` 拆成 `presentation_type` + `contact_channel` + `access_level`（DR-005），并用生成列 `primary_slot` 保证"全局只有一个主入口" |
| `community_invite_batches` | `V14` | `code_hmac UNIQUE`、`valid_from/expires_at`、`max_uses/use_count` + CHECK 约束 |
| `community_access_grants` | `V14` | `grant_hash UNIQUE`、`anonymous_subject_hash`、`expires_at` |
| `community_gate_events` | `V1` | `outcome`（FAILURE/SUCCESS）+ `expires_at`（24 小时） |
| `public_write_rate_limit_events` / `public_status_query_rate_limit_events` | `V11` / `V17` | 按主体哈希计数，靠 `expires_at` 过期 |
| `project_join_entries` | `V24` | 与 `community_entries` 同构，但**不设唯一主入口** |

## 收束：把这六站压成三句话

- **进来要凭据**：公开写用幂等键（一次性、不可回放）+ 网络限流 + 来源策略，联系方式加密入库并按 180 天到期删除。
- **查要凭据**：回执号 + 查询码，库内只存哈希、比较用常量时间、失败一律 404，不给出"存在性预言机"。
- **出去要凭据**：批次共享邀请码 → 一条 SQL 原子占额 → 30 分钟短期 grant；受控数据在 SQL 层就与公开数据分开投影，撤销批次即刻生效。
