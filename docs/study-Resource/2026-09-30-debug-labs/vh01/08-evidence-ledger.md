# 08 证据台账

按仓库要求，每个实验都要写清：**目标 / 假设 / 运行方法 / 结论 / 清理方式 / 是否值得迁移**。

## 1. 交付方（AI）执行记录 —— 只是基线，不是你的掌握证据

| # | 目标 | 假设 | 运行方法 | 结论 | 清理方式 | 值得迁移？ |
|---|---|---|---|---|---|---|
| A1 | 探针 A 能编译，并能证明列表/详情/关联/sitemap 四条读路径行为 | 手工组装 `ContentService` + 继承 `JdbcTemplate` 覆写 `query/queryForList/queryForObject` 足够，无需数据库 | 临时把探针 A 复制到 `apps/api/src/test/java/cn/voidhaven/api/service/`，`mvn -o -Dtest=ContentServiceDebugProbeTest test` | `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`，耗时 3.635 s，BUILD SUCCESS | 删除副本（已删），`git status` 只剩进场前的 `BootstrapAdminCommandTest.java` 改动 | 值得：可补"`ContentService` 无单元测试"盲区，但要按 `05` 第 9 节改造 |
| A2 | 基线驱动器测试在离线环境可跑 | `.m2` 已缓存依赖、`target/` 已有编译产物 | `mvn -o "-Dtest=CatalogCursorTest,CatalogSortTest,CatalogQueryTest,ContentSourcePolicyTest,ContentModuleRegistryTest,RelatedContentIdsTest,CatalogOrderServiceTest" test` | 7 个类共 `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`，总耗时 7.795 s，BUILD SUCCESS | 无（只生成 `target/` 产物，已 gitignore） | 不涉及 |
| A3 | 九站的行号锚点与代码一致 | 2026-09-30 快照的 `ContentService` / `CatalogCursor` / `CatalogSort` / `CatalogQuery` / `ContentSourcePolicy` / `PublicContentController` / `RequestIdFilter` / `ApiExceptionHandler` 未变 | 逐文件按锚点文本核对 | 全部命中 | 无 | 不涉及 |
| A4 | 探针 B 能证明入口层行为 | `MockMvcBuilders.standaloneSetup` + `setControllerAdvice(new ApiExceptionHandler())` 足以观察参数绑定与信封，无需 Spring 容器 | 临时复制到 `apps/api/src/test/java/cn/voidhaven/api/web/`，`mvn -o -Dtest=PublicContentControllerDebugProbeTest test` | 首次运行 4/5 通过（`$.fields` 写错，实际字段名是 `fieldErrors`），修正后 `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`，耗时 1.713 s | 删除副本（已删） | 值得：入口层此前完全没有可调试的测试 |
| A5 | 缺必填参数的返回码 | 以为会返回 400 | 探针 B 用例 5 打印实际响应 | **实测 500 `TEMPORARILY_UNAVAILABLE`**：`MissingServletRequestParameterException` 被 `@ExceptionHandler(Exception.class)` 兜住 | 只记录不改代码 | 待定：属产品行为边界，需产品仓库评估 |

**A1/A2 是执行方的验证结果，不构成你的掌握证据。** 只有你自己跑出来、写下来的记录才算。

## 2. 交付方遇到的坑（帮你省时间）

| 坑 | 现象 | 处理 |
|---|---|---|
| PowerShell 逗号拆参数 | `-Dtest=A,B,C` 直接把 B、C 当成新参数，报 `Missing argument in parameter list` | 整体加引号 `"-Dtest=A,B,C"` |
| `mvn` 不在 PATH | `mvn : The term 'mvn' is not recognized` | 用 `D:\Maven\apache-maven-3.9.4\bin\mvn.cmd` |
| 覆写 `JdbcTemplate` 的可行性需要先确认 | 担心方法签名不同或方法 final | 用 `javap` 查 spring-jdbc 7.0.9：`query(String, RowMapper<T>, Object...)` 与 `queryForObject(String, Class<T>, Object...)` 都存在，可覆写 |
| `ContentRevision` 的元数据字段不在构造器里 | 想造 `sourceMode=ORIGINAL` 的内容 | 构造后再调 `setEvolvableFields(markdown, documentVersion, schemaVersion, schemaSnapshotJson, metadataJson)` |

## 3. 学习者记录（你自己填）

### 3.1 九站执行记录

| 站 | 日期 | 是否真停住过 | 填完观察表 | 卡住的地方 |
|---|---|---|---|---|
| D1 游标 | | | | |
| D2 排序 | | | | |
| D3 SQL | | | | |
| D4 来源 | | | | |
| D5 主链 | | | | |
| D6 边界 | | | | |
| D7 详情与 404 | | | | |
| D8 关联与 sitemap | | | | |
| D9 HTTP 入口与信封 | | | | |

### 3.2 实验记录（按 `06-experiments.md` 编号）

| # | 目标 | 我的假设（运行前写） | 运行方法 | 结论（实际） | 清理方式 | 值得迁移？ |
|---|---|---|---|---|---|---|
| E1 | | | | | | |
| E2 | | | | | | |
| E3 | | | | | | |
| E4 | | | | | | |
| E5 | | | | | | |
| E6 | | | | | | |
| E7 | | | | | | |
| E8 | | | | | | |
| E9 | | | | | | |
| E10 | | | | | | |

### 3.3 收尾核对

| 检查项 | 结果 |
|---|---|
| 探针 A 副本已删除 | |
| 探针 B 副本已删除 | |
| `git -C 'D:\学习资料\voidhaven-platform' status --short` 只剩进场前那条 `BootstrapAdminCommandTest.java` | |
| `apps/api/src/main` 无任何改动 | |
| 未新增任何数据库写入、未启动 Docker/MySQL | |
| 闭卷答完 `07` 第一层并录了 3 分钟口述 | |
| 想落盘的注释已按 `09` 逐条确认 | |

## 4. 迁移评估（学完再答）

| 问题 | 回答 |
|---|---|
| 探针里哪些断言值得进产品测试？ | |
| 需要补哪些用例才算真正覆盖 `listCatalog`？ | |
| 哪些结论必须靠集成测试/真实栈补，不该放进单元测试？ | |
| 迁移走什么流程？ | 产品仓库建 Issue → 最小改动 → PR/CI，不从学习副本直接复制 |
