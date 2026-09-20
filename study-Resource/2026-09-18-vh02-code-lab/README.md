# VH-02 内容生命周期与双修订：代码阅读 · 测试 · 手动复现

- 日期：2026-09-18
- 代码版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `be219ac` + 61 项已暂存改动
- 运行时：`http://localhost:18082`（行为等同当前工作树）、`http://localhost:18080`（行为等同 HEAD）
- 工具链实测：JDK 17.0.18、Maven Wrapper 3.9.x（`-o` 离线可跑）、MySQL 8.4.11
- 合成数据：`LEARN-20260918-VH02-01`（资源）、`LEARN-20260918-VH02-REC`（招募）
- 本轮不改正式仓库文件，不写 `learning-state.yaml`

## 0. 本轮定位

上一轮 `outputs/voidhaven/2026-09-14-sprint/03-content-lifecycle-slice.md` 讲的是「设计意图 + 方法职责」：为什么拆四维状态、为什么要有两个修订指针。本轮把同一主题下沉一层，合格标准是：

> 写出的每一句结论，都能落到「文件:行号」「一条可复跑的命令」「一次真实响应或一行数据库记录」这三者之一。

三层证据各自的产物：

| 层 | 产物 | 本包文件 |
|---|---|---|
| 源码阅读 | 8 站带行号的阅读路线，每站配「读完自答」和「必须做实验才能回答」的点 | `01-code-reading-guide.md` |
| 自动化测试 | 实测命令 + 实测结果 + 每条测试证明什么 / 不证明什么 | `02-test-map.md` |
| 手动复现 | 已在 18082 真实栈执行过的请求矩阵、失败用例、数据库对照 | `03-manual-repro-runbook.md` |
| 自测 | 两层题单：费曼理解输出 + 面试追问链，答案折叠在文末 | `04-question-sheet.md` |

## 1. 版本事实（本轮实测，含一处需要修正的旧记录）

两个端口的容器项目名与直觉相反，本轮用只读 SQL 确认：

| Compose 项目 | 端口 | 判别标志 | 本轮归属判定 |
|---|---|---|---|
| `voidhaven-lab` | 18082 | `GET /api/v1/public/resources?sort=popularity` → 400 `CATALOG_SORT_INVALID`；`filterKey=resourceType` → 400 | 行为与**当前工作树**源码一致 |
| `voidhaven-repro-current` | 18080 | 同样两个请求 → 200，参数被静默忽略 | 行为与 **HEAD `be219ac`** 一致 |

判定依据：

- `git show HEAD:apps/api/src/main/java/cn/voidhaven/api/web/PublicContentController.java` 中 `/resources` 只声明 `pageSize`、`cursor`、`q`，所以 `sort`、`filterKey` 是未知参数被 Spring 丢弃。
- 工作树 `PublicContentController` 的暂存改动新增了排序/筛选参数校验，所以 18082 显式报错。
- 数据指纹：18082 的后台列表返回内容 ID `26CYD6HJB5C406497D7Y915Z45` 等 3 条；只读 SQL 显示这批 ID 属于 `voidhaven-lab` 的 MySQL。18080 的公开列表返回 `9E4TVH7FN6NR988EZ66YJ48NXN`，属于 `voidhaven-repro-current`。

**冲突提示**：`outputs/voidhaven/2026-09-14-sprint/09-evidence-ledger.md` 第 0 节把 `voidhaven-repro-current` / `18080` 记作 `RUNTIME_CURRENT_SOURCE`，与本次判定相反。本包不擅自改账本，只报告冲突；在做下一次手动复现登记前，需要你先决定以哪个口径为准。

## 2. VH-02 代码路径在两个构建里是否一致

`git diff --cached` 显示，61 项暂存改动里与内容生命周期相关的只有这些：

- `ContentItem.java`：只多了 `getUpdatedAt()`（第 43 行），状态方法一字未改。
- `ContentItemRepository.java`：新增 `findPublicSearch`、`findGraphByIds`，属于公开检索路径。
- `ContentManagementService.java`：改了 `writeRevisionProjections` 的 `tags` 过滤、关联竞赛校验抽成 `validateRelatedCompetitions`，`create/revise/submit/decide/publish/hide/show/archive/restore` 未改。

