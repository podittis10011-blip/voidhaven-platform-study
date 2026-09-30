# 03 单步脚本

配合 `02-breakpoint-map.md` 使用。每条都是一个"照着按"的动作序列。

## 0. 按键速查（IDEA Windows 默认键位）

| 键 | 动作 | 什么时候用 |
|---|---|---|
| `Shift+F9` | Debug 当前测试/类 | 开跑 |
| `F8` | Step Over：执行本行，不进方法 | 默认动作，90% 时间用这个 |
| `F7` | Step Into：进入被调用方法 | 想进 `CatalogQuery.selectIds`、`CatalogSort.resolve` 时 |
| `Shift+F8` | Step Out：跑完当前方法回到调用处 | 进错了库代码时立刻出来 |
| `F9` | Resume：跑到下一个断点 | 站点之间切换 |
| `Alt+F9` | Run to Cursor：跑到光标所在行 | 不想逐行按，直接冲到目标行 |
| `Alt+F8` | Evaluate Expression：现场求值 | 看表达式、改状态 |
| `Ctrl+F8` | 开关断点 | 快速布点/撤点 |
| `Alt+Shift+F9` | Debug 上一次的配置 | 反复重跑同一个用例 |

`Variables` 面板看当前帧的变量，`Frames` 面板看调用栈，`Watches` 面板常驻你关心的表达式（推荐加 `ranked.size()`、`pageSize`、`visible.size()`）。

## 1. D1 脚本：游标

1. 在 `CatalogCursor.java:24`、`:25`、`:40`、`:44`、`:50`、`:54`、`:57` 各下一个行断点。
2. 对 `CatalogCursorTest.round_trips_opaque_tokens_and_rejects_sort_mismatch`（第 11 行）开 Debug。
3. 第一次停在 `:24` 时，在 `Watches` 里加：`sort`、`key`、`id`。
4. `F8` 到 `:25`，`Alt+F8` 输入 `payload` 看明文；再 `F8`，`Alt+F8` 输入 `token` 看编码结果。
5. 继续 `F9`，会停在 `:40`（decode）。此时把 `value`、`payload`、`parts.length` 记进观察表。
6. 停在 `:44` 时注意 `sort` 与 `expected` 的比较；继续 `F9`，下一次进入 decode 时它们是同一个值，所以走到 `:47` 返回。
7. 最后一段 `assertThatThrownBy(() -> decode(token, UPDATED))` 会停在 `:44` 且条件为真，`F8` 一步就跳到 400——**这一跳就是 `CATALOG_CURSOR_MISMATCH` 的来源**。
8. 换个用例：Debug `accepts_legacy_ulid_only_for_default_sort`（第 24 行），在 `:54` 停住时把 `value` 和 `expected` 记下来，然后按 `F8` 看它是走 `:55`（legacy）还是落到 `:57`（400）。

**要做一次 Force Return 练习**：停在 `:44` 时右键 `Frames` 里的当前帧 → `Force Return`，填 `new CatalogCursor(expected, "x", "01JCONTENT0000000000000001", false)`，然后 `F9`。你会看到测试**没有**抛异常——这证明"400 是这个 if 造成的"，而不是某处神奇地报错。做完把 Force Return 撤掉（重新 Debug 即可，它不改变源码）。

## 2. D2 脚本：排序

1. 在 `CatalogSort.java:47`、`:48`、`:49`、`:50` 下断点，其中 `:49` 设为条件断点：`kind == cn.voidhaven.api.domain.ContentKind.RESOURCE`。
2. Debug `CatalogSortTest.user_choice_beats_admin_default_and_resources_cannot_use_deadline`（第 36 行）。
3. 断点会连续停 5～6 次（每次 `resolve` 调用一次）。**每停一次就在观察表写一行**：`requested`、`configured`、`resolved`。
4. 停在 `:49` 时按 `Alt+F8`，分别求值：`explicit`、`resolved == DEADLINE`、`kind == ContentKind.RESOURCE`。三个都为真才会进这个 if。
5. 继续 `F9`，会命中同一行的第二次（`RESOURCE + 显式 deadline`），这次进入 `:50` 的 `if (explicit != null)`，`F8` 一步就抛出 `CATALOG_SORT_UNSUPPORTED`。
6. 最后一次停在 `:42`（`parse` 的 `default ->`）是 `popularity`。

