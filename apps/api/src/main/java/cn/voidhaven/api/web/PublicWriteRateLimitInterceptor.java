package cn.voidhaven.api.web;

import cn.voidhaven.api.service.PublicStatusQueryRateLimiter;
import cn.voidhaven.api.service.PublicWriteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

//PublicWriteRateLimitInterceptor：公共写入速率限制拦截器
//PublicWriteRateLimitInterceptor的作用是：
//1. 检查请求是否为POST请求
//2. 如果请求为POST请求，则检查请求路径是否为/api/v1/public/submissions或/api/v1/public/partner-applications
//3. 如果请求路径为/api/v1/public/submissions或/api/v1/public/partner-applications，则检查网络是否在10次/15min的桶中
//4. 如果网络在桶中，则返回错误
//5. 如果网络不在桶中，则继续拦截

@Component
public class PublicWriteRateLimitInterceptor implements HandlerInterceptor {
    //WRITE_PATHS：写入路径集合
    private static final Set<String> WRITE_PATHS = Set.of(
            "/api/v1/public/submissions",
            "/api/v1/public/partner-applications");
    //STATUS_QUERY_PATHS：状态查询路径集合
    private static final Set<String> STATUS_QUERY_PATHS = Set.of(
            "/api/v1/public/submissions/status-query",
            "/api/v1/public/partner-applications/status-query");
    //writeLimiter：公共写入速率限制器
    private final PublicWriteRateLimiter writeLimiter;
    //statusQueryLimiter：公共状态查询速率限制器
    private final PublicStatusQueryRateLimiter statusQueryLimiter;
    //networkSubject：网络主题
    private final ClientNetworkSubject networkSubject;

    //PublicWriteRateLimitInterceptor的构造函数/构造器
    public PublicWriteRateLimitInterceptor(PublicWriteRateLimiter writeLimiter,
                                           PublicStatusQueryRateLimiter statusQueryLimiter,
                                           ClientNetworkSubject networkSubject) {
        this.writeLimiter = writeLimiter;
        this.statusQueryLimiter = statusQueryLimiter;
        this.networkSubject = networkSubject;
    }

    //preHandle：拦截器的主要方法
    //参数列表解释：
    //1. request：请求对象
    //2. response：响应对象
    //3. handler：处理器
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //如果请求方法不为POST，则继续拦截
        if (!"POST".equals(request.getMethod())) return true;
        //获取请求路径
        String path = request.getRequestURI();
        //如果请求路径为/api/v1/public/submissions或/api/v1/public/partner-applications，则检查网络是否在10次/15min的桶中
        if (WRITE_PATHS.contains(path)) writeLimiter.requireAvailable(networkSubject.resolve(request));
        //如果请求路径为/api/v1/public/submissions/status-query或/api/v1/public/partner-applications/status-query，则检查网络是否在10次/15min的桶中
        else if (STATUS_QUERY_PATHS.contains(path)) statusQueryLimiter.requireAvailable(networkSubject.resolve(request));
        //如果请求路径不为/api/v1/public/submissions或/api/v1/public/partner-applications或/api/v1/public/submissions/status-query或/api/v1/public/partner-applications/status-query，则返回true
        //继续拦截
        return true;
    }
}
