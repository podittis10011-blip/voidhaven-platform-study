package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

/** Public catalog sort modes; some kinds reject or remap unsupported combinations. */

//枚举类：
public enum CatalogSort {
    
    //三种类型：默认排序（管理后台人工排序列表）、截至时间、更新时间
    DEFAULT,
    DEADLINE,
    UPDATED;

    public String wire() {
        return name().toLowerCase();
    }

    //排序逻辑：用户
    // 1. 用户传了 sort？ → 用用户的
    // 2. 没传？ → 读数据库里的默认排序（configuredSort）
    // 3. 还没有？ → 用 DEFAULT

    // 特殊：RESOURCE 没有 deadline
    //   - 用户 insist 要 deadline → 报错
    //   - 后台误配成 deadline → 悄悄改成 UPDATED

    public static CatalogSort parse(String raw) {
        //用户未选择排序方式（raw）及选择的排序方式非法
        if (raw == null || raw.isBlank()) return null;


        //switch语句，提取排序方式String字段raw，raw为default时抛出异常
        return switch (raw.trim().toLowerCase()) {
            case "default" -> DEFAULT;
            case "deadline" -> DEADLINE;
            case "updated" -> UPDATED;
            //Java基础知识科普：一种函数编程写法，lambda表达式
            //raw为default时抛出异常
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_INVALID", "目录排序方式无效。");
        };
    }

    public static CatalogSort resolve(ContentKind kind, String requested, CatalogSort configured) {
        CatalogSort explicit = parse(requested);
        CatalogSort resolved = explicit != null ? explicit : (configured == null ? DEFAULT : configured);
        if (resolved == DEADLINE && kind == ContentKind.RESOURCE) {
            if (explicit != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_UNSUPPORTED", "学习资源不支持按截止日期排序。");
            }
            return UPDATED;
        }
        return resolved;
    }
}