**Evaluate 练习**：任意一次停下时，按 `Alt+F8` 输入 `CatalogSort.resolve(ContentKind.RESOURCE, null, CatalogSort.DEADLINE)`，回车。你会得到 `UPDATED`——这就是"后台误配静默降级"的最短证明，不用等测试跑到那一行。

## 3. D3 脚本：SQL

1. 先按 `02` 把 `CatalogQuery.java:87` 设成**日志断点**（不暂停，`Evaluate and log` 填 `sql.toString() + "  ||ARGS||  " + args`）。
2. 再在 `:37`、`:43`、`:46`、`:54`、`:65`、`:74`、`:80`、`:83`、`:85`、`:132`、`:148` 下普通断点（`:65` 加条件 `spec.query() != null`）。
3. Debug `CatalogQueryTest.default_sort_uses_manual_order_then_id_and_encodes_stable_keys`（第 48 行）。
4. 每次停在 append 语句时，**在 `Console` 里看上一次日志断点打印的 SQL 片段**，和 `Variables` 里的 `sql` 对象对照，体会"SQL 是一段段长出来的"。
5. 停在 `:54` 时按 `Alt+F8` 求值 `sql.toString()`，会看到此时还没有 where 之后的任何条件。
6. 停在 `:132` 时 `Alt+F8` 求值 `args`（是个 `ArrayList`），看它此刻的元素个数与顺序。
7. 换第二个用例 `deadline_cursor_places_nulls_last`（第 61 行）重跑，重点看 `:46`、`:148`：DEADLINE 会多 join 两张详情表，游标条件里带 `or <expr> is null`。

**要能自己回答**：`CatalogQueryTest` 断言的是 `sql.sql()` 字符串。这段 SQL 能不能在 MySQL 上执行、走不走索引，这条测试**没有**证明。别在这里过度自信。

## 4. D4 脚本：来源策略

1. 先加**异常断点**：`Run → View Breakpoints → + → Java Exception Breakpoints`，填 `cn.voidhaven.api.web.ApiException`，勾 `Caught exception`。
2. 在 `ContentSourcePolicy.java:34`、`:36`、`:40`、`:46`、`:47` 下断点。
3. Debug `ContentSourcePolicyTest.rejects_local_and_reserved_sources_outside_the_local_profile`（第 16 行）。
4. 这个用例会连续调 8 次 `isAllowed`，你会反复停在 `:40`。**每次停都按 `Alt+F8` 求值这一段**：
   ```java
   uri.getScheme() + " | " + host + " | " + (uri.getUserInfo() != null)
   ```
   把结果逐行写进观察表，你就会看清"8 类拒绝条件里到底哪一条命中"。
5. 遇到 `https://127.0.0.1/rules` 时，`F7` 进 `isUnsafeLiteralAddress`（第 52 行），看 `InetAddress.getByName` 返回的地址与 `isLoopbackAddress()` 的结果。
6. 最后停在异常断点时，确认抛的是 `SOURCE_URL_REQUIRED`，且栈顶就是 `requireAllowed`（第 25 行）。
7. 换 `accepts_only_the_configured_local_fixture_source_when_explicitly_enabled`（第 30 行），在 `:36` 停住时对比 `localFixtureSource` 与传入的 `source`——你会发现是**整体字符串相等**，所以端口差一位就不放行。

## 5. D5 脚本：主链（探针）

