# 03 核心链路 B：草稿 → 审核 → 发布 → 再编辑

时间盒 25–35 分钟。读路径讲「公众看到什么」；本段讲「那条记录怎么变成公众能看到的」。

课后矩阵：`study-Resource/2026-09-18-vh02-code-lab/`。

## 本段要打开的文件

| 角色 | 路径 |
|---|---|
| 合作方入口 | `apps/api/src/main/java/cn/voidhaven/api/web/PartnerController.java` |
| 管理入口 | `.../web/AdminController.java` |
| 状态机 | `.../service/ContentManagementService.java` |
| 实体迁移 | `.../domain/ContentItem.java`（`submit/approve/publish/hide/archive`） |
| 修订行 | `.../domain/ContentRevision.java` |
| 来源写时校验 | `.../service/ContentSourcePolicy.java` 的 `requireAllowed` |
| 模块/Markdown | `ContentModuleRegistry.java`、`MarkdownDocumentService.java` |

Partner 与 Admin **共用** `ContentManagementService`。差异只有：Partner 走 `owned(id, ownerProfileId)`，Admin 走 `find(id)` / `submitAsAdmin`。面试说「两套 CRUD」是错的。

## 1. 主链状态机

```text
create          → DRAFT, visibility=HIDDEN, revision_number=1
submit          → PENDING_REVIEW（仅 DRAFT 或 CHANGES_REQUESTED）
decide(APPROVE) → APPROVED          需要 If-Match
decide(CHANGES) → CHANGES_REQUESTED 必须填原因
decide(REJECT)  → REJECTED          必须填原因
publish         → workflow=PUBLISHED, publication=ACTIVE,
                  visibility=PUBLIC, publishedRevision=currentRevision
```

对应 HTTP（合作方 / 管理端）：

| 动作 | Partner | Admin |
|---|---|---|
| 创建 | `POST /api/v1/partner/content-items` | `POST /api/v1/admin/content-items` |
| 保存草稿 | `PATCH .../{id}` | 同 |
| 送审 | `POST .../{id}/submit-review` | 同 |
| 审核 | （无） | `POST /api/v1/admin/reviews/{id}/decision` |
| 发布 | （无，管理员发） | `POST .../{id}/publish` |

`create` 要点（`ContentManagementService.create`）：

1. `prepare(draft)`：模块 schema、Markdown→安全 Document JSON、`ContentSourcePolicy.requireAllowed`。
2. 新建 `ContentItem`：ULID、`DRAFT`、`HIDDEN`、`ACTIVE`，挂上 `owner_partner_profile_id`。
3. 插入 `ContentRevision` 第 1 版，再回写 `current_revision_id`。
4. 类型明细表投影（竞赛截止日期、资源类型等）。
5. 审计 `CONTENT_CREATED`。

整段 `@Transactional`：修订插入失败则内容行一起回滚。

## 2. `publish()` 到底改了什么

实体方法（`ContentItem.java`）：

```text
workflowStatus     = PUBLISHED
publicationStatus  = ACTIVE
closedAt           = null
visibility         = PUBLIC
recordState        = ACTIVE
publishedRevision  = currentRevision     // 双指针在这里会合
招募且无运营状态     → operationalStatus = OPEN
```

Service 在调用它之前还要：

- `findByIdForUpdate`（悲观锁）
- 当前必须是 `APPROVED`
- 文档里引用的媒体必须都还在
- 招募必须至少有一个有效行动入口
- 合作方公开条数未超 `content_quota`（锁 profile 行）

发布后公开读路径才能 join 到 `published_revision_id`。在此之前，匿名用户查详情只能 404。

## 3. 已发布后再 `revise`：双指针分开

这是本课最容易讲错的点。打开 `revise()` 注释：两个并发编辑如果都从同一个 `revision_number` 自增，第二下会撞 `UNIQUE(content_item_id, revision_number)`，客户端看到的应是 409 版本冲突，而不是 500 唯一约束。

所以 `revise` 用 `lockedOwned` = `SELECT ... FOR UPDATE`，并且先比对 `If-Match` 与 `item.version`。

然后分叉：

| 当前工作流 | 做什么 |
|---|---|
| `DRAFT` / `CHANGES_REQUESTED` | **原地改** 当前修订行（还没上过线） |
| `PUBLISHED` | `revision_number+1` **新建** 候选修订；`current` 指向新行；**不改** `published_revision_id` |

最后一律 `beginCandidateRevision()`：工作流回到 `DRAFT`，公开站继续显示旧的 published。新草稿再次走 submit → decide → publish，发布时才把 published 指针拨过来。

```text
发布后编辑期间：
  current_revision_id    → 新草稿（公众不可见）
  published_revision_id  → 旧已审版本（公众仍看这个）
```

**八股：为什么不直接 UPDATE 正文？** 需要审核闸门、需要回滚到上一版、需要审计「哪一版被拒绝」。修订表是事件性快照，内容表是当前指针。

## 4. 运营四动作 ≠ 工作流

| 方法 | 改哪一维 | 公开读后果 |
|---|---|---|
| `hide` | `visibility=HIDDEN` | 404，published 指针还在 |
| `show` | `visibility=PUBLIC` | 重新出现（要再查配额） |
| `archive` | `record_state=ARCHIVED` + 隐藏 | 404 |
| `restore` | 回到 ACTIVE+HIDDEN+APPROVED，`publication=NONE` | 不会自动上线 |
| `closePublic` | `publication_status=CLOSED` | 生命周期任务用，结束 ≠ 下线 |

面试原话应对：**结束不等于下线，下线不等于归档，归档不等于删除。** 用四个字段，而不是一个 `status` 枚举把所有意思塞进去。

## 5. 锁怎么配

| 场景 | 机制 | 挡住什么 |
|---|---|---|
| 两人同时 PATCH 同一草稿 | `If-Match` ≠ `version` → 409；JPA `@Version` 兜底 | 后写覆盖先写 |
| 已发布内容并发 revise | `FOR UPDATE` + 唯一约束 | 两个 `revision_number=N+1` |
| 发布 / 重新上线 | `FOR UPDATE` + 配额锁 | 超配额公开 |
| 审核 decide | `If-Match` | 送审后又被改了还按旧版批 |

**八股口诀：** 乐观锁适合冲突少、要失败得快；悲观锁适合「读了必须接着写、且有唯一约束」。本项目两种都用，且 HTTP 把乐观锁暴露成 `If-Match`，让前端能提示「请刷新」。

## 6. 面试必答四句

1. 已上线内容编辑不立刻改公开页，因为只动 `current_revision`。
2. `revise` 对已发布项加悲观锁，是防 `revision_number` 唯一约束竞态，不是为了「显得专业」。
3. Partner / Admin 共用 Service，差异是 owner 隔离。
4. 结束 / 下线 / 归档 / 删除是四维，不要画成一条直线。

## 闭卷题

**问：** 一条已发布资源正在改草稿。此时公开详情接口读的是哪一行？`current_revision_id` 和 `published_revision_id` 是否相同？

**答：** 读 `published_revision` 那一行。两指针不同：`current` 指向新草稿，`published` 仍指向上次审核通过的修订。直到管理员再次 publish，两指针才重新会合。
