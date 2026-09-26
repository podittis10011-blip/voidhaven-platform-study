# VH-02 题单：费曼理解输出 + 秋招面试追问链

使用方式：先合上 `01`～`03`，按题序闭卷作答并写下来（不是心里想一遍）。全部答完后，再向后对照文末的折叠参考答案。

题目来源标注：`代码` = 本轮源码阅读，`风险` = 测试盲区或设计取舍，`面经` = 公开面经常见问法，`边界` = 官方能力边界（HTTP 语义 / JPA / Spring 事务）。

---

## 第一层：费曼理解输出

### F1 为什么不能把内容发布压缩成一个 `published=true/false`

- 答题范围：四个状态维度 + 两个修订指针。
- 可选线索：到期但没被隐藏；隐藏但不等于删除；审核通过但还没发布。
- 预期深度：一张 4 行表格 + 每个维度举一个「和别的维度不同步」的场景。约 200 字。来源：`代码`

### F2 「编辑已发布内容」和「编辑草稿」，代码里走的不是同一条路

- 答题范围：`revise` 的两条分支。
- 可选线索：`content_revisions` 的行数会不会变；`audit_logs` 里两个不同的 action 名。
- 预期深度：说清触发条件、各自改了哪张表、为什么必须这样分。约 250 字。来源：`代码` + `风险`

### F3 为什么恢复归档内容后不会自动重新公开

- 答题范围：`restore()` 的字段联动的合法性来源。
- 可选线索：`restore()` 只设了 `APPROVED`；谁负责把 `APPROVED` 变成 `PUBLIC`。
- 预期深度：说清「恢复 = 回到可再次走发布流程的起点」。约 120 字。来源：`代码`

### F4 `If-Match`、`@Version`、`Idempotency-Key` 三者分别在防什么

- 答题范围：三个机制的输入、比较对象、失败码。
- 可选线索：一个防丢更新，一个防重放，一个只是载体。
- 预期深度：表格 + 一句话区分「并发」和「重复」。约 250 字。来源：`面经` + `边界`

### F5 `revise` 里为什么同时用悲观锁和版本校验

- 答题范围：`lockedOwned` 与 `expectedVersion` 比对的先后顺序。
- 可选线索：不加锁时两个并发请求会不会算出同一个 `revision_number`；锁能不能识别「用户拿的是旧页面」。
- 预期深度：说清两道防线各自负责的失败模式，并指出哪一道返回给用户更友好。约 250 字。来源：`代码` + `边界`

### F6 写接口返回的 `revision` 为什么可能比数据库里小 1

- 答题范围：`@Transactional` + 脏检查 + `saveAndFlush`。
- 可选线索：哪些方法有 `saveAndFlush`，哪些没有；`managed(item)` 读的是什么。
- 预期深度：给出实测数字（`publish` 响应 3 / 重读 4），并说明前端在什么情况下才会被这个差异咬到。约 250 字。来源：`风险`

### F7 审核「退回」和「拒绝」为什么必须写原因，而「批准」可以不写

- 答题范围：Controller DTO 校验与 Service 校验的分工。
- 可选线索：为什么 `hide` 的空白原因得到的是 `VALIDATION_FAILED` 而不是 `ACTION_REASON_REQUIRED`。
- 预期深度：说清「校验层次」这个概念，并给出两个不同的错误码例子。约 200 字。来源：`代码` + `面经`

### F8 公开页面为什么读 `publishedRevision` 而不是 `currentRevision`

- 答题范围：VH-01 与 VH-02 的衔接点。
- 可选线索：候选修订进入 `PENDING_REVIEW` 时，公开页应该看到什么。
- 预期深度：结论 + 一个反例（如果用 `currentRevision` 会怎样）。约 150 字。来源：`代码`

### F9 到期关闭为什么要查明细表，而不是查 `details_json`

- 答题范围：投影表存在的理由。
- 可选线索：`ContentLifecycleService.hasPassedDeadline` 的两条分支（`deadlineAt` / `deadlineDate`）。
- 预期深度：说清「查询/排序/约束」与「文档存储」的分工，并提到时区处理。约 200 字。来源：`代码` + `边界`

### F10 为什么在 `publish` 里把送审时已经做过的校验再做一遍

- 答题范围：`publish` 的三道复查。
- 可选线索：送审到发布之间，媒体、行动入口、合作方配额都可能变。
- 预期深度：说清「发布是最后一道公开安全边界」。约 150 字。来源：`代码`

---

## 第二层：秋招面试模拟追问链

规则：每条链先给 30 秒结论，再展开 3 分钟；追问链要能连续接住。

### C1 「你们平台的内容是怎么发布的？」

