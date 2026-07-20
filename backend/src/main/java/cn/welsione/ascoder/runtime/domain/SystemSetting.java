package cn.welsione.ascoder.runtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统运行时设置表（systemSettings）实体。
 *
 * <p>每个 setting 是一个键值对，含类型、分类、说明。{@code key} 为主键，
 * 白名单由 {@code cn.welsione.ascoder.runtime.application.RuntimeSettingsService}
 * 维护：只允许写入预注册的 key，避免任意字段写入污染配置空间。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "systemSettings")
public class SystemSetting {

    @Id
    @Column(name = "`key`", length = 100, nullable = false)
    private String key;

    @Column(nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettingValueType valueType;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}