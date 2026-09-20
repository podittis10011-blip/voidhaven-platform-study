# VH-01 手动复现运行手册（只读为主）

## 0. 准备与版本核对

```powershell
$env:base = 'http://localhost:18082'   # 当前工作树构建

# 1) 确认服务活着
curl.exe -s http://localhost:18082/healthz      # {"status":"UP"}

# 2) 三个版本标记（18082 = 当前工作树；18080 = HEAD 构建，详见 README 第 1 节）
curl.exe -s "$env:base/api/v1/public/resources?pageSize=2" | Select-String -Pattern 'updatedAt|sort'
curl.exe -s -o NUL -w "%{http_code}\n" "$env:base/api/v1/public/resources?sort=popularity"
curl.exe -s -o NUL -w "%{http_code}\n" "$env:base/api/v1/public/resources?filterKey=resourceType"
```

上面前两条期望都是 `400`。

当前数据集（18082）：公开内容共 3 条，竞赛 / 资源 / 招募各 1 条。

| 类型 | id |
|---|---|
| RESOURCE | `26CYD6HJB5C406497D7Y915Z45` |
| COMPETITION | `762VX4TF9Y4QBP20A28AZM13BN` |
| RECRUITMENT | `EPE8H7YRNV60ZT0SQ0WJP22HAB` |

三条内容的 `sourceUrl` 都是 `http://localhost:18082/local-fixtures/source`。它能在非 HTTPS 下公开，是因为本地 fixture 白名单精确匹配了这个地址（`ContentSourcePolicy` 33 行）。

## 1. 只读请求矩阵

下表所有结果均为 2026-09-18 在本机实测。记录格式：状态码 + `code`（错误时）+ `requestId` + 结论。

### 1.1 成功路径

| # | 请求 | 实测结果 | 观察点 |
|---:|---|---|---|
| R-01 | `GET /api/v1/public/resources?pageSize=2` | 200，`items=1`，`sort=default`，响应里没有 `nextCursor` 字段 | 数据不足时 `hasNext=false`，`nextCursor` 为 null；Jackson 省略 null 字段，字段直接不出现 |
| R-02 | `GET /api/v1/public/resources?q=学习` | 200，`items=1` | 命中 `title` / `summary` / `source_name` |
| R-03 | `GET /api/v1/public/resources?q=zzz-no-match` | 200，`items=0` | 空结果是 200，不是 404 |
| R-04 | `GET /api/v1/public/resources?sort=updated` | 200，`sort=updated` | 显式排序覆盖默认值并回显 |
| R-05 | `GET /api/v1/public/competitions?sort=deadline` | 200，`sort=deadline` | 竞赛支持按截止日期排序 |
| R-06 | `GET /api/v1/public/competitions?status=OPEN` | 200，`items=0` | 状态白名单内所以不报错；合成竞赛的 `operational_status` 不是 `OPEN`，因此被过滤掉——「参数合法」和「有结果」是两件事 |
| R-07 | `GET /api/v1/public/content?module=resource` | 200，`items=1` | 模块路由与 `/resources` 等价 |
| R-08 | `GET /api/v1/public/content?module=resource.article` | 200，`items=0` | 模块存在但无数据，仍是 200 |
| R-09 | `GET /api/v1/public/resources/26CYD6HJB5C406497D7Y915Z45` | 200 | 详情成功 |
| R-10 | `GET /api/v1/public/content/26CYD6HJB5C406497D7Y915Z45/related` | 200，`items=0` | 关联为空 |
| R-11 | `GET /api/v1/public/resources?pageSize=0` | 200，`items=1` | `Math.max(1, ...)` 静默夹取为 1 |
| R-12 | `GET /api/v1/public/resources?pageSize=999` | 200，`items=1` | 夹取为 50；数据只有 1 条，看不出上限 |
| R-13 | `GET /api/v1/public/competitions?cursor=01HZXC0V4K7N9PQRSTVWXY0001` | 200，`items=0` | 旧 ULID 游标在 `DEFAULT` 下被接受并走 `c.id < ?`；伪造游标以 `0` 开头、字典序小于真实 id（以 `C` 开头），而条件要求 id 更小，所以结果为空。这条同时说明旧游标路径依赖 ULID 的字典序，也说明「游标合法」不等于「有数据」 |
| R-14 | `GET /api/v1/public/competitions?sort=deadline&cursor=<DEADLINE 游标>` | 200，`items=0` | 游标 sort 与请求 sort 一致，所以放行；合成竞赛的 `deadline_date` 正好等于游标里的 `2026-10-15`，同一排序键下要求 `c.id < '01HZX...'`，而真实 id 以 `C` 开头、字典序更大，所以被排除。这正好演示了「同键时用 id 做次级比较」 |

