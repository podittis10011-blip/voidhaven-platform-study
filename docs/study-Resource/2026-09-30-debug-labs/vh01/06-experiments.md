# 06 八个预测验证实验

规则：**先在表格里写预测，再改代码，再跑，最后填实际值。** 预测错了比预测对了更值钱——写清楚你原本以为会发生什么。

全部实验只改探针文件（在 `apps/api/src/test/java/cn/voidhaven/api/service/` 里的那份副本），不碰 `apps/api/src/main`。

---

## E1 把页大小从 2 改成 3

**改哪里**：用例 1 里两处 `2`（`service.listCatalog(..., 2, ...)` 与 `catalogRows(...)` 无关）。
**先预测**：`pageSize`、`hasNext`、`page.size()`、args 末项、`visible.size()`、`nextCursor`。

| 项 | 预测 | 实际 |
|---|---|---|
| `pageSize` | | |
| args 末项 | | |
| `hasNext` | | |
| `page.size()` | | |
| `nextCursor` | | |

<details><summary>参考</summary>

`pageSize=3`、args 末项 `4`、`ranked.size()=3` 不再大于 `pageSize` → `hasNext=false`、`page.size()=3`、`nextCursor=null`。
**关键点**：`pageSize+1` 的 "+1" 是相对**当次请求**的页大小，不是固定值。
</details>

## E2 让原始页中间那条来源非法

**改哪里**：用例 1 把 `itemB` 的来源改成 `"http://localhost:8080/local-fixtures/source"`（或直接用用例 2 的写法，把非法项放到**第一条**）。
**先预测**：`visible` 的条数、`visible` 的内容、`nextCursor` 指向谁。

<details><summary>参考</summary>

非法项在中间 → `visible=[A]`、游标仍指向 `B`；把非法项放到第一条 → `visible=[B]`、游标仍指向 `B`（原始页末条），此时**可见列表的最后一条就是游标本身**，容易让人误以为"游标取可见末条"。
**关键点**：要证明一条规则，得构造能区分两种解释的数据。
</details>

## E3 把替身仓储的返回顺序反过来

**改哪里**：`repositoryReturning` 里把 `found.sort(Comparator.comparing(ContentItem::getId).reversed())` 换成不排序（或升序）。
**先预测**：`visible` 的顺序会不会变？`nextCursor` 会不会变？

<details><summary>参考</summary>

都不变。`inOrder` 用 `LinkedHashMap` 按传入 `ids` 的顺序重排，替身返回顺序只影响 `byId` 的插入顺序。
**反证**：注释掉 `inOrder` 的第二个循环（或改成直接 `return new ArrayList<>(byId.values())`），`visible` 就会变成 `B, A`，而 `nextCursor` 仍然指向 `B`——页内容与游标立刻自相矛盾。这就是为什么这个循环不能省。
</details>

## E4 恰好返回 pageSize 条

**改哪里**：用例 1 的 `catalogRows(...)` 去掉 `ranked(ITEM_C, "3")`。
**先预测**：`hasNext`、`nextCursor`、SQL 的 limit。

<details><summary>参考</summary>

`hasNext=false`、`nextCursor=null`、limit 仍然是 3（SQL 照发 `pageSize+1`，只是数据库只返回 2 条）。
**关键点**：`hasNext` 是"查回来的行数"与"页大小"比较，不是数据库里还有没有数据。所以最后一页永远要多查一次空。
</details>

## E5 把后台默认排序配成 DEADLINE，请求竞赛

**改哪里**：用例 7 里把 `ContentKind.RESOURCE` 换成 `ContentKind.COMPETITION`。
**先预测**：`sort` 的返回值、SQL 里出现哪条 `order by`。

<details><summary>参考</summary>

返回 `deadline`，SQL 出现 `order by case when coalesce(cd.deadline_date, rd.deadline_date) is null then 1 else 0 end asc, ...`，并且多 join 两张详情表。
**关键点**：降级规则只针对 `RESOURCE`（它没有截止日期概念），不是"所有后台误配都降级"。
</details>

## E6 用旧版 ULID 游标翻页

**改哪里**：用例 1 的 `cursor` 传 `"01JCONTENT0000000000000002"`（26 位 ULID），`sort` 传 `null`（走 `DEFAULT`）。
**先预测**：SQL 的游标谓词长什么样、解码出的 `legacy`、`key`。

<details><summary>参考</summary>

谓词退化成 `and c.id < ?`，`legacy=true`、`key=""`。
**关键点**：旧客户端只有"按 id 倒序"这一种语义，所以只在 `DEFAULT` 排序下兼容；换成 `UPDATED` 会落到 400 `CATALOG_CURSOR_INVALID`。
</details>

## E7 pageSize 传 999

**改哪里**：用例 5 已经覆盖。自己做一次：把 `999` 换成 `51`，再换成 `50`。
**先预测**：三次的 limit 分别是多少。

<details><summary>参考</summary>

`51 → 51`（50+1）、`50 → 51`（50+1）、`999 → 51`。`50` 与 `51` 的 limit 相同，但语义不同：一个是"页大小 50"，另一个是"页大小被夹到 50"。
**关键点**：`Math.min(size, 50)` 让 `50` 和 `999` 在 SQL 上不可区分——**边界值必须和边界外一起测**。
</details>

## E8 手工造一个"段数不对"的游标

**改哪里**：用例 6 里把 `deadlineCursor` 换成手工构造的坏游标：

```java
String broken = "c1." + java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("DEFAULT|x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
```

**先预测**：抛哪个错误码？会不会变成 MISMATCH？

<details><summary>参考</summary>

`CATALOG_CURSOR_INVALID`。因为 `parts.length != 3` 在第 41 行先抛 `IllegalArgumentException`，被 `catch (Exception)` 兜成 INVALID；MISMATCH 只在"段数合法且 sort 解析成功但值不同"时出现。
**关键点**：两个 400 的优先级由代码顺序决定——先格式、后语义。
</details>

---

## E9 列表到底会不会查媒体

**改哪里**：探针 A 用例 8 的最后一行 `verify(media, times(1)).forPublishedRevision(anyString());`，把 `times(1)` 改成 `times(2)`。
**先预测**：测试会通过还是失败？失败信息里 "wanted 2 times but was 1" 说明什么？

<details><summary>参考</summary>

会失败，实际调用次数仍是 1。因为列表走 `toPublic(item, false)`，`includeMedia ? media.forPublishedRevision(r.getId()) : List.of()` 走的是 `List.of()` 分支。
**关键点**：`verify` 的次数断言是**反证工具**——你可以用"故意写错的次数"来确认自己对分支的判断，比读代码可靠。
</details>

## E10 把业务异常换成未捕获异常

**改哪里**：探针 B 用例 3，把 `new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_INVALID", ...)` 换成 `new IllegalStateException("synthetic failure")`，其余不动。
**先预测**：状态码、`code`、`retryable` 三个值分别变成什么？会命中哪个 `@ExceptionHandler`？

<details><summary>参考</summary>

状态码 500、`code=TEMPORARILY_UNAVAILABLE`、`retryable=true`，命中 `ApiExceptionHandler:69` 的 `@ExceptionHandler(Exception.class)`。
**关键点**：`retryable` 不是"业务觉得能不能重试"，而是纯粹由状态码算出来的（429 或 5xx 才为 true）。参数缺失之所以也变成 500 且 `retryable=true`，根因就在这里。
</details>

## 收尾

做完 5 个以上实验后，回到 `08-evidence-ledger.md`，把每个实验的"目标 / 假设 / 运行方法 / 结论 / 清理方式 / 是否值得迁移"补全。
