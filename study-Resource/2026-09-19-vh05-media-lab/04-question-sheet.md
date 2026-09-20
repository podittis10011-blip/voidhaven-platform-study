# VH-05 题单（两层：费曼理解输出 + 面试追问链）

## 使用说明

- 先闭卷作答，写完再展开文末参考答案对照。参考答案只用于校准，不要求逐字一致。
- 允许查阅 `01-code-reading-guide.md`、源码与官方文档；查过资料再作答，本轮记为 `ASSISTED`。
- 想作为候选证据：第一层手写完成后录一次 3 分钟口述，第二层按 30 秒版录一次。
- 题目来源逐题标注：`项目代码` / `项目风险` / `公开面经常见考点` / `官方能力边界`。这不是「高频统计」。

## 第一层：费曼理解输出

| # | 题目 | 答题范围与线索 | 建议篇幅 | 来源 |
|---:|---|---|---|---|
| F1 | 讲清一次正文图片上传从 HTTP 到 MinIO 再到 MySQL 的完整路径，标出每一步的产物 | 只讲成功路径；提示：`MediaController` 第 36～45 行 → `IdempotencyService` → `MediaValidator` → `MediaService` 第 42～76 行 → `S3MediaStorage.put` → `media_assets` / `revision_media` / `audit_logs` | 一张图 + 8 句 | 项目代码 |
| F2 | 为什么说这里的图片是「归一化」后才落地的？给出三个具体后果 | 提示：`MediaValidator` 第 26～42 行、`pom.xml` 第 40 行的 `imageio-webp` | 5 句 | 项目代码 |
| F3 | 公开、社群、后台三条读路径各由什么条件决定？为什么不能合并成一条 SQL？ | 提示：`MediaReadService` 第 22、28、34 行；`PublicMediaController`；`NoStoreResponseFilter` 第 17 行的路径清单 | 一张 3 行表 + 3 句 | 项目代码 |
| F4 | 一条已发布内容被下架，为什么下一次请求就取不到图？如果把响应改成 `max-age=3600` 会怎样？ | 提示：`PublicMediaController` 第 25～27 行的注释与 `noStore()`；`NoStoreResponseFilter` 没覆盖这条路径 | 4～5 句 | 项目代码 + 实测 |
| F5 | 越权读取为什么返回 `404` 而不是 `403`？这对「合作方只能看自己的内容」意味着什么？ | 提示：`MediaReadService.loadManaged` 的 SQL 直接判归属，查不到就是 `MEDIA_NOT_FOUND` | 4 句 | 项目代码 + 实测 |
| F6 | 上传成功之后，图片为什么可能不出现在正文里？系统在哪个时刻强制两者一致？ | 提示：`MarkdownDocumentService.mediaIds`、`ContentManagementService.syncReferencedMedia`（393 行）与 `requireCurrentMediaMatchesDocument`（417 行） | 5～6 句 | 项目代码 |
| F7 | 「每修订最多 10 张、合计最多 20 MB」是在哪里判断的？两个人同时上传最后一张额度时怎么保证不超？ | 提示：`MediaService` 第 53～56 行、第 46 行 `findByIdForUpdate` | 5 句 | 项目代码 |
| F8 | 对象存储没有事务：写完对象、写库失败会怎样？如果进程在 `put` 之后、`update` 之前**崩溃**呢？ | 提示：第 60～66 行的 `afterCompletion` 补偿、第 73 行的兜底分支；崩溃场景要说清「补偿代码有没有机会跑」 | 5 句 | 项目代码 + 项目风险（进阶） |
| F9 | 孤儿媒体是怎么被找出来删掉的？为什么要有 `media_deletion_jobs` 和租约？ | 提示：`MediaOrphanCleanupService` 第 29、43 行的两个阶段；`V7` 迁移的表结构；每天 01:30 的 `RetentionService` | 5～6 句 | 项目代码 |
| F10 | 上传失败时你可能看到 `500 TEMPORARILY_UNAVAILABLE`、连接被中断、`400 MEDIA_REJECTED` 三种形态，分别是什么原因？ | 用 `03` 的 W4～W9 实测结果回答；提示：`HandlerMethodValidationException`、`FileSizeLimitExceededException`、`client_max_body_size` | 一张 3 行表 | 项目风险 + 实测 |

## 第二层：面试模拟追问链

