# VH-01 代码阅读路线（六站）

阅读版本：工作树 HEAD `be219ac` + 61 项未提交改动。行号以 2026-09-18 的工作树为准，改动后可能漂移，用方法名兜底。

## 0. 一句话链路

```text
HTTP GET /api/v1/public/resources
  → PublicContentController.resources            （参数绑定，不含业务）
  → ContentService.listCatalog                   （夹取/校验/排序解析/游标解码）
  → CatalogQuery.selectIds                       （拼 SQL，取 id + sort_key）
  → JdbcTemplate.query                           （limit = pageSize + 1）
  → ContentService.inOrder                       （按 id 回查实体并恢复顺序）
  → ContentService.hasPublicSource               （来源 fail-closed 过滤）
  → ContentService.toPublic(item, false)         （不加载媒体）
  → PublicPage(items, nextCursor, sort)
```

详情链路多一步：`publicDetail` 用 `toPublic(item, true)`，才加载媒体、公开行动入口和最新核验。

## 第 1 站：HTTP 入口

文件：`apps/api/src/main/java/cn/voidhaven/api/web/PublicContentController.java`

| 行 | 内容 |
|---:|---|
| 10 | `@RestController @RequestMapping("/api/v1/public")`，公开前缀固定 |
| 21 | `/home` 走 `HomepageProjectionService.projectLive()`，四槽位 |
| 22 | `/content-modules` 返回前端可用的模块定义 |
| 23 | `/content?module=` 通用模块列表，`pageSize` 默认 20 |
| 24～25 | `/content/{id}` 详情、`/content/{id}/related` 关联 |
| 27～32 | 旧分类兼容路由：`competitions`、`resources`、`recruitments`，内部转成 `ContentKind` 再复用同一个 Service |

读完自答：

1. Controller 里有没有业务判断？如果 `pageSize` 夹取、来源校验写在 Controller，会带来什么测试和复用问题？
2. 旧路由和新模块路由最终是不是同一个方法？入口不同会不会产生两套分页行为？
3. `ok(...)` 里的 `requestId` 从哪来？它在手动复现时有什么用？

## 第 2 站：分页主干（最重要的一站）

文件：`apps/api/src/main/java/cn/voidhaven/api/service/ContentService.java`

| 行 | 内容 | 关键点 |
|---:|---|---|
| 23～26 | `record PublicPage(items, nextCursor, sort)`，另有 2 参构造器兼容旧调用 | 响应里带本次生效的排序 |
| 32 | `listCatalog(kind, moduleKey, size, cursor, query, sort, filterKey, filterValue, status)` | 所有公开列表的唯一主干 |
| 34 | `Math.max(1, Math.min(size, 50))` | 静默夹取，不报错 |
| 35～37 | 先解析模块，再定 `resolvedKind` | 模块不存在 → `CONTENT_MODULE_UNAVAILABLE` |
| 38～42 | `filterKey`/`filterValue` 必须同时给 | 否则 `CONTENT_FILTER_INCOMPLETE` |
| 43～46 | `status` 白名单 `OPEN/PAUSED/FILLED/EXPIRED/CANCELLED` | 否则 `CATALOG_STATUS_INVALID` |
| 47 | `CatalogSort.resolve(resolvedKind, sort, configuredSort(resolvedKind))` | 用户显式 > 后台默认 |
| 48 | `CatalogCursor.decode(cursor, applied)` | **解码发生在查询之前** |
| 49～60 | 筛选字段必须是模块声明的可筛字段，值按类型绑定到 `value_date` / `value_number` / `value_text` | 长度 > 512 或格式错误 → `CONTENT_FILTER_INVALID` |
| 61～65 | 组装 `CatalogQuery.Spec`，`limit = pageSize + 1` | 多查一条只为判断 `hasNext` |
| 66～67 | `jdbc.query(...)` 只取 `id` 与 `sort_key`，`encodeKey` 归一化成字符串 | 这一步不加载实体 |
| 68～69 | `hasNext = ranked.size() > pageSize`；`page` 是截断后的原始页 | 多出的那条不进响应 |
| 70 | `inOrder(ids)` 回查实体，再 `filter(hasPublicSource)`，再 `toPublic(item, false)` | 过滤在游标计算之前、载荷转换之中 |
| 71～75 | `nextCursor` 取 **原始页最后一条** 的 `(key, id)`，且仅当 `hasNext && !page.isEmpty()` | 边界的关键 |
| 76 | 返回 `new PublicPage(visible, nextCursor, applied.wire())` | `sort` 回显 |

