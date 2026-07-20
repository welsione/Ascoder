package cn.welsione.ascoder.common;

import cn.welsione.ascoder.common.security.ApiKeyEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 安全相关 Bean 配置，注册 ascoder-common 中的加密工具。
 *
 * <p>开发环境（{@code spring.profiles.active=local}）允许在密钥未配置时使用
 * 默认开发密钥，仅用于本地启动与单元测试；生产环境必须显式配置密钥。</p>
 */
@Configuration
public class SecurityConfiguration {

    private static final Set<String> DEV_PROFILES = new HashSet<>(Arrays.asList("local", "dev", "default"));

    @Bean
    public ApiKeyEncryptor apiKeyEncryptor(
            @Value("${ascoder.encryption-key:}") String encryptionKey,
            @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        boolean allowDefaultKey = isDevProfile(activeProfiles);
        return new ApiKeyEncryptor(encryptionKey, allowDefaultKey);
    }

    private boolean isDevProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            if (DEV_PROFILES.contains(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}