### 1.2 参数错误路径（全部 400）

| # | 请求 | 实测 `code` | 代码位置 |
|---:|---|---|---|
| E-01 | `GET /api/v1/public/resources?sort=popularity` | `CATALOG_SORT_INVALID` | `CatalogSort.parse` 22 行 |
| E-02 | `GET /api/v1/public/resources?sort=deadline` | `CATALOG_SORT_UNSUPPORTED` | `CatalogSort.resolve` 30～32 行（资源没有截止日期） |
| E-03 | `GET /api/v1/public/resources?cursor=not-a-cursor` | `CATALOG_CURSOR_INVALID` | `CatalogCursor.decode` 45 行 |
| E-04 | `GET /api/v1/public/competitions?cursor=01HZXC0V4K7N9PQRSTVWXY0001&sort=updated` | `CATALOG_CURSOR_INVALID` | 旧游标只在 `DEFAULT` 放行，`CatalogCursor.decode` 42～45 行 |
| E-05 | `GET /api/v1/public/competitions?sort=updated&cursor=<DEADLINE 游标>` | `CATALOG_CURSOR_MISMATCH` | `CatalogCursor.decode` 32～34 行 |
| E-06 | `GET /api/v1/public/resources?filterKey=resourceType` | `CONTENT_FILTER_INCOMPLETE` | `ContentService.listCatalog` 38～42 行 |
| E-07 | `GET /api/v1/public/resources?filterValue=GUIDE` | `CONTENT_FILTER_INCOMPLETE` | 同上 |
| E-08 | `GET /api/v1/public/competitions?filterKey=officialRulesUrl&filterValue=https://x.example` | `CONTENT_FILTER_UNAVAILABLE` | 模块未把该字段声明为可筛 |
| E-09 | `GET /api/v1/public/competitions?status=NOPE` | `CATALOG_STATUS_INVALID` | `ContentService.listCatalog` 43～46 行 |
| E-10 | `GET /api/v1/public/content?module=nope-module` | `CONTENT_MODULE_UNAVAILABLE` | `modules.requireActive`，35 行 |

### 1.3 隐藏语义路径

| # | 请求 | 实测结果 | 观察点 |
|---:|---|---|---|
| N-01 | `GET /api/v1/public/resources/does-not-exist-0001` | 404 `CONTENT_NOT_FOUND`，文案「内容不存在或当前不可见。」 | 不存在、隐藏、归档、未发布、来源不合规共用这一条出口 |
| N-02 | 比较响应头 `X-Request-Id` 与响应体 `requestId` | 一致 | 便于把前端报错对到服务端日志 |

### 1.4 一个容易被忽略的观察

当前 3 条合成内容都没有媒体、行动入口和核验记录，所以列表与详情的载荷看起来一样：

```text
列表：media = []   actions = []   verification 字段不出现
详情：media = []   actions = []   verification 字段不出现
```

这说明「列表不加载媒体、详情才加载」是**代码结构上的开关**（`toPublic(item, includeMedia)`），在当前数据集上无法用响应差异证明。要观察到差异，需要一条带媒体或行动入口的内容（属于 VH-05 的准备数据）。

## 2. A/B 对照实验：静默忽略 vs 显式报错

```powershell
$urls = @(
  '/api/v1/public/resources?sort=popularity',
  '/api/v1/public/resources?filterKey=resourceType',
  '/api/v1/public/competitions?status=NOPE',
  '/api/v1/public/resources?cursor=not-a-cursor'
)
foreach ($u in $urls) {
  foreach ($p in 18080, 18082) {
    $code = (Invoke-WebRequest -Uri "http://localhost:$p$u" -SkipHttpErrorCheck).StatusCode
    Write-Output "$p  $code  $u"
  }
}
```

实测结论：

