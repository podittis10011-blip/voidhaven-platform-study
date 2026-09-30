# 02 九站断点地图

## 0. 怎么用这张图

- 每条断点都给 **锚点文本**（源码里唯一的一段），行号只是 2026-09-30 快照的参考值。源码改动后按锚点文本重新定位，别背行号。
- 一次只开一站。同一屏开太多断点，你会分不清是哪个协作者在跑。
- "预期观察值"栏是给你**先预测**用的：先自己想，再按 `F8` 对照。
- IDEA 里下单行断点：行号左侧点一下（`Ctrl+F8`）。条件断点：右键断点 → `Condition` 填表达式。日志断点：右键断点 → 取消 `Suspend`，勾 `Evaluate and log` 填表达式。

四种断点类型在本包的用途：

| 类型 | 用途 | 什么时候用 |
|---|---|---|
| 行断点（Suspend） | 停下来看变量 | 默认，站点主锚点 |
| 条件断点 | 只在特定取值时停 | 循环里只想看第 N 次，或只看某个 id |
| 日志断点（不暂停） | 打印轨迹，不打断节奏 | D3 看 SQL 逐段拼装 |
| 异常断点 | 谁抛的错一眼看到 | D6 看错误码从哪一行抛出 |

**全局技巧（强烈建议）**：`Run → View Breakpoints → + → Java Exception Breakpoints`，填 `cn.voidhaven.api.web.ApiException`，勾 `Caught exception`。这样每次业务报错都会停在**抛出点**，而不是只停在测试的断言上。整个 D1～D6 都受益。

---

## D1 游标：`c1.` 载荷与四条出口

驱动器：`apps/api/src/test/java/cn/voidhaven/api/service/CatalogCursorTest.java`

```powershell
& $mvn -o -Dtest=CatalogCursorTest test
```

| # | 文件 | 锚点文本 | 类型 | 条件 | 停在这里看什么 |
|---|---|---|---|---|---|
| D1-1 | `CatalogCursorTest.java:14` | `assertThat(token).startsWith("c1.");` | 行 | — | 断言前的 `token` 全串：`c1.` 后面是什么；`cursor` 三个字段 |
| D1-2 | `CatalogCursor.java:24` | `String payload = sort.name() + "|" +` | 行 | — | 拼装前的 `sort / key / id` 三元组 |
| D1-3 | `CatalogCursor.java:25` | `return PREFIX + Base64.getUrlEncoder()...` | 行 | — | 编码结果；注意 `withoutPadding()`，末尾没有 `=` |
| D1-4 | `CatalogCursor.java:40` | `String[] parts = payload.split("\\|", 3);` | 行 | — | Base64 解开后的明文载荷，以及 `parts.length` |
| D1-5 | `CatalogCursor.java:44` | `if (sort != expected) {` | 行 | — | 载荷里的 sort 与本次请求的 sort；相等则继续，不等则下一行走 400 |
| D1-6 | `CatalogCursor.java:50` | `} catch (Exception error) {` | 行 | — | 捕获到的真实异常类型（`IllegalArgumentException`、`DateTimeParseException` 等）——400 的 `CATALOG_CURSOR_INVALID` 就是从这里兜住的 |
| D1-7 | `CatalogCursor.java:54` | `if (ULID.matcher(value).matches() && expected == CatalogSort.DEFAULT)` | 行 | — | 26 位 ULID 只有 `DEFAULT` 才放行；换成 `UPDATED` 时它落到最后的 `throw` |
| D1-8 | `CatalogCursor.java:57` | `throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_INVALID"` | 行 | — | 第四出口；`not-a-cursor` 就是走到这里 |

预期观察值（2026-09-30 实测口径）：

| 输入 | 出口 | 结果 |
|---|---|---|
| `null` / `"  "` | 第 34 行直接返回 | `null`，等同第一页 |
| `c1.<合法>` | 第 47 行返回 | `legacy=false`，`key/id` 还原 |
| `c1.<sort 不同>` | 第 45 行 | 400 `CATALOG_CURSOR_MISMATCH` |
| `c1.<base64 坏 / 段数≠3 / id 空>` | 第 51 行 | 400 `CATALOG_CURSOR_INVALID` |
| 26 位 ULID + `DEFAULT` | 第 55 行 | `legacy=true`，`key=""` |
| 26 位 ULID + `UPDATED` | 第 57 行 | 400 `CATALOG_CURSOR_INVALID` |

