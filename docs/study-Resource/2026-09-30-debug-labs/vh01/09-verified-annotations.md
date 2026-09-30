# 09 待验证注释清单

## 使用规则

1. **执行方一行都没有写进源码。** 本清单只是候选文本，等你亲手调试验证过之后，再决定要不要落盘。
2. 落盘格式统一：`// 学习·L3-已验证：<结论>`，一行，紧跟被注释的语句上方或行尾，不删原注释、不改代码行为。
3. 每条都要先回答"验证判据"栏里的问题，答得出来才写；答不出来就说明还没验证，别写。
4. 落盘后 `git diff` 里应该只有 `//` 开头的行增减。

## 候选清单

| # | 文件 | 锚点文本 | 建议注释 | 验证判据（先自己回答） |
|---|---|---|---|---|
| N1 | `service/ContentService.java` | `List<CatalogQuery.RankedId> page = hasNext ? ranked.subList(0, pageSize) : ranked;` | `// 学习·L3-已验证：多查的 1 条只用于判断 hasNext，不进响应。` | 把 `ranked` 造成 3 条、`pageSize=2`，`page.size()` 是几？第 3 条去哪了？ |
| N2 | `service/ContentService.java` | `nextCursor = CatalogCursor.of(applied, last.key(), last.id()).encode();` | `// 学习·L3-已验证：游标取原始页末条，不是过滤后的可见列表末条。` | 构造一条来源非法、位置在原始页中间的内容，`visible.size()` 与游标指向分别是什么？ |
| N3 | `service/ContentService.java` | `for (String id : ids) {` （`inOrder` 内第二个循环） | `// 学习·L3-已验证：IN 查询不保证顺序，用 LinkedHashMap 按游标顺序还原。` | 让替身按 id 倒序返回，`visible` 的顺序是什么？删掉这个循环会变成什么？ |
| N4 | `service/ContentService.java` | `return modules.requireKnown(moduleKey(item)).allowsOriginalSource() && "ORIGINAL".equals(...)` | `// 学习·L3-已验证：无外链的原创路径要模块允许；legacy "resource" 模块不允许。` | 同一份 `{"sourceMode":"ORIGINAL"}`，`moduleKey=null` 与 `moduleKey="resource.article"` 的结果各是什么？配置写在哪？ |
| N5 | `service/CatalogQuery.java` | `sql.append(" limit ?");` | `// 学习·L3-已验证：limit 是参数绑定，值为 pageSize+1。` | `pageSize` 传 0 / 50 / 999 时，limit 参数分别是几？ |
| N6 | `service/CatalogSort.java` | `if (explicit != null) {` | `// 学习·L3-已验证：同一降级分支里，用户显式要求报 400，仅后台误配才静默降级。` | 资源 + 无参 + 后台 `DEADLINE` 与 资源 + 显式 `deadline` 的结果分别是什么？ |
| N7 | `service/ContentService.java` | `includeMedia ? media.forPublishedRevision(r.getId()) : List.of()` | `// 学习·L3-已验证：详情传 true 会查媒体，列表/关联传 false 不查；实测列表后媒体调用次数不增加。` | 详情调用一次后再调列表，`media` 替身被调用几次？ |
| N8 | `web/ApiExceptionHandler.java` | `@ExceptionHandler(Exception.class)` | `// 学习·L3-已验证：通用兜底会把缺必填参数（客户端问题）也变成 500 TEMPORARILY_UNAVAILABLE 且 retryable=true。` | `GET /api/v1/public/content` 缺 `module` 时返回什么状态码与 code？在哪个断点停住？ |

## 明确不建议加注释的位置

| 位置 | 原因 |
|---|---|
| `Repository` 的 `@Query` 字符串上 | 注释会插进 JPQL，容易引入笔误；结论写进文档即可 |
| `ContentSourcePolicy.isUnsafeLiteralAddress` 的位运算里 | 逐行注释会长到盖过代码；D4 的观察表已经记录了每个分支的判定结果 |
| 任何你只"读过"但没"调过"的位置 | 未验证的理解一旦写进源码，会被后来的人当成事实 |
| `web/PublicContentController.java` 的参数注解上 | 注解行很短，塞注释会破坏可读性；D9 的观察表已经记录了默认值 20 的来源 |
