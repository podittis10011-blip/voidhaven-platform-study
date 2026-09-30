# 05 探针测试（A + B）

## 1. 复制 / 清理（先看这段）

```powershell
$lab      = 'D:\学习资料\voidhaven-platform'
$probeDir = "$lab\docs\study-Resource\2026-09-30-debug-labs\vh01\probe"
$testRoot = "$lab\apps\api\src\test\java\cn\voidhaven\api"

# 挂进来（两个探针各去自己的包目录，别拷错）
Copy-Item "$probeDir\ContentServiceDebugProbeTest.java"          "$testRoot\service" -Force
Copy-Item "$probeDir\PublicContentControllerDebugProbeTest.java" "$testRoot\web"     -Force

# 跑
Set-Location "$lab\apps\api"
$env:JAVA_HOME = 'D:\JDK\JDK17'
& 'D:\Maven\apache-maven-3.9.4\bin\mvn.cmd' -o "-Dtest=ContentServiceDebugProbeTest,PublicContentControllerDebugProbeTest" test

# 用完清理（IDEA 里右键 Delete 同理）
Remove-Item "$testRoot\service\ContentServiceDebugProbeTest.java" -Force
Remove-Item "$testRoot\web\PublicContentControllerDebugProbeTest.java" -Force
git -C $lab status --short
```

清理后 `git status` 应该只剩一条 **你进场前就存在**的改动：`M apps/api/src/test/java/cn/voidhaven/api/config/BootstrapAdminCommandTest.java`。那不是本包产生的，别顺手改它。

## 2. 两个探针各补哪个洞

| 探针 | 位置 | 补的洞 | 覆盖站点 |
|---|---|---|---|
| A `ContentServiceDebugProbeTest` | `service` 包 | 旧包盲区清单第一条：`ContentService` 没有单元测试，`listCatalog` / `publicDetail` / `related` / `publicSitemapEntries` 都没法下断点 | D5 / D6 / D7 / D8 |
| B `PublicContentControllerDebugProbeTest` | `web` 包 | 读路径的入口层没有可调试的测试：参数绑定、默认值、两种信封、错误映射只能靠读代码 | D9 |

两个探针都不是产品测试资产，**交付前我已跑绿**，所以你 Debug 时不会先撞编译错误。

## 3. 探针 A 的技术选择：为什么继承 `JdbcTemplate`

| 方案 | 问题 |
|---|---|
| `mock(JdbcTemplate.class)` + `when(jdbc.query(anyString(), any(), any()))` | `query(String, RowMapper, Object...)` 是可变参数，matcher 写法容易踩坑；SQL 与参数藏在 invocation 里，调试时不好看 |
| 继承 `JdbcTemplate` 覆写三个方法 | 把 SQL 和 args 存成字段；断点直接下在覆写方法里，点开就能看 |

覆写签名（spring-jdbc 7.0.9 实测存在）：

```java
public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args)
public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args)
public <T> T queryForObject(String sql, Class<T> requiredType, Object... args)
```

探针里没有 DataSource，这三个方法也永不调用真实实现，所以不会去连数据库。

## 4. 探针 A 的十二个用例

| # | 站点 | 用例 | 断点重点 | 要回答的问题 |
|---|---|---|---|---|
| 1 | D5 | `listCatalog_pages_the_raw_page_and_encodes_the_cursor_from_its_last_row` | `ContentService:51/90/98/102/105/110/215/221/116/120` | 3 条怎么查成 2 条、游标取谁、`inOrder` 为什么要存在 |
| 2 | D5 | `source_filter_can_shrink_a_page_while_the_cursor_still_follows_the_raw_page` | `:110`、`:116` | 本页变短了，游标为什么还指向被丢掉的那条 |
| 3 | D6 | `exactly_page_size_rows_means_there_is_no_next_page` | `:102`、`:112` | 为什么"刚好一页"不是"还有下一页" |
| 4 | D6 | `legacy_resource_module_rejects_original_source_while_resource_article_allows_it` | `:185/186/189/192` | 同一个 `sourceMode=ORIGINAL`，为什么模块不同结果不同 |
| 5 | D6 | `page_size_is_clamped_to_one_through_fifty_before_the_sql_is_built` | `:51` | 夹取发生在建 SQL 之前还是之后 |
| 6 | D6 | `cursor_sort_mismatch_fails_before_any_catalog_query_runs` | `:73` + `CatalogCursor:44` | 为什么排序不匹配时一次 SQL 都不会发 |
| 7 | D6 | `admin_default_deadline_on_resource_is_silently_downgraded_to_updated` | `:72`、`:198` | 后台误配降级与用户显式报错，是不是同一个 `if` |
| 8 | D7 | `publicDetail_loads_media_only_when_includeMedia_is_true` | `:123～127`、`:175` | 详情和列表为什么是两种形状；媒体被查了几次 |
| 9 | D7 | `publicDetail_turns_an_illegal_source_into_the_same_404` | `:125`、`:127` | 来源不合规为什么不给 403 |
| 10 | D7 | `publicDetail_returns_404_when_the_repository_finds_nothing` | `:124`、`:127` | 和不存在的条目是不是同一个错误码 |
| 11 | D8 | `related_merges_link_rows_and_details_then_drops_self` | `:140～146` | 两条来源怎么求并集、去重、保序；为什么要去掉自己 |
| 12 | D8 | `sitemap_reuses_the_same_visibility_predicate` | `:158`、`:159` | sitemap 为什么不能漏掉来源校验 |