停不下来怎么排查：`CatalogCursor.decode` 是 `static` 纯函数，断点必停。若没停，多半是你点在 `CatalogCursorTest.java` 而不是 `CatalogCursor.java`，或者 IDEA 提示 "no executable code at line"——换到锚点所在行。

---

## D2 排序：显式 > 后台默认 > 降级

驱动器：`CatalogSortTest`（在同一个 `CatalogCursorTest.java` 文件里，第 35 行起）

```powershell
& $mvn -o -Dtest=CatalogSortTest test
```

| # | 文件 | 锚点文本 | 类型 | 条件 | 停在这里看什么 |
|---|---|---|---|---|---|
| D2-1 | `CatalogSortTest.java:37` | `assertThat(CatalogSort.resolve(ContentKind.COMPETITION, null, CatalogSort.DEADLINE))` | 行 | — | 每个 `resolve` 调用前把"用户入参 / 后台配置"两个值写进观察表 |
| D2-2 | `CatalogSort.java:47` | `CatalogSort explicit = parse(requested);` | 行 | — | `requested` 是 `null` 还是具体字符串；`explicit` 解析结果 |
| D2-3 | `CatalogSort.java:48` | `CatalogSort resolved = explicit != null ? explicit : (configured == null ? DEFAULT : configured);` | 行 | — | 三元表达式的实际走向与 `resolved` 结果 |
| D2-4 | `CatalogSort.java:49` | `if (resolved == DEADLINE && kind == ContentKind.RESOURCE) {` | 条件断点 | `kind == ContentKind.RESOURCE` | 只停 RESOURCE 场景，看它怎么区分"用户点名"和"后台误配" |
| D2-5 | `CatalogSort.java:50` | `if (explicit != null) {` | 行 | — | 同一个 `if` 里的两条分支：true → 400 `CATALOG_SORT_UNSUPPORTED`；false → 悄悄 `return UPDATED` |
| D2-6 | `CatalogSort.java:42` | `default -> throw new ApiException(..., "CATALOG_SORT_INVALID"` | 行 | — | `parse("popularity")` 停在这里 |

预期观察值：

| 调用 | 用户入参 | 后台配置 | 结果 |
|---|---|---|---|
| 竞赛 + 无参 | `null` | `DEADLINE` | `DEADLINE`（用后台的） |
| 竞赛 + `"updated"` | `"updated"` | `DEADLINE` | `UPDATED`（用户覆盖后台） |
| 资源 + 无参 | `null` | `DEFAULT` | `DEFAULT` |
| 资源 + 无参 | `null` | `DEADLINE` | `UPDATED`（**静默降级，不报错**） |
| 资源 + `"deadline"` | `"deadline"` | 任意 | 400 `CATALOG_SORT_UNSUPPORTED` |
| 任意 + `"popularity"` | `"popularity"` | 任意 | 400 `CATALOG_SORT_INVALID` |

这一站要能回答：为什么"后台配错"降级、"用户点名"报错？→ 见旧包 `04-question-sheet.md` 的 I3。

---

## D3 SQL：看着它一段段拼出来

驱动器：`CatalogQueryTest`（同文件第 47 行起）

```powershell
& $mvn -o -Dtest=CatalogQueryTest test
```

先做一个**日志断点**（不暂停）：

| 位置 | 设置 |
|---|---|
| `CatalogQuery.java:87` `return new BoundSql(sql.toString(), args.toArray());` | 右键断点 → 取消 `Suspend`，勾 `Evaluate and log`，填 `sql.toString() + "  ||ARGS||  " + args` |

然后做法上断点：

