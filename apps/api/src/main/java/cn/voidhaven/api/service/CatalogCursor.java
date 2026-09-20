package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

//基于游标基准进行查询：
//传统页码分页offset在深页时越来越慢。
//游标分页，上一页最后一条的位置，下一页以此为基准进行查询操作

//游标排序参数列表中保存的三种关键字段，sort排序方式|key排序键值|id同排序键时的id
public record CatalogCursor(CatalogSort sort, String key, String id, boolean legacy) {
    private static final Pattern ULID = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final String PREFIX = "c1.";

    public static CatalogCursor of(CatalogSort sort, String key, String id) {
        return new CatalogCursor(sort, key == null ? "" : key, id, false);
    }

    public String encode() {
        String payload = sort.name() + "|" + (key == null ? "" : key) + "|" + id;
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    //业务逻辑代码解析：
    // cursor 为空        → null，第一页
    // c1.xxx 合法        → 新格式游标
    // 26位 ULID + DEFAULT → 旧客户端兼容（legacy=true）
    // 其他               → 400 无效游标
    public static CatalogCursor decode(String raw, CatalogSort expected) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.startsWith(PREFIX)) {
            try {
                //Base64解码 —> 拆成 sort/key/id
                String payload = new String(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), StandardCharsets.UTF_8);
                String[] parts = payload.split("\\|", 3);
                if (parts.length != 3 || parts[2].isBlank()) throw new IllegalArgumentException();
                CatalogSort sort = CatalogSort.valueOf(parts[0]);
                //sort必须和本次expect一致，否则抛出异常400 CATALOG_CURSOR_MISMATCH
                if (sort != expected) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_MISMATCH", "分页游标与当前排序不一致，请从第一页重新浏览。");
                }
                return new CatalogCursor(sort, parts[1], parts[2], false);
            } catch (ApiException error) {
                throw error;
            } catch (Exception error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_INVALID", "分页游标无效。");
            }
        }
        if (ULID.matcher(value).matches() && expected == CatalogSort.DEFAULT) {
            return new CatalogCursor(CatalogSort.DEFAULT, "", value, true);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_INVALID", "分页游标无效。");
    }
}