| # | 追问链 | 你要给出的关键点 | 来源 |
|---:|---|---|---|
| I1 | 「图片存哪儿？」→「为什么不用本地磁盘或数据库 BLOB？」→「桶设成公有不是更简单？」→「以后上 CDN 怎么改？」 | MinIO 的私有桶 + 应用层授权；本地磁盘在多副本/重建时丢，BLOB 让数据库体积和备份失控；公有桶等于放弃「下架立刻生效」；CDN 要处理 `no-store` 与回源鉴权，不能直接套用 | 项目代码 + 公开面经常见考点 |
| I2 | 「上传怎么防注入？」→「改后缀名能骗过你吗？」→「EXIF 怎么办？」→「解压炸弹呢？」→「WebP 支持哪来的？」 | 白名单 + 真解码 + 重编码；不信任扩展名与 `Content-Type`；重编码天然丢元数据；`MAX_PIXELS`/`MAX_EDGE`；JDK 自带 ImageIO 不认 WebP，靠 TwelveMonkeys 插件 | 项目代码 |
| I3 | 「私有图怎么授权？」→「每次请求都查库不慢吗？」→「加缓存还怎么保证下架立刻生效？」→「为什么不用预签名 URL？」 | 三条独立 SQL；索引足够，超时前先失效比快更重要；缓存要短 TTL + 主动失效；预签名 URL 一旦签发就无法撤回，与「下架立刻生效」直接冲突 | 项目代码 + 项目风险 |
| I4 | 「传了图但忘了写进正文？」→「用户重复点上传？」→「同一张图传两次会去重吗？」 | 送审时 `409 CONTENT_MEDIA_REFERENCE_INVALID`；幂等键 + 请求体指纹；**不去重**，同一张图两次上传是两个资产（去重会引入共享资产的删除与所有权难题） | 项目代码 + 实测 |
| I5 | 「删除是同步还是异步？」→「删失败了怎么办？」→「两个实例同时跑？」→「对象删掉了但状态没更新？」 | 两阶段：先入队改状态，再按租约删；失败落 `FAILED` + `attempts` + 5 分钟后重试；`locked_by`/`locked_until` 抢占保证只有一个实例处理；重跑是幂等的（对象不存在时 `delete` 不报错） | 项目代码 |
| I6 | 「这个上传接口有什么已知缺陷？」→「你怎么发现的？」→「打算怎么修？」 | 三处不一致：参数校验 500、超大文件 500/连接中断、送审响应版本号滞后；发现方式是真栈重放 + 对照 `ApiExceptionHandler` 的分支；修法是补 `@ExceptionHandler` 分支和补测试 | 项目风险 + 实测 |

## 自评标准

- 及格：F1、F3、F5、F6 能不看材料讲清因果；I1、I3 的追问都能接住。
- 良好：F4 能说清「`no-store` 由谁负责」；F8 能区分「事务回滚」与「进程崩溃」两种失败模型；I5 能讲清两实例竞争。
- 待补：任何一条只能复述结论、说不出「如果不这样会怎样」，或者把「桶是 private」当成唯一防线，就还没过关。

## 参考答案与解析

<details>
<summary>先自己答完 F1～F10、口述完 I1～I6，再展开</summary>

### F1

`POST /api/v1/media/uploads` 是 multipart 请求，必须带 `Idempotency-Key`（否则 `400 IDEMPOTENCY_KEY_REQUIRED`）和 CSRF 令牌。请求先过 `SecurityConfig` 第 30 行的 URL 规则（`/api/v1/media/**` 要 `PARTNER/ADMIN/SUPER_ADMIN`），再由 `MediaController` 第 37 行接收三个参数。第 43 行把 `contentItemId + altText + 原始文件名 + 文件字节的 SHA-256` 作为请求体指纹交给 `IdempotencyService`：键已存在且指纹相同就直接返回上次的响应，指纹不同返回 `409 IDEMPOTENCY_KEY_REUSED`。

真正执行时进入 `MediaService.upload`：先复查账号可用、`altText` 合法，然后 `MediaValidator` 真解码并重编码（产物：归一化字节 + 服务端认定的 mediaType/宽高/扩展名），再 `findByIdForUpdate` 锁住内容行，做归属与状态检查、配额检查，最后先 `storage.put` 写对象、再写 `media_assets`、`revision_media`、`audit_logs` 三张表。返回给前端的是 `{id, url:/api/v1/media/{id}/preview, mediaType, width, height, altText}`——**注意 url 是受控预览地址，不是公开地址**。

