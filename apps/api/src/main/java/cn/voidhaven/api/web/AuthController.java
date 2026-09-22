package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuthenticationRateLimiter;
import cn.voidhaven.api.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final ActivationService activations;
    private final AuthenticationRateLimiter rateLimiter;
    private final ClientNetworkSubject networkSubject;
    private final PasswordService passwords;

    public AuthController(AuthenticationConfiguration configuration, ActivationService activations,
                          AuthenticationRateLimiter rateLimiter, ClientNetworkSubject networkSubject,
                          PasswordService passwords) throws Exception {
        this.authenticationManager = configuration.getAuthenticationManager();
        this.activations = activations;
        this.rateLimiter = rateLimiter;
        this.networkSubject = networkSubject;
        this.passwords = passwords;
    }

    record LoginRequest(@NotBlank @Size(max = 100) String loginId, @NotBlank @Size(max = 128) String password) { }
    record Identity(String loginId, List<String> roles) { }
    record ActivationRequest(@NotBlank @Size(max = 256) String token, @NotBlank @Size(min = 12, max = 128) String password) { }
    record CsrfPayload(String headerName, String token) { }
    record PasswordChangeRequest(@NotBlank @Size(max = 128) String currentPassword,
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) { }
    record PasswordResetRequest(@NotBlank @Size(max = 256) String token,
                                @NotBlank @Size(min = 12, max = 128) String newPassword) { }
                  
    //整个过程是
    //1. 用户输入登录名和密码
    //2.先限流再认证
        //2.1 检查登录名是否在5次/15min的桶中
        //2.2 检查网络是否在20次/15min的桶中
        //2.3 如果登录名或网络在桶中，则返回错误
        //2.4 如果登录名或网络不在桶中，则继续认证
        //2.5 如果认证成功，则创建会话，并返回身份信息
        //2.6 如果认证失败，则清除安全上下文，并返回错误
        //2.7 如果认证失败，则无效化会话，并返回错误
        //2.8 如果认证成功，则登录成功，并返回身份信息
        //2.9 如果认证失败，则清除安全上下文，并返回错误
    //3.在认证前慧先拿到会话对象
        //3.1 如果会话对象为空，则创建会话  request.getSession(true);
        //3.2 如果会话对象不为空，则返回会话对象
        //3.3 如果会话对象不为空，则返回会话对象
    //4. 认证用户
        //4.1调用authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
        //4.2 如果认证成功，则返回身份信息
        //4.3 如果认证失败，则返回错误
    //5. 创建会话
        //5.1 调用request.changeSessionId();
        //5.2 调用request.getSession(false);
        //5.3 如果会话对象为空，则创建会话
        //5.4 如果会话对象不为空，则返回会话对象

   //POST/api/v1/auth/session不检查CSRF
    @PostMapping("/session")
    //登录方法
    //参数：LoginRequest body, HttpServletRequest request
    //返回：ApiResponse<Identity>
    ApiResponse<Identity> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        //开始登录
        //beginLogin同时占用两个桶：登录名5次/15min|网络20次/15min
        //beginLogin的业务逻辑代码写AuthenticationRateLimiter.java里：
        //在AuthenticationRateLimiter.java文件中
        //先检查登录名是否在5次/15min的桶中
        String attemptId = rateLimiter.beginLogin(body.loginId(), networkSubject.resolve(request));
        HttpSession session = request.getSession(true);
        //尝试登录
        try {
            //认证用户
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
            //创建会话
            request.changeSessionId();

            //设置安全上下文
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            //获取会话并设置会话
            session = request.getSession(false);
            if (session == null) throw new IllegalStateException("Authenticated session is unavailable");
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);
            //登录成功
            rateLimiter.loginSucceeded(attemptId);
            //返回身份信息
            return new ApiResponse<>(identity(authentication), requestId(request));
        //登录失败
        } catch (AuthenticationException error) {
            //记录登录失败
            rateLimiter.loginFailed(attemptId);
            //清除安全上下文
            SecurityContextHolder.clearContext();
            //无效化会话
            session.invalidate();
            //抛出异常
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "账号或密码不正确，或账号当前不可用。");
            //登录失败
        }
        //登录失败
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    ApiResponse<Identity> me(Authentication authentication, HttpServletRequest request) {
        return new ApiResponse<>(identity(authentication), requestId(request));
    }

    @GetMapping("/csrf")
    ApiResponse<CsrfPayload> csrf(CsrfToken token, HttpServletRequest request) {
        return new ApiResponse<>(new CsrfPayload(token.getHeaderName(), token.getToken()), requestId(request));
    }

    @PostMapping("/activation/consume")
    ApiResponse<Map<String, String>> activate(@Valid @RequestBody ActivationRequest body, HttpServletRequest request) {
        rateLimiter.beginActivation(networkSubject.resolve(request));
        var account = activations.consume(body.token(), body.password());
        return new ApiResponse<>(Map.of("loginId", account.getLoginId(), "message", "账号已激活，请使用新密码登录。"), requestId(request));
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody PasswordChangeRequest body, Authentication authentication, HttpServletRequest request) {
        passwords.change(authentication.getName(), body.currentPassword(), body.newPassword(), requestId(request));
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/password-reset/consume")
    ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        String loginId = passwords.consumeReset(body.token(), body.newPassword());
        return new ApiResponse<>(Map.of("loginId", loginId, "message", "密码已重置，请使用新密码登录。"), requestId(request));
    }

    private Identity identity(Authentication authentication) {
        return new Identity(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring("ROLE_".length())).sorted().toList());
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}