其他公开读取方法：

| 行 | 方法 | 要点 |
|---:|---|---|
| 78～82 | `publicDetail(kind, id)` | 查不到或来源不合规，统一 404 `CONTENT_NOT_FOUND` |
| 90～92 | `publicDetail(id)` | 同上，不限定 kind |
| 94～103 | `related(id)` | 先确认源内容可公开，再查双向关联，最多 24 条，固定无游标 |
| 110～119 | `publicSitemapEntries()` | 与详情用同一条 `hasPublicSource` 规则，保证 sitemap 里的链接不会 404 |
| 125～129 | `toPublic(item, includeMedia)` | 载荷开关：媒体、行动入口、核验 |
| 130～138 | `hasPublicSource(item)` | 读取路径的二次来源校验 |
| 139～149 | `moduleKey` / `blankToNull` / `configuredSort` | 后台默认排序读 `catalog_sort_settings`，异常时回落 `DEFAULT` |
| 150～157 | `inOrder(ids)` | 用 `LinkedHashMap` 按输入顺序重排，查不到的 id 直接跳过 |

读完自答：

1. `pageSize + 1` 相比额外执行一次 `count(*)` 省掉了什么？多出的那条去哪了？
2. 一页里被 `hasPublicSource` 过滤掉若干条时，`items.size()` 和 `nextCursor` 会怎样？客户端有没有可能收到空 `items` 但非空 `nextCursor`？
3. `configuredSort` 用 `try/catch` 包住数据库读取，这种「异常即默认」的写法在本场景合适吗？如果表被误删会怎样？
4. `inOrder` 会不会悄悄丢掉实体？什么情况下 id 查不到（提示：并发下架）？

## 第 3 站：游标模型

文件：`apps/api/src/main/java/cn/voidhaven/api/service/CatalogCursor.java`（全文 45 行）

| 行 | 内容 |
|---:|---|
| 10 | `record CatalogCursor(CatalogSort sort, String key, String id, boolean legacy)` |
| 11 | ULID 正则 `[0-9A-HJKMNP-TV-Z]{26}`：26 位，排除 `I` `L` `O` `U` |
| 12 | 前缀 `c1.` |
| 14～16 | `of(sort, key, id)`：`key` 为空时归一化成 `""`，`legacy=false` |
| 18～21 | `encode()`：载荷 `sort.name() + "\|" + key + "\|" + id`，Base64 URL 编码且去掉 `=` 填充 |
| 23～45 | `decode(raw, expected)` 四条出口 |

`decode` 的判定顺序（照着代码读能自己列出来）：

```text
1. 空值/空白            → null（当作第一页）
2. 以 "c1." 开头
   ├─ Base64 解码失败 / 段数 ≠ 3 / id 为空 / sort 名不在枚举里 → 400 CATALOG_CURSOR_INVALID
   ├─ 段内 sort ≠ expected                                  → 400 CATALOG_CURSOR_MISMATCH
   └─ 全部通过                                              → 正常游标
3. 匹配 26 位 ULID 且 expected == DEFAULT → 兼容旧游标，legacy = true
4. 其他                                    → 400 CATALOG_CURSOR_INVALID
```

写成代码的一个细节：`CatalogSort.valueOf(parts[0])` 抛的 `IllegalArgumentException` 会被第 38 行的 `catch (Exception)` 吃掉，转成 `CATALOG_CURSOR_INVALID`；而 `CATALOG_CURSOR_MISMATCH` 因为是 `ApiException`，被第 36 行原样抛出。

读完自答：

1. 为什么游标要携带 `sort`？只带 id 时，换排序继续翻页会读到什么？
2. 为什么还要携带 `key`？`id` 单独为什么不够？
3. `legacy=true` 分支为什么只在 `DEFAULT` 时放行？这个兼容分支是为谁保留的（提示：看 HEAD 版本的 `nextCursor` 生成逻辑）？

## 第 4 站：排序模型

文件：`apps/api/src/main/java/cn/voidhaven/api/service/CatalogSort.java`（全文 37 行）

| 行 | 内容 |
|---:|---|
| 7～10 | `enum CatalogSort { DEFAULT, DEADLINE, UPDATED }` |
| 12～14 | `wire()` 输出小写串，用于响应回显 |
| 16～24 | `parse(raw)`：空 → null；三个合法值 → 枚举；其他 → 400 `CATALOG_SORT_INVALID` |
| 26～36 | `resolve(kind, requested, configured)`：显式优先；`RESOURCE + DEADLINE` 时，配置来的静默降级为 `UPDATED`，用户显式要的报 400 `CATALOG_SORT_UNSUPPORTED` |