### F2

因为落地的不是用户给的那串字节，而是 `MediaValidator` 第 36～41 行「解码成像素、再按有无 alpha 编码成 PNG 或 JPEG」的结果。三个具体后果：

1. **元数据消失**：EXIF（含 GPS、设备信息）、ICC、注释、缩略图都不会进入对象存储；
2. **类型以解码结果为准**：把 `.txt` 改名成 `.png` 也过不了第 26～30 行的 `ImageIO.getImageReaders`；
3. **体积不可预测**：重编码后的字节可能比输入更大，所以第 42 行要再查一次 5 MB，可能出现「4.9 MB 的原图上传失败」。

本轮实测的直接证据：一张 69835 字节、1280×971 的 PNG 上传后，接口返回 `mediaType: image/jpeg`、对象大小 72432 字节——类型和体积都变了。

### F3

| 路径 | 入口 | 决定可见性的条件（`MediaReadService`） |
|---|---|---|
| 公开 | `GET /api/v1/public/media/{id}`（permitAll） | 第 22 行：资产 `ACTIVE`，且（正文挂在 `published_revision_id` 且内容 `PUBLIC+ACTIVE`）或（行动入口 `enabled+PUBLIC` 且内容公开且 `OPEN`）或（社群条目 `active+PUBLIC`）或（项目加入条目 `active+PUBLIC`） |
| 社群 | `GET /api/v1/public/community/media/{id}`（先校验 `X-Community-Grant`） | 第 34 行：社群条目 `active`（不限级别）或项目加入条目 `active` 或行动入口 `COMMUNITY_GATED` 且内容已发布公开且 `OPEN` |
| 后台 | `GET /api/v1/media/{id}/preview`（需登录且属主或管理员） | 第 28 行：挂在**当前修订**上的正文/行动入口，内容是 `ACTIVE`，且操作者是管理员或对应合作方；或未过期的行动入口暂存 |

不能合并的理由是**授权主体不同**：公开路径的谓词是关于「内容当前是否对陌生人可见」，后台路径的谓词是关于「这个账号是不是属主」。把两者塞进一条 SQL，很容易写出「满足任一条件即可读」的表达式——那就等于任意一条内容公开，它的所有媒体都能被任何人读到。

### F4

因为读路径每次都执行第 22 行的 SQL，`visibility` 一变成 `HIDDEN`，EXISTS 条件立刻不成立，接口直接抛 `404 MEDIA_NOT_FOUND`（本轮 R8 实测：下架后立刻 404，`requestId=ASHC3Z6CYE4AWMBFRAMY76K78K`）。让这个「立刻」成立的还有第二个前提：响应不能被缓存。`PublicMediaController` 第 25～27 行专门为这条路径设了 `CacheControl.noStore()`，而 `NoStoreResponseFilter` 第 17 行的路径清单只覆盖 `/api/v1/media/` 与含 `/community` 的路径，**不覆盖** `/api/v1/public/media/`。

如果改成 `max-age=3600`，已经拿到图的浏览器和中间缓存会在最长一小时内继续展示它，而下架按钮的语义就从「立刻失效」退化成「最多一小时后失效」——对「紧急下架」这种场景，这是不可接受的。

### F5

`loadManaged` 第 28 行的 SQL 把「是管理员」和「合作方档案属于该账号」写进了 EXISTS 条件里，查不到 key 就统一抛 `404 MEDIA_NOT_FOUND`（本轮 R4 实测：合作方读管理员内容里的资产得到 404）。返回 404 而不是 403 的价值是**不泄露存在性**：如果返回 403，攻击者可以拿 ID 逐个试探，用「403 说明存在、404 说明不存在」把整个媒体库的 ID 空间枚举出来；统一 404 让「不存在」和「存在但无权」不可区分。

这对合作方场景的含义是：**租户隔离是默认行为**，合作方连「别的内容有多少张图」都探测不到。代价是排障时不容易区分「路径写错」和「真的没权限」，所以日志里要靠 `requestId` 对齐。

### F6