| # | 文件 | 锚点文本 | 类型 | 停在这里看什么 |
|---|---|---|---|---|
| D3-1 | `CatalogQueryTest.java:49` | `CatalogQuery.BoundSql sql = CatalogQuery.selectIds(new CatalogQuery.Spec(` | 行 | 构造 `Spec` 的 10 个字段，特别是 `limit=21`（`pageSize=20` 才有的值） |
| D3-2 | `CatalogQuery.java:37` | `sql.append("select c.id as id, ").append(sortExpression(spec.sort()))` | 行 | 起始 SQL 只有 `select ... from ... join ...`，还没有 where |
| D3-3 | `CatalogQuery.java:43` | `sql.append("left join catalog_order_entries o on o.content_item_id = c.id` | 行 | DEFAULT 才追加的排序表 join |
| D3-4 | `CatalogQuery.java:46` | `sql.append("left join competition_revision_details cd on cd.revision_id = r.id ");` | 行 | DEADLINE 才追加的两张详情表 join |
| D3-5 | `CatalogQuery.java:54` | `sql.append("where c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' ");` | 行 | 公开可见性的两道硬条件写死在 SQL 里 |
| D3-6 | `CatalogQuery.java:65` | `sql.append("and (lower(r.title) like lower(concat('%', ?, '%'))` | 条件断点，条件 `spec.query() != null` | 关键词搜索会**加三个同名参数**，args 里会连续出现三次 `"数学"` |
| D3-7 | `CatalogQuery.java:74` | `if ("date".equals(spec.filterType())) sql.append("and f.value_date = ? ");` | 行 | 类型 → 列的映射：`date/number/其他` 分别绑到 `value_date / value_number / value_text` |
| D3-8 | `CatalogQuery.java:80` | `sql.append("and c.operational_status = ? ");` | 行 | 状态筛选 |
| D3-9 | `CatalogQuery.java:83` | `appendCursor(sql, args, spec.sort(), spec.cursor());` | 行 | 游标谓词在 ORDER BY **之前**追加 |
| D3-10 | `CatalogQuery.java:85` | `sql.append(" limit ?");` | 行 | limit 是参数绑定的，不是字符串拼接 |
| D3-11 | `CatalogQuery.java:132` | `sql.append("and (coalesce(o.sort_order, ").append(UNORDERED_RANK)` | 行 | DEFAULT 游标：`> rank or (= rank and id < )` |
| D3-12 | `CatalogQuery.java:148` | `sql.append("and (").append(expr).append(" > ? or (...) or ").append(expr).append(" is null) ");` | 行 | DEADLINE 游标：注意最后那个 `or ... is null`——保证空截止日期的记录不会在翻页时漏掉 |
| D3-13 | `CatalogQuery.java:157` | `sql.append("and (c.updated_at < ? or (c.updated_at = ? and c.id < ?)) ");` | 行 | UPDATED 游标 |
| D3-14 | `CatalogQuery.java:123` | `if (cursor.legacy()) {` | 行 | 旧 ULID 游标退化成 `c.id < ?` 单条件 |
| D3-15 | `CatalogQuery.java:101` | `return Integer.toString(sortKey == null ? UNORDERED_RANK : ...)` | 行 | 空排序键归一化成 `20000`；DEADLINE 的空键是 `"~"`（第 92 行） |

预期观察值（`CatalogQueryTest` 两条用例的实测口径）：

| 用例 | 关键断言 |
|---|---|
| `default_sort_...` | SQL 含 `left join catalog_order_entries`、含 `coalesce(o.sort_order, 20000)`、含 `c.id < ?`；args = `COMPETITION, 数学, 数学, 数学, 2, 01JCONTENT..., 21` |
| `deadline_cursor_places_nulls_last` | SQL 含 `case when coalesce(cd.deadline_date, rd.deadline_date) is null then 1 else 0 end asc`、含 `f.value_number = ?`、含 `c.operational_status = ?`；args 含 `RECRUITMENT / headcount / OPEN` |

注意：这条链接的是 `CatalogQuery.selectIds` 本身，断言的是 **SQL 字符串**。它**不能**证明这段 SQL 在 MySQL 上跑得通、走了什么索引——这是旧包盲区清单里的第二条，别把"字符串对"当成"执行对"。

---

## D4 来源策略：每一个 `return false`

驱动器：`ContentSourcePolicyTest.java`

```powershell
& $mvn -o -Dtest=ContentSourcePolicyTest test
```

