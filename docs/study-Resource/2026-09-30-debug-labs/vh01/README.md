# VH-01-DEBUG：用调试器走通 L3 公开读路径

| 项 | 值 |
|---|---|
| 学习副本 | `D:\学习资料\voidhaven-platform` |
| 代码版本 | HEAD `0bd9677`（2026-09-26），工作树只有 1 项既有改动（`apps/api/src/test/.../BootstrapAdminCommandTest.java`，**不是本包产生的**，不要顺手改） |
| 材料日期 | 2026-09-30 |
| 工具链 | IntelliJ IDEA 2026.2、JDK 17.0.18、Maven 3.9.4（`D:\Maven\apache-maven-3.9.4`），离线可跑 |
| 数据边界 | 全部为合成 ULID、合成标题、`example.edu` 合成来源；无真实账号、联系方式、业务数据 |
| 与旧包关系 | 本包是 [2026-09-18-vh01-code-lab](../../2026-09-18-vh01-code-lab/README.md) 的第四层证据。旧包是"读代码 + 看测试地图 + HTTP 手动复现"；本包是"下断点 + 单步 + 观察 + 注释" |

## 0. 本包解决什么问题

旧包 `02-test-map.md` 的盲区清单第一条写着：**`ContentService` 没有单元测试**，`listCatalog` 的参数校验、`nextCursor` 生成、`hasPublicSource` 过滤都没有自动化断言，只能靠手动复现和读代码。

读代码能告诉你"应该是什么"，调试器能让你"亲眼看到它是什么"。本包把整条公开读路径拆成 **9 个断点站点**，用 2 个探针测试 + 7 个现成测试做驱动器，让每一行都能停下来看值。

## 1. 九站路线

| 站 | 驱动器 | 主干落点 | 亲眼要看到的事 |
|---|---|---|---|
| D1 游标 | `CatalogCursorTest` | `CatalogCursor.encode / decode` | `c1.` + Base64 载荷长什么样；四条出口分别在哪一行返回 |
| D2 排序 | `CatalogSortTest` | `CatalogSort.parse / resolve` | 用户显式 > 后台默认；`RESOURCE` 配 `DEADLINE` 静默降级，用户点名要则 400 |
| D3 SQL | `CatalogQueryTest` | `selectIds / appendCursor / encodeKey` | 用日志断点看 SQL 一段段拼出来，以及 args 的顺序 |
| D4 来源 | `ContentSourcePolicyTest` | `isAllowed` | 每个 `return false` 分支停一次，看清 domain / IP / userinfo 怎么被拦下 |
| D5 主链 | 探针 A 用例 1～2 | `ContentService.listCatalog` 全 12 个节点 | 夹取、`pageSize+1`、`hasNext` 截页、`inOrder` 重排、来源过滤后本页变短、游标取原始页末条 |
| D6 边界 | 探针 A 用例 3～7 | 同上 | 恰好一页、`pageSize=0/999`、游标排序不匹配、模块原创差异、后台误配降级 |
| D7 详情与 404 | 探针 A 用例 8～10 | `publicDetail` / `toPublic` | 媒体开关 `includeMedia` 的真实效果；"来源不合规"和"不存在"为什么是同一个 404 |
| D8 关联与 sitemap | 探针 A 用例 11～12 | `related` / `publicSitemapEntries` | 关系表 + 详情字段怎么求并集去重；sitemap 与详情为什么共用同一条可见性谓词 |
| D9 HTTP 入口与信封 | 探针 B 用例 1～5 | `PublicContentController` + `ApiExceptionHandler` | 参数默认值 20 从哪来；`requestId` 来自 Filter；成功/错误两种信封形状；未捕获异常怎么兜 |

## 2. 文件清单

| 文件 | 用途 |
|---|---|
| `01-debug-setup.md` | IDEA 配置 + 断点能停住的自检 + 环境坑 |
| `02-breakpoint-map.md` | 九站断点地图：文件、锚点、断点类型、条件、预期值、排查 |
| `03-step-runbook.md` | 逐站按键脚本、Evaluate 表达式、条件/日志断点、Force Return 与 Drop Frame |
| `04-observation-sheets.md` | 九张观察表（先填，参考答案折叠在文末） |
| `05-probe-test.md` | 探针 A/B：用途、复制/清理命令、17 个用例各看什么 |
| `probe/ContentServiceDebugProbeTest.java` | 探针 A：Service 层 12 个用例（常驻本包，不随产品源码提交） |
| `probe/PublicContentControllerDebugProbeTest.java` | 探针 B：Controller 层 5 个用例 |
| `06-experiments.md` | 10 个"先预测再验证"实验 |
| `07-question-sheet.md` | 闭卷题单：旧包 F1/F2/F4 + 新增 D1–D9 + 面试追问链 |
| `08-evidence-ledger.md` | 证据台账：目标/假设/运行方法/结论/清理方式/是否迁移 |
| `09-verified-annotations.md` | 待你验证后才落盘的主源码注释清单 |
| `vh01-debug-sequence.mmd` / `.png` | 测试链路时序图（测试 / Service / CatalogQuery / 替身协作者） |
| `vh01-debug-flow.mmd` / `.png` | 列表读路径分支流程图（D5/D6，标注实测取值） |
| `vh01-debug-detail-flow.mmd` / `.png` | 详情、关联、sitemap 与 HTTP 信封流程图（D7/D8/D9） |

