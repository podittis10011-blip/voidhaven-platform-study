# VH04 闭卷题

先根据观察记录回答，再展开参考。每站都要能说明哪一部分是替身。

## D1 幂等拒绝、失败释放与回放

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

在途测试 actions.get() → 0；异体异常 code → IDEMPOTENCY_KEY_REUSED；业务失败后 release 调用次数 → 1；敏感例异常 / 回放例对象 → SENSITIVE_RESULT_NOT_REPLAYABLE / 类型化 record。

边界：本测试从 service 开始，没有限流拦截器或参数解析。REQUIRES_NEW 代理、锁竞争不能在此证实。

</details>

## D2 六个聚合的版本冲突

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

内容冲突 code → CONTENT_VERSION_CONFLICT；社群 / 目录冲突 code → COMMUNITY_ENTRY_VERSION_CONFLICT / CATALOG_VERSION_CONFLICT；首页 / 项目冲突 code → HOMEPAGE_VERSION_CONFLICT / PROJECT_JOIN_ENTRY_VERSION_CONFLICT；回执 updated / code / 历史写入次数 → 0 / CASE_VERSION_CONFLICT / 0。

边界：直接调用服务没有 HTTP If-Match 解析；mock 的 for update 字符串也没有真实锁。真实锁位置需逐实现核对。

</details>

## D3 三个限流委托

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

公开写输入主体 → 203.0.113.55；公开写锁服务参数 → 主体摘要、2、1 小时；查询委托方法 / 额度 → reservePublicStatusQuery / 60；登录完成方法 → completeLoginAttempt。

边界：可证明主体转换和桶方法选择，不能证明真实计数或隔离事务；D5 再查真库。

</details>

## D4 删除任务租约与重试

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

成功测试 removed → 1；失败测试 removed → 0；失败写入任务状态 → FAILED，携带异常类名；第三例 claims.getAllValues() → 两个不同的 instance-a: 开头值。

边界：租约校验的真实效果在 D5；单测不能直接改“数据库 locked_by 行”，模拟失去租约应改 SUCCEEDED 更新返回值。

</details>

## D5 真实 MySQL 并发（环境门槛）

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

公开写 / 登录 并发允许-拒绝 → 2-6 / 5-3；查询超额 / 写桶影响 → PUBLIC_STATUS_QUERY_RATE_LIMITED / 不增加对应公开写记录；邀请码成功数 / use_count → 2 / 2；咨询锁 / 租约 / login_id 唯一竞争 → 第二执行者拒绝 / A 持有 / 1 成功 1 冲突。

边界：本轮不启动 Docker，不把该 IT 当作通过。这里的服务/事务管理器由测试构造，也不能自动证明幂等 Spring 代理的独立事务语义。

</details>
