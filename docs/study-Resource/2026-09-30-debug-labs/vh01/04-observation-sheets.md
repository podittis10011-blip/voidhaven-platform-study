# 04 观察记录表

规则：**先在"我的预测"列写，再按 `F8` 看实际值，最后写结论。** 顺序反了就只是抄答案。

## D1 游标

| 观察点 | 我的预测 | 实际值 | 结论（一句话） |
|---|---|---|---|
| `c1.` 后面的载荷明文格式 | | | |
| `encode()` 结果末尾有没有 `=` | | | |
| `decode` 里 `parts.length` | | | |
| sort 不匹配时抛的错误码 | | | |
| ULID + `UPDATED` 时抛的错误码 | | | |
| `legacy=true` 时 `key` 是什么 | | | |

## D2 排序

| 场景 | 我的预测（结果或错误码） | 实际值 | 结论 |
|---|---|---|---|
| 竞赛 + 无参 + 后台 `DEADLINE` | | | |
| 竞赛 + `"updated"` + 后台 `DEADLINE` | | | |
| 资源 + 无参 + 后台 `DEADLINE` | | | |
| 资源 + `"deadline"` | | | |
| 任意 + `"popularity"` | | | |
| 三个断点里最先执行的是哪一个（`:47` / `:48` / `:49`） | | | |

## D3 SQL

| 观察点 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| DEFAULT 排序 join 了哪张表 | | | |
| DEADLINE 排序 join 了哪两张表 | | | |
| 关键词 `"数学"` 在 args 里出现几次 | | | |
| `headcount` 这种 number 筛选绑到哪一列 | | | |
| 游标谓词出现在 ORDER BY 之前还是之后 | | | |
| limit 是拼进字符串还是参数绑定 | | | |
| 空排序键归一化成什么（DEFAULT / DEADLINE） | | | |
| 这条测试**没有**证明的东西 | | | |

## D4 来源策略

| 输入 | 我的预测 | 实际（放行/拒绝 + 停在哪个锚点） | 命中原因 |
|---|---|---|---|
| `https://contest.example.edu/rules` | | | |
| `http://localhost:8080/...` | | | |
| `https://example.invalid/rules` | | | |
| `https://127.0.0.1/rules` | | | |
| `https://192.168.1.2/rules` | | | |
| `https://metadata.internal/rules` | | | |
| `https://user:password@contest.example.edu/rules` | | | |
| fixture 开启后的 `...:18080/local-fixtures/source` | | | |
| fixture 开启后的 `...:8080/local-fixtures/source` | | | |

## D5 主链（探针用例 1）

| 变量 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| `size` → `pageSize` | | | |
| `sql.args` 最后一项 | | | |
| `ranked.size()` | | | |
| `hasNext` | | | |
| `page.size()` | | | |
| 替身仓储返回的顺序 | | | |
| `visible` 的顺序 | | | |
| `nextCursor` 解出的 `key` / `id` | | | |
| 返回的 `sort` | | | |
| `jdbc.query` 被调用几次 | | | |
| `visible.size()` 与 `pageSize` 的关系 | | | |

## D6 边界

| 场景 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| 来源过滤后本页只剩 1 条，游标指向谁 | | | |
| 恰好 `pageSize` 条时 `nextCursor` | | | |
| `pageSize=0` → 实际值 → limit | | | |
| `pageSize=999` → 实际值 → limit | | | |
| 游标排序不匹配时 `queryCount` | | | |
| 同一 `ORIGINAL` 元数据在 legacy `resource` 的结果 | | | |
| 同一 `ORIGINAL` 元数据在 `resource.article` 的结果 | | | |
| 后台把资源默认排序配成 `DEADLINE` 的结果 | | | |
| 用户显式要求资源按 `deadline` 排的结果 | | | |

## D7 详情与 404

