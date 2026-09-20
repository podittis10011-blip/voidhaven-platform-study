# VH-03 认证、Session、CSRF 与权限：代码阅读 · 测试 · 手动复现

- 日期：2026-09-18
- 代码版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `be219ac`
- 版本核对：VH-03 涉及的文件全部已在 HEAD 中，且工作树没有改动它们（`git status --porcelain -- apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java apps/api/src/main/java/cn/voidhaven/api/web/AuthController.java apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java` 无输出）。VH-01/VH-02 的 61 项未提交改动集中在目录、首页与游标，与本链路无关。
- 运行时：`http://localhost:18080`（compose 项目 `voidhaven-repro-current`）、`http://localhost:18082`（compose 项目 `voidhaven-lab`），本轮实测两栈均在运行，VH-03 探针结果完全一致
- 工具链实测：JDK 17.0.18、Maven 3.9.4、Spring Boot 4.1.1、Spring Security 7.1.1、Spring Session 4.1.1、MySQL 8.4
- 数据：只用本地合成账号 `local-admin`、`local-partner`，以及一个不存在的探测登录名；没有真实账号、凭据、联系方式或业务数据

## 0. 本轮定位

把 VH-03 的三层证据补齐：

1. **源码阅读**：能说清「谁在建会话、谁在查令牌、谁在查角色、谁在查资格」四件事分别落在哪个类的哪一段。
2. **自动化测试**：知道现有 25 条相关单元测试证明了什么、没证明什么，并会自己跑。
3. **手动复现**：在真实栈上把「未登录 / 角色不足 / 资格失效 / 缺 CSRF」四类失败拿到可记录的响应和 `requestId`。

本轮不改 `learning-state.yaml`，不改正式源码，不新增业务功能。

## 1. 必须先知道的版本事实（本轮实测）

这几条决定了后面所有结论的解释方式，先记住：

| 事实 | 依据 |
|---|---|
| Spring Security 是 **7.1.1**，不是 5.x/6.x 的默认行为 | `apps/api/pom.xml` 父版本 `spring-boot-starter-parent 4.1.1`；本机 `.m2` 中解析到的 `spring-security-web-7.1.1.jar` |
| CSRF 令牌处理链由 **cookie 存原值 + Xor 处理器返回掩码值** 组成（默认行为，代码里没改） | 实测 `/auth/csrf` 返回的 token 长度 96，而 `XSRF-TOKEN` cookie 是 36 字符 UUID；把**裸 cookie 值**当请求头发回会被拒 |
| 过滤器顺序中 `SecurityContextHolderFilter = 700`、`CsrfFilter = 1100`、`ExceptionTranslationFilter = 4000`、`AuthorizationFilter = 4200` | `javap` 读取 `FilterOrderRegistration` 常量；`addFilterAfter(..., SecurityContextHolderFilter.class)` 落在 **701**，即排在 CSRF 与授权之前 |
| Spring Boot 4.1.1 默认让安全过滤链参与 **ERROR 派发**（`spring.security.filter.dispatcher-types` 默认 `EnumSet.allOf`），且 `AuthorizationFilter` 默认 `filterErrorDispatch = true` | `javap` 读取 `SecurityFilterProperties`、`AuthorizationFilter` 默认值；直接解释了「未登录 + 缺 CSRF 得到 401 而不是 403」 |
| 会话存在 MySQL，不靠停机保活 | `application.yml` 的 `spring.session.store-type: jdbc`，`V10__spring_session_jdbc.sql` 建 `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES` |
| 本地栈 cookie 没有 `Secure`，因为 `voidhaven.cookie-secure=false`；而 production profile **强制要求** Secure | 实测响应头；`ProductionConfigurationValidator` 第 88 行 `if (!cookieSecure) throw invalid("Production session cookies must be Secure")` |

## 2. 学习包结构

| 文件 | 用途 |
|---|---|
| `01-code-reading-guide.md` | 六站阅读路线，带文件与行号锚点，每站配「读完自答」 |
| `02-test-map.md` | VH-03 相关测试清单、实测命令与结果、盲区 |
| `03-manual-repro-runbook.md` | 已实测的请求矩阵 + 需要你自己动手的 6 个实验 + 记录模板 |
| `04-question-sheet.md` | 两层题单：费曼理解输出 + 面试追问链，答案折叠在文末 |

## 3. 建议顺序

1. 先读 `01` 的第 1～3 站，在纸上画出「请求进来以后依次碰谁」。
2. 跑 `02` 里的命令，确认 25 条测试通过，并读懂每条在断言什么。
3. 按 `03` 的矩阵自己重放一遍，重点记录 4 类失败的**状态码 + 响应体形状 + requestId**。
4. 做 `03` 里 E-01（会话固定化）与 E-03（裸 cookie 令牌对照）两个实验，这两个最能暴露「只背定义」的空洞。
5. 闭卷答 `04` 第一层，再对照答案；第二层按 30 秒 / 3 分钟两档口述。

## 4. 已完成 / 待你完成

已完成（AI 实测，只是对照基线，不构成你的掌握证据）：

- 版本与源码归属判定（第 1 节），并确认 18080 / 18082 在 VH-03 上行为一致。
- 25 条相关单元测试离线实跑：`Tests run: 25, Failures: 0, Errors: 0`（2026-09-18，JDK 17.0.18 / Maven 3.9.4）。
- 只读请求矩阵与 6 个探针实验，共 30+ 条请求的状态码、响应体与关键响应头。

待你完成：

- 自己跑一次测试并读懂 surefire 报告。
- 自己在 18080 或 18082 上重放矩阵，登记 `requestId`。
- 做 E-01～E-06，尤其是「账号被停用后已登录会话的表现」——它需要一次写操作，得你确认。
- 闭卷回答 `04` 第一层，并口述第二层追问链。

## 5. 证据与状态边界

- 本包的分析、题单与参考答案都是 AI 生成草稿，不是掌握证据。
- 只有你自己完成的闭卷解释、独立操作与运行结果，才可能作为证据。
- 本包只输出状态变更**提案**；`main_checkpoint` 与 `mastery_status` 的任何改动都需要你给出「目标锚点 + 旧值 + 新值」的明确授权。
- 复现只在本地合成数据栈上进行；不要用真实账号、真实联系方式或生产凭据重放这些请求。
