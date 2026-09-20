# VH-05 媒体上传、私有存储与访问控制：代码阅读 · 测试 · 手动复现

- 日期：2026-09-19
- 代码版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`，`git status --porcelain` 无输出（工作树干净）
- 运行时：`http://localhost:18080`（compose 项目 `voidhaven-repro-current`）
- 工具链实测：JDK 17.0.18、Maven 3.9.4、Spring Boot 4.1.1、Spring Security 7.1.1、MySQL 8.4、MinIO `RELEASE.2025-04-22T22-12-26Z`
- 数据：只用本地合成账号 `local-admin` / `local-partner`，以及一条合成内容 `LEARN-20260919-VH05-MEDIA-01`；没有真实账号、联系方式、密钥或业务数据
- 模式：`SPRINT｜VoidHaven 项目答辩` 的一条链路。本目录不修改 `learning-state.yaml`，也不代表任何知识点已经掌握

## 0. 本轮定位

VH-05 要回答的不是「上传怎么写」，而是四个必须能当场讲清的问题：

1. **对象为什么私有**：MinIO 桶被设成 `private`，9000 端口不映射到宿主，所有读取都要经过 API 的授权查询。
2. **谁能读到**：同一个 `media_assets` 行，在公开、社群、后台三条读路径上由三条不同的 SQL 决定，谁也不复用谁。
3. **上传时先发生什么、后发生什么**：先解码重编码，再锁内容行，再查配额，再写对象、写库、写审计；事务回滚时对象怎么补删。
4. **上传之后怎么和正文、生命周期绑在一起**：正文里的 `media:{ULID}` 与 `revision_media` 行必须一一对应，送审时会校验；下架后公开读取必须立刻失效。

本轮把源码阅读、自动化测试、真实栈复现三层证据补齐，并记录三处**实测与代码意图不一致**的地方（见第 4 节）。

## 1. 版本事实（本轮实测，先记住这四条）

| 事实 | 依据 |
|---|---|
| 18080 上跑的镜像**不含** HEAD 新增的内容字段定义，行为指纹与 `be219ac` 一致 | 实测 `GET /api/v1/public/content-modules`：competition 只返回 `officialRulesUrl,deadlineDate`，resource 只返回 `resourceType,audience`；HEAD 新增的 `competitionLevel`、`audience`、`participationForm`、`startDate`、`stage`、`relatedCompetitionIds` 一个都没出现 |
| 镜像 config 时间戳是 `2026-09-15T06:50Z`，而 `be219ac` 的提交时间是 `2026-09-15 17:48 +08`（即 `09:48Z`） | `docker image inspect voidhaven-repro-current-api:latest`、`git log --date=iso-local`。**时间戳早于提交**，所以只能说「be219ac 时代的树」，不能声称精确等于某个 commit |
| `be219ac..HEAD` 之间唯一影响媒体读取的改动是 `MediaReadService` 的 **2 处 `project_join_entries` 分支**（+2 条测试） | `git diff be219ac..HEAD -- apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java` |
| 因此：本轮在 18080 上取到的**正文媒体**证据对 HEAD 成立；`project_join_entries` 相关的公开/社群读取路径**既无法在 18080 上验证，也很可能不在该镜像里** | 同上；`MediaReadServiceTest` 有 2 条用例只断言 SQL 形状 |

> 冲突提示：`09-evidence-ledger.md` 的「批次 2 续跑记录」写的是「2026-09-18 从 HEAD `be219ac` 重新构建得到 `api=f1cfc6e13731`」。镜像时间戳（09-15 06:50Z）与该描述不一致。这里的做法是**记录冲突、不覆盖原文**，并改用行为指纹作为依据。

另一条容易踩的坑：`voidhaven-lab` 栈（18082）来自独立仓库 HEAD `eefc2eed`，谱系与 `voidhaven-platform` 不同，**不作为本链路证据来源**。

## 2. 学习包结构

| 文件 | 用途 |
|---|---|
| `01-code-reading-guide.md` | 六站阅读路线，逐站给文件、行号、关键点和「读完自答」 |
| `02-test-map.md` | VH-05 相关 19 条测试的清单、实测命令与结果、以及它们**没有**证明什么 |
| `03-manual-repro-runbook.md` | 已在 18080 实测的请求矩阵 + 需要你自己动手的 6 个实验 + 记录模板 |
| `04-question-sheet.md` | 两层题单：费曼理解输出 + 面试追问链，参考答案折叠在文末 |

