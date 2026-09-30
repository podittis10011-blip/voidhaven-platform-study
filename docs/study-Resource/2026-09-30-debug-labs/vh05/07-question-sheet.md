# VH05 闭卷题

先根据观察记录回答，再展开参考。每站都要能说明哪一部分是替身。

## D1 真实图片解码与重编码

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

测试帧 validated.width() / height() → 2 / 3；validated.mediaType() → image/png；ImageIO.read(validated.bytes) 的结果 → 可解码图片；非图片异常 code → MEDIA_REJECTED。

边界：真实解码/重编码已被驱动；元数据剥离是代码机制，专门的带元数据样本覆盖仍待补，不能以测试方法名替代证据。

</details>

## D2 上传、二维码暂存与失败补偿

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

正文上传主顺序 → 校验→put→media_assets→revision_media→audit；二维码暂存表 → content_action_media_reservations；无同步失败 / 回滚完成后 delete 次数 → 各 1；提交完成后 delete 次数 → 0。

边界：手工回调证明补偿分支，不证明事务管理器真实触发或对象真删除。删除异常被吞掉；回滚后的无数据库记录对象不能笼统声称必被现有孤儿扫描找到。

</details>

## D3 公开、社群和后台媒体读取

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

公开 ca 分支 → PUBLIC + 已发布修订 + OPEN + PUBLIC 可见 + ACTIVE；社群 ca 分支 → COMMUNITY_GATED，仍要求 OPEN/已发布；后台路径 → current_revision 与暂存 reservations；项目公开 / 社群路径 → 公开含 pje.access_level；社群测试不含该过滤。

边界：只证明 SQL 形状与调用参数；SQL 执行、真实授权和索引未验证。

</details>

## D4 孤儿清理与租约

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

成功 removed → 1；失败 removed → 0；成功 SQL 状态顺序 → DELETION_PENDING → SUCCEEDED / DELETED；失败任务状态 → FAILED，可重试。

边界：扫描起点是数据库 media_assets；无法凭此保证所有仅存在于对象存储的孤儿都能被发现。

</details>

## D5 正文媒体引用、富文本与缓存

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

测试帧 document.path("version").asInt() → 2；markdown.mediaIds(document) → 一个合成 ULID；HTML / 远程图片 / 事件字段 → 拒绝；媒体响应缓存头 → no-store。

边界：mediaIds 只抽取 ID，不查询数据库，也不验证归属；归属校验在写入修订服务。旧稿所称 legacyToMarkdown 往返不是这两个测试内容。

</details>