| # | 文件 | 锚点文本 | 类型 | 条件 | 停在这里看什么 |
|---|---|---|---|---|---|
| D4-1 | `ContentSourcePolicy.java:34` | `if (value == null || value.isBlank()) return false;` | 行 | — | 空值短路 |
| D4-2 | `ContentSourcePolicy.java:40` | `if (!"https".equalsIgnoreCase(uri.getScheme()) \|\| host.isBlank() \|\| host.equals("localhost")` | 行 | — | 一个 `if` 里塞了 8 类拒绝条件；`Evaluate` 输入每段表达式，看是哪一段为 true |
| D4-3 | `ContentSourcePolicy.java:46` | `return true;` | 行 | — | 唯一放行点 |
| D4-4 | `ContentSourcePolicy.java:56` | `InetAddress address = InetAddress.getByName(host);` | 条件断点，条件 `host.matches("[0-9.]+") \|\| host.contains(":")` | 只看字面量地址（`127.0.0.1`、`192.168.1.2`）的判定 |
| D4-5 | `ContentSourcePolicy.java:47` | `} catch (IllegalArgumentException error) {` | 行 | — | `URI.create` 失败的兜底（fail-closed） |
| D4-6 | `ContentSourcePolicy.java:36` | `if (localFixturesEnabled && localFixtureSource.equals(source)) return true;` | 行 | — | 本地 fixture 白名单：**整体字符串相等**，不是前缀匹配，所以 `8080` 与 `18080` 结果不同 |
| D4-7 | `ContentSourcePolicyTest.java:11` | `assertThatCode(() -> policy.requireAllowed("https://contest.example.edu/rules"))` | 行 | — | `requireAllowed` 与 `isAllowed` 的关系：前者是"不合规就抛 `SOURCE_URL_REQUIRED`" |

预期观察值：

| 输入 | 结果 | 拦在哪 |
|---|---|---|
| `https://contest.example.edu/rules` | 放行 | 第 46 行 |
| `http://localhost:8080/...` | 拒绝 | 第 40 行（scheme 不是 https） |
| `https://example.invalid/rules` | 拒绝 | 第 41 行（`.invalid`） |
| `https://127.0.0.1/rules` | 拒绝 | 第 43 行 → `isUnsafeLiteralAddress` 回环地址 |
| `https://192.168.1.2/rules` | 拒绝 | 同上，site-local |
| `https://metadata.internal/rules` | 拒绝 | 第 43 行（`.internal`） |
| `https://user:password@contest.example.edu/rules` | 拒绝 | 第 43 行（`getUserInfo() != null`） |
| 打开 fixture 后 `http://localhost:18080/local-fixtures/source` | 放行 | 第 36 行 |
| 打开 fixture 后 `http://localhost:8080/local-fixtures/source` | 拒绝 | 端口不同，字符串不相等 |

这一站要能回答：为什么"写时校验"之外还要"读时再判一次"？→ 见 `ContentService.hasPublicSource` 的 javadoc 与旧包 F7。

---

## D5 主链：`ContentService.listCatalog` 十二个节点

驱动器：探针测试。先按 `05-probe-test.md` 把它复制进 `apps/api/src/test/java/cn/voidhaven/api/service/`。

```powershell
& $mvn -o -Dtest=ContentServiceDebugProbeTest test
```

跑用例 1 `listCatalog_pages_the_raw_page_and_encodes_the_cursor_from_its_last_row`（正常翻页）：

