# 02 核心链路 A：公开内容读路径

时间盒 25–35 分钟。这是平台最常被追问的工程实现。对照源码讲控制流，不要背文件。

课后矩阵：`study-Resource/2026-09-18-vh01-code-lab/`。

## 本段要打开的文件

| 角色 | 路径 |
|---|---|
| 入口 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicContentController.java` |
| 主逻辑 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentService.java` |
| 游标 | `.../service/CatalogCursor.java` |
| 排序 | `.../service/CatalogSort.java` |
| SQL | `.../service/CatalogQuery.java` |
| 来源策略 | `.../service/ContentSourcePolicy.java` |
| 公开查询 | `.../persistence/ContentItemRepository.java` |
| DTO | `.../web/PublicContentDto.java` |
| 信封 | `.../web/ApiResponse.java` |

## 1. HTTP 入口长什么样

`GET /api/v1/public/resources`（竞赛/招募同构）把查询参数原样交给 `ContentService.listCatalog`：

- `pageSize` 默认 20
- `cursor` 上一页末尾
- `q` 关键词
- `sort` `default` / `deadline` / `updated`
- `filterKey` + `filterValue` 必须成对
- `status` 仅招募运营状态白名单

Controller 自己不查库。成功时包成 `ApiResponse(data, requestId)`，`requestId` 来自 `RequestIdFilter` 放进 request attribute 的 ULID。

详情：`GET /api/v1/public/content/{id}` → `publicDetail` → `toPublic(item, includeMedia=true)`。列表 `includeMedia=false`，避免 N 条内容各打一轮媒体。

## 2. `listCatalog` 控制流

文件：`ContentService.java` 的 `listCatalog`。

```text
1. pageSize 夹到 [1, 50]
2. 解析 module；确定 kind
3. filterKey/filterValue 必须同时有或同时无，否则 400 CONTENT_FILTER_INCOMPLETE
4. status 不在 {OPEN,PAUSED,FILLED,EXPIRED,CANCELLED} → 400
5. CatalogSort.resolve：用户 sort > 后台默认 > DEFAULT
   RESOURCE + deadline：用户明确要 → 400；后台误配 → 静默改 UPDATED
6. CatalogCursor.decode(cursor, appliedSort)
   游标里的 sort 必须等于本次 sort，否则 400 CATALOG_CURSOR_MISMATCH
7. CatalogQuery.selectIds(..., limit = pageSize + 1)
   SQL：join content_revisions r on r.id = c.published_revision_id
8. jdbc.query 得到 [{id, sort_key}]
9. hasNext = ranked.size() > pageSize；截掉多的 1 条
10. inOrder(ids) 按 SQL 顺序回填 JPA 实体（IN 查询无序，用 LinkedHashMap 重排）
11. filter(hasPublicSource) 再 toPublic(item, false)
12. nextCursor = 本页最后一条的 (sort, key, id) 编码
13. 返回 PublicPage(items, nextCursor, applied.wire())
```

第 11 步是读路径的灵魂：**写时校验不够，读时再 fail-closed。** 库里若有历史脏 URL，公开列表直接丢掉，详情变 404。

## 3. 游标为什么内嵌 sort

`CatalogCursor` 载荷：`c1.` + Base64(`SORT|key|id`)。

- 空 cursor = 第一页。
- 合法 `c1.*`：解码后 `sort` 必须等于本次请求。
- 旧客户端可能只传 26 位 ULID，且仅当本次是 DEFAULT 时兼容。

**为什么把 sort 放进游标：** 用户第一页按截止日期排，第二页偷偷改成更新时间，若仍用旧 cursor，会从「截止日期序」的中间位置按「更新时间序」继续，页与页重叠或空洞。直接 400 让他从第一页重来，比静默错更好。

**八股：游标 vs OFFSET。**

| | OFFSET/LIMIT | 游标 |
|---|---|---|
| 第 1000 页 | 数据库仍扫过前面 999 页再丢 | 从上次 key 接着走，稳定 |
| 中间插入/删除 | 漏行或重复 | 以「上一页最后一条」为锚，语义清晰 |
| 缺点 | 实现简单 | 不能随便跳页；换排序必须重来 |

`LIMIT n+1`：用「是不是多出 1 条」判断有没有下一页，避免 `COUNT(*)`。

## 4. 来源 fail-closed

`ContentSourcePolicy.isAllowed`：

- 必须 `https`
- host 不能是 localhost / `.local` / `.internal` / `.test` / 字面量内网 IP
- 不能带 userinfo（`https://user:pass@host`）
- 本地 profile 才允许合成 fixture URL

`hasPublicSource` 两条合法路：

1. `sourceUrl` 通过 `isAllowed`
2. 无合格外链，但模块允许原创，且 metadata `sourceMode=ORIGINAL`

任一失败：列表丢弃；详情 `orElseThrow` 404 `CONTENT_NOT_FOUND`，文案是「内容不存在或当前不可见」。

## 5. 为什么是 404 不是 403

未登录用户探测 `GET /api/v1/public/resources/{草稿id}`：

- 403 等于告诉他「这条存在，你没资格」
- 404 把「没有」和「不该给你看」合成一种响应

公开查询 SQL 已经要求 `publishedRevision is not null AND visibility=PUBLIC AND recordState=ACTIVE`。草稿、隐藏、归档在仓储层就查不到。`hasPublicSource` 是第二道，连「已发布但来源非法」也变成同一 404。

**Sitemap 用同一谓词**，避免搜索引擎拿到会 404 的 URL。

## 6. DTO 与信封

- 实体不进 JSON。`PublicContentDto` 是 record，字段即响应 key。
- 列表/详情共用 DTO，用 `includeMedia` 控制媒体、行动入口、核验是否加载。
- 成功信封：`ApiResponse<T>(data, requestId)`。错误走 `ApiExceptionHandler` → `{code, message, requestId, ...}`。
- 错误码形态 `<AGGREGATE>_<REASON>`，如 `CATALOG_SORT_INVALID`、`CONTENT_NOT_FOUND`。

**八股：实体直接返回的危害。** 懒加载触发、密码哈希泄露、把 `currentRevision` 草稿正文漏到公开接口。本项目公开读永远取 `item.getPublishedRevision()`。

## 7. 面试必答三句

1. 未发布或来源不合规 → **统一 404**，避免存在性泄露。
2. 来源过滤发生在截页之后，所以本页 `items.size()` 可能小于 `pageSize`，但 `nextCursor` 仍可能非空。
3. 公开读 join 的是 `published_revision_id`，草稿 `current_revision` 对匿名用户不存在。

## 闭卷题

**问：** 过滤后本页只有 3 条、`pageSize=20`，为什么还可能返回 `nextCursor`？

**答：** SQL 先按可见性/排序取出 21 条 id（20+1）。`hasNext` 在来源过滤之前就定了。这 20 条里可能有若干来源非法被丢掉，但「后面还有下一页」这个事实不变。客户端应继续带 cursor 请求，而不是以为结束了。