30 秒要求：一句话说明状态机 + 双指针 + 审核不可绕过。
3 分钟要求：`DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED`，说明每一步的前置条件、改动的字段、失败时的错误码。
追问链：

1. 谁能从 `DRAFT` 直接到 `PUBLISHED`？为什么不行？
2. 送审的人自己能批准吗？代码里怎么体现？
3. 发布后再隐藏，公众看到什么？
4. 隐藏和归档有什么区别？恢复之后会怎样？
5. 已经到期的竞赛内容，`workflowStatus` 是什么？

### C2 「已发布的内容要改标题，但线上不能中断，你怎么做？」

30 秒要求：新建候选修订，`publishedRevision` 不动，审核发布后才切换指针。
3 分钟要求：说明 `revise` 的两条分支、`cloneCurrent` 复制了什么、`content_revisions` 的行数和 `revision_number` 的变化、公开页在候选期间的返回。
追问链：

1. 为什么不能就地更新那条修订行？
2. 候选修订的图片、行动入口、筛选字段怎么办？
3. 反复保存草稿会不会产生一堆修订行？数据库里怎么验证？
4. 候选期间运营又改了一次已发布版，会发生什么？
5. 这套「双指针」有没有代价？代价是什么？

### C3 「两个人同时改同一份内容，怎么保证不互相覆盖？」

30 秒要求：`If-Match` 乐观锁给用户可理解的冲突，悲观锁给服务器串行的写入，`Idempotency-Key` 管重放。
3 分钟要求：讲清 `expectedVersion` 比对发生在锁之后、旧版本返回 409、冲突时前端保留输入；再说幂等键的哈希与重放规则。
追问链：

1. 只加 `If-Match` 够不够？为什么还要锁？
2. 只加锁够不够？为什么还要 `If-Match`？
3. 同一 `Idempotency-Key` 换了请求体会怎样？
4. 幂等记录里存的响应体是什么？为什么敏感结果不能重放？
5. 你会怎么用测试证明「真的并发」？

---

## 参考答案（请先闭卷作答再看）

<details>
<summary>F1 为什么不能只用 published 布尔值</summary>

| 维度 | 值 | 不同步的例子 |
|---|---|---|
| `workflowStatus` | DRAFT / PENDING_REVIEW / CHANGES_REQUESTED / APPROVED / PUBLISHED / REJECTED / CLOSED | 审核已通过（APPROVED）但还没发布，公众看不到 |
| `publicationStatus` | NONE / ACTIVE / CLOSED | 竞赛到期后 `CLOSED`，但内容可能仍然公开可见 |
| `visibility` | PUBLIC / HIDDEN | 被隐藏时不等于删除，也不等于过期 |
| `recordState` | ACTIVE / ARCHIVED / DELETED | 归档会自动隐藏；隐藏不一定归档 |

补充：`publicationStatus` 是 V2 迁移才加的（`V2__add_publication_status.sql`），注释明说它描述「已发布修订的生命周期，与下一个候选修订相互独立」。

常见误区：把 `CLOSED` 说成 `recordState` 的值。实际上 `CLOSED` 同时出现在 `WorkflowStatus` 与 `PublicationStatus` 两个枚举里，考试时要说清你说的是哪一个。
</details>

<details>
<summary>F2 两条 revise 分支</summary>

触发条件在 `ContentManagementService.java:146`：`createCandidate = item.getWorkflowStatus() == WorkflowStatus.PUBLISHED`。

- 是 PUBLISHED：`nextRevision = previous.getRevisionNumber() + 1`，`saveAndFlush` 一条新 `content_revisions` 行（第 148-154 行），复制正文引用到的媒体、写投影、复制行动入口，然后把 `currentRevision` 指向新行。审计 `CONTENT_REVISION_CREATED`。
- 不是 PUBLISHED（DRAFT / CHANGES_REQUESTED）：`revision = previous`，直接 `updateDraft(...)` 覆盖同一行（第 155-165 行），再用 `replaceRevisionProjections` 先删后写投影。审计 `CONTENT_DRAFT_SAVED`。

两条分支最后都调用 `beginCandidateRevision()`（第 166 行）把 `workflowStatus` 拉回 DRAFT，并且都不碰 `publishedRevision`。

实测证据：`CONTENT_REVISION_CREATED = 1`、`CONTENT_DRAFT_SAVED = 3`；`content_revisions` 里该内容只有 2 行。

常见误区：以为「保存草稿」也会新增修订行。只有第一次从 PUBLISHED 派生候选才会。
</details>

<details>
<summary>F3 恢复后为什么不自动公开</summary>