| # | 文件 | 锚点文本 | 类型 | 停在这里看什么 |
|---|---|---|---|---|
| D5-1 | `ContentService.java:51` | `int pageSize = Math.max(1, Math.min(size, 50));` | 行 | `size` 是调用方给的原始值，`pageSize` 才是实际值 |
| D5-2 | `ContentService.java:54` | `ContentModuleRegistry.Module module = moduleKey == null ? null : modules.requireActive(moduleKey);` | 行 | `moduleKey=null` 时短路，**不会**去查模块注册表 |
| D5-3 | `ContentService.java:55` | `ContentKind resolvedKind = module == null ? kind : module.legacyKind();` | 行 | 老路由（`/resources`）靠入参 `kind`；模块路由靠 `module.legacyKind()` |
| D5-4 | `ContentService.java:60` | `if ((normalizedFilterKey == null) != (normalizedFilterValue == null)) {` | 行 | 用异或比较"是否同时提供"，只有一边为空才 400 |
| D5-5 | `ContentService.java:72` | `CatalogSort applied = CatalogSort.resolve(resolvedKind, sort, configuredSort(resolvedKind));` | 行 | 注意求值顺序：**先**调 `configuredSort`（查库），再算 resolve |
| D5-6 | `ContentService.java:198` | `String value = jdbc.queryForObject("select default_sort from catalog_sort_settings where kind = ?", ...)` | 行 | 探针里这一句返回的是桩值；返回 `null` 时走第 203 行回落 `DEFAULT` |
| D5-7 | `ContentService.java:73` | `CatalogCursor decoded = CatalogCursor.decode(blankToNull(cursor), applied);` | 行 | 解码发生在建 SQL **之前**，所以排序不匹配时一次查询都不会发 |
| D5-8 | `ContentService.java:90` | `CatalogQuery.BoundSql sql = CatalogQuery.selectIds(new CatalogQuery.Spec(` | 行 | `limit` 参数是 `pageSize + 1`（第 93 行） |
| D5-9 | `ContentService.java:98` | `List<CatalogQuery.RankedId> ranked = jdbc.query(sql.sql(), (row, index) -> new CatalogQuery.RankedId(` | 行 | 这一句**只查 id 与排序键**，不加载实体；进 `RecordingJdbcTemplate` 看捕获到的 SQL |
| D5-10 | `ContentService.java:102` | `boolean hasNext = ranked.size() > pageSize;` | 行 | 探针返回 3 条、`pageSize=2` → `hasNext=true` |
| D5-11 | `ContentService.java:105` | `List<CatalogQuery.RankedId> page = hasNext ? ranked.subList(0, pageSize) : ranked;` | 行 | 多出来的第 3 条在这里被丢掉，**不进响应** |
| D5-12 | `ContentService.java:110` | `List<PublicContentDto> visible = inOrder(ids).stream().filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();` | 行 | 一行里三个动作：回查实体 → 来源过滤 → 转 DTO；`toPublic(item, false)` 表示列表不加载媒体 |
| D5-13 | `ContentService.java:215` | `for (ContentItem item : items.findPublicByIds(ids)) byId.put(item.getId(), item);` | 行 | 替身仓储按 `id desc` 返回（模拟真实 `@Query`），顺序被打乱 |
| D5-14 | `ContentService.java:221` | `for (String id : ids) {` | 行 | 第二个循环把顺序掰回游标顺序——`inOrder` 的全部意义就在这两行 |
| D5-15 | `ContentService.java:112` | `if (hasNext && !page.isEmpty()) {` | 行 | 两个条件缺一不可；空页时即使 `hasNext` 也不会给游标 |
| D5-16 | `ContentService.java:116` | `nextCursor = CatalogCursor.of(applied, last.key(), last.id()).encode();` | 行 | `last` 取自**原始页** `page`，不是过滤后的 `visible` |
| D5-17 | `ContentService.java:120` | `return new PublicPage(visible, nextCursor, applied.wire());` | 行 | 响应三元组：可见列表、下一页游标、本次生效排序 |
| D5-18 | `ContentService.java:185` | `if (revision == null) return false;` | 行 | `hasPublicSource` 的第一道闸 |
| D5-19 | `ContentService.java:186` | `if (revision.getSourceUrl() != null && sources.isAllowed(revision.getSourceUrl())) return true;` | 行 | 合法路径一：有外链且策略放行 |
| D5-20 | `ContentService.java:189` | `return modules.requireKnown(moduleKey(item)).allowsOriginalSource() && "ORIGINAL".equals(...)` | 行 | 合法路径二：模块允许原创 **且** 元数据是 `ORIGINAL` |
| D5-21 | `ContentService.java:192` | `private String moduleKey(ContentItem item) { return item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey(); }` | 行 | `moduleKey` 为空时按 kind 回退到 legacy 模块名——这一行是 D5-22 差异的根源 |

预期观察值（实测口径）：