读完自答：

1. 同一个「不支持」的能力，为什么后台配置写错要降级、用户显式请求要报错？
2. 如果 `RESOURCE` 的默认排序被配置成 `DEADLINE` 而后台没人发现，用户实际看到的是什么排序？这个行为算不算「配置错误被隐藏」？

## 第 5 站：SQL 生成

文件：`apps/api/src/main/java/cn/voidhaven/api/service/CatalogQuery.java`

先说结论：这是**手写 SQL 片段**，不是 JPQL。原因是排序键、筛选列、游标比较都需要按类型动态拼接，而这些只能在受控分支里生成。

| 行 | 内容 |
|---:|---|
| 12 | `UNORDERED_RANK = 20000`：没人排过序的内容统一落到这个排名 |
| 14～26 | `BoundSql(sql, args)`、`RankedId(id, key)`、`Spec(...)` |
| 30～77 | `selectIds(spec)` |
| 32～33 | `select c.id, <sortExpression> as sort_key` |
| 34 | `join content_revisions r on r.id = c.published_revision_id`：公开读取一律用**已发布修订** |
| 35～37 | `DEFAULT` 才 `left join catalog_order_entries`（人工排序表） |
| 38～41 | `DEADLINE` 才 join 竞赛/招募的详情表 |
| 42～44 | 有筛选才 join `revision_filter_values` |
| 45 | `where c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE'` |
| 46～51 | 按 `module_key` 或 `kind` 过滤 |
| 52～57 | 搜索：`title` / `summary` / `source_name` 三个 `lower(...) like`，参数绑定 3 次 |
| 58～67 | 筛选：`field_key` 固定，值落到 `value_date` / `value_number` / `value_text` |
| 68～71 | `operational_status` 过滤 |
| 72 | `appendCursor(...)` |
| 73～75 | `orderBy(...)` + `limit ?`（值为 `pageSize + 1`） |
| 79～91 | `encodeKey(sort, sortKey)`：游标里的 key 必须能反解回可比较类型 |
| 93～99 | `sortExpression(sort)`：`DEFAULT` → `coalesce(o.sort_order, 20000)`；`DEADLINE` → `coalesce(cd.deadline_date, rd.deadline_date)`；`UPDATED` → `c.updated_at` |
| 101～107 | `orderBy(sort)`：三种排序结尾都是 `c.id desc` |
| 109～144 | `appendCursor(...)`：legacy / DEFAULT / DEADLINE / UPDATED 四种形状 |
| 146～151 | `parseRank(key)`：整数解析失败静默回落 20000 |

key 的字符串归一化对照表：

| 排序 | 归一化 | 空值表现 | 解码还原 |
|---|---|---|---|
| `DEFAULT` | 整数排名 | `20000` | `Integer.parseInt`（失败回落 20000） |
| `DEADLINE` | `yyyy-MM-dd` | `~` | `Date.valueOf(key)` / `"~"` 走空值分支 |
| `UPDATED` | ISO 瞬时串 | 无空值（`updated_at` 非空） | `Instant.parse(key)` |

`appendCursor` 的四种形状：

```text
legacy   : and c.id < ?
DEFAULT  : and (coalesce(o.sort_order,20000) > ? or (coalesce(o.sort_order,20000) = ? and c.id < ?))
DEADLINE : 空 key → and <expr> is null and c.id < ?
           有 key → and (<expr> > ? or (<expr> = ? and c.id < ?) or <expr> is null)
UPDATED  : and (c.updated_at < ? or (c.updated_at = ? and c.id < ?))
```

注意 `DEFAULT` 与 `DEADLINE` 的排序键是升序，所以用 `>`；`UPDATED` 是降序，所以用 `<`。

读完自答：

1. 三种排序为什么都必须以 `c.id desc` 收尾？
2. `DEADLINE` 非空游标分支里那个 `or <expr> is null` 是干什么的？如果删掉它，翻页到空值区会发生什么？
3. `parseRank` 对非法 key 静默回落 20000，而排序名非法会报错。这两种不一致的取舍各有什么风险？
4. 用户输入为什么不会导致 SQL 注入？指出代码里「拼接」和「绑定」的分界。

## 第 6 站：来源策略与载荷

文件：`apps/api/src/main/java/cn/voidhaven/api/service/ContentSourcePolicy.java`

