package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rechecks account eligibility for every authenticated request and invalidates stale sessions. */

//AccountEligibilityFilter：账户资格过滤器
//AccountEligibilityFilter的作用是：
//1. 检查账户是否已停用或资格不可用
//2. 如果账户已停用或资格不可用，则清除安全上下文，并返回错误
//3. 如果账户未停用或资格可用，则继续过滤
//AccountEligibilityFilter的实现代码在AccountEligibilityFilter.java文件中

@Component
public class AccountEligibilityFilter extends OncePerRequestFilter {

    //accounts：账户仓库
    private final AccountRepository accounts;

    //AccountEligibilityFilter的构造函数
    public AccountEligibilityFilter(AccountRepository accounts) { this.accounts = accounts; }

    //doFilterInternal：过滤器的主要方法
    @Override
    //参数列表解释：
        //1. request：请求对象
        //2. response：响应对象
        //3. chain：过滤器链
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        //获取安全上下文
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //如果安全上下文不为空，则检查账户是否已停用或资格不可用
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && accounts.findByLoginId(authentication.getName()).filter(account -> account.isActive()).isEmpty()) {
            //获取会话对象
            HttpSession session = request.getSession(false);
            //如果会话对象不为空，则无效化会话
            if (session != null) session.invalidate();
            //清除安全上下文
            SecurityContextHolder.clearContext();
            //设置响应状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            //设置响应内容类型
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            //设置响应头
            response.setHeader("Cache-Control", "no-store");
            //设置响应内容
            response.getWriter().write("{\"code\":\"ACCOUNT_INACTIVE\",\"message\":\"当前账号已停用或资格不可用。\",\"requestId\":\"" + requestId(request) + "\"}");
            return;
        }
        //继续过滤
        chain.doFilter(request, response);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
