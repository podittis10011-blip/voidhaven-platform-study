# 07 闭卷题单

## 使用说明

- 先闭卷答，写完再展开文末参考答案。
- 允许查源码和本包材料；查过就记为 `ASSISTED`，不算独立掌握证据。
- 想当候选证据：第一层手写完成后录一次 3 分钟口述。
- 第一层的 F1/F2/F4 直接沿用旧包 `2026-09-18-vh01-code-lab/04-question-sheet.md` 的题号，方便对照；本包新增 D1–D6，问的都是**只有调过试才能答得具体**的东西。

## 第一层：理解输出

### 沿用旧包

| # | 题目 | 本包要求 |
|---|---|---|
| F1 | 把 VH-01 读路径从 HTTP 讲到 DTO，标出每一处关键分支 | 这次要**标注断点编号**（如"夹取见 D5-1"），而不是只讲文字 |
| F2 | `c1.` 载荷由哪几段组成？`decode` 有哪几条出口？ | 每条出口配一个你亲眼看过的输入 |
| F4 | `nextCursor` 取的是哪一条？为什么不能取可见列表的最后一条？ | 用 D6-1 的实际值说明 |

### 本包新增

| # | 题目 | 提示 |
|---|---|---|
| D1 | `nextCursor` 和"本页可见列表"在什么情况下会指向**不同**的行？为什么这是对的？ | D5-16、D6-1 |
| D2 | `inOrder` 里的 `LinkedHashMap` 在防什么？如果删掉第二个循环，页内容和游标会怎样自相矛盾？ | D5-13、D5-14 与实验 E3 |
| D3 | `pageSize + 1` 里的 "+1" 是相对谁而言？`pageSize=50` 和 `pageSize=999` 的 SQL 为什么一样？ | D5-8、D6-3、实验 E7 |
| D4 | 排序不匹配的 400 为什么发生在"一次 SQL 都没发"之前？这个顺序有什么产品意义？ | D5-7、D6-4 |
| D5 | 同一个 `{"sourceMode":"ORIGINAL"}`，为什么 legacy `resource` 被拒、`resource.article` 放行？这个差异写在哪份配置里？ | D5-20、D5-21、D6-5 |
| D6 | 后台把资源默认排序配成 `DEADLINE` 会静默降级，用户显式传 `deadline` 却报 400。这条分界线的判断依据是什么？ | D2-5、D6-6 |
| D7 | 详情和列表共用同一个 DTO，靠什么产生两种形状？这个开关的收益和代价各是什么？ | D7-4、D7-6；实习观察：媒体被查询了几次 |
| D8 | 关联 id 从哪两个地方来？为什么用 `LinkedHashSet` 而不是 `HashSet`？sitemap 为什么和详情用同一条谓词？ | D8-1～D8-9 |
| D9 | `requestId` 从哪来、存在哪、怎么进信封？错误信封有哪些字段，`retryable` 怎么算出来的？ | D9-4、D9-5、D9-7；实习观察：不挂 Filter 时是什么 |

## 第二层：面试追问链

| # | 追问链 | 要给出的关键点 |
|---|---|---|
| I1 | 「列表怎么分页？」→「为什么不用 offset？」→「排序键不唯一会怎样？」→「翻到第二页时用户换了排序呢？」 | keyset 分页；offset 的两个失效场景；排序键必须唯一稳定，不唯一就加次级键；游标内嵌 sort，不匹配直接 400 |
| I2 | 「这段代码你怎么验证过？」→「单元测试证明了什么？」→「没证明什么？」→「那怎么补？」 | 探针证明了控制流与分支取值；没证明真实 SQL 可执行性、索引、并发；补法是集成测试/真实栈，不是再加断言 |
| I3 | 「为什么写时校验了，读时还要再校验来源？」→「如果读时不校验会怎样？」 | 历史脏数据、手工导入、策略收紧后的存量内容；读时 fail-closed 让列表丢弃、详情 404，避免把不合规来源公开展示 |
| I4 | 「你怎么知道这段 SQL 是这么拼的？」→「日志断点和普通断点的区别？」→「Drop Frame 能撤销吗？」 | 日志断点看拼装轨迹不打断节奏；Drop Frame 只退执行位置，不退已发生的副作用（E3/`queryCount` 会暴露） |
| I5 | 「接口返回 500 了，怎么排查？」→「怎么区分是客户端传错还是服务端坏了？」→「你们现在的兜底会把这两类混在一起吗？」 | 错误信封里 `code` 是分类真源；`requestId` 是日志检索键（`MDC` 里同名）；本项目的通用兜底确实会把缺参（客户端问题）变成 500，这是可讨论的边界，不要装作没有 |