上传接口只做两件事：写 `media_assets`（资产的物理与元数据）和写 `revision_media`（资产与修订的挂载关系）。正文内容存在修订的 `body_json` 里，只有在保存正文时才更新：`ContentManagementService.syncReferencedMedia`（第 393～398 行）以正文解析出的图片 ID 列表为准**重写** `revision_media`——没被引用的挂载会被删掉（本轮实测：PATCH 前 2 行、PATCH 后 1 行）。

强制一致的时机是**送审**：`submit` 第 126 行调用 `requireCurrentMediaMatchesDocument`（第 417～422 行），要求「正文引用集合」与「已挂载集合」完全相等，否则 `409 CONTENT_MEDIA_REFERENCE_INVALID`。本轮 `03` 的 W12 实测就是这个：上传了两张图但正文里一张都没写，送审直接被拒（`requestId=AH73G61EB16P4KBFTVC458GGEF`）。

这道校验的用途是防止「修订存了图、但正文压根不展示」，也顺带保证了公开投影（`forPublishedRevision`）与正文渲染结果一致。

### F7

判断在 `MediaService.upload` 第 53～56 行：一条 `count(*)` 数当前修订挂了几张，一条 `sum(size_bytes)` 算总字节，超线就 `409 MEDIA_LIMIT_EXCEEDED`。它们在**锁内**执行——第 46 行的 `findByIdForUpdate(contentItemId)` 先对内容行加悲观锁，于是同一内容的并发上传被串行化：第二个请求要等第一个事务提交后才能读到新的计数，不会两个请求都看到「9 张」然后一起塞进去。

要注意的是：锁的粒度是「内容行」，所以并发的代价落在同一篇内容上，而不是整个表；不同内容之间的上传互不阻塞。另外配额按**修订**计算，创建候选修订时 `copyReferencedMedia`（第 380 行）会把旧修订的媒体挂到新修订上，所以改稿不会凭空获得额外额度。

### F8

两种失败要分开说：

1. **写库抛异常、事务回滚**：第 60～66 行注册的 `afterCompletion` 补偿会在事务结束时看到状态不是 `COMMITTED`，于是删除刚才写进去的对象，避免留下无人引用的孤儿。第 72～74 行还处理了「根本不在事务里」的情况（没有同步激活时直接删）。
2. **进程在 `put` 之后、`update` 之前崩溃**：补偿代码没有机会执行，对象会成为控制台里看得见、数据库里没有记录的孤儿。这类问题不能靠事务解决，只能靠**周期性对账**——本项目正是用 `MediaOrphanCleanupService` 反着做：不看对象存储，而是在数据库里找「`ACTIVE` 但无人引用」的资产再删对象。反过来（对象存在、数据库无记录）在本项目里没有专门的清理器，属于已知的残留风险。

### F9

分两个阶段（`MediaOrphanCleanupService`）：第 29～41 行 `queueUnattachedAssets` 先用一条 `for update` 的查询找出「`ACTIVE`、且没有任何 `revision_media` / `community_entries` / `content_action_entries` / 未过期暂存引用」的资产，把状态改成 `DELETION_PENDING` 并插入 `media_deletion_jobs`；第 43～71 行 `processDueDeletions` 再按租约抢占任务、删对象、把资产置 `DELETED`、任务置 `SUCCEEDED`。

「先入队」的意义有三点：删除对象**不可回滚**，先把意图落库才能在失败后重试；`DELETION_PENDING` 这个中间状态让并发上传能看见「这个资产正在被删」；任务表把「删什么」和「删到哪一步」记录下来，于是重启、重试、审计都有依据。租约（`locked_by` + `locked_until`，10 分钟）解决的是多实例竞争：抢到行的那次调用才处理，超时未完成的任务会被下一次调用重新捡起来。

本轮实测的对照状态：`XJZ024TZ9NS3D32NM501R6ZN59` 已经 `rev_links=0`、`action_links=0`、`live_reservations=0`，确实符合入队条件，但 `media_deletion_jobs` 仍是 0 行——因为定时任务每天 01:30 才跑。

### F10

