package com.example.chatbot.config;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserRepository userRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, 
                                                      UserDetailsService userDetailsService,
                                                      PasswordEncoder passwordEncoder) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService)
               .passwordEncoder(passwordEncoder);
        return builder.build();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护，方便API测试
            .csrf(csrf -> csrf.disable())
            
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 允许公开访问的路径
                .requestMatchers("/api/auth/**", "/api/public/**", "/error").permitAll()
                // 静态资源允许公开访问
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // 允许访问根路径下的HTML文件
                .requestMatchers("/*.html").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            
            // 配置表单登录
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .defaultSuccessUrl("/index.html", true)
                .failureUrl("/login.html?error=true")
                .permitAll()
            )
            
            // 配置登出
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout"))
                .logoutSuccessUrl("/login.html?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            
            // 配置记住我功能
            .rememberMe(remember -> remember
                .key("uniqueAndSecretKey")
                .tokenValiditySeconds(86400) // 1天
            );
        
        return http.build();
    }
    
    /**
     * 初始化测试用户并修复旧用户数据
     */
    @Bean
    public CommandLineRunner initTestUsers(PasswordEncoder passwordEncoder) {
        return args -> {
            // 修复所有已存在的用户账户状态
            fixExistingUserAccounts();
            
            // 检查是否已经存在测试用户
            if (userRepository.findByUsername("admin") == null) {
                // 创建管理员用户
                User adminUser = new User();
                adminUser.setUsername("admin");
                adminUser.setPassword(passwordEncoder.encode("admin123"));
                adminUser.setEmail("admin@example.com");
                adminUser.setRole("ROLE_ADMIN");
                adminUser.setCreatedAt(LocalDateTime.now());
                adminUser.setEnabled(true);
                adminUser.setAccountNonExpired(true);
                adminUser.setAccountNonLocked(true);
                adminUser.setCredentialsNonExpired(true);
                userRepository.save(adminUser);
                System.out.println("已创建管理员用户: admin / admin123");
            }
            
            if (userRepository.findByUsername("user") == null) {
                // 创建普通用户
                User normalUser = new User();
                normalUser.setUsername("user");
                normalUser.setPassword(passwordEncoder.encode("user123"));
                normalUser.setEmail("user@example.com");
                normalUser.setRole("ROLE_USER");
                normalUser.setCreatedAt(LocalDateTime.now());
                normalUser.setEnabled(true);
                normalUser.setAccountNonExpired(true);
                normalUser.setAccountNonLocked(true);
                normalUser.setCredentialsNonExpired(true);
                userRepository.save(normalUser);
                System.out.println("已创建普通用户: user / user123");
            }
        };
    }
    
    /**
     * 修复已存在用户的账户状态
     */
    private void fixExistingUserAccounts() {
        List<User> allUsers = userRepository.findAll();
        int fixed = 0;
        
        for (User user : allUsers) {
            boolean needsUpdate = false;
            
            // 确保角色不为空
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("ROLE_USER");
                needsUpdate = true;
            }
            
            // 确保账户状态标志正确设置
            if (!user.isEnabled()) {
                user.setEnabled(true);
                needsUpdate = true;
            }
            
            if (!user.isAccountNonExpired()) {
                user.setAccountNonExpired(true);
                needsUpdate = true;
            }
            
            if (!user.isAccountNonLocked()) {
                user.setAccountNonLocked(true);
                needsUpdate = true;
            }
            
            if (!user.isCredentialsNonExpired()) {
                user.setCredentialsNonExpired(true);
                needsUpdate = true;
            }
            
            // 如果需要更新，保存用户
            if (needsUpdate) {
                userRepository.save(user);
                fixed++;
            }
        }
        
        if (fixed > 0) {
            System.out.println("已修复 " + fixed + " 个用户账户的状态");
        }
    }
}