# 00 通用调试学习法

六层共用这一套方法。各层的 `01-debug-walkthrough.md` 给出精确驱动、夹具与断点，`03-step-runbook.md` 给出操作顺序，方法本身在这里。

## 1. 五类断点位置

不是随便点几行就能学到东西。每个站点固定从这五类位置里挑：

| 类型 | 放哪 | 为什么在这停 |
|---|---|---|
| 基准断点 | 驱动器测试的**断言前一行** | 拿到"全流程跑完后的最终状态"，作为对照 |
| 入口断点 | 被测方法**第一行** | 记录入参真实值，确认前置条件 |
| 分支断点 | 每个 `if / switch / try-catch` 的**第一行** | 看清走了哪条分支——这是读源码看不出来的信息 |
| 交互断点 | 每个**协作者交互点**（`verify` 前、替身 `thenReturn` 前后） | 区分"业务代码算了什么"和"它对外部依赖提了什么要求" |
| 状态断点 | **状态变更前后**（`submit()`、`publish()`、`beginCandidateRevision()`） | 对比双指针与两个"版本号"的迁移 |

## 2. 单步策略

| 操作 | IDEA 键（Windows 默认） | 什么时候用 |
|---|---|---|
| Step Over | `F8` | 默认动作，90% 时间用它 |
| Step Into | `F7` | 想进自己项目的方法（`CatalogQuery.selectIds`、`item.publish()`） |
| Step Out | `Shift+F8` | 进错了库代码，立刻出来 |
| Resume | `F9` | 站点之间跳转 |
| Run to Cursor | `Alt+F9` | 直接冲到目标行，不逐行按 |
| Evaluate Expression | `Alt+F8` | 现场求值、临时改状态验证假设 |
| Force Return | Frames 面板右键 | 让方法提前返回，观察下游失败分支 |
| Drop Frame | Frames 面板右键 | **回退重跑**（只对纯内存单测有效，且不退已发生的副作用） |
| 日志断点 | 断点右键 → 取消 Suspend + `Evaluate and log` | 循环里打印轨迹，不打断节奏 |
| 异常断点 | `View Breakpoints → + → Java Exception Breakpoints` | 一眼看到错误码从哪一行抛出（填 `cn.voidhaven.api.web.ApiException`） |

## 3. 观察记录规范

每张观察表固定四列：**观察点 / 我的预测 / 实际值 / 结论**。

三条纪律：

1. **先写预测再按 F8。** 顺序反了就只是抄答案，学不到任何东西。
2. **预测错了要写原因。** 错的预测才是信息量最大的那条记录。
3. **区分"代码行为"和"替身行为"。** `Mockito` 未打桩时返回 `null` / 空集合、`queryForObject` 返回 `null` 让排序回落默认值——这些是替身的效果，不是产品行为。

## 4. 注释规范

本套材料**不预先改任何主源码**。注释按下面的格式，在你亲手验证之后才落盘：

```java
// 学习·<层号>-已验证：<一句话结论>
```

| 规则 | 说明 |
|---|---|
| 只加不改 | `git diff` 里应该只有以 `//` 开头的行 |
| 一行一条 | 不写整段讲解；讲解留在文档里 |
| 有验证判据才写 | 每层 `02-questions-and-evidence.md` 给了判据问题，答不出来就别写 |
| 不写进 `@Query` 字符串 | 会插进 JPQL/SQL，容易引入笔误 |

## 5. 探针（probe）是什么

**探针 = 为了能下断点而写的临时驱动器测试。** 它出现在某一层缺少单元测试的主干路径上（例如 VH01 的 `ContentService`、VH02 的 `revise`）。

| 规则 | 说明 |
|---|---|
| 常驻文档包 | 探针源码放在对应层的 `probe/` 目录，不随产品源码提交 |
| 用时复制 | 拷进 `apps/api/src/test/java/cn/voidhaven/api/<包>/` 后在 IDEA 里 Debug |
| 用完删除 | 学完必须删副本并确认 `git status` 干净 |
| 交付前跑绿 | 每层探针都经过 `mvn -o -Dtest=<探针类> test` 验证，保证你按 F9 不会先撞编译错误 |
| 不代表产品测试 | 探针里的替身、桩值都是"为了看得见"，不是产品断言资产 |

安装 / 清理：VH02～VH06 使用包根 `manage-probes.ps1`，按各层 `05-probe-test.md` 操作；不再使用覆盖复制和无条件删除模板。VH01 仍按自己的探针说明。

## 6. 环境与坑（六层通用）

| # | 坑 | 处理 |
|---|---|---|
| 1 | `mvn` / `npm` / `docker` 都不在 PATH | Maven 用 `D:\Maven\apache-maven-3.9.4\bin\mvn.cmd`；npm 在 `C:\Program Files\nodejs\npm.cmd`；Docker 确实没有 CLI |
| 2 | 一个文件里有多个测试类 | `CatalogCursorTest.java` 里同时有 `CatalogCursorTest` / `CatalogSortTest` / `CatalogQueryTest`；按类名跑有效，按文件搜只能找到第一个 |
| 3 | PowerShell 把逗号当参数分隔符 | 多类一起跑必须整体加引号：`"-Dtest=A,B,C"` |
| 4 | 全量 `mvn test` 会拉起 Testcontainers 用例 | 这个 pom 没配 `excludedGroups`；Docker 不可用时它们是**静默 Skipped**，报告里看 `Skipped` 列 |
| 5 | `mvn verify` 才会跑 `*IT` | `DistributedSafetyIT` 以 `IT` 结尾，`mvn test` 不执行它 |
| 6 | Mockito 默认返回值会骗人 | 未打桩的 `query` 返回空 List、`queryForObject` 返回 `null`；后者会让 `configuredSort` 悄悄回落默认值 |
| 7 | IDEA 打开位置 | 打开 `apps/api` 而不是仓库根目录（根目录会把 `node_modules` 一起索引） |
| 8 | 类没有编译 | 断点是灰底红圈时先 `Run` 一次让 IDEA 编译 |

## 7. 一次完整的调试学习循环

```text
① 定链路（一次只学一条，用一句话写出来）
   → ② 选驱动器（挑断言最全的那个测试，或用探针）
   → ③ 布断点（从上表五类位置里挑，总数控制在 10 个以内）
   → ④ 单步 + 改数据 + 观察（先预测，再按 F8）
   → ⑤ 填观察表 + 做预测实验 + 写注释候选
   → ⑥ 记录证据台账（目标/假设/运行方法/结论/清理/是否迁移）
```

## 本版证据纪律

源码推断、单测/探针、调试器暂停、真实环境分开记录。BUILD SUCCESS 不能代替断点证据；Skipped 不计通过。每站先填预测后填实际。实验脚本一次只运行一个，执行期间不要同时编辑相同文件；finally 恢复原字节后再重跑基线。匿名接口并非原理上不能使用 CSRF 令牌，幂等和限流也不等价于 CSRF 防护。
