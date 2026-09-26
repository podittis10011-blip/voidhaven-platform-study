# VH-02 源码阅读路线（8 站）

阅读目标不是「知道有生命周期」，而是在任何时候都能回答这三个问题：

1. 这个行为写在**哪个类的哪一行**？
2. 它改了**数据库里的哪几个字段**？
3. 它依赖**哪个前置条件**，不满足时抛哪个错误码？

约定：所有行号对应 `D:\Projects\voidhaven\voidhaven-platform` 当前工作树（HEAD `be219ac` + 61 项暂存改动）。VH-02 相关代码与 HEAD 一致，见 `README.md` §2。

---

## 第 0 站：先把模型画出来（不开编辑器）

目标：不查资料写出下面这张表，再打开代码核对。

| 维度 | 字段 | 取值 | 回答的问题 | 由谁改 |
|---|---|---|---|---|
| 流程状态 | `workflowStatus` | DRAFT / PENDING_REVIEW / CHANGES_REQUESTED / APPROVED / PUBLISHED / CLOSED / REJECTED | 候选修订审核到哪一步 | `ContentItem` 状态方法 |
| 发布状态 | `publicationStatus` | NONE / ACTIVE / CLOSED | 已经发布的那一版是否仍在有效期 | `publish()` / `closePublic()` |
| 可见性 | `visibility` | PUBLIC / HIDDEN | 现在是否允许公众看到 | `publish()` / `hide()` / `show()` |
| 记录状态 | `recordState` | ACTIVE / ARCHIVED / DELETED | 这条业务记录还活着吗 | `archive()` / `restore()` / `markDeleted()` |
| 当前修订 | `currentRevision` | 修订行 ID | 后台编辑器正在读写哪一版 | `revise()` / `cloneCurrent()` |
| 已发布修订 | `publishedRevision` | 修订行 ID | 公众实际读到哪一版 | 只有 `publish()` 会改 |
| 乐观锁版本 | `version` | 自增 long | 调用者手里的是不是过期页面 | JPA `@Version` |

关键判断（先答再往下读）：

- 为什么「隐藏」不改 `workflowStatus`？
- 为什么「到期」只改 `publicationStatus`？
- 为什么「归档后恢复」不能直接变回 PUBLIC？

---

## 第 1 站：数据模型与不可变约束

读这些位置：

| 位置 | 看什么 |
|---|---|
| `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:12-24` | 四个状态维度、两个修订外键、`@Version` |
| `.../domain/ContentItem.java:49-61` | 9 个状态方法，每个方法只做「同时改哪几个字段」这一件事 |
| `.../domain/WorkflowStatus.java:2` | 枚举 7 个值，注意 `CLOSED` 也在这里（但发布到期走的是 `publicationStatus`，不要混淆） |
| `.../domain/PublicationStatus.java:4` | 注释直说：「当前已发布修订的生命周期，与下一个候选修订互相独立」 |
| `.../domain/ContentRevision.java:9-30` | 修订是**不可变快照 + 一个可变草稿位**：`updateDraft` 只允许改当前草稿 |
| `.../domain/ContentRevision.java:62-73` | `updateDraft(...)` 一口气覆盖 17 个字段，说明「改草稿」= 就地覆盖同一行 |
| `apps/api/src/main/resources/db/migration/V1__initial_schema.sql:42-61` | `content_items` 表结构 + 三个 CHECK 约束 |
| `.../V1__initial_schema.sql:62-79` | `content_revisions` 表，重点第 76 行 `UNIQUE (content_item_id, revision_number)` |
| `.../V1__initial_schema.sql:80-81` | 两个修订指针是外键，指向 `content_revisions(id)` |
| `.../V1__initial_schema.sql:145-156` | `review_records`：审核决定是**独立表**，不是给 item 打标记 |
| `.../db/migration/V2__add_publication_status.sql:1-10` | `publication_status` 是后加的，回填规则：有发布修订且记录活跃 → ACTIVE |

读完自答：