`ContentItem.restore()`（第 60 行）一次性设置：`recordState = ACTIVE`、`visibility = HIDDEN`、`workflowStatus = APPROVED`、`publicationStatus = NONE`、`archivedAt = null`。

它刻意把状态放在「已批准但未发布、且隐藏」，等于把这条内容送回发布流程的入口；真正对外公开只能由 `publish()` 完成，而 `publish()` 会重新检查媒体一致性、招募行动入口、合作方配额。

实测：restore 后公开详情 404；直接 `show` 返回 409 `CONTENT_NOT_SHOWABLE`（因为 `publicationStatus == NONE`）；重新 `publish` 后公开详情恢复 200。

常见误区：说「恢复等于重新公开」。恢复只是恢复记录本身。
</details>

<details>
<summary>F4 三个机制的分工</summary>

| 机制 | 输入 | 比较对象 | 失败结果 | 防什么 |
|---|---|---|---|---|
| `If-Match` | 客户端上次读到的实体版本 | `content_items.version` | 409 `CONTENT_VERSION_CONFLICT` | 丢更新（把别人的修改静默覆盖） |
| `@Version`（JPA） | —— | 数据库里的 version 列 | `ObjectOptimisticLockingFailureException` → 同上 409 | 同一实体被并行修改 |
| `Idempotency-Key` | 客户端生成的请求标识 | `idempotency_records` 的键 + 请求体哈希 | 409 `IDEMPOTENCY_KEY_REUSED` | 重放（重复扣减、重复创建） |

一句话区分：`If-Match` 防「并发」，`Idempotency-Key` 防「重复」。

注意：`@Version` 不是「内容版本号」，真正的第几版是 `content_revisions.revision_number`。实测里 `version = 16` 而 `revision_number` 只到 2，两者完全独立。
</details>

<details>
<summary>F5 为什么锁和版本校验都要</summary>

先看顺序：`ContentManagementService.java:139` 先 `lockedOwned`（`findByIdForUpdate`，`select ... for update`），第 140 行才比较 `expectedVersion`。

两道防线负责不同的失败模式：

- 悲观锁负责「服务器内部的并发写」。两个请求同时对同一 PUBLISHED 内容做修改时，若不加锁，两者都会基于 `previous.getRevisionNumber()` 算出同一个新修订号，第二条 `saveAndFlush` 会去撞 `UNIQUE (content_item_id, revision_number)`，返回的是数据库完整性冲突，而不是可解释的业务冲突。代码注释就写在第 134-138 行。
- 版本校验负责「用户手里的页面过期了」。锁只能保证串行，不能知道调用者看的是哪一版；`expectedVersion` 不匹配时直接返回 409，用户可以刷新后重试，也不会覆盖别人刚做的修改。

补充：`decide` 只做版本校验不加锁，因为它不需要新建修订号，只需要「不基于过期状态做决定」。
</details>

<details>
<summary>F6 响应里的 revision 为什么滞后</summary>

`ContentManagementService.Managed` 里的 `revision` 直接来自 `item.getVersion()`（第 425 行）。而 `submit` / `decide` / `publish` / `hide` / `show` / `archive` / `restore` 这些方法在返回 DTO 之前**没有** `saveAndFlush`，实体的 `@Version` 要等事务提交时由 Hibernate 递增。

实测：

- `publish` 响应 `revision=3`，紧接着 `GET /admin/content-items/{id}` 得到 `4`。
- `APPROVE` 响应 `revision=2`，重读得到 `3`。
- `create` / `revise` 里有 `saveAndFlush`，所以响应是新鲜值。

前端影响：`workspace.tsx:1631-1635` 用 `update(updated)` 把响应写回列表行。如果用户在同一行上连续做两次需要 `If-Match` 的动作，第二次就会用滞后值 → 误报冲突。当前 UI 流程里「送审」之后要跳到审核页重新加载，恰好避开了这个组合；所以 e2e 用 mock 数据（版本固定 7）也发现不了。

可提的修法：让这些方法统一 `saveAndFlush` 后再构造 DTO，或在 Controller 返回前重新读一次。
</details>

<details>
<summary>F7 校验层次与两类错误码</summary>

校验分三层：

1. Bean Validation（Controller 层，进 Service 之前）：`ReviewDecision.decision` 有 `@Pattern`，`ActionReason.reason` 有 `@NotBlank`。违反时统一 400 `VALIDATION_FAILED` + `fieldErrors`。
2. Service 里的业务校验：`require(...)`、`requireReason(...)`。违反时 400/409 + 具体业务码。
3. 数据库约束：唯一键、外键。违反时由 `ApiExceptionHandler` 转成 409 `DATA_INTEGRITY_CONFLICT`。

