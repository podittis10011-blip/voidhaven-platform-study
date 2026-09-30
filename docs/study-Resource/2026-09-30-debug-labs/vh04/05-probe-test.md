# VH04 探针安装与清理

本层复用现有测试，不新增探针。D5 需要 Docker；其环境门槛不能用 mock 探针替代。

## 探针的证明范围

PasswordEncoder 使用真实编码器但未启动容器；PublicCase 的 Supplier 真执行但 JDBC 仅记录；PublicContactReveal 真加解密但审计为 mock；拦截器手工调用不证明 MVC 顺序；媒体同步回调手工触发不代表真实事务。

不要在源码主干加入调试修改；探针原件留在 docs，测试目录只放副本。
