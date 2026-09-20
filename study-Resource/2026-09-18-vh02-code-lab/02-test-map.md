# VH-02 测试地图（含实测结果与盲区）

所有结果来自本机 2026-09-18 实测。命令工作目录：`D:\Projects\voidhaven\voidhaven-platform\apps\api`。

## 1. 与 VH-02 相关的测试资产

| 测试类 | 用例数 | 覆盖什么 | 用什么打桩 | 不能证明什么 |
|---|---|---|---|---|
| `domain/ContentItemStateTest` | 4 | `publish` / `archive` / `closePublic` / `restore` 的字段联动 | 纯对象，无桩 | 前置条件（谁允许调用）、持久化 |
| `service/ContentManagementServiceTest` | 4 | `create` 写类型明细、非中文缺原始标题、`decide` 版本冲突、`reopen` 被拒内容 | Mockito：`ContentItemRepository`、`ContentRevisionRepository`、`JdbcTemplate`、`AuditService` | **没有覆盖 `revise`、`publish`、`hide/show/archive/restore/delete`** |
| `service/ContentRevisionCandidateServiceTest` | 2 | `cloneCurrent` 复制哪些表、修订号取 `max+1`、`publishedRevision` 不变 | Mockito：修订仓储 + `JdbcTemplate` | 真实唯一约束、真实并发 |
| `service/ContentLifecycleServiceTest` | 1 | 竞赛按 `competition_revision_details.deadline_date` 关闭，并写审计 | Mockito：仓储 + `JdbcTemplate` | cron 触发、咨询锁、批量事务边界 |
| `service/ContentActionServiceTest` | 5 | 已发布内容替换行动入口时只写入新候选、审核中不可改、二维码需确认等 | Mockito | 与 `revise` 的交互、真实唯一约束 |
| `service/IdempotencyServiceTest` | 5 | 键缺失/超长、同键同体重放、同键异体、敏感结果不可重放 | Mockito：`JdbcTemplate`、预留服务 | 并发下的真实竞争（需库支持） |
| `integration/DistributedSafetyIT` | 1（含多段） | 限流、咨询锁（含 `content-lifecycle` 锁名）、媒体租约的真实并发 | Testcontainers MySQL | **不涉及内容修订逻辑** |
| `integration/MySqlAndMinioIntegrationTest` | 1 | Flyway 23 个迁移能在空库跑完、关键表/列存在、私有桶读写 | Testcontainers MySQL + MinIO | 业务状态机 |
| `web/e2e/site.spec.ts:242` | 1 | 管理员生命周期 UI 闭环（送审→审核→发布→隐藏→公开→归档→恢复） | Playwright 拦截 `/api/v1/**` | 真实后端 |
| `web/e2e/site.spec.ts:271` | 1 | 候选修订保留富文本/隐藏字段，冲突不丢输入 | Playwright 拦截 | 真实后端 |
| `web/e2e/site.spec.ts:612` | 1 | 已发布内容「保存候选修订并继续编辑」后可上传图片 | Playwright 拦截 | 真实后端 |

## 2. 实测命令与结果

### 2.1 定向跑 VH-02 相关单测

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform\apps\api'
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'
.\mvnw.cmd -o -B test "-Dtest=ContentItemStateTest,ContentLifecycleServiceTest,ContentManagementServiceTest,ContentRevisionCandidateServiceTest"
```

实测结果（2026-09-18 11:45:40，离线模式 `-o`）：

```text
Running cn.voidhaven.api.domain.ContentItemStateTest        Tests run: 4, Failures: 0, Errors: 0
Running cn.voidhaven.api.service.ContentLifecycleServiceTest Tests run: 1, Failures: 0, Errors: 0
Running cn.voidhaven.api.service.ContentManagementServiceTest Tests run: 4, Failures: 0, Errors: 0
Running cn.voidhaven.api.service.ContentRevisionCandidateServiceTest Tests run: 2, Failures: 0, Errors: 0
Results: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

注意两个细节：

- `-Dtest=A,B,C` 用**逗号**分隔。用 `+` 会报 `No tests matching pattern`。
- 想看具体断言要打开 `target/surefire-reports/cn.voidhaven.api.<类名>.txt`，命令行只给汇总。

### 2.2 全量套件

```powershell
.\mvnw.cmd -o -B test
```

实测结果：`Tests run: 126, Failures: 0, Errors: 1`，`BUILD FAILURE`。

唯一一条错误不是断言失败：

```text
MySqlAndMinioIntegrationTest.migrates_an_empty_mysql_database_and_uses_a_private_s3_compatible_bucket
  » UnsatisfiedLink Failed to create temporary file for /com/sun/jna/win32-x86-64/jnidispatch.dll library: 拒绝访问。
```