所以：

- 审核「退回/拒绝」不写原因 → 400 `REVIEW_REASON_REQUIRED`，因为 `ReviewDecision.reason` 只声明了 `@Size(max=1000)`，空值能进到 Service。
- `hide`/`archive`/`restore` 不写原因 → 400 `VALIDATION_FAILED`，因为 `ActionReason.reason` 已经有 `@NotBlank`，`ContentManagementService.java:448` 的 `ACTION_REASON_REQUIRED` 从这些入口根本到不了。

常见误区：背了一堆业务错误码，却说不清哪些其实是 Bean Validation 拦下来的。
</details>

<details>
<summary>F8 公开读路径为什么用 publishedRevision</summary>

公开查询的仓储方法全部带 `publishedRevision is not null and visibility = 'PUBLIC' and recordState = 'ACTIVE'`（`ContentItemRepository.java:10-32`），前台展示的标题/摘要/正文都从 `publishedRevision` 取。

如果改成 `currentRevision`：运营把一份已发布内容拉进编辑器改成候选修订的瞬间，公众就会看到未审核的草稿，审核这一层形同虚设。

实测：候选修订处于 DRAFT、PENDING_REVIEW 期间，`GET /api/v1/public/resources/{id}` 仍返回旧标题；重新发布后立刻变成新标题。
</details>

<details>
<summary>F9 到期判断为什么查明细表</summary>

`ContentLifecycleService.hasPassedDeadline`（第 82-91 行）按类型查 `competition_revision_details` / `recruitment_revision_details`，读 `deadline_at` 与 `deadline_date` 两列。

理由：

- 明细表列是强类型（`TIMESTAMP(6)` / `DATE`），可以直接比较、可以建索引，JSON 文档做不到。
- `deadlineDate` 是「自然日」语义，比较时统一换算到 `Asia/Shanghai` 的日期（第 90 行），避免用 UTC 判定出错一天。
- `details_json` 仍然保留原始文档，用于回显和审计，而不是用于调度判断。

补充：`ContentLifecycleService` 只处理 COMPETITION 与 RECRUITMENT，`RESOURCE` 直接返回 false（第 83 行）；招募关闭后不会被自动归档，因为第 76 行的归档分支显式排除了 RECRUITMENT。
</details>

<details>
<summary>F10 发布前为什么复查</summary>

`publish`（第 205-218 行）在状态校验之后还要：

1. `requireCurrentMediaMatchesDocument`：送审后正文或媒体可能被改动过。
2. 招募必须有有效行动入口（`contentActions.hasEnabledAction`）：送审时合法，不代表发布时还存在。
3. `requirePartnerPublicationCapacity`：合作方配额与档案状态可能在这期间变化；该方法还会 `findByIdForUpdate` 锁住合作方档案。

核心论点：送审是编辑流程的检查点，发布是对外暴露的最后一刻。判定合格的代码不会因为「之前查过」就跳过公开前的复查。
</details>

<details>
<summary>C1～C3 口述要点补丁</summary>

C1 容易被追问倒的点：

- `publish` 只接受 `APPROVED`，所以「草稿直接发布」是 409 而不是 400（状态前置条件属于冲突，不属于参数错误）。
- 审核用的是 `POST /admin/reviews/{id}/decision`，不是内容自身的接口，因为审核记录要落到 `review_records` 里。
- 到期后 `workflowStatus` 仍是 `PUBLISHED`，变的是 `publicationStatus = CLOSED`（招募还会把 `operationalStatus` 设为 `EXPIRED`）。

C2 容易被追问倒的点：

- 候选修订会复制行动入口（`revise` 第 153 行调用 `contentActions.copyActions`），但只复制新正文引用到的媒体（`copyReferencedMedia`）。
- 修订号用 `previous.getRevisionNumber() + 1`，不是 `max+1`；后者是 `cloneCurrent` 的做法。两者在「历史修订号有空洞」时结果不同。
- 代价：一条内容可能留下多份不可变历史，媒体要按修订维护引用，公开读每次都要连 `publishedRevision`。

C3 容易被追问倒的点：

- `Idempotency-Key` 的哈希是**请求体**的 SHA-256，所以同键不同体是 409 `IDEMPOTENCY_KEY_REUSED`。
- 一次性敏感结果（比如账号激活凭据）走 `executeSingleDelivery`，重复请求返回 `SENSITIVE_RESULT_NOT_REPLAYABLE`，不会把凭据再吐一次。
- 证明并发要用多线程/多会话，且在 `DistributedSafetyIT` 那种 Testcontainers 环境里跑；「连续快速点击」不算并发证据。
</details>