| 观察点 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| 详情走哪个仓储方法 | | | |
| 详情传 `includeMedia` 的值 | | | |
| 列表传 `includeMedia` 的值 | | | |
| 详情时 `media` 被查询几次 | | | |
| 列表时 `media` 被查询几次 | | | |
| 来源非法时抛的错误码 | | | |
| 仓储查不到时抛的错误码 | | | |
| 这两个错误码是不是同一个 | | | |

## D8 关联与 sitemap

| 观察点 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| 关系表 SQL 是单向还是双向 | | | |
| id 集合用什么容器去重保序 | | | |
| 关系表 `[B,C]` + 详情 `[A,B]` 的最终顺序 | | | |
| 关联结果有没有 `nextCursor` | | | |
| 关联条数上限 | | | |
| sitemap 的每类条数上限 | | | |
| 来源不合规的已发布内容会不会进 sitemap | | | |
| sitemap 与详情是不是同一条可见性规则 | | | |

## D9 HTTP 入口与信封

| 观察点 | 我的预测 | 实际值 | 结论 |
|---|---|---|---|
| `/resources` 不传 `pageSize` 时实际值 | | | |
| 这个默认值写在哪里 | | | |
| 不挂 Filter 时 `requestId` | | | |
| 挂 `RequestIdFilter` 后 `requestId` 形态 | | | |
| 响应头里多了什么 | | | |
| `ApiException` 时的状态码与信封字段 | | | |
| 未捕获异常时的状态码与 `retryable` | | | |
| 缺必填参数 `module` 时的状态码 | | | |
| 为什么是这个状态码 | | | |
| 这条结论能外推到真实安全链吗 | | | |

---

## 参考答案

<details>
<summary>先自己填完再看</summary>

### D1

| 观察点 | 实际值 |
|---|---|
| 载荷明文 | `DEADLINE|2026-10-15|01HZXC0V4K7N9PQRSTVWXY0001` |
| 末尾 `=` | 没有（`withoutPadding()`） |
| `parts.length` | `3`（`split("\\|", 3)`，第三段允许再含 `\|`） |
| sort 不匹配 | `CATALOG_CURSOR_MISMATCH` |
| ULID + `UPDATED` | `CATALOG_CURSOR_INVALID` |
| `legacy=true` 的 `key` | 空字符串 `""` |

### D2

| 场景 | 结果 |
|---|---|
| 竞赛 + 无参 + 后台 `DEADLINE` | `DEADLINE` |
| 竞赛 + `"updated"` | `UPDATED`（用户覆盖后台） |
| 资源 + 无参 + 后台 `DEADLINE` | `UPDATED`，**不报错** |
| 资源 + `"deadline"` | 400 `CATALOG_SORT_UNSUPPORTED` |
| `"popularity"` | 400 `CATALOG_SORT_INVALID` |
| 断点执行顺序 | `:47`（算 explicit）→ `:48`（定 resolved）→ `:49`（判断降级） |

### D3

| 观察点 | 实际值 |
|---|---|
| DEFAULT join | `left join catalog_order_entries o ...` |
| DEADLINE join | `competition_revision_details cd` + `recruitment_revision_details rd` |
| `"数学"` 出现次数 | 3 次（title / summary / source_name 各一次） |
| number 筛选列 | `f.value_number = ?` |
| 游标谓词位置 | ORDER BY 之前（appendCursor 在 orderBy 之前调用） |
| limit | 参数绑定：`limit ?` + args 里放数值 |
| 空排序键 | DEFAULT → `"20000"`；DEADLINE → `"~"` |
| 没证明的东西 | SQL 能在 MySQL 执行、索引是否命中、真实空值排序行为 |

### D4

| 输入 | 结果 | 原因 |
|---|---|---|
| `https://contest.example.edu/rules` | 放行 | 唯一放行点 `:46` |
| `http://localhost:8080/...` | 拒绝 | scheme 不是 https |
| `https://example.invalid/rules` | 拒绝 | host 以 `.invalid` 结尾 |
| `https://127.0.0.1/rules` | 拒绝 | 字面量地址且回环 |
| `https://192.168.1.2/rules` | 拒绝 | 字面量地址且 site-local |
| `https://metadata.internal/rules` | 拒绝 | host 以 `.internal` 结尾 |
| 带凭据 URL | 拒绝 | `uri.getUserInfo() != null` |
| fixture `:18080` | 放行 | 与配置的 `localFixtureSource` 完全相等 |
| fixture `:8080` | 拒绝 | 字符串不等（端口不同） |

