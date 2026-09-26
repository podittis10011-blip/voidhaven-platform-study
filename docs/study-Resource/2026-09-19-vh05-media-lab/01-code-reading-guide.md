# VH-05 代码阅读路线（六站）

阅读版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`（工作树干净）。
行号以 2026-09-19 的工作树为准；改动后可能漂移，用方法名兜底。

## 0. 一句话链路

```text
上传（一次性）：
POST /api/v1/media/uploads（multipart + Idempotency-Key + X-XSRF-TOKEN）
  → SecurityConfig 第 30 行         （/api/v1/media/** 要 PARTNER/ADMIN/SUPER_ADMIN）
  → CsrfFilter                      （状态改变方法必须有令牌）
  → MediaController.upload          （取账号、角色、是否为管理员；算文件 sha256）
  → IdempotencyService.execute      （键 + 请求体指纹，落 idempotency_records）
  → MediaValidator.validate         （真解码 + 重编码，丢元数据，卡三道限制）
  → MediaService.upload             （锁内容行 → 归属/状态 → 配额 → 先写对象再写库）
  → S3MediaStorage.put              （MinIO 私有桶，key = content/{revisionId}/{assetId}.{ext}）
  → MySQL：media_assets + revision_media + audit_logs

读取（每次都重新判权）：
GET /api/v1/public/media/{id}            → loadPublic      （匿名，要求已发布且可见）
GET /api/v1/public/community/media/{id}  → loadCommunity   （要 X-Community-Grant）
GET /api/v1/media/{id}/preview           → loadManaged     （要登录，且是属主或管理员）
        ↓ 三者都返回 Cache-Control: no-store
```

## 第 1 站：HTTP 入口与三条端点

文件：`apps/api/src/main/java/cn/voidhaven/api/web/MediaController.java`（68 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 28～34 | `GET /api/v1/media/{id}/preview` | 唯一「受控预览」读入口；先解析账号与管理员身份，再交给 `MediaReadService.loadManaged` |
| 33 | `cacheControl(CacheControl.noStore())` | 每次都要回落数据库判断，绝不让浏览器或中间缓存留下副本 |
| 36～37 | `POST /api/v1/media/uploads` | 正文图片上传；`@RequestParam @NotBlank String contentItemId`、`@NotBlank @Size(max=240) String altText`、`@RequestParam("file") MultipartFile` |
| 42 | `file.getBytes()` 失败 → `400 MEDIA_REJECTED` | 传输层读不出来才算「读不出文件」，与「不是图片」是两种错误 |
| 43 | `idempotency.execute(key, new UploadIntent(...), ...)` | **请求体指纹** = `contentItemId + altText + 原始文件名 + 文件字节的 SHA-256`（第 63、66 行） |
| 47～61 | `POST /api/v1/media/action-uploads` | 行动入口二维码上传，走 `uploadAction`，与正文图片**不是同一套配额和表** |
| 64 | `accountId(auth)` | 每个请求都复查账号是否仍 `ACTIVE`，否则 `401 ACCOUNT_INACTIVE` |
| 65 | `isAdministrator(auth)` | `ROLE_ADMIN` 或 `ROLE_SUPER_ADMIN`；合作方为 `false` |

配套看授权规则：`apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java` 第 30 行——`/api/v1/public/**` 在 permitAll 里，`/api/v1/media/**` 与 `/api/v1/partner/**` 一起要求 `PARTNER/ADMIN/SUPER_ADMIN`。

**读完自答**

1. 同一个 URL 前缀 `/api/v1/media/**`，为什么「预览」和「上传」用同一条授权规则也不会出事？
2. 幂等指纹里为什么要放**文件字节的哈希**，只放 `altText` 和文件名会漏掉哪种重复提交？
3. `requestId(request)` 是从哪里来的？第 42 行的异常里为什么没有单独传 `requestId`？

## 第 2 站：校验与归一化（最容易讲错的一站）

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MediaValidator.java`（56 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 19～21 | `MAX_BYTES = 5MB`、`MAX_PIXELS = 25_000_000`、`MAX_EDGE = 6_000` | 三道限制：字节数、总像素、单边边长 |
| 23～24 | `validate(byte[])` 入口 | 空数组或超 5 MB 直接 `400 MEDIA_REJECTED` |
| 26～30 | `ImageIO.getImageReaders(stream)` | **靠真实解码器判断类型**，不信 `Content-Type`，也不信扩展名 |
| 31 | 白名单 `JPEG/JPG/PNG/WEBP` | WebP 能读出来是因为 `pom.xml` 第 40 行引入了 `com.twelvemonkeys.imageio:imageio-webp`（JDK 自带 ImageIO 不认 WebP） |
| 34 | 尺寸校验 | `width/height > 6000` 或 `width*height > 2500 万` 直接拒绝，防解压炸弹 |
| 36～41 | 解码 → 有 alpha 就输出 PNG，否则输出 JPEG | **重编码**，顺带丢掉 EXIF、ICC、注释等输入元数据 |
| 42 | 重编码后再查一次 5 MB | 输出可能比输入更大，所以要在这一步复查 |

这条链路的关键词不是「校验」，而是**归一化**：进库和进对象存储的永远是「解出来再压回去」的像素，不是用户给的那串字节。

**读完自答**

1. 攻击者把 `shell.jsp` 改名成 `photo.png` 并设置 `Content-Type: image/png`，会在哪一行被挡住？
2. 一张带 GPS 的 JPEG 上传后，为什么用户下载回来已经看不到 GPS？
3. 为什么「原图 4.9 MB」也可能在重编码后失败？

## 第 3 站：写路径、事务与补偿

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java`

`upload` 的十步（第 42～76 行）：

| 行 | 步骤 | 为什么在这个位置 |
|---:|---|---|
| 43 | 复查账号仍可用 | 会话可以活 8 小时，资格可能中途变化 |
| 44 | 校验 `altText` 非空且 ≤240 字 | 服务层再挡一次，避免绕过 Controller 的调用 |
| 45 | `validator.validate(input)` | **先做最贵、最纯的计算**，失败时不占用数据库连接 |
| 46 | `items.findByIdForUpdate(contentItemId)` | 悲观锁锁住内容行：同一内容并发上传时按顺序排队 |
| 47～50 | 非管理员必须拥有该内容 | 不属于自己 → `404 CONTENT_NOT_FOUND`（不泄露「存在但无权」） |
| 51 | 只允许 `ACTIVE` + `DRAFT/CHANGES_REQUESTED` | 已送审/已发布 → `409 CONTENT_MEDIA_NOT_EDITABLE` |
| 53～56 | 查当前修订已挂几张、共多少字节 | 上限：每修订 10 张、合计 20 MB → `409 MEDIA_LIMIT_EXCEEDED` |
| 58～59 | 先 `storage.put(key, ...)` | 对象存储没有事务，只能「先写、失败再删」 |
| 60～66 | 注册 `afterCompletion` 补偿 | 事务**回滚**时删刚写进去的对象，避免留下孤儿 |
| 68～69 | 写 `media_assets` + `revision_media` | `sort_order` 用当前条数，`alt_text` 存起来 |
| 70 | 写 `audit_logs`（`MEDIA_UPLOADED`） | 谁、什么时候、往哪个修订加了一张图 |

同一份文件里还有两条**用途相近但别混**的写路径：

| 方法 | 行 | 产物 | 生命周期 |
|---|---:|---|---|
| `uploadCommunity` | 79 | `key = community/{assetId}.{ext}`，只写 `media_assets` | 社群二维码，**不挂修订**，因此孤儿清理会跳过 `community/%` |
| `uploadAction` | 107 | `key = content-action/{contentItemId}/{assetId}.{ext}` + `content_action_media_reservations`（24 小时有效） | 行动入口图片，先「暂存」，被 `ContentActionService` 第 103、111 行 `requireActionMedia` / `consumeActionMedia` 消费后才算挂上 |

存储实现：`S3MediaStorage.java` 第 29 行 `put`、第 34 行 `get`、第 47 行 `delete`；`get` 把 MinIO 的 404 翻译成 `404 MEDIA_NOT_FOUND`。
客户端配置：`MediaStorageConfiguration.java` 第 17 行建 `S3Client`，第 26 行要求 **production profile 下 endpoint 必须是 HTTPS**。

**读完自答**

1. 为什么 `put` 要在 `jdbc.update` 之前？把顺序反过来会发生什么？
2. 事务提交成功、但审计写库失败时（第 70 行抛异常），对象会被删掉吗？
3. `uploadCommunity` 的 key 前缀为什么要在孤儿清理的 SQL 里被显式排除？

## 第 4 站：三条读路径与访问控制（本链路的核心）

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java`（38 行）

四条查询各自解决一个问题，**没有任何一条复用另一条的可见性条件**：

| 行 | 方法 | 谁在用 | 可见性条件（原文要点） |
|---:|---|---|---|
| 18 | `forPublishedRevision(revisionId)` | `ContentService` 第 127 行生成公开详情里的 `media[]` | 按 `sort_order` 列出该修订的 `ACTIVE` 资产，`url` 直接给出 `/api/v1/public/media/{id}` |
| 22 | `loadPublic(assetId)` | `PublicMediaController` 第 23 行 | `media_assets.lifecycle_state='ACTIVE'` **且** 满足四个 EXISTS 之一：正文挂在 `published_revision_id` 且 `visibility='PUBLIC' AND record_state='ACTIVE'`；或行动入口 `enabled AND access_level='PUBLIC'` 且内容公开且 `operational_status='OPEN'`；或社群条目 `active AND access_level='PUBLIC'`；或项目加入条目 `pje.active AND pje.access_level='PUBLIC'`（最后一段是 HEAD 新增的） |
| 28 | `loadManaged(assetId, accountId, administrator)` | `MediaController` 第 32 行 | 正文或行动入口挂在**当前修订**上、内容 `record_state='ACTIVE'`，并且「是管理员 或 合作方档案属于当前账号且 `active`」；或者是**未过期**的行动入口暂存（`expires_at > now`），同样限管理员或本人 |
| 34 | `loadCommunity(assetId)` | `PublicMediaController` 第 33 行 | 社群条目 `active`（不限 access_level）；或项目加入条目 `active`；或行动入口 `access_level='COMMUNITY_GATED'` 且内容已发布公开且 `OPEN` |

调用方还各加了一层：

| 文件 | 行 | 作用 |
|---|---:|---|
| `PublicMediaController.java` | 22、30 | 公开读不校验身份；社群读先 `community.requireGrant(grant)` 再查库 |
| `NoStoreResponseFilter.java` | 17 | 对 `/api/v1/media/`、含 `/community` 的路径等强制 `no-store`。**注意它不覆盖 `/api/v1/public/media/`**，那条路径的 `no-store` 完全靠 `PublicMediaController` 第 27 行自己设置 |

**读完自答**

1. 一条已经发布的内容被下架，为什么下一次请求（而不是等到缓存过期）就取不到图？
2. 把公开读和受控预览合并成一条 SQL，会失去哪一条安全属性？
3. `loadManaged` 里的暂存分支为什么要判 `expires_at`，而 `loadPublic` 不需要？

## 第 5 站：正文引用与内容生命周期的耦合

正文里的图片不是 HTML `<img>`，而是一个受控 Markdown 语法 `![说明](media:{26 位 ULID})`，解析后变成 JSON 块 `{type:"image", mediaId, alt}`。

| 文件 | 行 | 作用 |
|---|---:|---|
| `MarkdownDocumentService.java` | 23 | 图片行正则：`^!\[([^\]]{1,240})]\(media:([A-Za-z0-9]{26})\)$` |
| 同上 | 59 | 命中后写出 `type=image` 的块；第 62 行只要正文里出现 `![` 但不成整行合法图片，就 `400 MARKDOWN_INVALID` |
| 同上 | 106～108 | `mediaIds(document)` 抽出正文引用到的媒体 ID 集合 |
| `RichTextPolicy.java` | 47 | 二次把关：`mediaId` 必须是 26 位、`alt` 非空且 ≤240；块上**不允许**出现别的属性 |
| `ContentManagementService.java` | 393～398 | `syncReferencedMedia`：以正文为准**重写** `revision_media`，引用到不存在的资产 → `400 CONTENT_MEDIA_REFERENCE_INVALID` |
| 同上 | 380～391 | `copyReferencedMedia`：已发布内容改稿时，把旧修订的媒体**复制**到新候选修订 |
| 同上 | 417～422 | `requireCurrentMediaMatchesDocument`：送审前要求「正文引用的集合」与「已挂载的集合」**完全相等**，否则 `409 CONTENT_MEDIA_REFERENCE_INVALID` |

读这一站的目的是把三个日期对齐：**上传时**资产被创建并立刻挂到当前修订；**保存正文时**挂载关系被正文重新声明；**送审时**两者必须一致。所以「上传了但没写进正文就送审」会被卡住，这正是本轮 `03` 里 W12 实测到的 `409`。

**读完自答**

1. 合作方上传了 3 张图，只在正文里用了 1 张，最终 `revision_media` 里有几行？另外两张的下场是什么？
2. 为什么送审要校验「一一对应」，而不是「已挂载的一定要出现在正文里」就够了？
3. 正文里手写一个别人的 `media:{id}` 会发生什么？在哪一行被拦？

## 第 6 站：孤儿回收与删除队列

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java`（80 行）

| 行 | 方法 | 做什么 |
|---:|---|---|
| 29～41 | `queueUnattachedAssets` | 找出「`ACTIVE` 且没被任何 `revision_media` / `community_entries` / `content_action_entries` / 未过期暂存引用」的资产（`for update` 加锁），改状态为 `DELETION_PENDING`，并插入 `media_deletion_jobs` |
| 43～71 | `processDueDeletions` | 用 `locked_by`/`locked_until`（10 分钟租约）抢占最多 100 条待删任务，删对象 → 资产置 `DELETED` → 任务置 `SUCCEEDED`；抛异常则 `FAILED` + `attempts+1` + 5 分钟后重试 |
| 73～76 | `removeUnattachedAssets` | 两步合一，供 `RetentionService` 调用 |

触发方式：`RetentionService.java` 第 27 行 `@Scheduled(cron = "${voidhaven.retention.cron:0 30 1 * * *}", zone = "Asia/Shanghai")`——**每天 01:30**，没有 HTTP 入口，所以本地不会因为「等一下」就自动清理。

表结构来源：`V1__initial_schema.sql` 第 105～122 行（`media_assets`、`revision_media`）、`V4__add_revision_media_alt_text.sql`（`alt_text`）、`V7__harden_management_workflows.sql` 第 4～21 行（`lifecycle_state` 的 CHECK 约束与 `media_deletion_jobs`）、`V21__content_action_media_reservations.sql`（行动入口暂存表）。

**读完自答**

1. 为什么删除要先「入队」，而不是在 `queueUnattachedAssets` 里直接删对象？
2. 两个实例同时跑清理，靠什么保证一个对象只被删一次？
3. 对象已经删掉、但任务状态没来得及更新就宕机了，会发生什么？

## 附：四个常见误解

| 误解 | 实际情况 |
|---|---|
| 「桶设成 private 就安全了」 | 桶私有只挡住匿名直连；真正的边界是 API 每次都重新执行第 4 站那几条 SQL，因此下架能立刻生效 |
| 「`Content-Type` 是 `image/png` 就说明是图片」 | 校验靠 `ImageIO.getImageReaders` 真实解码（第 2 站第 26～30 行），扩展名和请求头都不可信 |
| 「上传成功就会出现在正文里」 | 上传只写数据库挂载关系；正文里还必须有对应的 `![alt](media:id)` 行，送审时两者必须一致（第 5 站） |
| 「图片是原样存进对象存储的」 | 存进去的是**重编码后**的像素（带 alpha 存 PNG，否则存 JPEG），元数据已被丢弃 |