| 请求 | 18080（HEAD 构建） | 18082（当前源码） |
|---|---|---|
| `sort=popularity` | 200，参数被忽略 | 400 `CATALOG_SORT_INVALID` |
| `filterKey=resourceType`（缺 value） | 200，参数被忽略 | 400 `CONTENT_FILTER_INCOMPLETE` |
| `status=NOPE` | 200，参数被忽略 | 400 `CATALOG_STATUS_INVALID` |
| `cursor=not-a-cursor` | 200，游标被当成 id 比较 | 400 `CATALOG_CURSOR_INVALID` |
| 列表响应字段 | 只有 `items` | `items` + `sort`，item 多 `updatedAt` |

用这四条回答「为什么静默忽略是坏的」：客户端会以为筛选、排序、翻页都生效了，实际拿到的是另一份结果，问题被推到线上才暴露。

## 3. 手算一个游标

```powershell
function New-Cursor([string]$payload) {
  'c1.' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)).TrimEnd('=').Replace('+','-').Replace('/','_')
}

$deadline = New-Cursor 'DEADLINE|2026-10-15|01HZXC0V4K7N9PQRSTVWXY0001'
$default  = New-Cursor 'DEFAULT|20000|01HZXC0V4K7N9PQRSTVWXY0001'
$deadline
$default
```

`$deadline` 的实测值：`c1.REVBRExJTkV8MjAyNi0xMC0xNXwwMUhaWEMwVjRLN045UFFSU1RWV1hZMDAwMQ`

接着做四件事，边做边对照 `CatalogCursor.decode`：

1. 用 `$deadline` 请求 `competitions?sort=deadline` → 200，解码成功。
2. 换成 `competitions?sort=updated` → 400 `CATALOG_CURSOR_MISMATCH`，验证 sort 绑定。
3. 把载荷改成两段（`DEADLINE|2026-10-15`）→ 400 `CATALOG_CURSOR_INVALID`，验证段数校验。
4. 把 sort 名改成 `SOMETHING` → 同样 `CATALOG_CURSOR_INVALID`，验证 `valueOf` 抛错被兜住。

## 4. 为什么现在做不出真实的 nextCursor 翻页

`nextCursor` 只在 `hasNext = ranked.size() > pageSize` 时生成，而每类内容只有 1 条：

```text
pageSize=1 → 查到 1 条 → hasNext=false → nextCursor=null
```

想观察真实翻页，必须先有同类内容的第 2 条。这与 `09-evidence-ledger.md` 第 5.2 节中断的 `VH-01-CURSOR-01` 是同一件事。

## 5. W-01：补第二条资源（写操作，需你确认后再做）

本节是提案，尚未执行。只写入隔离的本地数据卷，使用合成标题 `LEARN-20260918-CURSOR-02`，不动正式源码。

```text
1) POST /api/v1/auth/session                    登录，保留 Cookie
2) GET  /api/v1/auth/csrf                       取 CSRF 令牌
3) POST /api/v1/admin/content-items             创建草稿（Idempotency-Key + X-XSRF-TOKEN）
4) POST /api/v1/admin/content-items/{id}/submit-review
5) POST /api/v1/admin/reviews/{id}/decision     带 If-Match: 1
6) POST /api/v1/admin/content-items/{id}/publish
7) GET  /api/v1/public/resources?pageSize=1     期望 items=1 且 nextCursor 非空
8) GET  /api/v1/public/resources?pageSize=1&cursor=<nextCursor>   第 2 页
```

执行前提醒：

- 请求体与请求头以 `contracts/openapi/voidhaven-v0.yaml` 的 `ContentDraft`、`IdempotencyKey`、`IfMatch` 定义为准。
- 中途失败时，先用只读 SQL 查 `content_items` 的四维状态（`workflow_status`、`publication_status`、`visibility`、`record_state`）再决定继续或清理，不要直接改库。
- 这条链路同时属于 VH-02（内容生命周期）。只想练 VH-01 的话可以先跳过。

## 6. 另外三个改代码观察实验（改完必须还原）

1. 把 `CatalogCursor.decode(token, CatalogSort.DEADLINE)` 里的 `DEADLINE` 改成 `UPDATED`，观察 `CATALOG_CURSOR_MISMATCH`。
2. 删掉 `CatalogQuery.orderBy` 中 `DEFAULT` 分支结尾的 `, c.id desc`，思考同一 `sort_order` 的多条记录顺序为什么不再稳定。
3. 把 `CatalogSort.resolve` 里 `RESOURCE` 显式 `DEADLINE` 的抛错改成静默降级，思考前端会怎样错误展示。
