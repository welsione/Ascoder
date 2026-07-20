package cn.welsione.ascoder.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import lombok.extern.slf4j.Slf4j;

/**
 * AES-256/GCM 对称加密工具，用于 API Key 等敏感数据的加密存储与解密读取。
 *
 * <p>每次加密随机生成 12 字节 IV，IV 前置于密文（前 12 字节为 IV，后续为密文），
 * 输出 Base64 编码的 (IV + ciphertext)。</p>
 *
 * <p>密钥获取策略：</p>
 * <ol>
 *   <li>构造函数传入的 base64Key 非空 → 优先使用</li>
 *   <li>未传入密钥但 allowDefaultKey=true → 使用开发环境默认密钥（仅用于本地开发）</li>
 *   <li>以上均不满足 → 首次调用 {@link #encrypt(String)} 或 {@link #decrypt(String)}
 *       时抛 {@link IllegalStateException}，引导用户配置 ASCODER_ENCRYPTION_KEY 环境变量</li>
 * </ol>
 */
@Slf4j
public class ApiKeyEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 开发环境默认密钥（Base64 编码的 32 字节），仅用于本地开发与单元测试。
     * 生产环境必须通过 ASCODER_ENCRYPTION_KEY 环境变量显式配置密钥。
     */
    private static final String DEFAULT_DEV_KEY = "YXNjb2Rlci1kZWZhdWx0LWRldi1rZXktMzJieXRlcyE=";

    private SecretKeySpec keySpec;
    private final SecureRandom secureRandom;
    private final String base64Key;
    private final boolean usedDefaultKey;

    /**
     * 构造加密器（不允许使用开发默认密钥）。
     *
     * @param base64Key Base64 编码的 256 位（32 字节）AES 密钥，可为 null 或空
     */
    public ApiKeyEncryptor(String base64Key) {
        this(base64Key, false);
    }

    /**
     * 构造加密器，可选允许使用开发默认密钥。
     *
     * @param base64Key      Base64 编码的 256 位（32 字节）AES 密钥，可为 null 或空
     * @param allowDefaultKey 是否允许在密钥未配置时使用开发环境默认密钥
     */
    public ApiKeyEncryptor(String base64Key, boolean allowDefaultKey) {
        this.base64Key = base64Key;
        this.secureRandom = new SecureRandom();
        boolean defaultKeyUsed = false;
        if (base64Key != null && !base64Key.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length >= 32) {
                this.keySpec = new SecretKeySpec(keyBytes, "AES");
                log.info("ApiKeyEncryptor 初始化完成（使用自定义密钥）");
            } else {
                log.warn("ascoder.encryption-key 长度不足 32 字节，将在首次加解密时校验");
            }
        } else if (allowDefaultKey) {
            byte[] keyBytes = Base64.getDecoder().decode(DEFAULT_DEV_KEY);
            this.keySpec = new SecretKeySpec(keyBytes, "AES");
            defaultKeyUsed = true;
            log.warn("ApiKeyEncryptor 使用开发环境默认密钥（仅用于本地开发），生产环境必须设置 ASCODER_ENCRYPTION_KEY 环境变量");
        } else {
            log.warn("ascoder.encryption-key 未配置，将在首次加解密时校验");
        }
        this.usedDefaultKey = defaultKeyUsed;
    }

    /**
     * 加密明文，返回 Base64 编码的 (IV + ciphertext)。
     *
     * @param plaintext 明文
     * @return Base64 编码的密文
     * @throws IllegalStateException 密钥未配置或长度不足
     */
    public String encrypt(String plaintext) {
        ensureKeyReady();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            log.debug("加密完成，明文长度: {}", plaintext.length());
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密 Base64 编码的 (IV + ciphertext)，返回原始明文。
     *
     * @param ciphertext Base64 编码的密文
     * @return 原始明文
     * @throws IllegalStateException 密钥未配置或长度不足
     */
    public String decrypt(String ciphertext) {
        ensureKeyReady();
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(encrypted);
            log.debug("解密完成");
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    /**
     * 是否使用了开发环境默认密钥（用于运维检查与告警）。
     */
    public boolean isUsingDefaultKey() {
        return usedDefaultKey;
    }

    /**
     * 延迟校验密钥，确保首次加解密前密钥已正确配置。
     */
    private void ensureKeyReady() {
        if (keySpec != null) {
            return;
        }
        if (base64Key == null || base64Key.isEmpty()) {
            throw new IllegalStateException("加密密钥未配置：请设置环境变量 ASCODER_ENCRYPTION_KEY");
        }
        throw new IllegalStateException("加密密钥必须为 32 字节（256 位）的 Base64 编码密钥");
    }
}