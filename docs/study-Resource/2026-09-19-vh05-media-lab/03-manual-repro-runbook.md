# VH-05 手动复现运行手册

## 0. 环境与账号

| 项目 | 值 |
|---|---|
| 栈 | `http://localhost:18080`，compose 项目 `voidhaven-repro-current` |
| 镜像 | `voidhaven-repro-current-api:f1cfc6e13731`、`web:e1ccb11c9e0e`；行为指纹与 `be219ac` 一致（镜像 config 时间戳 `2026-09-15T06:50Z`，早于该提交，故只作「be219ac 时代的树」理解） |
| 源码 | `D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`（工作树干净） |
| 账号 | `local-admin`（`ADMIN,SUPER_ADMIN`）、`local-partner`（`PARTNER`） |
| 口令 | 取 `.env` 里的 `VOIDHAVEN_BOOTSTRAP_ADMIN_PASSWORD` / `VOIDHAVEN_BOOTSTRAP_PARTNER_PASSWORD`，不要抄进任何公开材料 |
| 本轮合成数据 | 内容 `LEARN-20260919-VH05-MEDIA-01`，ID `BV6PMTTK5WVXDEYAPDV8TRBQM5`；媒体资产 `YY5AG8Z8HM00XQT5G4D2DDCB43`（已引用）与 `XJZ024TZ9NS3D32NM501R6ZN59`（已解绑） |

先用只读请求确认栈活着：

```powershell
$b='http://localhost:18080/api/v1'
curl.exe -s -i "$b/public/content-modules" | Select-Object -First 6
```

脚本骨架（PowerShell 7，必须用 `-SkipHttpErrorCheck` 才能读到 4xx 的响应体）：

```powershell
$envMap=@{}; Get-Content 'D:\Projects\voidhaven\voidhaven-platform\.env' |
  Where-Object {$_ -match '^[A-Z]'} | ForEach-Object { $kv=$_ -split '=',2; $envMap[$kv[0]]=$kv[1] }
$b='http://localhost:18080/api/v1'

function Login($u,$p){
  $s=New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $null=Invoke-WebRequest "$b/auth/session" -Method POST -WebSession $s -ContentType 'application/json' `
    -Body (@{loginId=$u;password=$p}|ConvertTo-Json) -SkipHttpErrorCheck
  $t=((Invoke-WebRequest "$b/auth/csrf" -WebSession $s -SkipHttpErrorCheck).Content|ConvertFrom-Json).data.token
  $s | Add-Member -NotePropertyName tok -NotePropertyValue $t -Force
  $s
}