0. 先按 `05-probe-test.md` 把探针复制进 `apps/api/src/test/java/cn/voidhaven/api/service/`。
1. 在 `ContentService.java` 的 `:51`、`:54`、`:55`、`:60`、`:72`、`:73`、`:90`、`:98`、`:102`、`:105`、`:110`、`:112`、`:116`、`:120` 下断点。
2. 在探针的 `RecordingJdbcTemplate.query`（`@Override public <T> List<T> query(...)`）下断点——这是观察"业务代码对外部依赖提了什么要求"的最佳位置。
3. Debug `listCatalog_pages_the_raw_page_and_encodes_the_cursor_from_its_last_row`。
4. 每次停下就在观察表写一行。**重点三处**：
   - `:98` 停下时，在 `Watches` 加 `sql.sql()`（探针里叫 `sql` 的是 `CatalogQuery.BoundSql`）→ 看完整 SQL；再看 `sql.args()` 最后一项是不是 `3`。
   - `:105` 停下时看 `ranked` 与 `page`：`ranked.size()=3`、`page.size()=2`，第 3 条凭空消失。
   - `:215` 和 `:221` 两个循环各停一次：第一个循环结束时 `byId` 的 key 顺序是乱的（`C, B, A`），第二个循环结束后 `ordered` 是 `A, B`。**这就是 `inOrder` 存在的唯一理由。**
5. 跑到 `:120` 时 `Alt+F8` 求值 `visible.size()` 与 `nextCursor`。

**Drop Frame 练习**：停在 `:102` 时，右键 `Frames` 里的 `listCatalog` 帧 → `Drop Frame`，会把执行点退回该方法的第一个断点，**但已经产生的副作用（`jdbc.query` 已经记了一次）不回滚**。观察 `queryCount` 变成 2 你就懂了：Drop Frame 只退"执行位置"，不退"世界状态"。所以它只适合纯内存的探索，不能当"撤销"。

## 6. D6 脚本：边界

按 `02` 的 D6 表逐条换用例，每条只做一件事：

| 用例 | 只开这几个断点 | 只回答一个问题 |
|---|---|---|
| `source_filter_can_shrink_...` | `ContentService:110`、`:116` | 本页 `visible` 只有 1 条，为什么 `nextCursor` 还指向被丢掉的那条？ |
| `exactly_page_size_rows_...` | `ContentService:102`、`:112` | 为什么"刚好一页"不算"还有下一页"？ |
| `page_size_is_clamped_...` | `ContentService:51` | `0` 和 `999` 分别被改成几？SQL 的 limit 参数是几？ |
| `cursor_sort_mismatch_...` | `CatalogCursor:44` + `ContentService:98` | 断言 `queryCount()==0` 证明了什么顺序关系？ |
| `legacy_resource_module_...` | `ContentService:185`、`:186`、`:189`、`:192` | 同一个 `sourceMode=ORIGINAL`，为什么一个被拒一个放行？ |
| `admin_default_deadline_...` | `ContentService:72`、`:198` | 后台配置和用户显式请求，走的是同一个 `if` 吗？ |

每条做完，把结论写进 `04-observation-sheets.md` 对应表格的最后一行。

## 7. D7 脚本：详情与 404

1. 在 `ContentService.java:123`、`:124`、`:125`、`:126`、`:127` 下断点，另在 `:175` 的 `includeMedia ? ...` 处下一个。
2. Debug 探针 A 的 `publicDetail_loads_media_only_when_includeMedia_is_true`。
3. 停在 `:124` 时看 `items.findPublicByIdAndKind(...)` 的返回：`Optional` 里有值才继续；`F8` 过去后链式调用会进 `:125`。
4. 停在 `:126` 时 `Alt+F8` 求值 `includeMedia`（应为 `true`），然后 `F7` 进 `toPublic`，看 `:175` 那一行的三元表达式：true 分支会去调 `media`。
5. 断言通过后，用例会**接着调列表**：这时再在 `:110` 停一次，`Alt+F8` 求值 `page.items().get(0).media()`，得到空列表。这一前一后就是"同一个 DTO、两种形状"的现场。
6. 换 `publicDetail_turns_an_illegal_source_into_the_same_404`，在 `:125` 停下时 `Alt+F8` 求值 `sources.isAllowed(item.getPublishedRevision().getSourceUrl())`（应得 `false`），`F9` 直接跳到 `:127` 抛 404。
7. 再换 `publicDetail_returns_404_when_the_repository_finds_nothing`，停在 `:124` 时看一下 `Optional.empty()`——**两条完全不同的失败路径，最后是同一个错误码**。

## 8. D8 脚本：关联与 sitemap

