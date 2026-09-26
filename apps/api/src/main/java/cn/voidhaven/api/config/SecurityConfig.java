package cn.voidhaven.api.config;
import cn.voidhaven.api.persistence.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import cn.voidhaven.api.web.AccountEligibilityFilter;


// 9.26备注(path:02)：下方为保留的学习草稿。Argon2 是密码哈希，不是可逆加密；
// @EnableMethodSecurity 默认开启 pre/post 支持，@Secured 需显式启用 securedEnabled。
// SecurityConfig类出现的注释：
// 1. @Configuration：标记该类为Spring的配置类，用于定义Spring Bean。
// 2. @EnableWebSecurity：启用Spring Security的Web安全功能。
// 3. @EnableMethodSecurity：启用方法级别的安全性，允许在方法上使用@Secured注解。
// 4. @Value("${voidhaven.cookie-secure:false}")：从配置文件中获取voidhaven.cookie-secure属性的值，默认为false。
// 5. @Bean PasswordEncoder passwordEncoder()：定义一个PasswordEncoder Bean，用于密码加密。
// 6. @Bean UserDetailsService userDetailsService(AccountRepository accounts)：定义一个UserDetailsService Bean，用于获取用户信息。
// 7. @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder)：定义一个DaoAuthenticationProvider Bean，用于认证用户。
// 8. @Bean SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider, AccountEligibilityFilter accountEligibilityFilter)：定义一个SecurityFilterChain Bean，用于过滤请求。

// 安全配置
@Configuration @EnableWebSecurity @EnableMethodSecurity
public class SecurityConfig {
    // 从配置文件中获取voidhaven.cookie-secure属性的值，默认为false。
    @Value("${voidhaven.cookie-secure:false}") boolean cookieSecure;
    // 定义一个PasswordEncoder Bean，用于密码加密。
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    // 定义一个UserDetailsService Bean，用于获取用户信息。
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) { 
        // 返回一个UserDetailsService Bean，用于获取用户信息。
        // 将account.getRoles()转换为List<SimpleGrantedAuthority>。
        return login -> accounts.findByLoginId(login).filter(account -> account.isActive()).map(account -> User.withUsername(account.getLoginId()).password(account.getPasswordHash()).authorities(account.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode())).toList()).build()).orElseThrow(() -> new UsernameNotFoundException("Account not found")); 
        // 如果用户不存在，则抛出UsernameNotFoundException异常。

        //该写法使用了Java 8的Stream API，一般写法为：
        // List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        // for (Role role : account.getRoles()) {
        //     authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
        // }
        // UserDetails user = User.withUsername(account.getLoginId()).password(account.getPasswordHash()).authorities(authorities).build();
        // return user;
    }

    // 定义一个DaoAuthenticationProvider Bean，用于认证用户。
    @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) { DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder); return provider; }
    // 定义一个SecurityFilterChain Bean，用于过滤请求。
    @Bean SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider, AccountEligibilityFilter accountEligibilityFilter) throws Exception {
        // 定义一个CookieCsrfTokenRepository Bean，用于CSRF保护。

        // 9.26校正（path:02）：这里配置的是 CSRF Cookie，不是 Session Cookie；不能将二者的 HttpOnly 设置混为一谈。
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse(); csrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(cookieSecure));

        //以下代码是Spring Security的配置，用于配置HTTP安全。
        // 定义一个authenticationProvider(provider)，用于认证用户。
        // 定义一个csrf(config)，用于CSRF保护。
        // 定义一个ignoringRequestMatchers("/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume")，用于忽略某些请求。
        // 定义一个authorizeHttpRequests(auth)，用于授权请求。
        // 定义一个requestMatchers("/healthz", "/actuator/health/**", "/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/csrf", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume").permitAll()，用于允许某些请求。
        // 定义一个requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")，用于允许某些请求。
        // 定义一个requestMatchers("/api/v1/partner/**", "/api/v1/media/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN")，用于允许某些请求。
        // 定义一个anyRequest().authenticated()，用于允许所有请求。

        // 9.26校正（path:02）：
        // authenticated() 要求已认证，不是允许所有请求；permitAll 与 CSRF 豁免是独立规则。
        // 路径角色校验之后，Service 仍须校验内容归属。下方两次 build() 是学习副本已有的待核查问题。
        http.authenticationProvider(provider).csrf(config -> config.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume"))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/healthz", "/actuator/health/**", "/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/csrf", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume").permitAll().requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN").requestMatchers("/api/v1/partner/**", "/api/v1/media/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN").anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)).exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))).httpBasic(httpBasic -> httpBasic.disable())
            .addFilterAfter(accountEligibilityFilter, SecurityContextHolderFilter.class).build();
        // 返回一个HttpSecurity Bean。
        return http.build();
    }
}