性质：JNA 要往临时目录释放 `jnidispatch.dll`，被当前沙箱拒绝。属于**环境限制**，不是代码缺陷；在普通终端重跑该用例即可确认。

### 2.3 `verify` 与 `*IT`

`pom.xml:50-58` 只配了 `maven-failsafe-plugin`（`integration-test` + `verify`），所以：

- `mvnw test` 跑 surefire，匹配 `*Test`。
- `DistributedSafetyIT` 名字以 `IT` 结尾，**`mvnw test` 不会执行它**，必须 `mvnw verify`。
- `MySqlAndMinioIntegrationTest` 名字以 `Test` 结尾，所以 `mvnw test` 会执行它；它内部还有 `Assumptions.assumeTrue(dockerAvailable(), ...)`（第 28 行），Docker 不可用时会记成 `Skipped` 而不是 `Failure`，所以只看「Failures: 0」会误以为这条链路被覆盖过。本轮它连 Docker 判定都没走到，就先被 JNA 报错拦下了。

### 2.4 前端测试本轮无法执行

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform\apps\web'
npx vitest run
npx playwright test -g "管理员生命周期与关键操作原因"
```

两条命令在本沙箱内都以 `Error: spawn EPERM` 结束（Vite 在 Windows 上要 `exec` 系统命令做真实路径解析，Playwright 要拉起 dev server）。需要在**非沙箱终端**里跑。另外，`playwright.config.ts` 的 `webServer` 指向本地 Vite，`e2e` 里大量拦截 `/api/v1/**`，所以即使跑通也只能标记为 `FRONTEND_MOCK_E2E`。

## 3. VH-02 行为 × 证据层矩阵

`✔` = 有覆盖，`△` = 部分覆盖，`✘` = 无覆盖。

| 行为 | Java 单测 | 集成 IT | Playwright（mock） | 真实栈手动复现 |
|---|---|---|---|---|
| 四维状态字段联动（`ContentItem` 状态方法） | ✔ | ✘ | △ | ✔ |
| `create` 的投影写入 | ✔ | △（表存在性） | ✘ | ✔ |
| `revise` 在 PUBLISHED 时新建修订行 | ✘ | ✘ | △ | ✔ |
| `revise` 在 DRAFT 时就地覆盖同一修订行 | ✘ | ✘ | ✘ | ✔ |
| 候选期公开页仍读旧 `publishedRevision` | ✘ | ✘ | ✘ | ✔ |
| 再次发布后 `publishedRevision` 切换 | ✘ | ✘ | △ | ✔ |
| 乐观锁 `If-Match` 冲突 | ✔（`decide`） | ✘ | ✔ | ✔ |
| 审核原因必填 | ✘ | ✘ | △ | ✔ |
| 归档后恢复仍隐藏、必须重新发布 | ✔（实体） | ✘ | △ | ✔ |
| 到期自动关闭 / 三天后归档 | ✔（单条） | ✘ | ✘ | ✘（需等 cron） |
| 合作方发布配额 | ✘ | ✘ | ✘ | ✘（本轮未执行） |
| 招募发布前必须有有效行动入口 | ✘ | ✘ | ✘ | ✔ |
| 正文图片引用与已上传媒体一致 | ✘ | △（只验证媒体侧） | △ | ✘（本轮未上传图片） |
| 幂等同键同体重放 | ✔ | ✘ | ✔ | ✔ |
| 幂等同键异体 | ✔ | ✘ | ✘ | ✔ |
| 并发编辑产生的 `(item, revision_number)` 唯一约束竞争 | ✘ | ✘ | ✘ | ✘ |

最需要你自己补的四个格子：

1. `revise` 两条分支（单测完全缺失，只能靠手动复现或自己写测试）。
2. 到期自动关闭（要等 cron 或改 `voidhaven.lifecycle.cron` 后重启）。
3. 合作方配额（需要 `local-partner` 账号 + 调整配额）。
4. 真实并发编辑（需要两个线程/两个会话同时对同一内容提交，观察 409 与唯一约束）。

## 4. 判断测试质量的三个提问（面试也会问）

1. **这条测试如果删掉，代码会以什么方式坏掉？** 例如删掉 `clone_uses_the_highest_existing_revision_number`，`cloneCurrent` 即使写成 `source.getRevisionNumber()+1` 也能通过另一条测试，但会在「历史修订号有空洞」时撞唯一约束。
2. **测试里的数字是真实观察还是编出来的？** `ContentRevisionCandidateServiceTest` 把 `max(revision_number)` 打桩成 7，断言新修订号是 8 —— 这验证了「用 max 而不是用 current」，但完全没验证 SQL 本身。
3. **Mock 掉的东西会不会正好是要验证的东西？** `ContentManagementServiceTest` 把 `JdbcTemplate` 整个 mock 掉，所以「投影写对了吗」只能验证调用了哪条 SQL 前缀，不能验证参数和约束。
