# VH-01 测试地图：现有测试证明了什么

## 0. 先跑一遍（离线也能跑）

工作目录：`D:\Projects\voidhaven\voidhaven-platform\apps\api`

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform\apps\api'

# 四条纯单元测试，不依赖 MySQL / MinIO / Docker
mvn -o -Dtest='CatalogCursorTest' test
mvn -o -Dtest='CatalogSortTest' test
mvn -o -Dtest='CatalogQueryTest' test
mvn -o -Dtest='ContentSourcePolicyTest' test

# 或一次跑完这四条
mvn -o -Dtest='CatalogCursorTest,CatalogSortTest,CatalogQueryTest,ContentSourcePolicyTest' test
```

两条提示：

1. 仓库里的 `mvnw.cmd` 只有 `.mvn/wrapper/maven-wrapper.properties`，没有 wrapper jar，离线环境下可能先去下载发行包；本机已有 `D:\Maven\apache-maven-3.9.4`，直接用 `mvn` 更稳。
2. `-o` 是离线模式。本机 `.m2` 仓库已完整，四条测试离线可跑。

本机实测（2026-09-18 11:41，JDK 17.0.18 / Maven 3.9.4）：

```text
CatalogCursorTest        Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
CatalogQueryTest         Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
CatalogSortTest          Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
ContentSourcePolicyTest  Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Results: Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS（Total time 6.558 s）
```

报告位置：`apps/api/target/surefire-reports/`。看报告先看 `Tests run` 后面的 `Failures / Errors / Skipped`，再看 `Time elapsed`——本轮四条加起来不到 0.3 秒，说明它们都是纯逻辑测试，没有任何 IO。

## 1. 一个容易踩的坑

`CatalogCursorTest`、`CatalogSortTest`、`CatalogQueryTest` 三个类写在**同一个文件** `src/test/java/cn/voidhaven/api/service/CatalogCursorTest.java` 里，都是包级可见类。

- 按文件搜索只会找到 `CatalogCursorTest.java`；
- 按类名跑 `-Dtest=CatalogSortTest` 是有效的，因为编译后各自生成独立 class 文件；
- 想跟着文件读代码时，别以为后两个类不存在。

## 2. 逐条读测试在断言什么

文件：`apps/api/src/test/java/cn/voidhaven/api/service/CatalogCursorTest.java`

| 类 / 用例 | 断言内容 | 覆盖的行为 |
|---|---|---|
| `CatalogCursorTest.round_trips_opaque_tokens_and_rejects_sort_mismatch` | `encode()` 结果以 `c1.` 开头；解码后 `key`、`id`、`legacy=false` 均保持；用 `UPDATED` 解码 `DEADLINE` 游标抛 `CATALOG_CURSOR_MISMATCH` | 游标往返 + 排序绑定 |
| `CatalogCursorTest.accepts_legacy_ulid_only_for_default_sort` | 26 位 ULID 在 `DEFAULT` 下得到 `legacy=true`；在 `UPDATED` 下抛 `CATALOG_CURSOR_INVALID`；`not-a-cursor` 抛 `CATALOG_CURSOR_INVALID` | 旧游标兼容 + 非法输入 |
| `CatalogSortTest.user_choice_beats_admin_default_and_resources_cannot_use_deadline` | 显式 > 后台默认；`RESOURCE` 配置 `DEADLINE` → 降级 `UPDATED`；`RESOURCE` 显式 `deadline` → `CATALOG_SORT_UNSUPPORTED`；`popularity` → `CATALOG_SORT_INVALID` | 排序解析与降级规则（5 条断言） |
| `CatalogQueryTest.default_sort_uses_manual_order_then_id_and_encodes_stable_keys` | SQL 含 `left join catalog_order_entries`、`coalesce(o.sort_order, 20000)`、`c.id < ?`；参数含 `COMPETITION`、`"数学"`、`2`、id、`21`；`encodeKey(DEFAULT, null) == "20000"`、`encodeKey(DEADLINE, null) == "~"` | DEFAULT 排序 SQL 形状 + 空 key 归一化 |
| `CatalogQueryTest.deadline_cursor_places_nulls_last` | SQL 含 `coalesce(cd.deadline_date, rd.deadline_date) is null then 1 else 0`、`f.value_number = ?`、`c.operational_status = ?`；参数含 `RECRUITMENT`、`headcount`、`OPEN` | DEADLINE 空值排最后 + 类型化筛选 + 状态过滤 |

文件：`apps/api/src/test/java/cn/voidhaven/api/service/ContentSourcePolicyTest.java`

| 用例 | 断言内容 |
|---|---|
| `accepts_a_real_https_source_in_every_profile` | 非本地模式下 `https://contest.example.edu/rules` 放行 |
| `rejects_local_and_reserved_sources_outside_the_local_profile` | `http://`、`example.invalid`、`127.0.0.1`、`192.168.1.2`、`metadata.internal`、带凭据 URL 全部拒绝，且错误信息包含 `HTTPS` |
| `accepts_only_the_configured_local_fixture_source_when_explicitly_enabled` | 开启本地 fixture 后，只有配置里的 `http://localhost:18080/local-fixtures/source` 放行，`8080` 同路径仍拒绝 |