| 行 | 内容 |
|---:|---|
| 13～22 | 构造注入两个配置：`voidhaven.local-fixture-sources-enabled`、`voidhaven.public-base-url` |
| 24～26 | `requireAllowed(value)`：写入路径用，不通过就抛 `SOURCE_URL_REQUIRED` |
| 29～32 | `isAllowed` 的注释：这是 fail-closed 守卫，历史数据或手工导入的数据可能绕过写时校验 |
| 33～50 | 放行规则：本地 fixture 精确匹配 → 放行；否则必须 HTTPS，且拒绝 `localhost`、`.localhost`、`.invalid`、`.test`、`.example`、`.local`、`.internal`、带凭据的 URL、不安全字面量地址 |
| 52～69 | `isUnsafeLiteralAddress`：环回、私网、链路本地、组播、`0.0.0.0/8`、`224+`、`100.64/10`、`192.0.0/24`、`192.0.2/24`、`198.18/15`、`198.51.100/24`、`203.0.113/24`、`fc00::/7`、`2001:db8::/32` |

文件：`apps/api/src/main/java/cn/voidhaven/api/web/PublicContentDto.java`（全文 12 行）

- 6～12 行是一个 `record`，字段就是公开响应形状：`body`、`details` 是 `JsonNode`，末尾新增 `Instant updatedAt`。
- 列表与详情是**同一个 DTO**，差异由 `includeMedia` 决定，不是两个类。

读完自答：

1. 合成数据的 `sourceUrl` 是 `http://localhost:18082/local-fixtures/source`，为什么它能公开？换成 `http://localhost:8080/...` 会怎样？
2. `hasPublicSource` 的两条放行路径分别是什么？「没有外链也能公开」的合法路径需要什么条件？
3. 如果写入校验没做，读路径这道守卫会不会拦住？拦不住的是什么情况（提示：`sourceUrl` 为 null 但模块允许原创）？

## 附加站：前端怎么用游标

| 文件 | 位置 | 要点 |
|---|---|---|
| `apps/web/src/lib/catalog.ts` | 11～13、77、88～91 | 查询状态里保存 `cursor`，把它拼回 URL 参数 |
| `apps/web/src/lib/api.ts` | 476、494～503、539～546 | 请求时透传 `cursor` |
| `apps/web/src/pages/public.tsx` | 381、398、644～673 | 有 `cursor` 显示「上一页」；`nextCursor` 非空显示「下一页」；空列表显示 `EmptyState` |

值得注意的观察点：`public.tsx` 的「下一页」按钮只判断 `nextCursor` 是否为空，所以「本页 0 条 + `nextCursor` 非空」时页面会同时显示「没有找到匹配内容」和一个可点的「下一页」。这是产品层面的取舍，不是 bug，但答辩时要说清。

## 版本差异地图（为什么这轮值得重学）

工作树相对 HEAD 在 VH-01 链路上的新增/修改：

| 文件 | 状态 | 影响 |
|---|---|---|
| `service/CatalogCursor.java` | 新增 | 引入 `c1.` 结构化游标与旧 ULID 兼容 |
| `service/CatalogSort.java` | 新增 | 三种排序 + 解析/降级规则 |
| `service/CatalogQuery.java` | 新增 | 手写排序与游标 SQL |
| `service/ContentService.java` | 修改 | `PublicPage` 增加 `sort`；`nextCursor` 改由排序键生成；筛选/状态校验加强 |
| `web/PublicContentDto.java` | 修改 | 新增 `updatedAt` |
| `resources/content-modules.v1.json` | 修改 | 新增 `competitionLevel`、`participationForm`、`startDate`、`stage`、`headcount`、`relatedCompetitionIds` 等可筛字段 |
| `db/migration/V23__catalog_order_and_sort.sql` | 新增 | `catalog_sort_settings`、`catalog_order_entries`、三个索引 |
| `service/CatalogOrderService.java`、`web/CatalogOrderAdminController.java` | 新增 | 后台维护默认排序与人工顺序 |

HEAD 版本的旧行为（18080 可以直接观察到）：`nextCursor` 直接返回裸 ULID，`pageSize + 1` 之后取 `rawPage` 最后一条的 `id`，**没有排序概念**，未知参数被 Spring 静默忽略。

这解释了两件事：

1. 为什么当前版本的 `CatalogCursor` 要保留 `legacy` 分支——旧客户端手里可能还握着裸 ULID 游标。
2. 为什么这轮学习不能沿用 `02-public-content-slice.md`：那份材料描述的是 HEAD 时代的实现（`c.id < cursor`），与当前工作树已不一致。