## 自评标准

- 及格：F1、F2、D1、D4 能不看材料说清因果；I1 四个追问都能接住。
- 良好：D5 能说出配置依据（`content-modules.v1.json` 的 `allowsOriginalSource`），D2 能自己构造反例说明 `inOrder` 的作用，D9 能说清 `requestId` 的完整来源链路与 `retryable` 的计算规则。
- 待补：任何一条只能说结论、说不出"不这样会怎样"，就还没过关。

---

## 参考答案

<details>
<summary>先自己答完再展开</summary>

### D1

当原始页里有条目被 `hasPublicSource` 过滤掉时。游标代表的是**"这次查询扫到哪一行"**（原始页末条），而不是"用户看到哪一行"；若取可见列表末条，下一页会从被过滤条目**之前**继续，导致重复扫描甚至死循环。

### D2

防的是 `findPublicByIds`（`IN` 查询）不保证与传入顺序一致。删掉第二个循环后，页内容顺序由替身/数据库顺序决定（实测会变成 `B, A`），而 `nextCursor` 仍取 `ranked` 里的 `ITEM_B`——响应里"A 在 B 前面"却说"下一页从 B 之后开始"，客户端要么重复要么漏读。

### D3

"+1"是相对**当次请求**的页大小。`Math.min(size, 50)` 把 50 和 999 都变成 50，所以 SQL 的 limit 参数都是 51；两者在 SQL 层面不可区分，只能靠边界外用例和边界用例一起测才能覆盖。

### D4

因为 `CatalogCursor.decode(...)` 在 `CatalogQuery.selectIds(...)` **之前**执行（`ContentService:73` 早于 `:90`）。产品意义：非法/过期游标不会消耗数据库资源，也不会泄露任何一行数据；客户端必须从第一页重新开始。

### D5

`hasPublicSource` 的第二条路径要求 `modules.requireKnown(moduleKey(item)).allowsOriginalSource()` 为真。`moduleKey(item)` 在条目没有显式模块时回退到 legacy 模块名（`resource`），而 `content-modules.v1.json` 里 legacy `resource` 的 `allowsOriginalSource` 是 `false`，`resource.article` 是 `true`。所以差异不在元数据，而在模块配置。

### D6

看 `explicit` 是否为空：`CatalogSort.resolve` 里 `parse(requested)` 有值说明**用户显式要求**，做不到就报 400（否则用户以为自己在按截止日期排序）；`explicit == null` 说明只是后台配置，静默降级成 `UPDATED`（否则一次误配会让整站资源列表不可用）。

### D7

靠 `toPublic(item, includeMedia)` 的布尔开关：详情传 `true`，会去查媒体、公开行动入口与最新核验；列表/首页/关联传 `false`，只给轻量字段。
收益：一个 DTO 覆盖两种场景，前端字段一致，序列化路径唯一；代价：字段是否为空取决于调用点，读代码时光看 DTO 定义看不出形状差异，必须回到调用点（这也正是 F8 那道题的考点）。
实测证据：用例 8 里详情调用后 `media` 替身被调用 1 次，紧接着调列表，次数**仍然是 1**。

### D8

两条来源：① 关系表的双向 union（`revision_content_relations`）；② 详情 JSON 里的 `relatedCompetitionIds`（历史数据兼容）。
用 `LinkedHashSet` 是因为它同时满足"去重"和"保留插入顺序"——换成 `HashSet`，关联结果的展示顺序会在每次请求间漂移。
sitemap 与详情共用 `hasPublicSource`，是为了避免搜索引擎收录一个点开就 404 的 URL：列表丢弃、详情 404、sitemap 排除，三处是同一条规则。

### D9

`requestId` 由 `RequestIdFilter` 用 `Ids.ulid()` 生成，写进 `request.setAttribute(ATTRIBUTE, id)`、响应头 `X-Request-Id` 和 `MDC`（日志检索用）；Controller 的 `ok(...)` 只是从 request attribute 里取出来放进 `ApiResponse`。所以不挂 Filter 时它是 `null`。
错误信封字段：`code`、`message`、`requestId`、`fieldErrors`、`retryable`。`retryable` 在 `ApiExceptionHandler.response(...)` 里由状态码算出：`429` 或 `5xx` 才为 `true`；429 还会额外带 `Retry-After: 900`。

</details>