## 3. 相邻测试（不直接属于 VH-01，但读列表时会遇到）

| 文件 | 相关性 |
|---|---|
| `service/ContentModuleRegistryTest.java` | 模块定义、可筛字段解析；`filterKey` 校验依赖它 |
| `service/CatalogOrderServiceTest.java` | 后台人工排序：拒绝旧版本、拒绝重复内容、拒绝给资源设 DEADLINE 默认排序、复用已有行 |
| `service/RelatedContentIdsTest.java` | `related()` 里从 `details` 抽取关联 id |
| `service/HomepageAssemblerTest.java`、`HomepageEligibilityTest.java`、`HomepageAdministrationServiceTest.java` | 首页四槽位编排；列表页打开时也会读 `/home` |
| `integration/MySqlAndMinioIntegrationTest.java` | 迁移共 23 个版本、`catalog_order_entries` 与 `catalog_sort_settings` 建表成功、默认排序设置 3 行 |

## 4. 盲区清单（本轮的真正重点）

| 盲区 | 现状 | 后果 | 现在靠什么覆盖 |
|---|---|---|---|
| `ContentService` 没有单元测试 | 测试目录里没有 `ContentServiceTest` | `listCatalog` 的参数校验（`CONTENT_FILTER_INCOMPLETE`、`CATALOG_STATUS_INVALID`、`CONTENT_FILTER_INVALID`）、`nextCursor` 生成、`hasPublicSource` 过滤都没有自动化断言 | 只能靠手动复现（`03`）+ 源码阅读 |
| `CatalogQueryTest` 只断言 SQL 字符串 | 用 `assertThat(sql.sql()).contains(...)` | 拼接出来的 SQL 是否真能被 MySQL 执行、索引是否被用到、空值分支真实行为，全都没证明 | 需要集成测试或手动 SQL 验证 |
| `nextCursor` 取原始页最后一条这条规则 | 无测试 | 一旦有人改成「取可见列表最后一条」，测试不会红 | 源码阅读 + 概念理解 |
| 「本页 0 条但 `nextCursor` 非空」 | 无测试 | 客户端行为退化不会被发现 | 源码阅读 + 自造数据集 |
| `pageSize` 夹取 | 无测试 | `0`、`-5`、`999` 的真实行为只能靠请求验证 | 手动复现 |
| 真实 `hasPublicSource` 过滤路径 | 无测试（策略类本身有测试，过滤调用点没有） | 「来源不合规的已发布内容不会出现在列表」这条产品承诺没有回归保护 | 需要历史脏数据才能复现，本机合成数据造不出来 |
| 前端 `nextCursor` 处理 | `apps/web/src/lib/*.test.ts` 有单元测试，但不覆盖空页边界 | 空页 + 有下一页时 UI 文案可能误导 | 前端单测 + 阅读 `public.tsx` |

## 5. `mvn test` 与 `mvn verify` 的差别（跑全量前必看）

- pom 里没有配置 surefire 的 `excludedGroups`，所以 `MySqlAndMinioIntegrationTest`（`@Tag("integration")`）**会被 `mvn test` 执行**，它用 Testcontainers 启动 `mysql:8.4` 和 MinIO，必须本机 Docker 可用。
- 本机 2026-09-18 11:05 那次全量 `mvn test` 中，这个用例是 `Errors: 1`，异常是 `ContainerLaunchException: Container startup failed for image mysql:8.4`，根因 `Status 503: Docker Desktop is unable to start`，耗时 130.2 秒。
- 也就是说：**跑全量测试前确认 Docker Desktop 正常**，否则会看到一条与业务无关的红。
- `mvn verify` 会额外跑 failsafe（`*IT.java`，例如 `DistributedSafetyIT`）。

## 6. 可以自己加的实验（改测试，不改主代码）

1. 给 `CatalogQueryTest` 加一条 `UPDATED` 排序的游标断言，检查是否出现 `c.updated_at < ?` 而不是 `>`。
2. 给 `CatalogCursorTest` 加一条「`c1.` 载荷里 sort 名写成 `SOMETHING`」的用例，确认拿到的是 `CATALOG_CURSOR_INVALID`（`valueOf` 抛错被兜住）。
3. 给 `CatalogCursorTest` 加一条「`c1.` 载荷两段」的用例，确认 `parts.length != 3` 走无效分支。
4. 想验证 `parseRank` 静默回落：构造 `DEFAULT|abc|<ulid>` 游标，观察 SQL 参数变成 `20000` 而不是报错。

这些实验只改测试文件，属于可逆操作；跑完记得看清 `Failures` 是你预期的那一条。
