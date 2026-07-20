package cn.welsione.ascoder.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyEncryptor 单元测试。
 *
 * <p>密钥校验延迟到首次加解密时：构造允许空密钥，应用可零配置启动；
 * 首次调用 encrypt/decrypt 时才校验密钥可用性。</p>
 */
class ApiKeyEncryptorTests {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private ApiKeyEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new ApiKeyEncryptor(VALID_KEY);
    }

    @Test
    void encryptThenDecrypt_shouldReturnOriginalPlaintext() {
        String plaintext = "test-api-key-12345";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptThenDecrypt_emptyString_shouldReturnEmptyString() {
        String plaintext = "";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void constructor_nullKey_shouldNotThrow() {
        // 构造时不抛异常，应用可零配置启动
        assertDoesNotThrow(() -> new ApiKeyEncryptor(null));
    }

    @Test
    void constructor_emptyKey_shouldNotThrow() {
        assertDoesNotThrow(() -> new ApiKeyEncryptor(""));
    }

    @Test
    void constructor_shortKey_shouldNotThrow_butEncryptFails() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        ApiKeyEncryptor localEncryptor = assertDoesNotThrow(() -> new ApiKeyEncryptor(shortKey));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> localEncryptor.encrypt("test"));
        assertEquals("加密密钥必须为 32 字节（256 位）的 Base64 编码密钥", ex.getMessage());
    }

    @Test
    void encrypt_withNullKey_shouldThrowIllegalStateException() {
        ApiKeyEncryptor localEncryptor = new ApiKeyEncryptor(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> localEncryptor.encrypt("test"));
        assertTrue(ex.getMessage().contains("加密密钥未配置"));
    }

    @Test
    void encrypt_withEmptyKey_shouldThrowIllegalStateException() {
        ApiKeyEncryptor localEncryptor = new ApiKeyEncryptor("");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> localEncryptor.encrypt("test"));
        assertTrue(ex.getMessage().contains("加密密钥未配置"));
    }

    @Test
    void encrypt_differentPlaintexts_shouldProduceDifferentCiphertexts() {
        String encrypted1 = encryptor.encrypt("key-one");
        String encrypted2 = encryptor.encrypt("key-two");
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void constructor_allowDefaultKey_nullKey_usesDefaultKey() {
        ApiKeyEncryptor defaultEncryptor = new ApiKeyEncryptor(null, true);
        assertTrue(defaultEncryptor.isUsingDefaultKey());
        // 默认密钥可用，encrypt 不抛异常
        String encrypted = defaultEncryptor.decrypt(defaultEncryptor.encrypt("test"));
        assertEquals("test", encrypted);
    }

    @Test
    void constructor_allowDefaultKey_emptyKey_usesDefaultKey() {
        ApiKeyEncryptor defaultEncryptor = new ApiKeyEncryptor("", true);
        assertTrue(defaultEncryptor.isUsingDefaultKey());
        String encrypted = defaultEncryptor.encrypt("test");
        assertEquals("test", defaultEncryptor.decrypt(encrypted));
    }

    @Test
    void constructor_disallowDefaultKey_nullKey_notUsingDefault() {
        ApiKeyEncryptor noKeyEncryptor = new ApiKeyEncryptor(null, false);
        assertFalse(noKeyEncryptor.isUsingDefaultKey());
        // 不允许默认密钥时，encrypt 抛异常
        assertThrows(IllegalStateException.class, () -> noKeyEncryptor.encrypt("test"));
    }

    @Test
    void constructor_withCustomKey_notUsingDefault() {
        ApiKeyEncryptor customEncryptor = new ApiKeyEncryptor(VALID_KEY, true);
        assertFalse(customEncryptor.isUsingDefaultKey());
    }
}