| 你看到的 | 真实原因 | 位置 |
|---|---|---|
| `500 TEMPORARILY_UNAVAILABLE`（`altText` 为空/超长/缺失） | `@NotBlank`/`@Size` 在 `@RequestParam` 上触发 `HandlerMethodValidationException`，而 `ApiExceptionHandler` 只处理了「请求体校验」`MethodArgumentNotValidException`，没有这个方法参数分支 | `MediaController` 第 37 行 → `ApiExceptionHandler` 第 68 行兜底 |
| `500`（5 MB < 文件 ≤ 6 MB） | Spring multipart 的 `max-file-size: 5MB` 拒绝，`FileSizeLimitExceededException` 同样没有专门处理 | `application.yml` 第 6 行 |
| 连接中断，拿不到 JSON（文件 > 6 MB） | 请求在 nginx 就被拒绝，HTTP 层已经断了，应用根本没机会返回结构化错误 | `infra/nginx/default.conf` 第 10 行 `client_max_body_size 6m` |
| `400 MEDIA_REJECTED`（内容是文本） | 这才是**预期**的失败：参数校验通过、业务校验拒绝，返回项目统一的 `ApiError` 形状 | `MediaValidator` 第 24～30 行 |

一句话总结：只有「业务层主动抛出的 `ApiException`」才稳定地映射成 4xx；框架层抛出的异常如果没人接，就会落进兜底的 500，掩盖掉真实的用户输入错误。

### I1 要点

MinIO 私有桶；不落本地磁盘是因为容器重建、多副本、迁移都会丢本地文件；不用 BLOB 是因为数据库体积、备份与复制成本会被图片拖垮，而且大字段会污染 Buffer Pool。公有桶省掉的是「鉴权代码」，代价是放弃下架立即生效、无法按用户/社群区分粒度、也无法做租户隔离。上 CDN 时不能只是套一层缓存：`no-store` 与「回源时如何鉴权」都要一起设计，否则 CDN 会把「下架前的图」缓存下来。

### I2 要点

白名单只认 `JPEG/PNG/WEBP`，且通过真实解码器判定；改扩展名、伪造 `Content-Type` 在第 2 站第 26～30 行被挡。EXIF 靠重编码天然丢弃（第 36～41 行），不需要单独写「清元数据」逻辑。解压炸弹靠 `MAX_EDGE=6000` 与 `MAX_PIXELS=2500 万` 两道限制（第 20～21、34 行）。WebP 不是 JDK 自带能力，是 `pom.xml` 第 40 行引入 TwelveMonkeys 插件后才可解码——如果回答「Java 都支持」，那是错的。

### I3 要点

三条独立 SQL，条件各不相同；每次请求一次带索引的 EXISTS 查询，相比「图片被越权读取」的风险，这点延迟可以接受。要加缓存，必须同时加**短 TTL + 主动失效**（例如内容状态变更时清缓存），否则下架不再立刻生效。预签名 URL 的问题是**签发后无法撤回**：链接在有效期内即使内容已下架也能直接访问对象存储，正好破坏本链路最重要的属性。

### I4 要点

送审会因「正文引用集合 ≠ 已挂载集合」返回 `409 CONTENT_MEDIA_REFERENCE_INVALID`；重复点击要带同一个 `Idempotency-Key` 才能返回原结果，换新键就会产生新资产（指纹包含文件 SHA-256，改内容再复用旧键会得到 `409 IDEMPOTENCY_KEY_REUSED`）。系统**不做内容去重**：同一张图上传两次是两个资产，这是有意的——如果做去重，就得处理「一个资产被多个修订/多个租户引用时的引用计数与删除归属」，复杂度远高于收益。

### I5 要点

异步两阶段：先入队改 `DELETION_PENDING`，再按租约删。删失败写 `FAILED` + `attempts+1` + 5 分钟后重试（第 66～69 行）；多实例靠 `locked_by`/`locked_until` 抢占（第 45～54 行）；「对象删了但状态没更新」的后果是资产停在 `DELETION_PENDING`，之后的调用重新抢到任务再删一次——对象已经不存在，`delete` 不报错，所以整体是幂等的。

### I6 要点

三处：参数校验失败返回 500（应为 400）、5～6 MB 文件返回 500、送审响应里的版本号比库内小 1（照着它发 `If-Match` 会 409）。发现方式不是读代码猜，而是真栈重放：改了一次 `altText`、传了两个超限文件、把响应体里的版本号直接拿去做下一个请求，然后去 `docker logs` 里核对异常类型（`HandlerMethodValidationException`、`FileSizeLimitExceededException`）。修法是给 `ApiExceptionHandler` 补「方法参数校验」「缺少请求参数」「上传超限」三类分支，并为每类补一条 MockMvc 测试；同时明确「版本号一律以 `GET` 到的值为准」。

</details>