1. 在 `ContentService.java:140`、`:141`、`:142`、`:143`、`:144`、`:145`、`:146` 下断点。
2. Debug `related_merges_link_rows_and_details_then_drops_self`。
3. 停在 `:141` 时，`F7` 进 `RecordingJdbcTemplate.queryForList`（探针里的覆写），看捕获到的 SQL——注意它是**双向 union**。
4. `F8` 回来停在 `:142`，`Alt+F8` 求值 `source.details()`，看详情 JSON 里的 `relatedCompetitionIds`；再按 `F8`，求值 `ids`（此时是 `LinkedHashSet`），确认 `B` 没有重复。
5. 停在 `:143` 时求值 `ids.contains(id)`（源内容自己）——`remove` 是防自环，不是"总是会删掉一个"。
6. `F9` 到断言，确认顺序是 `B, C, A`：关系表的顺序在前，详情字段的新元素追加在后。
7. 换 `sitemap_reuses_the_same_visibility_predicate`，在 `:158` 停住看 `findPublicByKind` 的入参（`PageRequest.of(0, 2000)`），在 `:159` 停住看非法来源那条被 `hasPublicSource` 挡掉。

## 9. D9 脚本：HTTP 入口与信封

0. 先按 `01-debug-setup.md` 把探针 B 复制进 `apps/api/src/test/java/cn/voidhaven/api/web/`。
1. 在 `PublicContentController.java:39`、`:67`、`:72`，`RequestIdFilter.java:12`，`ApiExceptionHandler.java:23`、`:69`、`:74`、`:77` 下断点。
2. **先加 MockMvc 的观察技巧**：`Variables` 面板里把 `request` 展开，看 `request.getParameterMap()`——这就是参数绑定的原始输入。
3. Debug `resources_endpoint_binds_parameters_and_defaults_page_size_to_twenty`：停在 `:67` 时 `Alt+F8` 求值 `pageSize`（应是 20，来自注解默认值，不是你传的）。
4. 换 `request_id_comes_from_the_filter_not_from_the_controller`：第一次请求（不带 Filter）停在 `:72` 时求值 `req.getAttribute(RequestIdFilter.ATTRIBUTE)`，得到 `null`；第二次请求（带 `RequestIdFilter`）先在 `RequestIdFilter.java:12` 停住，看生成的 ULID，再在 `:72` 停住时求值同一个 attribute，得到同一个 ULID。
5. 换 `api_exception_is_mapped_to_the_error_envelope`：在 `ApiExceptionHandler.java:23` 停住，看 `exception.code()` 与 `exception.getMessage()`；`F8` 进 `:74`，`Alt+F8` 求值 `status`（400）与 `status.is5xxServerError()`（false）——`retryable` 就是这么算出来的。
6. 换 `unexpected_failure_falls_through_to_the_generic_handler`：注意它**没有**停在 `:23`，而是停在 `:69`——这就是"专门处理"和"通用兜底"的分水岭。
7. 最后跑 `missing_required_parameter_reveals_the_generic_fallback`：控制台会打印 `[PROBE-D9] status=500 ...`。停下来想清楚：`module` 缺失时，异常在**进入 Controller 之前**就抛了，所以 `:67` 根本不会停——你在断点面板里看到它"没命中"，本身就是证据。

**要能自己回答**：为什么业务异常有专属信封（400/404），而参数缺失却变成 500？→ 因为 `@ExceptionHandler(Exception.class)` 的兜底范围太宽，缺参抛的是 Spring MVC 自己的异常，没有专门分支。

## 10. 调试纪律

1. 一次只开一站，断点总数控制在 10 个以内。
2. `F7` 只在你想进**自己项目**的方法时用；一旦进了 `java.*`、`org.springframework.*`、`org.mockito.*` 立刻 `Shift+F8`。
3. 任何"意外"先区分两类：**代码行为**和**替身行为**。`queryForObject` 返回 `null` 让排序回落 `DEFAULT`，这是替身默认值 + 代码 `catch (Exception)` 兜底的组合，不是产品 bug。
4. 不修改 `apps/api/src/main`。要造场景就改探针或新写实验用例。
