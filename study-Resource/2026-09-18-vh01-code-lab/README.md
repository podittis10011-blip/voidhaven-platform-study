# VH-01 公开内容查询：代码阅读 · 测试 · 手动复现

- 日期：2026-09-18
- 代码版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `be219ac` + 61 项未提交改动（当前工作树）
- 运行时：`http://localhost:18082`（当前工作树构建）、`http://localhost:18080`（HEAD 构建）
- 工具链实测：JDK 17.0.18、Maven 3.9.4、Node v24.12.0
- 数据：仅 `LEARN-*` 合成数据，未使用真实账号、联系方式或业务信息

## 0. 本轮定位

把 VH-01 的三层证据补齐：

1. **源码阅读**：能指出每个行为落在哪个类的哪一段，尤其是游标、排序、双层过滤、404 语义。
2. **自动化测试**：知道现有测试证明了什么、没证明什么，能自己跑并读懂报告。
3. **手动复现**：在真实栈上按请求矩阵拿到可记录的响应，区分 200 成功、400 参数错误、404 隐藏语义。

不写入 `learning-state.yaml`，不修改正式源码（只读），不新增业务功能。

## 1. 必须先知道的版本事实（本轮实测）

两个端口不是同一份代码。用三个标记可以当场分辨：

| 标记 | 18082 | 18080 |
|---|---|---|
| 列表响应字段 | 有 `sort`，item 有 `updatedAt` | 无 `sort`、无 `updatedAt` |
| `GET /api/v1/public/resources?sort=popularity` | 400 `CATALOG_SORT_INVALID` | **200**，参数被静默忽略 |
| `GET /api/v1/public/resources?filterKey=resourceType` | 400 `CONTENT_FILTER_INCOMPLETE` | **200**，参数被静默忽略 |
| 首页槽位 | `competitions, focus, recruitments, resources` | `competitions, recruitments, resources` |
| `competition` 模块字段 | 含 `competitionLevel`、`participationForm`、`startDate` | 只有 `officialRulesUrl`、`deadlineDate` |
| 建议用途 | 当前源码的手动复现（`RUNTIME_CURRENT_SOURCE`） | HEAD 基线对照，用来演示「静默忽略 vs 显式报错」 |

判定依据（可自己复核）：

- `git show HEAD:apps/api/src/main/java/cn/voidhaven/api/web/PublicContentController.java` 里 `/resources` 只有 `pageSize`、`cursor`、`q` 三个参数，所以 `sort`、`filterKey`、`status` 是未知参数，被 Spring 忽略。
- HEAD 的 `ContentService.PublicPage` 只有 `(items, nextCursor)`；工作树新增了 `sort` 字段。18080 的响应里没有 `sort`。
- `updatedAt` 是工作树未提交改动新增的 DTO 字段（`git diff --cached -- apps/api/.../PublicContentDto.java`），只有 18082 返回。

**影响**：`09-evidence-ledger.md` 第 5.1 节把 18080 记作 `RUNTIME_CURRENT_SOURCE`，与今天的实测不一致。在做任何手动复现登记之前，先决定用哪个端口作为当前源码运行时，并修正该记录（本包不擅自改账本）。

## 2. 学习包结构

| 文件 | 用途 |
|---|---|
| `01-code-reading-guide.md` | 六站阅读路线，带文件与行号锚点，每站配「读完自答」 |
| `02-test-map.md` | VH-01 相关测试清单、实测命令与结果、盲区 |
| `03-manual-repro-runbook.md` | 只读请求矩阵（已实测）、A/B 对照实验、可选写路径、记录模板 |
| `04-question-sheet.md` | 两层题单：费曼理解输出 + 面试追问链，答案折叠在文末 |

## 3. 建议顺序

1. 先读 `01` 的第 1～2 站，把链路和游标模型画在纸上（不看 `04` 答案）。
2. 跑 `02` 里的四条命令，确认自己会读 surefire 报告。
3. 在 18082 上按 `03` 的矩阵逐条复现，记录 `requestId`。
4. 做一遍 18080 / 18082 的 A/B 对照，用第一手观察解释「为什么报错比静默忽略好」。
5. 闭卷答 `04` 的第一层，再对照答案；第二层按 30 秒 / 3 分钟两档口述。

## 4. 已完成 / 待你完成

已完成（AI 实测，可作为对照基线，不构成你的掌握证据）：

- 环境与版本归属判定（第 1 节）。
- 四条单元测试命令：`Tests run: 8, Failures: 0, Errors: 0`（2026-09-18 11:41，离线模式）。
- 只读请求矩阵 26 条：状态码与错误码全部实测。

待你完成：

- 自己跑一次测试并读懂报告（`02`）。
- 自己在 18082 上重放请求矩阵并登记 `requestId`（`03`）。
- 闭卷回答 `04` 第一层，并口述第二层追问链。
- 决定是否需要补第二条资源，做出真实的 `nextCursor` 翻页（`03` 的 W-01，写操作，需你确认）。

## 5. 证据与状态边界

- 本包中的分析、题单、参考答案都属于 AI 生成草稿，不是掌握证据。
- 只有你自己完成的闭卷解释、独立操作与运行结果，才可能作为证据。
- 任何状态变更（`main_checkpoint`、`mastery_status`）都需要你给出「目标锚点 + 旧值 + 新值」的明确授权；本包只输出提案。
