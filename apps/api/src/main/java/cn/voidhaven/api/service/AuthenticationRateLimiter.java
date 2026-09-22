package cn.voidhaven.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class AuthenticationRateLimiter {
    private static final String HMAC = "HmacSHA256";
    private final RateLimitLockService locks;
    private final String secret;
    private final int loginFailureLimit;
    private final int loginNetworkLimit;
    private final int activationNetworkLimit;
    private final Duration window;

    public AuthenticationRateLimiter(RateLimitLockService locks,
            @Value("${voidhaven.public-write-rate-limit-secret:local-development-rate-limit-secret}") String secret,
            @Value("${voidhaven.auth-login-failure-limit:5}") int loginFailureLimit,
            @Value("${voidhaven.auth-login-network-limit:20}") int loginNetworkLimit,
            @Value("${voidhaven.auth-activation-network-limit:10}") int activationNetworkLimit,
            @Value("${voidhaven.auth-rate-limit-window-minutes:15}") long windowMinutes) {
        if (loginFailureLimit < 1 || loginNetworkLimit < 1 || activationNetworkLimit < 1 || windowMinutes < 1) {
            throw new IllegalStateException("Authentication rate-limit configuration is invalid");
        }
        this.locks = locks;
        this.secret = secret;
        this.loginFailureLimit = loginFailureLimit;
        this.loginNetworkLimit = loginNetworkLimit;
        this.activationNetworkLimit = activationNetworkLimit;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    //beginLogin
    public String beginLogin(String loginId, String networkSubject) {
        String networkHash = hash("network:" + networkSubject);
        String loginHash = hash("login:" + loginId.trim().toLowerCase());

        //locks.reserveAuth的作用是：
        //1. 检查网络是否在20次/15min的桶中
        //2. 检查登录名是否在5次/15min的桶中
        //3. 如果网络或登录名在桶中，则返回错误
        //4. 如果网络或登录名不在桶中，则返回成功
        //reserveAuth的实现代码在RateLimitLockService.java文件中
        locks.reserveAuth(RateLimitLockService.LOGIN_NETWORK, networkHash, loginNetworkLimit, window, "ATTEMPT");
        
        //返回唯一的ID
        return locks.reserveAuth(RateLimitLockService.LOGIN_ID, loginHash, loginFailureLimit, window, "ATTEMPT");
    }

    public void loginFailed(String attemptId) {
        locks.completeLoginAttempt(attemptId, false);
    }

    public void loginSucceeded(String attemptId) {
        locks.completeLoginAttempt(attemptId, true);
    }

    public void beginActivation(String networkSubject) {
        String networkHash = hash("activation:" + networkSubject);
        locks.reserveAuth(RateLimitLockService.ACTIVATION_NETWORK, networkHash, activationNetworkLimit, window, "ATTEMPT");
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Cannot hash authentication subject", error); }
    }
}