## 5. 探针 B 的五个用例

| # | 用例 | 断点重点 | 要回答的问题 |
|---|---|---|---|
| 1 | `resources_endpoint_binds_parameters_and_defaults_page_size_to_twenty` | `PublicContentController:53`、`:67` | 默认值 20 从哪来；Controller 有没有业务判断 |
| 2 | `request_id_comes_from_the_filter_not_from_the_controller` | `RequestIdFilter:12`、`PublicContentController:72` | `requestId` 由谁生成、存哪、怎么进信封 |
| 3 | `api_exception_is_mapped_to_the_error_envelope` | `ApiExceptionHandler:23`、`:74` | 业务异常的信封有哪些字段，`retryable` 怎么算 |
| 4 | `unexpected_failure_falls_through_to_the_generic_handler` | `ApiExceptionHandler:69` | 未捕获异常为什么是 500 而不是 400 |
| 5 | `missing_required_parameter_reveals_the_generic_fallback` | 观察用例 | 缺必填参数走谁；为什么 Controller 里的断点没命中 |

## 6. 交付前实测结果

2026-09-30，JDK 17.0.18 / Maven 3.9.4 / 离线 `-o`：

```text
ContentServiceDebugProbeTest            Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.635 s
PublicContentControllerDebugProbeTest   Tests run:  5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.713 s
BUILD SUCCESS
```

关键观测口径（你可以自己复现，不要直接背）：

| 用例 | 观测 |
|---|---|
| A1 | `ranked.size()=3`、`pageSize=2`、`hasNext=true`、args 末项 `3`、`visible=[A,B]`、游标 `key=2 / id=ITEM_B`、`queryCount=1` |
| A2 | `page=[A,B]` 但 `visible=[A]`，游标仍是 `ITEM_B` |
| A3 | 恰好 2 条 → `hasNext=false` → `nextCursor=null`，limit 仍是 3 |
| A4 | legacy 模块 `sourcedForPublic=false`；`resource.article` 为 `true` |
| A5 | `0 → limit 2`；`999 → limit 51` |
| A6 | `CATALOG_CURSOR_MISMATCH` 且 `queryCount=0` |
| A7 | 后台误配 → `sort=updated`、SQL 含 `order by c.updated_at desc`；用户显式 → `CATALOG_SORT_UNSUPPORTED` |
| A8 | 详情 `media` 1 条、`verify(times(1))`；随后列表仍只查 1 次 |
| A9 | 来源非法 → 404 `CONTENT_NOT_FOUND` |
| A10 | 仓储空 → 同一个 404 |
| A11 | 并集顺序 `B, C, A`，`nextCursor=null`，SQL 含 `content_items source` |
| A12 | sitemap 只剩合法来源那条 |
| B1 | `verify(..., eq(20), ...)` 通过 |
| B2 | 无 Filter → `"requestId":null`；有 Filter → 26 位 ULID + `X-Request-Id` |
| B3 | 400 + `{code, message, requestId, fieldErrors, retryable:false}` |
| B4 | 500 `TEMPORARILY_UNAVAILABLE` + `retryable:true` |
| B5 | **500** `TEMPORARILY_UNAVAILABLE`（缺 `module` 也走通用兜底） |

## 7. 探针里最值得盯的四行

```java
// (1) 一句 SQL 只取 id + 排序键，不加载实体
List<CatalogQuery.RankedId> ranked = jdbc.query(sql.sql(), (row, index) -> new CatalogQuery.RankedId(
        row.getString("id"), CatalogQuery.encodeKey(applied, row.getObject("sort_key"))), sql.args());

// (2) 多查的那一条只用来判断有没有下一页，不进响应
List<CatalogQuery.RankedId> page = hasNext ? ranked.subList(0, pageSize) : ranked;

// (3) 游标取原始页最后一条，不是可见列表最后一条
nextCursor = CatalogCursor.of(applied, last.key(), last.id()).encode();

// (4) 详情与列表共用同一个 DTO，只靠这个开关产生两种形状
includeMedia ? media.forPublishedRevision(r.getId()) : List.of()
```

## 8. 两个探针各证不了什么

| 探针 | 证不了的东西 |
|---|---|
| A | SQL 在真实 MySQL 上能执行、走不走索引、空值排序是否如预期；并发下架/插入时的翻页稳定性；Spring 注入与 `@Transactional` 代理 |
| B | 真实过滤器链（Security、CSRF、会话、`AccountEligibilityFilter`）、真实 HTTP 序列化细节、路径授权；`RequestIdFilter` 之外的行为都是替身或最小环境 |

这两条边界要能自己说出来，别把"探针绿了"当成"功能验证过了"。

## 9. 想让它更值钱

这两个探针的长期价值是补上"`ContentService` 无单元测试"这条盲区。如果以后要迁移到产品仓库，注意三点：

1. 去掉 `RecordingJdbcTemplate` 的"脚本化"色彩，改成 Mockito 或真实集成测试。
2. 补上参数校验的错误码用例（`CONTENT_FILTER_INCOMPLETE`、`CATALOG_STATUS_INVALID`、`CONTENT_FILTER_INVALID`），这一轮没覆盖。
3. 通过产品仓库的 Issue / PR / CI 流程，不要直接从学习副本复制。