| 变量 | 值 |
|---|---|
| `size → pageSize` | `2 → 2`（未触发夹取） |
| `sql.args` 最后一项 | `3`（= `pageSize + 1`） |
| `ranked.size()` | `3` |
| `hasNext` | `true` |
| `page.size()` | `2`（第 3 条被 subList 丢弃） |
| 替身仓储返回顺序 | `C, B, A`（id desc） |
| `visible` 顺序 | `A, B`（被 `inOrder` 掰回游标顺序） |
| `nextCursor` 解码 | `key="2"`、`id=ITEM_B` |
| 返回 `sort` | `"default"` |
| `jdbc.query` 调用次数 | `1` |

---

## D6 边界：四个反直觉场景

同样用探针，换用例。

| # | 用例 | 要看到的事 | 预期 |
|---|---|---|---|
| D6-1 | `source_filter_can_shrink_a_page_while_the_cursor_still_follows_the_raw_page` | 断点放在 `D5-12` 与 `D5-16` | 原始页 `[A, B]`、B 来源非法 → `visible=[A]`；游标仍是 B 的 `key=2`。**本页 1 条 < pageSize 2，但仍能翻页** |
| D6-2 | `exactly_page_size_rows_means_there_is_no_next_page` | 断点放在 `D5-10` / `D5-15` | `ranked.size()=2`、`pageSize=2` → `hasNext=false` → `nextCursor=null`；SQL 里 limit 仍是 3 |
| D6-3 | `page_size_is_clamped_to_one_through_fifty_before_the_sql_is_built` | 断点放在 `D5-1`，两次调用各停一次 | `0 → 1 → limit 2`；`999 → 50 → limit 51`。**静默夹取，不报错** |
| D6-4 | `cursor_sort_mismatch_fails_before_any_catalog_query_runs` | 打开全局 `ApiException` 异常断点 + `D5-7` | 停在 `CatalogCursor.java:45`；断言里 `jdbc.queryCount()==0`，证明一次查询都没发 |
| D6-5 | `legacy_resource_module_rejects_original_source_while_resource_article_allows_it` | 断点放在 `D5-20` / `D5-21`，两条内容各停一次 | 同一个 `{"sourceMode":"ORIGINAL"}`：`moduleKey=null`（→ `resource`）**被拒**；`moduleKey="resource.article"` **放行**。差异只来自 `content-modules.v1.json` 里的 `allowsOriginalSource` |
| D6-6 | `admin_default_deadline_on_resource_is_silently_downgraded_to_updated` | 断点放在 `D5-5` / `D5-6`，两次调用各停一次 | 第一次：`configured=DEADLINE`、`requested=null` → 降级 `UPDATED`，SQL 出现 `order by c.updated_at desc`；第二次：`requested="deadline"` → 400 `CATALOG_SORT_UNSUPPORTED` |

---

## D7 详情与 404：媒体开关与统一隐藏

驱动器：探针 A 用例 8～10。先按 `05-probe-test.md` 把探针 A 复制进 `apps/api/src/test/java/cn/voidhaven/api/service/`。

```powershell
& $mvn -o -Dtest=ContentServiceDebugProbeTest#publicDetail_loads_media_only_when_includeMedia_is_true test
```

| # | 文件 | 锚点文本 | 类型 | 停在这里看什么 |
|---|---|---|---|---|
| D7-1 | `ContentService.java:123` | `public PublicContentDto publicDetail(ContentKind kind, String id) {` | 行 | 详情走的是 `findPublicByIdAndKind`，与列表的 `findPublicByIds` 是两条不同的仓储方法 |
| D7-2 | `ContentService.java:124` | `return items.findPublicByIdAndKind(id, kind)` | 行 | 仓储已经把"未发布 / 非 PUBLIC / 非 ACTIVE"挡在外面 |
| D7-3 | `ContentService.java:125` | `.filter(this::hasPublicSource)` | 行 | 第二道闸：连"已发布但来源非法"也一起变成 404 |
| D7-4 | `ContentService.java:126` | `.map(item -> toPublic(item, true))` | 行 | **详情传 true**，列表传 false，这是同一个 DTO 的两种形状 |
| D7-5 | `ContentService.java:127` | `.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND"` | 行 | 三条失败路径（查不到、来源非法、仓储空）最后都汇到这一行 |
| D7-6 | `ContentService.java:175` | `includeMedia ? media.forPublishedRevision(r.getId()) : List.of()` | 行 | 媒体只在 true 时才去查；断在 `MediaReadService` 替身上能看到调用次数 |
| D7-7 | `ContentService.java:137` | `Optional<ContentItem> findPublicById`（`publicDetail(String id)` 重载） | 行 | 不限定 kind 的重载，供 `/content/{id}` 使用 |

