package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.*;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

//DTO层解释：
//数据传输对象，不同层不同系统之间传输数据（相当于专门用于传数据的Java对象）
//

//record关键字，Java提供的一种特殊类型，适合“只负责出数据保存的数据类”，
//加上后避免重复写大量样板代码，编译器会自动提供与record载体相关的内容.
public record PublicContentDto(String id, ContentKind kind, String moduleKey, int schemaVersion, String title, String summary, JsonNode body, String languageCode,
                               String languageLabel, String originalTitle, String sourceName, String sourceUrl, String rightsBasis,
                               String authorDisplayName, String reprintPermission, String adaptationPermission, JsonNode details, long revision,
                               List<PublicMediaDto> media, String operationalStatus,
                               List<cn.voidhaven.api.service.ContentActionService.PublicAction> actions,
                               cn.voidhaven.api.service.ContentVerificationService.PublicVerification verification,
                               Instant updatedAt) { }
// 参数列表解析：
// | 字段                     | 类型                   | 小白理解       |
// | ---------------------- | -------------------- | ---------- |
// | `id`                   | String               | 内容 ID      |
// | `kind`                 | ContentKind          | 内容类型       |
// | `moduleKey`            | String               | 属于哪个内容模块   |
// | `schemaVersion`        | int                  | 数据结构版本     |
// | `title`                | String               | 标题         |
// | `summary`              | String               | 摘要         |
// | `body`                 | JsonNode             | 正文/主体 JSON |
// | `languageCode`         | String               | 语言代码       |
// | `languageLabel`        | String               | 语言显示名称     |
// | `originalTitle`        | String               | 原始标题       |
// | `sourceName`           | String               | 来源名称       |
// | `sourceUrl`            | String               | 来源网址       |
// | `rightsBasis`          | String               | 权利/版权依据    |
// | `authorDisplayName`    | String               | 作者显示名称     |
// | `reprintPermission`    | String               | 转载权限       |
// | `adaptationPermission` | String               | 改编权限       |
// | `details`              | JsonNode             | 扩展详情       |
// | `revision`             | long                 | 内容修订版本     |
// | `media`                | List<PublicMediaDto> | 图片等媒体数据    |
// | `operationalStatus`    | String               | 运营状态       |
// | `actions`              | List<PublicAction>   | 可执行操作      |
// | `verification`         | PublicVerification   | 验证/认证信息    |
// | `updatedAt`            | Instant              | 最后更新时间     |