也就是说：**VH-02 的九个状态方法与审核规则在 HEAD 与工作树完全一致**。已在 18080 上对照实测三条失败用例，结果与 18082 逐字相同：

| 用例 | 18080 | 18082 |
|---|---|---|
| 已发布内容直接送审 | 409 `CONTENT_NOT_SUBMITTABLE` | 409 `CONTENT_NOT_SUBMITTABLE` |
| 用旧 `If-Match` 审核 | 409 `CONTENT_VERSION_CONFLICT` | 409 `CONTENT_VERSION_CONFLICT` |
| 对非 PENDING_REVIEW 审核 | 409 `CONTENT_NOT_IN_REVIEW` | 409 `CONTENT_NOT_IN_REVIEW` |

因此本包的源码阅读结论对两个端口都成立；但**手动复现只在 18082 做过**，写证据时不要跨端口混用 ID 和 `requestId`。

## 3. 学习包结构

| 文件 | 用途 |
|---|---|
| `01-code-reading-guide.md` | 8 站阅读路线：数据模型 → HTTP 入口 → 状态机主链 → 双修订 → 校验与投影 → 事务与锁 → 时钟驱动变化 → 审计与前端契约 |
| `02-test-map.md` | VH-02 相关测试清单、实测命令与结果、覆盖矩阵与盲区 |
| `03-manual-repro-runbook.md` | 已实测的请求矩阵（37 条编号用例）、失败用例、数据库对照、记录模板 |
| `04-question-sheet.md` | 两层题单 + 折叠参考答案 + 常见误区 |

## 4. 建议顺序

1. 先读 `01` 的第 1～2 站，**合上文件**画一张「四个状态维度 + 两个修订指针 + 版本号」的表，再打开 `ContentItem.java:12-24` 核对。
2. 读第 3～4 站（`revise` 与两条候选修订创建路径），这是本轮唯一的「容易看漏」点。
3. 跑 `02` 里的两条命令，自己看 `target/surefire-reports` 而不是只看我给的数字。
4. 在 18082 上按 `03` 的矩阵重放，重点是自己拿到 `requestId`，并对着 `content_revisions` 看两条修订行。
5. 闭卷答 `04` 第一层；第二层按 30 秒 / 3 分钟两档口述。

## 5. 已完成（AI 实测，可作为对照基线，不构成你的掌握证据）

- 源码定位：9 个状态方法、2 条候选修订创建路径、8 处 HTTP 入口，全部给到 `文件:行号`。
- 单元测试：4 个类 11 条用例通过（2026-09-18 11:45，离线模式）；全量 126 条中 1 条因沙箱限制报错（见 `02` 第 4 节）。
- 真实栈复现：37 条编号用例实测（36 条在 18082，1 条跨构建对照在 18080），覆盖成功主链、16 条失败用例、幂等重放；数据库侧确认 `content_revisions` 两行、`published_revision_id` 与 `current_revision_id` 的切换。

## 6. 待你完成

- 自己跑一次测试并读懂 surefire 报告，说清哪条测试根本**没执行**（integration tag + `Assumptions`）。
- 自己在 18082 上重放矩阵，独立拿到 `requestId`，并解释 `version` 在响应里为什么会比库里小 1。
- 闭卷回答 `04` 第一层，再口述第二层。
- 判断 §1 的账本冲突要按哪个口径修正（这是你的决定，本包只给证据）。

## 7. 证据与状态边界

- 本包的分析、题单、参考答案都是 AI 生成草稿，不是掌握证据。
- 只有你自己完成的闭卷解释、独立操作与运行结果，才可能作为证据。
- 任何状态变更（`main_checkpoint`、`mastery_status`）都需要你给出「目标锚点 + 旧值 + 新值」的明确授权；本包只输出提案。
- 本轮没有修改正式项目文件；18082 / 18080 的容器与数据卷保持原状。