1. `content_items` 与 `content_revisions` 是几对几？外键方向说明了什么？
2. `UNIQUE (content_item_id, revision_number)` 这条约束会在什么并发场景下被撞到？
3. 为什么 `submittedAt` 一直留在 `content_items` 上，而不是记在修订表里？

只靠读代码回答不了、必须实验的点：

- 前后端都叫「版本」的东西其实有两个：`revision` 是实体乐观锁版本，`revisionNumber` 是修订序号。用 `03` 的数据库对照确认它们确实会分叉。

---

## 第 2 站：HTTP 入口与三个横切关注点

读这些位置：

| 位置 | 看什么 |
|---|---|
| `.../web/AdminController.java:146-151` | `POST /admin/content-items` 创建：只要求 `Idempotency-Key` |
| `.../web/AdminController.java:153-160` | `PATCH /admin/content-items/{id}` 修改：`Idempotency-Key` + `If-Match`，且把 `id`/`version`/`draft` 一起放进幂等请求体 |
| `.../web/AdminController.java:162-167` | 送审：`Idempotency-Key`，**不需要** `If-Match` |
| `.../web/AdminController.java:169-176` | 审核：`Idempotency-Key` + `If-Match`，决定值受 `@Pattern` 限制 |
| `.../web/AdminController.java:186-191` | 发布：只需要 `Idempotency-Key` |
| `.../web/AdminController.java:193-205` | 隐藏（要原因）/ 重新公开（不要原因） |
| `.../web/AdminController.java:207-219` | 归档 / 恢复，都要求 `ActionReason` |
| `.../web/AdminController.java:235-242` | 招募业务状态：`If-Match` + 状态 + 原因 |
| `.../web/AdminController.java:244-250` | 删除：`@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| `.../web/PartnerController.java:82-115` | 合作方同一套动作，但调用 Service 时多传 `profile.getId()` 做归属校验 |
| `.../config/SecurityConfig.java:29-30` | CSRF 只有 4 个豁免入口；`/api/v1/public/**`、`/admin/**`、`/partner/**` 的权限边界 |

读完自答：

1. 哪些动作需要 `If-Match`，哪些不需要？请说出背后的判断标准（提示：只有「基于某次读取做决定」才需要）。
2. Controller 有没有做状态判断？如果没有，它做的是什么？
3. `idempotency.execute(key, Map.of("id", id, "version", expected, "draft", draft), ...)` 里为什么要塞 `version` 和 `draft`？

必须实验的点：

- 把 `If-Match` 换成上一轮的旧值，观察 `409 CONTENT_VERSION_CONFLICT` 的 `message` 在「审核」和「编辑」两个接口里是不是同一句话（实测不是，见 `03` R10/R11）。

---

## 第 3 站：状态机主链（`ContentManagementService`）

先看整体：

```text
create(105) → submit(122) → decide(174) → publish(205) → hide/show/archive/restore(220-224)
                              ↑
                          revise(131) 会产生候选修订并回到 DRAFT
```

| 位置 | 看什么 | 关键点 |
|---|---|---|
| `.../service/ContentManagementService.java:105-120` | `create` | 顺序：校验 → 存 item（拿到聚合根）→ 存第 1 个修订 → 写投影 → 回填 `currentRevision` → 审计。注意有**两次** `saveAndFlush` |
| `.../ContentManagementService.java:122-129` | `submit` | 只有 `DRAFT`/`CHANGES_REQUESTED` 可送审；送审前必须 `requireCurrentMediaMatchesDocument` |
| `.../ContentManagementService.java:131-172` | `revise` | 本轮重点，见第 4 站 |
| `.../ContentManagementService.java:174-191` | `decide` | 版本校验 → 状态校验 → 原因校验 → 三种决定 → `recordReview` + 通用审计 |
| `.../ContentManagementService.java:193-203` | `reopen` | 只允许 `REJECTED` 且 `ACTIVE`，必须有原因 |
| `.../ContentManagementService.java:205-218` | `publish` | 先 `findByIdForUpdate` 加锁，再复查媒体、招募行动入口、合作方配额 |
| `.../ContentManagementService.java:220-224` | 隐藏/显示/归档/恢复/删除 | 每个方法都是「`require(...)` 前置断言 + 实体方法 + 审计」三件套 |
| `.../ContentManagementService.java:449-457` | 配额与断言 | `requirePartnerPublicationCapacity` 用 `findByIdForUpdate` 锁合作方档案；`require(...)` 统一抛 409 |

读完自答：

1. `publish` 为什么要在已经批准之后再检查一次媒体、行动入口和配额？送审时不是查过了吗？
2. `hide` 的前置条件是 `publishedRevision != null && visibility == PUBLIC && recordState == ACTIVE`，为什么必须有第一个条件？
3. 为什么 `delete` 是 `markDeleted()` 而不是删除行？这对 VH-01 的公开查询意味着什么？

必须实验的点：

- `restore()` 把 `workflowStatus` 设成 `APPROVED`、`publicationStatus` 设成 `NONE`，此时 `show` 会拒绝，而 `publish` 会接受。实测见 `03` L4/E6/L5。

---

## 第 4 站：双修订到底在哪里分叉（本轮重点）

同一个业务动作「编辑已发布内容」，代码里有**两条**实现路径：

路径 A：`ContentManagementService.revise`（`PATCH /admin/content-items/{id}`）

| 位置 | 看什么 |
|---|---|
| `.../ContentManagementService.java:139` | 用 `lockedOwned` 而不是 `find`/`owned`，注释在第 134-138 行解释了原因 |
| `.../ContentManagementService.java:140` | 版本不符 → `CONTENT_VERSION_CONFLICT` |
| `.../ContentManagementService.java:141-142` | `ACTIVE` 且（DRAFT / CHANGES_REQUESTED / PUBLISHED）才可编辑 |
| `.../ContentManagementService.java:144-146` | 取 `previous = currentRevision`；`createCandidate = (workflowStatus == PUBLISHED)` |
| `.../ContentManagementService.java:148-154` | **分支 1**：新建修订行、复制媒体引用、写投影、复制行动入口、把 `currentRevision` 指向新修订 |
| `.../ContentManagementService.java:155-165` | **分支 2**：就地 `updateDraft` 覆盖同一个修订行，并 `replaceRevisionProjections` |
| `.../ContentManagementService.java:166-170` | 两条分支最后都 `beginCandidateRevision()`（回到 DRAFT），审计动作名不同 |

路径 B：`ContentActionService.replace`（`PUT /admin/content-items/{id}/actions`，只对招募）

| 位置 | 看什么 |
|---|---|
| `.../service/ContentActionService.java:89-92` | DRAFT / CHANGES_REQUESTED / PUBLISHED 才能改行动入口 |
| `.../service/ContentActionService.java:97-100` | `candidateCreated = (status == PUBLISHED)`；是则调用 `candidates.cloneCurrent(item, actorId)` |
| `.../service/ContentActionService.java:106-112` | 删除/写入的是 `target` 的 action，**已发布那一版的 action 不动** |
| `.../service/ContentRevisionCandidateService.java:23-53` | 克隆实现：修订号取 `max(revision_number)+1`（第 25-28 行），再逐表复制类型明细、筛选投影、关系、媒体、行动入口 |

对照阅读的价值：

- 路径 A 只复制**新正文引用到的**媒体（`copyReferencedMedia`，第 380-392 行），并复制行动入口（第 153 行）。
- 路径 B 复制**全部**投影（第 39-44 行），语义是「先把旧版完整搬过来，再整表替换」。
- 两条路径都会让 `workflowStatus` 回到 DRAFT，但都**不碰** `publishedRevision`、`publicationStatus`、`visibility`。

读完自答：

1. 为什么编辑已发布内容必须新建修订行，而不能就地覆盖？如果就地覆盖会破坏什么？
2. 路径 A 的 `nextRevision = previous.getRevisionNumber() + 1` 和路径 B 的 `max(revision_number) + 1` 有区别吗？在什么数据形态下会不一样？
3. 已在草稿状态时反复保存，`content_revisions` 会不会变多？数据库里该看哪一列验证？

必须实验的点：

- 实测证明「PUBLISHED 时编辑」产生新修订行，「DRAFT 时反复保存」不产生。证据是审计动作计数 `CONTENT_REVISION_CREATED = 1`、`CONTENT_DRAFT_SAVED = 3`，见 `03` §4。

---

## 第 5 站：校验与投影（状态对了，数据对不对）

| 位置 | 看什么 |
|---|---|
| `.../ContentManagementService.java:312-327` | `prepare`：模块必须存在且启用、模块不可变、`schemaVersion` 冲突检测、Markdown 解析、富文本白名单、语言归一化、原创/外部来源分支 |
| `.../ContentManagementService.java:329-351` | `validate`：必填、原始标题、转载/改编许可取值、模块元数据、来源 URL 策略、截止时间格式、按类型追加规则 |
| `.../ContentManagementService.java:353-371` | `writeRevisionProjections`：把 `details` 拆进 `revision_filter_values` + `revision_content_relations` |
| `.../ContentManagementService.java:373-378` | `writeTypedDetails`：三种类型各自的明细表 |
| `.../ContentManagementService.java:380-399` | 媒体引用：新建修订时按新正文挑，草稿保存时整表重建 |
| `.../ContentManagementService.java:400-407` | `replaceRevisionProjections`：先删后写，保证草稿反复保存不会累积脏投影 |
| `.../ContentManagementService.java:417-424` | `requireCurrentMediaMatchesDocument`：正文引用集合必须与 `revision_media` 集合**完全相等** |
| `apps/api/src/main/java/cn/voidhaven/api/service/ContentSourcePolicy.java:33-50` | 来源 URL 白名单：必须 HTTPS，且排除 localhost、`.invalid/.test/.example/.local/.internal`、私网与保留地址 |
| `.../ContentSourcePolicy.java:36` | 本地合成来源只在 `local-fixture-sources-enabled=true` 时放行 |

读完自答：

1. 为什么「投影表」要按修订维度存，而不是只存在 item 上？
2. `writeRevisionProjections` 里为什么 `tags` 类型的字段被跳过？（提示：工作树第 357 行的暂存改动）
3. 媒体校验用的是「集合相等」而不是「包含」，这会拦住哪一类错误？

---

## 第 6 站：事务、锁和两个「版本」

| 位置 | 看什么 |
|---|---|
| `.../ContentManagementService.java:26,105,122,131,174,193,205,220-224,226` | 所有写方法都带 `@Transactional`；读方法带 `@Transactional(readOnly = true)` |
| `.../persistence/ContentItemRepository.java:42-44` | `findByIdForUpdate` = `@Lock(PESSIMISTIC_WRITE)`，生成 `select ... for update` |
| `.../ContentItemRepository.java:39-41` | `findWithCurrentRevisionById` 用 `@EntityGraph`，不加锁 |
| `.../ContentManagementService.java:428-432` | `lockedOwned`：先加锁再校验归属 |
| `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:24` | `@Version private Long version` |
| `apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java:20-36` | 幂等：请求体哈希 → 预留 → 执行 → 回写响应；异常时释放预留 |
| `.../ContentManagementService.java:425` | `managed(item)` 直接读实体 `getVersion()` —— 这一行是「响应里的 version 可能滞后」的原因，见下 |

三个必须分开的概念：

| 名字 | 存哪 | 谁在变 | 用在哪 |
|---|---|---|---|
| `revisionNumber` | `content_revisions.revision_number` | 每次新建修订 +1 | 业务语义：第几版内容 |
| `version`（`@Version`） | `content_items.version` | 每次事务提交时 Hibernate +1 | 乐观锁：`If-Match` 比对 |
| `Idempotency-Key` | `idempotency_records` | 每个写请求一个 | 重放保护：同键同体不重复执行 |

必须实验的点（本轮发现）：

- **状态变更类接口（submit / decide / publish / hide / show / archive / restore）的响应里，`revision` 是本次写入前的值**，因为那些方法在 `return managed(item)` 之前没有 `saveAndFlush`，版本号要到事务提交时才由 Hibernate 递增。
- 实测：`publish` 响应 `revision=3`，紧接着 `GET /admin/content-items/{id}` 得到 `4`；`APPROVE` 响应 `2`，重读得到 `3`。
- 而 `create` / `revise` 里有 `saveAndFlush`，所以响应里是新鲜值。
- 前端影响：`apps/web/src/pages/workspace.tsx:1631-1635` 直接 `update(updated)`，把响应写回列表行；只有「编辑」页会重新拉取（`workspace.tsx:751` 的 `revisionOverride ?? editing.revision`）。因此在同一行上连续做两次**需要 `If-Match`** 的动作才会踩到，而当前 UI 路径（送审后跳到审核页重新加载）恰好避开了它。

读完自答：

1. `@Transactional` 保证了什么？不能保证什么？（提示：代理调用边界、外部副作用、`open-in-view: false`）
2. `revise` 同时用锁和 `If-Match`，为什么不算重复劳动？
3. `publish` 用了 `findByIdForUpdate`，`decide` 只用 `find`。这样安排合理吗？

---

## 第 7 站：由时钟驱动的状态变化

前 6 站都是「有人点按钮」。这一站是「没人点按钮也会变」。

| 位置 | 看什么 |
|---|---|
| `.../service/ContentLifecycleService.java:47-54` | `@Scheduled(cron = "${voidhaven.lifecycle.cron:0 10 1 * * *}", zone = "Asia/Shanghai")`；有咨询锁就串行执行 |
| `.../ContentLifecycleService.java:56-66` | `run(now)`：一条条处理，单条失败只吞自己的异常，不回滚整轮 |
| `.../ContentLifecycleService.java:68-80` | `processOne`：ACTIVE 且过期 → `closePublic(now)`；招募同时置 `operationalStatus = "EXPIRED"`；非招募关闭满 3 天 → `archive()` |
| `.../ContentLifecycleService.java:82-91` | 截止时间读**明细表**而不是 `details_json`；`deadlineAt` 精确到秒，`deadlineDate` 按 Asia/Shanghai 的自然日比较 |
| `.../persistence/ContentItemRepository.java:47-49` | `findTimedPublicItems`：只捞 COMPETITION / RECRUITMENT，且必须已发布、PUBLIC、ACTIVE、`publicationStatus in (ACTIVE, CLOSED)` |
| `apps/api/src/main/resources/application.yml` 的 `voidhaven` 段 | **没有** `lifecycle.cron` 配置项，所以线上用的是代码里的默认值 `0 10 1 * * *` |

读完自答：

1. 为什么 `processOne` 要先 `findByIdForUpdate(id)` 再判断，而不是直接改 `run` 里拿到的那条实体？
2. 为什么「关闭」和「归档」是两个独立判断，而不是 if/else？
3. 一个招募内容关闭后会不会被自动归档？读第 76 行的条件再回答。

必须实验的点（本轮没做）：

- 这条链路**无法用手动请求触发**，只能等 cron 或改配置重启。判定为「需要等待/需改配置」，不作为本轮复现证据。想验证就写一个带固定 `Instant` 的单测，见 `ContentLifecycleServiceTest`。

---

## 第 8 站：审计与前端契约

| 位置 | 看什么 |
|---|---|
| `apps/api/src/main/java/cn/voidhaven/api/service/AuditService.java:9-11` | `record(...)`：通用审计，写 `before_summary` / `after_summary`（JSON） |
| `.../AuditService.java:12-14` | `recordReview(...)`：审核专用表 `review_records` |
| `apps/api/src/main/java/cn/voidhaven/api/service/AuditQueryService.java:14-15,35-39` | 读模型 `Entry` **故意不返回** `before_summary`/`after_summary` |
| `apps/web/src/lib/api.ts:442-445` | 前端每个受保护写请求前先取 `/auth/csrf`，再带 `csrf.headerName` 头发请求 |
| `apps/web/src/lib/api.ts:446-453` | `jsonWrite`：默认自动生成 `Idempotency-Key` |
| `apps/web/src/lib/api.ts:466-473` | `revise`：`PATCH` + `If-Match: String(revision)` |
| `apps/web/src/lib/api.ts:826-850` | 前端对生命周期的全部调用面 |
| `apps/web/src/pages/workspace.tsx:827-848` | `runAction`：只有 approve/changes/reject 带 `item.revision` |
| `apps/web/src/components/editor.tsx:1023-1024` | 按钮文案分叉：已发布时是「保存候选修订并继续编辑」 |

读完自答：

1. 为什么审计要同时写 `audit_logs` 和 `review_records`？各解决什么问题？
2. 审核的 `before/after` 只记了 `workflowStatus`，这样够吗？如果不够，你在 `audit_logs` 表里能看到什么？
3. 前端为什么在「保存候选修订」成功后要 `setRevision(result.revision)`？

---

## 一页速查：失败路径 → 错误码 → 代码位置

| 场景 | HTTP | 错误码 | 抛出的位置 |
|---|---|---|---|
| 草稿直接发布 | 409 | `CONTENT_NOT_APPROVED` | `ContentManagementService.java:209` |
| 已发布直接送审 | 409 | `CONTENT_NOT_SUBMITTABLE` | `ContentManagementService.java:125` |
| 对非待审内容审核 | 409 | `CONTENT_NOT_IN_REVIEW` | `ContentManagementService.java:179` |
| 旧 `If-Match` 审核 | 409 | `CONTENT_VERSION_CONFLICT` | `ContentManagementService.java:178` |
| 旧 `If-Match` 编辑 | 409 | `CONTENT_VERSION_CONFLICT` | `ContentManagementService.java:140` |
| 退回/拒绝但没写原因 | 400 | `REVIEW_REASON_REQUIRED` | `ContentManagementService.java:181`（`ReviewDecision.reason` 只有 `@Size`，空值能进到 Service） |
| 非法审核决定 | 400 | Bean Validation（发生在进 Service 之前） | `AdminController.java:49` 的 `@Pattern` |
| 隐藏/归档/恢复缺原因 | 400 | `VALIDATION_FAILED` | `AdminController.java:51` 的 `@NotBlank` —— Bean Validation 先拦下，所以 `ContentManagementService.java:448` 的 `ACTION_REASON_REQUIRED` 从这三个入口到不了 |
| 写接口完全不带请求体 | 500 | `TEMPORARILY_UNAVAILABLE` | `ApiExceptionHandler.java:68-72` 兜底；`HttpMessageNotReadableException` 没有专门 handler |
| 恢复后直接重新公开 | 409 | `CONTENT_NOT_SHOWABLE` | `ContentManagementService.java:221` |
| 招募无有效行动入口就发布 | 409 | `RECRUITMENT_ACTION_REQUIRED` | `ContentManagementService.java:211-213` |
| 合作方公开配额用满 | 409 | `PARTNER_QUOTA_EXCEEDED` | `ContentManagementService.java:449-456` |
| 正文图片引用与已上传不一致 | 409 | `CONTENT_MEDIA_REFERENCE_INVALID` | `ContentManagementService.java:421` |
| 归档内容再编辑 | 409 | `CONTENT_NOT_EDITABLE` | `ContentManagementService.java:141-142` |
| 同键不同体重放 | 409 | `IDEMPOTENCY_KEY_REUSED` | `IdempotencyService.java:63` |
| 未归档却恢复 | 409 | `CONTENT_NOT_RESTORABLE` | `ContentManagementService.java:223` |
| 非拒绝态重开 | 409 | `CONTENT_NOT_REOPENABLE` | `ContentManagementService.java:197` |
| 对已公开内容再次公开 | 409 | `CONTENT_NOT_SHOWABLE` | `ContentManagementService.java:221` |