预期观察值（探针 A 实测）：

| 用例 | 结果 |
|---|---|
| 用例 8 详情 | `detail.media()` 有 1 条；`verify(media, times(1))` 通过 |
| 用例 8 随后调列表 | 列表 DTO `media()` 为空，且 `media` 替身**仍只被调用 1 次**（列表没查媒体） |
| 用例 9 来源非法 | 抛 404 `CONTENT_NOT_FOUND` |
| 用例 10 仓储查不到 | 抛同一个 404 `CONTENT_NOT_FOUND` |

本站要能回答：为什么"来源不合规"要和"不存在"合成同一个 404？→ 见旧包 I4：403/404 的信息量与存在性泄露。

---

## D8 关联与 sitemap

驱动器：探针 A 用例 11～12。

| # | 文件 | 锚点文本 | 类型 | 停在这里看什么 |
|---|---|---|---|---|
| D8-1 | `ContentService.java:140` | `PublicContentDto source = publicDetail(id);` | 行 | `related()` 的第一步是确认**源内容本身可公开**，源都看不到就不给关联 |
| D8-2 | `ContentService.java:141` | `LinkedHashSet<String> ids = new LinkedHashSet<>(jdbc.queryForList("select distinct related_id from (` | 行 | 关系表是双向 union；`distinct` 去重后仍要保持顺序，所以用 `LinkedHashSet` |
| D8-3 | `ContentService.java:142` | `ids.addAll(RelatedContentIds.fromDetails(source.details()));` | 行 | 第二条来源：详情 JSON 里的 `relatedCompetitionIds`（历史数据兼容） |
| D8-4 | `ContentService.java:143` | `ids.remove(id);` | 行 | 防自环 |
| D8-5 | `ContentService.java:144` | `if (ids.isEmpty()) return new PublicPage(List.of(), null);` | 行 | 没有关联时提前返回，`nextCursor` 恒为 null |
| D8-6 | `ContentService.java:145` | `List<String> limited = ids.stream().limit(24).toList();` | 行 | 硬上限 24 条 |
| D8-7 | `ContentService.java:146` | `List<PublicContentDto> values = inOrder(limited).stream().filter(this::hasPublicSource)` | 行 | 关联结果也走同一套 `inOrder` + 来源过滤 + 轻量 DTO |
| D8-8 | `ContentService.java:158` | `for (ContentItem item : items.findPublicByKind(kind, PageRequest.of(0, 2000)))` | 行 | sitemap 按 kind 遍历，每类最多 2000 条 |
| D8-9 | `ContentService.java:159` | `if (hasPublicSource(item)) result.add(new SitemapEntry(item.getId(), kind));` | 行 | **与详情共用同一条谓词**，保证 sitemap 里的链接不会 404 |

预期观察值（探针 A 实测）：

| 用例 | 输入 | 结果 |
|---|---|---|
| 用例 11 | 关系表返回 `[B, C]`、详情声明 `[A, B]` | 并集去重后 `items` 顺序为 `B, C, A`；`nextCursor=null`；`queryForList` 的 SQL 含 `content_items source` |
| 用例 12 | `RESOURCE` 类下一条合法 + 一条来源非法 | `SitemapEntry` 只剩合法那条，`kind=RESOURCE` |

---

## D9 HTTP 入口与信封

驱动器：探针 B（5 个用例）。先把它复制进 `apps/api/src/test/java/cn/voidhaven/api/web/`。

```powershell
& $mvn -o "-Dtest=PublicContentControllerDebugProbeTest" test
```

