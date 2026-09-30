package cn.voidhaven.api.web;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.service.ContentActionService;
import cn.voidhaven.api.service.ContentModuleRegistry;
import cn.voidhaven.api.service.ContentService;
import cn.voidhaven.api.service.HomepageProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学习用探针 B（不属于产品测试资产）：给"HTTP 入口 → 参数绑定 → 响应信封 → 错误映射"提供驱动器。
 *
 * 用途：L3 读路径的 D9 站。ContentService 被替身替换，只观察 Controller 这一层：
 * 参数绑定与默认值、ApiResponse / ApiError 信封、requestId 的来源。
 *
 * 边界：这是 standalone MockMvc，不加载 Spring 容器、不经过 Security 过滤器链，
 * 也证明不了 CSRF、会话、授权与真实数据库行为。
 *
 * 五个调试入口：
 *   1. resources_endpoint_binds_parameters_and_defaults_page_size_to_twenty
 *   2. request_id_comes_from_the_filter_not_from_the_controller
 *   3. api_exception_is_mapped_to_the_error_envelope
 *   4. unexpected_failure_falls_through_to_the_generic_handler
 *   5. missing_required_parameter_reveals_the_generic_fallback
 */
class PublicContentControllerDebugProbeTest {
    private static final String ITEM_A = "01JCONTENT0000000000000001";

    @Test
    void resources_endpoint_binds_parameters_and_defaults_page_size_to_twenty() throws Exception {
        ContentService content = mock(ContentService.class);
        when(content.listCatalog(any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ContentService.PublicPage(List.of(card("合成卡片A")), "c1.next", "default"));

        mvc(content).perform(get("/api/v1/public/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(ITEM_A))
                .andExpect(jsonPath("$.data.items[0].title").value("合成卡片A"))
                .andExpect(jsonPath("$.data.sort").value("default"))
                .andExpect(jsonPath("$.data.nextCursor").value("c1.next"));

        // @RequestParam(defaultValue = "20") 的默认值在这里被观察到
        verify(content).listCatalog(eq(ContentKind.RESOURCE), isNull(), eq(20), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void request_id_comes_from_the_filter_not_from_the_controller() throws Exception {
        ContentService content = mock(ContentService.class);
        when(content.listCatalog(any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ContentService.PublicPage(List.of(), null, "default"));

        // 不带 RequestIdFilter：requestId 只能是 null
        MvcResult withoutFilter = mvc(content).perform(get("/api/v1/public/competitions"))
                .andExpect(status().isOk()).andReturn();
        assertThat(withoutFilter.getResponse().getContentAsString()).contains("\"requestId\":null");

        // 带上 RequestIdFilter：响应头出现 X-Request-Id，信封里的 requestId 变成 26 位 ULID
        MvcResult withFilter = mvcWithRequestId(content).perform(get("/api/v1/public/competitions"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String requestId = JsonMapper.builder().build()
                .readTree(withFilter.getResponse().getContentAsString()).path("requestId").asText();
        assertThat(requestId).matches("[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    void api_exception_is_mapped_to_the_error_envelope() throws Exception {
        ContentService content = mock(ContentService.class);
        when(content.listCatalog(any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_INVALID", "目录排序方式无效。"));

        mvc(content).perform(get("/api/v1/public/resources").param("sort", "popularity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_SORT_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void unexpected_failure_falls_through_to_the_generic_handler() throws Exception {
        ContentService content = mock(ContentService.class);
        when(content.listCatalog(any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("synthetic failure"));

        mvc(content).perform(get("/api/v1/public/resources"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    /**
     * 观察用例：/content 的 module 是必填参数，缺失时走谁？
     *
     * 实测结论（2026-09-30）：走 {@code @ExceptionHandler(Exception.class)} 通用兜底，
     * 返回 500 TEMPORARILY_UNAVAILABLE，而不是 400。
     * 原因：这是 standalone MockMvc + 只有本 Controller 与 ApiExceptionHandler 的最小环境，
     * 缺参抛出的是 Spring MVC 的 MissingServletRequestParameterException，没有专门的
     * @ExceptionHandler，于是被通用兜底接住。
     * 该行为未经真实安全链验证，只作为观察记录写进 D9 站，不作为"产品缺陷"结论。
     */
    @Test
    void missing_required_parameter_reveals_the_generic_fallback() throws Exception {
        ContentService content = mock(ContentService.class);

        MvcResult result = mvc(content).perform(get("/api/v1/public/content")).andReturn();
        String body = result.getResponse().getContentAsString();
        System.out.println("[PROBE-D9] status=" + result.getResponse().getStatus() + " body=" + body);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(body).contains("TEMPORARILY_UNAVAILABLE");
    }

    // ---------- 夹具 ----------

    private static MockMvc mvc(ContentService content) {
        return MockMvcBuilders.standaloneSetup(controller(content))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static MockMvc mvcWithRequestId(ContentService content) {
        return MockMvcBuilders.standaloneSetup(controller(content))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    private static PublicContentController controller(ContentService content) {
        return new PublicContentController(
                content,
                mock(ContentModuleRegistry.class),
                mock(ContentActionService.class),
                mock(HomepageProjectionService.class));
    }

    private static PublicContentDto card(String title) {
        var json = JsonMapper.builder().build();
        return new PublicContentDto(
                ITEM_A, ContentKind.RESOURCE, "resource", 1, title, "合成摘要",
                json.readTree("{\"version\":1,\"blocks\":[]}"), "zh-CN", "中文", null,
                "合成来源", "https://contest.example.edu/rules", "已授权", "合成作者",
                "NOT_ALLOWED", "NOT_ALLOWED", json.readTree("{}"), 1L, List.of(), "OPEN",
                List.of(), null, Instant.parse("2026-09-30T00:00:00Z"));
    }
}
