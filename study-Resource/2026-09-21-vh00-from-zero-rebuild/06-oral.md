# 06 口述题单与追问链

合上源码再练。第一层你必须能独立说完；第二层是面试官追问。参考答案在文末，先不要看。

## 第一层：三档口述

### A. 30 秒（产品 + 栈 + 核心模型）

要求覆盖：给谁用、后端栈、双修订、Cookie 会话、不用 Redis。不要报 Controller 名。

### B. 3 分钟（读 / 写 / 安全 各约 1 分钟）

- **读：** 公开目录只 join 已发布修订；游标内嵌 sort；`pageSize+1`；来源 fail-closed；未发布 404。
- **写：** 草稿→待审→批准→发布；发布把 published 指针拨到 current；再编辑新建候选修订；Partner/Admin 共用 Service。
- **安全：** Cookie Session 进 MySQL；CSRF 掩码 token；公开写豁免 CSRF 但有幂等+限流；`If-Match` + `@Version`；停用账号立刻 401。

### C. Maven / Spring 八股 60 秒

starter 是依赖约定；`ddl-auto=validate` + Flyway；`@Transactional` 代理失效；乐观锁 vs `FOR UPDATE`。

## 第二层：追问链（每题 30 秒 / 展开 3 分钟）

1. 游标分页比 `OFFSET` 好在哪？换排序为什么要报 `CATALOG_CURSOR_MISMATCH`？
2. 为什么未发布内容是 404 不是 403？
3. 来源过滤后本页不足 `pageSize` 但还有 `nextCursor`，正常吗？
4. `current_revision_id` 和 `published_revision_id` 什么时候相同、什么时候分开？
5. 已发布内容 `revise` 为什么要悲观锁？
6. 结束、下线、归档、删除各改哪一列？
7. 为什么不用 Redis？限流和会话放 MySQL 的代价是什么？
8. 公开投稿为什么豁免 CSRF？还靠什么挡住重复提交？
9. 把 CSRF cookie 裸值当 header 为什么失败？
10. Flyway 已经执行过的 V1 能不能改一列注释？
11. OpenAPI 和 Controller 冲突时听谁的？
12. 401 和 403 在本项目分别在什么时候出现？
13. `Idempotency-Key` 重复但 body 不同呢？相同 key 还在处理呢？
14. 为什么 `hibernate.ddl-auto` 不用 `update`？
15. 账号和内容所有权为什么要拆开？

## 课后下钻

| 还想练 | 去哪 |
|---|---|
| 读路径请求矩阵 | `study-Resource/2026-09-18-vh01-code-lab/` |
| 双修订与审核失败用例 | `.../2026-09-18-vh02-code-lab/` |
| CSRF / 会话固定 / 停用 | `.../2026-09-18-vh03-auth-lab/` |
| 幂等 / If-Match / 独立事务 | `.../2026-09-19-vh04-idempotency-concurrency/` |
| 媒体上传 | `.../2026-09-19-vh05-media-lab/` |
| 公开投稿 | `.../2026-09-19-vh06-public-input-lab/` |

---

## 参考答案（先自己说完再打开）

### A. 30 秒样例

VOIDHAVEN 是给本校学生用的竞赛、学习资源和团队招募发现平台，不做学生账号和私信。后端是 Spring Boot 4、Java 17、Maven、MySQL、Flyway。内容用双修订指针：编辑改 current，公开站只读 published。登录是 Cookie 会话加 CSRF，写操作带幂等键和 If-Match。单机部署，会话、限流、锁都在 MySQL，没有 Redis。

### B. 3 分钟样例要点

读路径：`PublicContentController` → `ContentService.listCatalog`。SQL join `published_revision_id`，limit 是 pageSize+1，游标是 `c1.` Base64 的 sort|key|id，换排序必须从第一页来。写完后再用 `ContentSourcePolicy` 滤一遍，非法来源详情 404。

写路径：`ContentManagementService` 一条链 create/submit/decide/publish。publish 把 visibility 设 PUBLIC 并把 published 指到 current。已发布后再改会新建 revision_number+1，公开页不变。合作方和管理员走同一个 Service，合作方多一道 owner 校验。

安全：Spring Security 配 Cookie CSRF，接口返回 XOR 掩码。公开 `/public/**` 豁免 CSRF，靠幂等表和限流行。Admin/Partner 必须带 CSRF。账号被停后 `AccountEligibilityFilter` 立刻把会话作废返回 401。版本冲突 409，限流 429。

### C. 八股 60 秒样例

Maven 用 Wrapper，日常 `mvnw verify`。Boot starter 只是依赖和自动配置，安全链我们自己写。表结构 Flyway 管，Hibernate 只 validate，不用 update。事务靠代理，同类自调用无效，所以幂等预约单独 REQUIRES_NEW。冲突少的用 `@Version`，要串行插入修订号的用 `SELECT FOR UPDATE`。

### 追问短答

1. OFFSET 深页要跳过前面所有行，中间插入会漏/重。游标从上一页最后一条接着走。sort 编进游标，防止第一页按截止日期、第二页按更新时间错位，所以 mismatch 直接 400。
2. 403 等于承认资源存在。公开站把「没有」和「不该看」合成 404。
3. 正常。hasNext 在来源过滤前根据 n+1 判定。
4. 刚发布时相同；发布后改草稿时 current 前移、published 不动；再次 publish 会合。
5. 两个事务同时读到 revision_number=N 再各插 N+1，会撞唯一约束变成 500。先锁行，再让 If-Match 把冲突变成 409。
6. 结束 `publication_status`；下线 `visibility`；归档 `record_state=ARCHIVED`；删除 `DELETED`。后两者同时隐藏。
7. 单机单副本，少一个组件。代价是限流和会话跟数据库可用性绑在一起，水平扩展要做非混合切流。
8. 匿名没有登录 Cookie，CSRF 模型不适用。靠 Idempotency-Key、限流、最小回执。
9. 处理器要的是 XOR 掩码，cookie 里是原值，校验失败 403。应 GET `/auth/csrf`。
10. 不能。checksum 会炸。追加 V25。
11. OpenAPI。实现跟着契约改。
12. 没登录或会话/资格失效 401；已登录但角色不够（Partner 打 Admin）403。
13. 不同 body → `IDEMPOTENCY_KEY_REUSED`；在途 → `REQUEST_IN_PROGRESS`，用同一 key 等。
14. update 不可审计、不可回滚、环境会分叉。validate 让实体和迁移必须一致。
15. 登录账号是操作者，内容挂在 `partner_profile`。负责人离职可以过户，历史稿不会因为账号删除消失。