$a=Login $envMap['VOIDHAVEN_BOOTSTRAP_ADMIN_LOGIN']   $envMap['VOIDHAVEN_BOOTSTRAP_ADMIN_PASSWORD']
$p=Login $envMap['VOIDHAVEN_BOOTSTRAP_PARTNER_LOGIN'] $envMap['VOIDHAVEN_BOOTSTRAP_PARTNER_PASSWORD']
$ah=@{'X-XSRF-TOKEN'=$a.tok}; $ph=@{'X-XSRF-TOKEN'=$p.tok}
```

确认镜像版本的只读办法（不依赖镜像时间戳）：把现场返回的内容模块定义与两个提交对照——

```powershell
$m=(Invoke-WebRequest "$b/public/content-modules" -SkipHttpErrorCheck).Content|ConvertFrom-Json
$m.data.modules | ForEach-Object { "$($_.key): " + (($_.fields|ForEach-Object key) -join ',') }
# 现场实测：competition: officialRulesUrl,deadlineDate ／ resource: resourceType,audience
# 这与 be219ac 一致；HEAD 会多出 competitionLevel、audience、participationForm、startDate、stage、relatedCompetitionIds
```

两个坑先说在前面：

- **`If-Match` 必须带引号**。.NET 会校验头格式，写 `-Headers @{'If-Match'='1'}` 会直接报 `The format of value '1' is invalid`；要写 `'If-Match'='"1"'`。服务端 `AdminController` 第 369 行会 `replace("\"","")`，所以带引号能正常解析。
- **上传必须带 `X-XSRF-TOKEN`**。缺令牌是 `403` + Spring Boot 默认错误体 `{"timestamp","status":403,"error":"Forbidden","path":...}`，不是项目的 `ApiError` 形状，很容易误判成权限问题。

## 1. 读路径矩阵（已实测，可逐条重放）

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| R1 | `GET /public/media/DOESNOTEXIST0000000000000`（匿名） | `404` + `{"code":"MEDIA_NOT_FOUND",...}`，`requestId=KETD5SEVSGZAWJNAXPE57663HW` | 公开读不认识该资产时统一 404，不区分「不存在」与「不可见」 |
| R2 | `GET /media/{id}/preview`（匿名） | `401`，**空体** | `/api/v1/media/**` 先被 URL 规则拦住，进不了 Controller |
| R3 | `GET /media/YY5AG…/preview`（管理员会话） | `200`，`Content-Type: image/jpeg`，`Cache-Control: no-store`，72432 字节 | 受控预览可用，且禁用缓存 |
| R4 | `GET /media/YY5AG…/preview`（合作方会话，资产属管理员内容） | `404 MEDIA_NOT_FOUND`，`requestId=1HK4Z04X55372W7N187D01VAKC` | 越权读取返回 404，不泄露资产是否存在 |
| R5 | `GET /public/media/YY5AG…`（内容还是草稿时） | `404 MEDIA_NOT_FOUND`，`requestId=ZW9Q1SB1C8X2D0CXQ5JC40C2G1` | 未发布 → 公开路径不可读，即使资产已存在 |
| R6 | 发布后 `GET /public/media/YY5AG…`（匿名） | `200`，`image/jpeg`，`Cache-Control: no-store`，72432 字节 | 公开路径只在「已发布 + 公开 + ACTIVE」时放行 |
| R7 | `GET /public/competitions/BV6PMT…`（发布后） | `200`，`media[0] = {id, url:/api/v1/public/media/{id}, altText, mediaType:image/jpeg, 1280×971}` | 公开投影只给 `public/media` 地址，不给受控预览地址 |
| R8 | 下架后再 `GET /public/media/YY5AG…` | `404`，`requestId=ASHC3Z6CYE4AWMBFRAMY76K78K` | **下架立刻生效**，不依赖缓存过期 |
| R9 | 下架后 `GET /media/YY5AG…/preview`（管理员） | `200` | 属主/管理员的后台可见性不受公开可见性影响 |
| R10 | 重新上架后 `GET /public/media/YY5AG…` | `200` | 可见性随内容状态实时反转，没有单独的「媒体发布开关」 |

**R8 为什么能立刻生效**（面试会被追问）：`PublicMediaController` 第 27 行显式 `CacheControl.noStore()`，而且 `NoStoreResponseFilter` 的路径清单里**没有** `/api/v1/public/media/`——这条路径的 `no-store` 完全靠 Controller 自己写，属于「不要顺手删掉的那一行」。

## 2. 上传矩阵（已实测）

前置：管理员会话，`contentItemId=BV6PMTTK5WVXDEYAPDV8TRBQM5`（`DRAFT`），`file=docs/evidence/admin-remediation-public-smoke.png`（1280×971，69835 字节）。

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| W1 | 正常上传（`altText=合成图片说明`） | `200` + `{id, url:/api/v1/media/{id}/preview, mediaType:image/jpeg, width:1280, height:971}` | PNG 输入被**重编码成 JPEG**（无 alpha），返回的是服务端认定的类型与尺寸 |
| W2 | 同一个 `Idempotency-Key` + 同样的表单再发一次 | `200`，`id` 与第一次**完全相同** | 重放返回原结果，不产生第二个资产 |
| W3 | 同一个 key，改 `altText` 再发 | `409` + `IDEMPOTENCY_KEY_REUSED`，`requestId=MYC4T7N485RZG238JN0W8B1GN1` | 键复用但请求体指纹不同，拒绝执行 |
| W4 | `altText` 传空格 | **`500` + `TEMPORARILY_UNAVAILABLE`**，`requestId=H3E4YZHV9RJ3S2X83DCQXB63SR` | 预期是 `400 MEDIA_ALT_TEXT_REQUIRED`，见第 4 节 |
| W5 | `altText` 传 241 个字符 | **`500`**，`requestId=M1XWVD6GDSX236FDG2H603903W` | 同上，`@Size` 校验失败没被映射成 400 |
| W6 | 完全不传 `altText` 参数 | **`500`**，`requestId=YAWB0KSJYRCD8XATV95BH97EWF` | 缺参也是 500 |
| W7 | 上传内容是文本、后缀名是 `.png` 的文件 | `400` + `MEDIA_REJECTED`（仅支持 JPEG、PNG 或 WebP 图片。），`requestId=EZJT71EJ0ZME38ES3P8VY717G6` | 内容判断生效 |
| W8 | 上传 5.5 MB 文件（过得了网关、过不了 API） | **`500` + `TEMPORARILY_UNAVAILABLE`**，`requestId=ZBZBRCKWBNHNXB4T57W8322DSP` | API 日志里是 `FileSizeLimitExceededException: The field file exceeds its maximum permitted size of 5242880 bytes` |
| W9 | 上传 6 MB 文件 | 客户端 `Error while copying content to a stream`（连接被断开，拿不到 JSON） | nginx 日志：`client intended to send too large body: 6291975 bytes` |
| W10 | 往已发布内容（`9E4TVH7FN6NR988EZ66YJ48NXN`）上传 | `409` + `CONTENT_MEDIA_NOT_EDITABLE`，`requestId=FT7AAMTJKEMQAAVHVYPQ38WFNB` | 只有草稿/退回修改状态能加正文图片 |
| W11 | 用合作方会话往管理员的内容上传 | `404` + `CONTENT_NOT_FOUND`，`requestId=CDXHTVR122DA0G7AFKX0B4G87W` | 跨租户按「不存在」处理 |
| W12 | 上传后**不改正文**直接送审 | `409` + `CONTENT_MEDIA_REFERENCE_INVALID`，`requestId=AH73G61EB16P4KBFTVC458GGEF` | 正文引用与挂载必须一一对应 |

**两条限制的分工**（W8/W9 的意义）：

```text
文件 ≤ 5 MB         → 业务校验生效，能拿到结构化错误
5 MB < 文件 ≤ 6 MB  → nginx 放行，Spring multipart 拒绝 → 现在表现为 500
文件 > 6 MB         → nginx 直接拒绝（413），客户端只看到连接中断
```

## 3. 完整成功链路（已实测）

```text
1) POST /api/v1/admin/content-items                  创建草稿（需 Idempotency-Key + X-XSRF-TOKEN）
2) POST /api/v1/media/uploads                        上传（W1）
3) PATCH /api/v1/admin/content-items/{id}            正文写入 ![说明](media:{id})（需 If-Match: "<version>"）
4) POST /api/v1/admin/content-items/{id}/submit-review
5) POST /api/v1/admin/reviews/{id}/decision          body {"decision":"APPROVE"}，注意路径里的是内容 id
6) POST /api/v1/admin/content-items/{id}/publish
7) GET  /api/v1/public/competitions/{id}             公开详情里出现 media[] 与 public/media 地址
8) GET  /api/v1/public/media/{assetId}               200 image/jpeg
9) POST /api/v1/admin/content-items/{id}/hide        下架
10) GET /api/v1/public/media/{assetId}               立刻 404
```

实测中三个值得单独记住的事实：

| 事实 | 证据 |
|---|---|
| 第 3 步会把**未被正文引用的**挂载关系删掉 | PATCH 前 `revision_media` 2 行，PATCH 后只剩被引用的 1 行；`media_assets` 里另一个资产仍是 `ACTIVE`（对应 E-05） |
| 第 5 步 `decision` 的 `If-Match` 必须取**最新**版本号 | 送审那次响应里给的是 `revision=2`，而随后 GET 到的是 `version=3`；跟着 2 去发会 `409 CONTENT_VERSION_CONFLICT`（机制推断：响应体取自 flush 之前的内存值，未做源码级验证） |
| 送审接口路径里的 `{id}` 是内容 id，不是 review id | `AdminController` 第 166 行把 `id` 直接传给 `content.decide(id, ...)`，`decide` 内部第 177 行用 `find(id)` 查内容 |

<details>
<summary>可直接运行的完整脚本（管理员一条龙）</summary>

```powershell
# 依赖第 0 节的 $b / $a / $ah / $envMap
$cid=((Invoke-WebRequest "$b/admin/content-items/BV6PMTTK5WVXDEYAPDV8TRBQM5" -WebSession $a -SkipHttpErrorCheck).Content|ConvertFrom-Json).data
$png=Get-Item 'D:\Projects\voidhaven\voidhaven-platform\docs\evidence\admin-remediation-public-smoke.png'
$asset='YY5AG8Z8HM00XQT5G4D2DDCB43'

# 3) 正文写入引用
$src="VH-05 合成正文。`n`n![幂等键对照图](media:$asset)`n`n末尾段落。"
$draft=@{kind='COMPETITION';title='LEARN-20260919-VH05-MEDIA-01';summary='本地合成学习数据，用于 VH-05 媒体链路复现。';
  body=@{version=1;blocks=@(@{type='paragraph';text='VH-05 合成正文。'})};markdownSource=$src;
  languageCode='zh-CN';sourceName='本地合成来源';sourceUrl='https://example.com/vh05';
  rightsBasis='本地合成学习数据';authorDisplayName='本地合成账号';reprintPermission='NOT_ALLOWED';
  adaptationPermission='ATTRIBUTION_ALLOWED';details=@{officialRulesUrl='https://example.com/rules';deadlineDate='2030-10-15'}}|ConvertTo-Json -Depth 12

$r=Invoke-WebRequest "$b/admin/content-items/$($cid.id)" -Method PATCH -WebSession $a `
  -Headers ($ah+@{'Idempotency-Key'=[guid]::NewGuid().ToString();'If-Match'="`"$($cid.revision)`""}) `
  -ContentType 'application/json' -Body $draft -SkipHttpErrorCheck
"PATCH => $($r.StatusCode)"

# 4) 送审
$r=Invoke-WebRequest "$b/admin/content-items/$($cid.id)/submit-review" -Method POST -WebSession $a `
  -Headers ($ah+@{'Idempotency-Key'=[guid]::NewGuid().ToString()}) -SkipHttpErrorCheck
"送审 => $($r.StatusCode)"

# 5) 批准（先用 GET 取一次最新 version，别用送审响应里的数字）
$ver=((Invoke-WebRequest "$b/admin/content-items/$($cid.id)" -WebSession $a -SkipHttpErrorCheck).Content|ConvertFrom-Json).data.revision
$r=Invoke-WebRequest "$b/admin/reviews/$($cid.id)/decision" -Method POST -WebSession $a `
  -Headers ($ah+@{'Idempotency-Key'=[guid]::NewGuid().ToString();'If-Match'="`"$ver`""}) `
  -ContentType 'application/json' -Body '{"decision":"APPROVE"}' -SkipHttpErrorCheck
"批准 => $($r.StatusCode)"

# 6) 发布 → 7~8) 公开读取
$r=Invoke-WebRequest "$b/admin/content-items/$($cid.id)/publish" -Method POST -WebSession $a `
  -Headers ($ah+@{'Idempotency-Key'=[guid]::NewGuid().ToString()}) -SkipHttpErrorCheck
"发布 => $($r.StatusCode)"
"公开媒体 => " + (Invoke-WebRequest "$b/public/media/$asset" -SkipHttpErrorCheck).StatusCode
```

</details>

## 4. 需要你自己动手的六个实验

### E-01 证明「桶是私有的」，而不只是「我以为它是私有的」（只读，最推荐先做）

```powershell
$docker="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe"
$ak=$envMap['VOIDHAVEN_MEDIA_ACCESS_KEY']; $sk=$envMap['VOIDHAVEN_MEDIA_SECRET_KEY']; $bk=$envMap['VOIDHAVEN_MEDIA_BUCKET']
& $docker run --rm --entrypoint sh --network voidhaven-repro-current_default -e "AK=$ak" -e "SK=$sk" -e "BK=$bk" `
  pgsty/mc:RELEASE.2026-09-16T00-00-00Z -c 'mc alias set l http://minio:9000 "$AK" "$SK"; mc anonymous get "l/$BK"; mc ls --recursive "l/$BK"'
```

本轮实测：`Access permission for 'l/voidhaven-media' is 'private'`，对象列表里能看到 `content/6QTBF902F9WCGPHASGMRT324GE/YY5AG8Z8HM00XQT5G4D2DDCB43.jpg`。
补充观察：`docker ps` 里 minio 容器没有映射任何宿主端口，所以「浏览器直连对象存储」这条路在本地方案里根本不存在。

**要你回答**：如果只把桶设成 private，但有人把 9000 端口映射到宿主并允许匿名读，会怎样？这条防线到底靠几件事共同成立？

### E-02 越权读取的三种写法（只读）

分别用未登录会话、合作方会话、管理员会话请求**同一个** `GET /media/{asset}/preview`，记录三组状态码与响应体形状。

**要你回答**：为什么 R4 是 404 而不是 403？换成 403 会泄露什么？

### E-03 下架后立刻失读（只读，最推荐）

对已发布内容依次执行 `hide` → `GET /public/media/{asset}` → `GET /media/{asset}/preview` → `show` → `GET /public/media/{asset}`，同时用浏览器开发者工具看 `Cache-Control`。

**要你回答**：如果 `PublicMediaController` 第 27 行被删掉、其他代码不变，会出现什么安全后果？为什么 `NoStoreResponseFilter` 帮不上这条路径？

### E-04 幂等键的三种用法（会写数据）

用同一个 `Idempotency-Key` 发三次：完全相同 → 改 `altText` → 改文件内容（换一张图）。记录每次的 `id` 与状态码。

**要你回答**：为什么指纹里要放文件字节的 SHA-256？只看文件名和 `altText` 会漏掉哪种真实场景？

### E-05 孤儿资产进入删除队列（会写数据，触发方式由你决定）

`XJZ024TZ9NS3D32NM501R6ZN59` 现在已经没有任何引用（`revision_media` 里查不到它），但它还是 `ACTIVE`。三种观察方式：

1. 用只读 SQL 证明它**符合**入队条件（本轮实测：`rev_links=0`、`action_links=0`、`live_reservations=0`、`lifecycle_state=ACTIVE`）；
2. 等下一次定时任务（每天 01:30，`RetentionService` 第 27 行）后查 `media_assets.lifecycle_state` 与 `media_deletion_jobs`；
3. 参照 `MediaOrphanCleanupServiceTest` 的构造方式写一个本地实验（不改项目源码）。

```sql
-- 只读：谁会被入队
select ma.id, ma.lifecycle_state,
       (select count(*) from revision_media rm where rm.media_asset_id=ma.id) rev_links,
       (select count(*) from content_action_entries ca where ca.media_asset_id=ma.id) action_links,
       (select count(*) from content_action_media_reservations r
         where r.media_asset_id=ma.id and r.consumed_at is null and r.expires_at > current_timestamp(6)) live_reservations
  from media_assets ma;
```

**要你回答**：为什么必须先入队再删？`community/%` 前缀为什么被排除在外？

### E-06 更正三处「500」（需要改代码，建议先只写方案）

不动代码，先写清楚：`ApiExceptionHandler` 要新增哪几个 `@ExceptionHandler` 分支、分别映射成什么状态码与业务错误码；`HandlerMethodValidationException` 里怎么把违反的字段名填进 `fieldErrors`。

**要你回答**：这三处不修，前端要写多少种「看起来像故障、其实是用户输入问题」的分支？给出你打算补的测试名。

## 5. 记录模板

```yaml
case_id: VH-05-__
chain_id: VH-05
title:
evidence_type: RUNTIME_CURRENT_SOURCE | TEST_CURRENT | LAB_EXPERIMENT
source_head: 2295a41
runtime_image: voidhaven-repro-current-api:f1cfc6e13731   # 与源码版本存在差异，需一起记录
preconditions:
  account_role:
  business_state:
  synthetic_data_prefix: LEARN-20260919
operation:
  method:
  url:
  security_headers: "只记是否存在，不记录敏感值"
expected:
  http_status:
  error_code:
actual:
  http_status:
  error_code:
  request_id:
  response_summary:
observations:
  before_database:
  after_database:
  minio_object_key:
code_anchors:
  controller:
  service:
  persistence:
proof_boundary:
  proves:
  does_not_prove:
result: PASS | FAIL | BLOCKED
```

## 6. 本轮留下的合成数据与清理

| 对象 | 当前状态 | 清理方式与影响 |
|---|---|---|
| 内容 `LEARN-20260919-VH05-MEDIA-01`（`BV6PMTTK5WVXDEYAPDV8TRBQM5`） | `HIDDEN`，历史上发布过一次 | 可保持现状作为对照；若要删，用 `POST /api/v1/admin/content-items/{id}/delete`，会连带影响其修订与媒体挂载 |
| 资产 `YY5AG8Z8HM00XQT5G4D2DDCB43` | `ACTIVE`，被该修订引用 | 随内容处理；直接删对象会让已发布内容出现坏图 |
| 资产 `XJZ024TZ9NS3D32NM501R6ZN59` | `ACTIVE`，已无引用 | 会被下一次 retention 入队删除；想立刻清掉需自行触发 |
| `idempotency_records` 里的几条键 | 已记录 | 由 retention 任务按 180 天窗口清理 |

清理前请先用只读查询确认目标 ID，再执行写操作；不要用 SQL 直接删表，也不要在真实环境照抄这条链路。

## 7. 安全边界

- 这套凭据只用于本地合成环境。不要写进截图、博客、简历或任何公开材料，也不要用真实账号替换。
- 不要上传真实人物照片、联系方式、内部文档或带真实元数据的图片；EXIF 被丢弃不代表「上传是安全的」。
- 本手册里的所有写入操作都会在本地数据库留下记录，请在确认栈是学习用的 compose 项目后再执行。