| # | 文件 | 锚点文本 | 类型 | 停在这里看什么 |
|---|---|---|---|---|
| D9-1 | `PublicContentController.java:39` | `return ok(content.listPublicModule(module, pageSize, cursor, query, sort, filterKey, filterValue, status), req);` | 行 | Controller 只做参数绑定与转发，零业务判断 |
| D9-2 | `PublicContentController.java:67` | `return ok(content.listCatalog(ContentKind.RESOURCE, null, pageSize, cursor, query, sort, ...), req);` | 行 | 老路由 `/resources` 把 kind 写死成 `RESOURCE` |
| D9-3 | `PublicContentController.java:53` | `@RequestParam(defaultValue = "20") int pageSize,` | 行 | 默认值来自注解；探针 B 用 `verify(..., eq(20), ...)` 证明传下去的就是 20 |
| D9-4 | `PublicContentController.java:72` | `private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, (String) req.getAttribute(RequestIdFilter.ATTRIBUTE)); }` | 行 | `requestId` 从 request attribute 取；没有 Filter 时是 `null` |
| D9-5 | `RequestIdFilter.java:12` | `String id = Ids.ulid(); request.setAttribute(ATTRIBUTE, id); response.setHeader("X-Request-Id", id); MDC.put("requestId", id);` | 行 | 真正生成 requestId 的地方，同时写响应头与日志上下文 |
| D9-6 | `ApiExceptionHandler.java:23` | `ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {` | 行 | 业务异常 → 状态码 + 错误码 + 消息 |
| D9-7 | `ApiExceptionHandler.java:74` | `private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, Map<String, String> fields, HttpServletRequest request)` | 行 | 信封字段顺序与 `retryable` 的计算：只看 429 与 5xx |
| D9-8 | `ApiExceptionHandler.java:77` | `if (status == HttpStatus.TOO_MANY_REQUESTS) builder.header(HttpHeaders.RETRY_AFTER, "900");` | 行 | 429 才带 `Retry-After` |
| D9-9 | `ApiExceptionHandler.java:69` | `ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request)` | 行 | 通用兜底：任何未被专门处理的异常都变成 500 |
| D9-10 | `ApiExceptionHandler.java:68` | `@ExceptionHandler(Exception.class)` | 行 | **这一行就是"缺必填参数 → 500"的原因** |

预期观察值（探针 B 实测）：

| 用例 | 观察 |
|---|---|
| 1 参数绑定 | `GET /resources` 不带参数 → `pageSize` 默认 20；响应 `$.data.items[0].title` = 合成标题；`$.data.sort` / `$.data.nextCursor` 原样透传 |
| 2 requestId | 不挂 Filter → 响应体里 `"requestId":null`；挂上 `RequestIdFilter` → 响应头 `X-Request-Id` 存在，信封里是 26 位 ULID |
| 3 业务异常 | Service 抛 `ApiException(400, CATALOG_SORT_INVALID)` → HTTP 400，信封 `{code, message, requestId, fieldErrors, retryable:false}` |
| 4 未捕获异常 | Service 抛 `IllegalStateException` → HTTP 500 `TEMPORARILY_UNAVAILABLE`，`retryable:true` |
| 5 **缺必填参数** | `GET /content` 缺 `module` → **HTTP 500 `TEMPORARILY_UNAVAILABLE`**（不是 400），因为被 `@ExceptionHandler(Exception.class)` 兜住 |

用例 5 的边界要记牢：**这个结论来自 standalone MockMvc**（只挂了本 Controller 与 `ApiExceptionHandler`，不经过 Security 过滤器链）。它说明"通用兜底会把客户端传错和服务端故障混在一起"，但要不要改是产品决策，本包不改代码。

---

## 相邻站点（本包不展开，但调试时一定会路过）

| 文件 | 为什么路过 |
|---|---|
| `ContentModuleRegistryTest.java:13` | `module.legacyKind()`、`allowsOriginalSource()` 的数据来源；D5-3、D5-20 依赖它 |
| `RelatedContentIdsTest.java:11` | `ContentService.related(id)`（第 142 行）里的 id 合并规则 |
| `CatalogOrderServiceTest.java:44` | 后台人工排序写入侧的 `DEFAULT`/`DEADLINE` 校验，与 D2 的读侧规则互为镜像 |
| `ContentItem.java` 的 `publish()`（第 169 行附近） | 探针夹具里 `item.publish()` 决定了 `publishedRevision`、`visibility`、`recordState` 三个字段，读路径的 SQL 前提全在这里 |
