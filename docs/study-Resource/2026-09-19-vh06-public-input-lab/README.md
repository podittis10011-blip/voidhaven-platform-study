# VH-06 公开投稿、合作申请与社群门禁：代码阅读 · 测试 · 手动复现

- 日期：2026-09-19
- 代码版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`，工作树干净（`git status --porcelain` 输出 0 行）
- 版本基线：JDK 17.0.18、Spring Boot 4.1.1、Spring Security 7.1.1、MySQL 8.4、Flyway `V1`～`V24`
- 与本链路相关的最近两次提交：`b34ac3b feat: rework public participation entries and publish the trust pages`、`2295a41 chore(infra): allow the theme bootstrap script and wire community-gate variables`
- 数据：全部分析基于合成数据与本地配置；没有真实账号、真实联系方式、真实邀请码

## 0. 本轮定位

把 VH-06 的三层证据补齐，并明确区分「源码断言」与「运行实测」：

1. **源码阅读**：能说清「谁在收表单、谁在去重、谁在限流、谁在发券、谁在放行受控入口」这五件事分别落在哪个类的哪一段。
2. **自动化测试**：知道现有 38 条相关单元测试证明了什么、没证明什么，并会自己跑。
3. **手动复现**：在真实栈上把「重复提交 / 限流 / 错误查询码 / 无效邀请码 / 缺 grant / 撤销 grant」六类结果变成可记录的响应与 `requestId`。

本轮不改 `learning-state.yaml`，不改正式源码，不新增业务功能。

## 1. 本轮的证据边界（先看这里，避免把推断当事实）

| 证据层 | 本轮状态 | 说明 |
|---|---|---|
| `SOURCE_CURRENT` | **已完成** | 逐文件读完 VH-06 的 Controller、Service、Support、迁移与 OpenAPI 契约 |
| `TEST_CURRENT` | **已完成（AI 实测）** | 12 个纯单元测试类、38 条用例，`Failures: 0, Errors: 0`（见 `02-test-map.md`） |
| `BUILD_CURRENT` | 沿用仓库既有记录 | 本轮只跑 `test`，未单独执行打包 |
| `RUNTIME_CURRENT_SOURCE` | **未完成** | 本机 Docker 未运行（`docker` 不在 PATH、18080/18082 无监听），因此**没有一条 HTTP 实测** |
| `FRONTEND_MOCK_E2E` | 未执行 | 未运行 Vitest / Playwright |

因此本包里所有 HTTP 状态码、响应体形状、响应头都标为**源码预期**，你要在真实栈上把它们变成实测记录。`03-manual-repro-runbook.md` 就是为这件事写的。

## 2. 必须先知道的版本与源码事实

| 事实 | 依据 | 类型 |
|---|---|---|
| 公开写路径 `/api/v1/public/**` **整体 CSRF 豁免**，安全兜底靠 `Idempotency-Key` + 按网络限流 + `SameSite=Strict` | `SecurityConfig` 第 29、30 行 | 源码断言 |
| 公开写与状态查询是**两个独立的限流桶**，默认 8 次/小时 与 60 次/小时 | `PublicWriteRateLimiter`、`PublicStatusQueryRateLimiter`、`PublicWriteRateLimitInterceptor` 第 14～19 行 | 源码断言 |
| 限流主体是**网络地址**，且默认只有直连地址；本地 `local` profile 才开启 `trust-proxy-headers` 并信任 10/8、172.16/12、192.168/16 | `ClientNetworkSubject`、`application-local.yml` | 源码断言 |
| 回执查询用「26 位回执号 + 16～256 位查询码」，库里只存查询码的 **SHA-256**，比较用 `MessageDigest.isEqual` | `PublicCaseService` 第 88～98 行、`SecureTokens` | 源码断言 |
| 一次性敏感结果（查询码）走 `executeSingleDelivery`：**永不回放**，重放同键返回 409 `SENSITIVE_RESULT_NOT_REPLAYABLE` | `IdempotencyService` 第 37～61 行 | 源码断言 |
| 邀请码是**批次共享码**：库内存 `HMAC-SHA256(code)`，用一条带 `use_count < max_uses` 的 UPDATE 原子占额 | `CommunityService` 第 76～80、186～196 行 | 源码断言 |
| 短期授权 `grant` 是 32 字节随机值，库内存 SHA-256；有效期默认 30 分钟；撤销批次会**删除该批次全部 grant** | `CommunityService` 第 82～91、208～214 行；`V14__community_access.sql` | 源码断言 |
| 门禁失败计数阈值来自 DR-002：**同一匿名主体 24 小时内 8 次失败 → 冷却 30 分钟** | `CommunityService` 第 68～74 行；`docs/decisions/DR-002-...md` 第 5.2 节 | 源码 + 规范断言 |
| 受控内容用 `COMMUNITY_GATED` 标记，公开读取时**不查 grant 就只投影 `access_level='PUBLIC'`** | `CommunityService.publicEntries` 第 94～108 行；`ProjectJoinService.publicEntries` | 源码断言 |
| 受控联系方式用 **AES-256-GCM**（带 AAD 上下文）加密，查看必须写审计 `PRIVATE_CONTACT_REVEALED` | `PrivateValueCipher`、`PublicCaseAdministrationService.reveal` | 源码断言 |

## 3. 本轮发现的 5 个待验证疑点

这些是**源码级推断**，不是实测结论。前两条建议优先做实验证实，它们是很好的面试素材（"你在项目里发现过什么缺陷"）。

| 编号 | 疑点 | 依据 | 证实方法 |
|---|---|---|---|
| F-01 | `CommunityService.verify` 的失败事件写入，可能被同一事务回滚，导致「8 次失败 → 冷却」**永远不触发** | 该方法标注 `@Transactional`；失败事件 `insert` 之后立刻 `throw new ApiException(...)`；`ApiException extends RuntimeException`（`ApiException.java` 第 3 行） | 真实栈连续输错邀请码 9 次，观察第 9 次是否变成 429；同时 `select count(*) from community_gate_events where outcome='FAILURE'` |
| F-02 | 429 的 `Retry-After: 900` 是全局硬编码，与各自窗口不一致 | `ApiExceptionHandler.response` 固定写 `900`；社群冷却文案是 30 分钟，公开写窗口是 3600 秒 | 触发任一 429，读 `Retry-After`，与文案/配置比对 |
| F-03 | `PublicCaseService` 里那两个 `@Transactional protected` 方法属于**自调用**，注解实际不生效 | `createSubmission(key, cmd)` 调 `this.createSubmission(cmd, contact)`；事务实际由 `IdempotencyService.executeSingleDelivery` 的 `@Transactional` 提供 | 在 `createSubmission(command, contact)` 上打断点看是否有独立事务；或读线程事务名 |
| F-04 | 社群门禁与私密联系方式两个开关被**耦合**：只开门禁、不配密钥环时，`/community-gate/verify` 返回的是 `PRIVATE_CONTACT_DISABLED` 而不是 `COMMUNITY_NOT_CONFIGURED` | `verify()` 第 66 行调 `requireOperational()` = `requireEnabled()` + `privateValues.requireKeys()` | 密钥环留空启动，调 verify 与 `/public/capabilities` 对比 |
| F-05 | 后台对合作申请有**两套入口**，状态机各写一遍，容易漂移 | `PublicCaseAdministrationService.transitionPartnerApplication`（全套 8 个状态）与 `PartnerAccountProvisioningService.approveApplication/decideApplication`（只接受 REJECT 或走批准建号） | 读两个类的状态白名单与 SQL，对比 `PublicCaseStates.PARTNER` |

## 4. 学习包结构

| 文件 | 用途 |
|---|---|
| `01-code-reading-guide.md` | 六站阅读路线，带文件与行号锚点，每站配「读完自答」 |
| `02-test-map.md` | VH-06 相关测试清单、实测命令与结果、盲区 |
| `03-manual-repro-runbook.md` | 请求矩阵（源码预期）+ 需要你自己动手的 7 个实验 + 记录模板 |
| `04-question-sheet.md` | 两层题单：费曼理解输出 + 面试追问链，答案折叠在文末 |

## 5. 建议顺序

1. 先读 `01` 的第 1～2 站，用一句话说清「一次公开投稿会依次经过谁」。
2. 跑 `02` 里的命令，确认 38 条测试通过，并读懂每条在断言什么。
3. 读 `01` 的第 3～5 站，重点吃透「查询码」与「grant」这两个凭据模型的区别。
4. 启动本地栈（`03` 第 0 节），按矩阵重放，登记 `requestId`，先把 F-01、F-02、F-04 三条疑点证实或推翻。
5. 闭卷答 `04` 第一层，再对照答案；第二层按 30 秒 / 3 分钟两档口述。

## 6. 已完成 / 待你完成

已完成（AI 实测，只是对照基线，不构成你的掌握证据）：

- 源码归属判定与行号锚点（`SOURCE_CURRENT`）。
- 12 个相关单元测试类离线实跑：`Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`。
- 对本链路 5 个疑点的源码级定位（F-01～F-05）。

待你完成：

- 自己跑一次测试，并打开 `apps/api/target/surefire-reports/` 读原始报告。
- 在真实栈上重放请求矩阵，登记状态码 + 响应体 + `requestId`。
- 做 E-01～E-07，尤其是 E-02（邀请码原子占额）与 E-06（失败事件是否被回滚）。
- 闭卷回答 `04` 第一层，并口述第二层追问链。

## 7. 证据与状态边界

- 本包的分析、题单与参考答案都是 AI 生成草稿，不是掌握证据。
- 只有你自己完成的闭卷解释、独立操作与运行结果，才可能作为证据。
- 本包只输出状态变更**提案**；`main_checkpoint` 与 `mastery_status` 的任何改动都需要你给出「目标锚点 + 旧值 + 新值」的明确授权。
- 复现只在本地合成数据栈上进行；查询码、邀请码、grant 与联系方式都属于凭据或私密数据，**不得**进入截图、笔记、博客或共享材料。