## 3. 建议顺序

1. 先读 `01` 的第 1～4 站，把「一次上传依次碰谁」和「三条读路径各自查什么」画成两张图。
2. 跑 `02` 里的命令，确认 19 条测试通过，重点看 `MediaReadServiceTest` 断言的是 SQL 字符串而不是 SQL 结果。
3. 按 `03` 的矩阵重放一遍，重点记录**三类失败的响应形状**：`401` 空体、`404 MEDIA_NOT_FOUND`、`500 TEMPORARILY_UNAVAILABLE`。
4. 做 `03` 的 E-01（MinIO 匿名策略）与 E-03（下架后立刻失读），这两个最能证明「私有存储 + 应用层授权」不是口号。
5. 闭卷答 `04` 第一层，再对照答案；第二层按 30 秒 / 3 分钟两档口述。

## 4. 本轮实测发现的三处「代码意图 ≠ 运行时行为」

都不是猜测，都能用 `03` 里的命令复现：

| 现象 | 触发方式 | 根因位置 |
|---|---|---|
| 图片说明为空、超 240 字、或参数缺失时，返回 **`500 TEMPORARILY_UNAVAILABLE`**，而不是预期的 `400 MEDIA_ALT_TEXT_REQUIRED` | 上传时把 `altText` 传成空格 / 241 字符 / 不传 | `MediaController` 第 37、48 行的 `@NotBlank @Size` 触发 `HandlerMethodValidationException`，`ApiExceptionHandler` 没有这个分支，落到第 68 行的兜底 `Exception` |
| 5 MB～6 MB 的文件返回 **`500`**；大于 6 MB 的文件**连接被直接中断**，客户端拿不到 JSON | 上传 5.5 MB / 6 MB 文件 | 两层独立限制：`application.yml` 第 6 行 `max-file-size: 5MB` 溢出后未单独处理；`infra/nginx/default.conf` 第 10 行 `client_max_body_size 6m` 在网关层就拒绝 |
| 送审响应里的 `revision` 比随后 `GET` 到的 `version` **小 1** | 送审后立刻拿响应体里的 `revision` 去发 `If-Match` | 跟着它发会得到 `409 CONTENT_VERSION_CONFLICT`。机制推断是「响应体取自 flush 之前的内存值」，未做源码级验证；可靠做法是**以 GET 到的 version 为准** |

这三处都属于**可讲的真实工程问题**，面试里比背「上传用 multipart」有价值得多。修不修、怎么修，由你决定；本轮只定位，不改项目源码。

## 5. 已完成 / 待你完成

已完成（AI 实测，只是对照基线，不构成你的掌握证据）：

- 源码归属与版本边界判定（第 1 节）。
- 19 条相关单测 `clean` 后重跑：`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`（2026-09-19，JDK 17.0.18 / Maven 3.9.4）。
- 真实栈请求矩阵：公开 / 社群 / 后台三条读路径、多类失败路径、完整「上传 → 引用 → 送审 → 批准 → 发布 → 下架」成功链路，共 20+ 条请求的状态码、响应体与关键响应头。
- MinIO 桶匿名策略、对象 key 命名、MySQL 四张相关表的行级状态。

待你完成：

- 自己跑一次测试并读 surefire 报告。
- 在 18080 上重放 `03` 的矩阵，登记 `requestId` 与响应头。
- 做 E-01～E-06，尤其是 E-05（孤儿资产进入删除队列）需要触发一次 retention 任务，得你决定怎么触发。
- 闭卷回答 `04` 第一层，并口述第二层追问链。
- 决定这三处不一致是否要在项目仓库里修，以及要不要形成独立的修复任务。

## 6. 证据与状态边界

- 本包的分析、题单与参考答案都是 AI 生成草稿，不是掌握证据。
- 只有你自己完成的闭卷解释、独立操作与运行结果，才可能作为候选证据。
- 本包只输出状态变更**提案**；`main_checkpoint` 与 `mastery_status` 的任何改动都需要你给出「目标锚点 + 旧值 + 新值」的明确授权。
- 复现只在本地合成数据栈上进行；不要用真实账号、真实联系方式或生产凭据重放这些请求。
- 本轮在 18080 上留下的合成数据：内容 `LEARN-20260919-VH05-MEDIA-01`（`HIDDEN` 状态）与两个媒体资产。清理方式与影响见 `03` 第 6 节。