## 3. 建议顺序（约 4～5 小时，可分两次）

1. `01` 配好环境，让断点能停在 `CatalogCursorTest` 上（15 分钟）。
2. 第一批：D1 → D2 → D3 → D4，每站 20～30 分钟，先读断点地图，再单步，再填 `04`。
3. 第二批：拷入探针 A，走 D5（40 分钟）→ D6（20 分钟）→ D7（20 分钟）→ D8（20 分钟）。
4. 拷入探针 B，走 D9（25 分钟）——这一站第一次看到 HTTP 信封和错误映射。
5. `06` 的 10 个实验挑 6 个做，每个都"先写预测再运行"（35 分钟）。
6. 闭卷答 `07` 第一层 + 口述 3 分钟（25 分钟），最后清理并把结果填进 `08`。

## 4. 运行方法

```powershell
# 工作目录必须在这里，Maven 用绝对路径（mvn 不在 PATH）
Set-Location 'D:\学习资料\voidhaven-platform\apps\api'
$env:JAVA_HOME = 'D:\JDK\JDK17'
$mvn = 'D:\Maven\apache-maven-3.9.4\bin\mvn.cmd'

# 基线四站（D1～D4），离线可跑
& $mvn -o "-Dtest=CatalogCursorTest,CatalogSortTest,CatalogQueryTest,ContentSourcePolicyTest,ContentModuleRegistryTest,RelatedContentIdsTest,CatalogOrderServiceTest" test

# 两个探针（先按 05-probe-test.md 复制进对应测试目录）
& $mvn -o "-Dtest=ContentServiceDebugProbeTest,PublicContentControllerDebugProbeTest" test
```

报告位置：`apps/api/target/surefire-reports/`。先看 `Tests run` 后的 `Failures / Errors / Skipped`，再看耗时。

## 5. 本包交付前的实测基线

2026-09-30 实测（JDK 17.0.18 / Maven 3.9.4 / 离线 `-o`）：

```text
ContentServiceDebugProbeTest            Tests run: 12, Failures: 0, Errors: 0, Skipped: 0   Time elapsed: 3.635 s
PublicContentControllerDebugProbeTest   Tests run:  5, Failures: 0, Errors: 0, Skipped: 0   Time elapsed: 1.713 s
CatalogCursorTest                       Tests run:  2, Failures: 0, Errors: 0, Skipped: 0
CatalogOrderServiceTest                 Tests run:  3, Failures: 0, Errors: 0, Skipped: 0
CatalogQueryTest                        Tests run:  2, Failures: 0, Errors: 0, Skipped: 0
CatalogSortTest                         Tests run:  1, Failures: 0, Errors: 0, Skipped: 0
ContentModuleRegistryTest               Tests run:  2, Failures: 0, Errors: 0, Skipped: 0
ContentSourcePolicyTest                 Tests run:  3, Failures: 0, Errors: 0, Skipped: 0
RelatedContentIdsTest                   Tests run:  2, Failures: 0, Errors: 0, Skipped: 0
合计                                    Tests run: 32, Failures: 0, Errors: 0
```

探针是交付前跑绿后才放进本包的，所以你按下 F9 时不会先撞到编译错误。这条基线是**我的**执行结果，不是你的掌握证据。

## 6. 一个实测发现的边界（值得记住）

探针 B 的用例 5 记录了一条真实行为：`GET /api/v1/public/content` **缺少必填参数 `module`** 时，返回的是

```text
500 TEMPORARILY_UNAVAILABLE   （不是 400）
```

原因是这个参数没有默认值，缺失时抛的是 Spring MVC 的 `MissingServletRequestParameterException`，而 `ApiExceptionHandler` 里没有专门处理它，于是被 `@ExceptionHandler(Exception.class)` 通用兜底接住。

本包只把它记成**观察结论**，不判定为缺陷、也不改代码。它同时说明一件事：**通用兜底会把"客户端传错"和"服务端坏了"混在一起**——这正是为什么读路径的参数校验要尽量写在 Service 里显式抛 `ApiException`。

## 7. 完成标准

- 九站每一站至少有一个断点真正停住过，并把实际值填进 `04`。
- `06` 里至少 6 个实验做到"先写预测、再运行、后对照"。
- 闭卷答 `07` 第一层，并录一次 3 分钟口述。
- 结束时 `apps/api` 下 `git status` 只剩那条进场前就存在的 `BootstrapAdminCommandTest.java` 改动，两个探针副本都已删除。
- 想要注释落盘时，按 `09` 的清单逐条确认后再写。

## 8. 边界

- 本轮只做公开读路径（L3）。写入幂等（L1）、安全入口（L4）、媒体（L5）不在本包内。
- 两个探针都用替身协作者，**证明不了**真实 MySQL 的执行计划、索引使用与并发行为；探针 B 是 standalone MockMvc，**不经过 Security 过滤器链**，证明不了 CSRF、会话与授权。这些属于集成测试与真实栈的范围，旧包 `02-test-map.md` 第 5 节已说明。
- 本包不改 `apps/api` 源码、不改迁移、不改契约；所有材料只在 `docs/` 下。