### D5

| 变量 | 实际值 |
|---|---|
| `size → pageSize` | `2 → 2` |
| args 最后一项 | `3` |
| `ranked.size()` | `3` |
| `hasNext` | `true` |
| `page.size()` | `2` |
| 替身返回顺序 | `C, B, A` |
| `visible` 顺序 | `A, B` |
| `nextCursor` | `key="2"`、`id=01JCONTENT0000000000000002` |
| `sort` | `"default"` |
| `jdbc.query` 次数 | `1` |
| 关系 | 未过滤时 `visible.size() == page.size() == pageSize` |

### D6

| 场景 | 实际值 |
|---|---|
| 本页剩 1 条 | 游标仍指向原始页末条 `ITEM_B`（`key=2`） |
| 恰好一页 | `nextCursor = null` |
| `pageSize=0` | 夹到 1，limit = 2 |
| `pageSize=999` | 夹到 50，limit = 51 |
| 排序不匹配 | `queryCount = 0`，一次目录查询都没发 |
| legacy `resource` + `ORIGINAL` | 被拒（`allowsOriginalSource=false`） |
| `resource.article` + `ORIGINAL` | 放行 |
| 后台误配 | 静默降级 `UPDATED`，SQL 为 `order by c.updated_at desc` |
| 用户显式 `deadline` | 400 `CATALOG_SORT_UNSUPPORTED` |

### D7

| 观察点 | 实际值 |
|---|---|
| 详情仓储方法 | `findPublicByIdAndKind(id, kind)`；不限定 kind 的重载用 `findPublicById(id)` |
| 详情 `includeMedia` | `true` |
| 列表 `includeMedia` | `false` |
| 详情时 `media` 查询次数 | 1 |
| 列表时 `media` 查询次数 | 仍然是 1（列表没查） |
| 来源非法 | 404 `CONTENT_NOT_FOUND` |
| 仓储查不到 | 404 `CONTENT_NOT_FOUND` |
| 是不是同一个 | 是。三条失败路径都汇到 `orElseThrow` 那一行 |

### D8

| 观察点 | 实际值 |
|---|---|
| 关系表方向 | 双向 union（`rel.target_content_item_id` 与 `source.id` 各查一次） |
| 容器 | `LinkedHashSet`，去重且保留插入顺序 |
| 最终顺序 | `B, C, A`（关系表 `[B,C]` 在前，详情字段新增的 `A` 追加在后） |
| `nextCursor` | 恒为 `null`（固定上限，不分页） |
| 关联上限 | 24 |
| sitemap 上限 | 每 kind 2000 |
| 非法来源进 sitemap | 不会 |
| 与详情同规则 | 是，都用 `hasPublicSource` |

### D9

| 观察点 | 实际值 |
|---|---|
| `/resources` 默认 `pageSize` | 20 |
| 默认值出处 | `@RequestParam(defaultValue = "20")` |
| 不挂 Filter 时 `requestId` | `null` |
| 挂 Filter 后 `requestId` | 26 位 ULID |
| 响应头 | `X-Request-Id` |
| `ApiException` | HTTP 400/404/409，信封 `{code, message, requestId, fieldErrors, retryable}`；`retryable` 仅在 429 或 5xx 为 true |
| 未捕获异常 | 500 `TEMPORARILY_UNAVAILABLE`，`retryable=true` |
| 缺 `module` | **500 `TEMPORARILY_UNAVAILABLE`** |
| 原因 | `MissingServletRequestParameterException` 没有专门分支，被 `@ExceptionHandler(Exception.class)` 兜住 |
| 能否外推 | 不能完全外推：这是 standalone MockMvc，不经过 Security 过滤器链；需要真实栈复核 |

</details>
