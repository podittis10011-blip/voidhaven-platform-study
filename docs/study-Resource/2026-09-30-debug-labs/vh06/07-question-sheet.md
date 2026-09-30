# VH06 闭卷题

先根据观察记录回答，再展开参考。每站都要能说明哪一部分是替身。

## D1 公开写拦截器与主体

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

写入例 request.getMethod() / getRequestURI() → POST / /api/v1/public/submissions；写入桶调用 / 查询桶调用 → 1 / 0；GET 时任一限流桶调用 → 0；mock 超额异常 code → PUBLIC_WRITE_RATE_LIMITED。

边界：手工 preHandle 仅证明分派及异常传播；MVC 顺序、真实额度消耗、Retry-After、最终 HTTP 429 需真实栈或专门 MVC 测试。

</details>

## D2 查询桶与公开写桶分离

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

路径 → /api/v1/public/submissions/status-query；探针 write 调用次数 → 0；锁服务方法 / 额度 → reservePublicStatusQuery / 60；异常 code → PUBLIC_STATUS_QUERY_RATE_LIMITED。

边界：委托隔离不等于真实数据库计数隔离；真库见 VH04 D5。

</details>

## D3 回执生成、哈希存储与查询

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

created.id().length() / queryCode().length() → 26 / 43（32 随机字节 Base64URL 无填充）；jdbc.writes.get(0)[6] → SHA-256，64 字符；status.status() / 第二次查询 → RECEIVED / 仍成功；三种失败 code → 均 CASE_QUERY_INVALID。

边界：16～256 是请求校验允许长度范围，不是本实现生成长度。探针执行回调但不验证真实幂等预约、SQL 或 Spring 事务代理。

</details>

## D4 公开投影与 grant 委托

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

无 grant 项目 SQL → 包含 access_level = PUBLIC；无 grant requireGrant 调用次数 → 0；有 grant SQL / 委托 → 无 PUBLIC 限制 / requireGrant(shared-grant)；加密 AAD 前缀 → PROJECT_JOIN_ENTRY:。

边界：SQL 形状不证明真实数据库已过滤；grant 签发、校验、撤销全链仍需真实栈。

</details>

## D5 真实解密与查看审计

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

result.contact() → wechat:synthetic-only；审计 action → PRIVATE_CONTACT_REVEALED；审计 after JSON → {"revealed":true}，无联系值；错误 AAD / 空记录 → 解密失败 / 联系记录不存在；均无审计调用。

边界：真实密码学 + 模拟仓储/审计；verify 证明发起审计调用，不证明数据库多了一行，也不证明 HTTP 权限。

</details>

## D6 人工投递与行动入口候选

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

人工投递审计 action → MANUAL_DELIVERY_RECORDED；未知主体错误码 → MANUAL_DELIVERY_SUBJECT_INVALID；行动入口写入修订 → 新的 candidate，published 保持原对象；受控文本存储 / 审计 → 密文 / 不含明文联系值。

边界：人工投递记录并不等于系统发送消息；现有测试只验证记录请求与业务约束。

</details>
