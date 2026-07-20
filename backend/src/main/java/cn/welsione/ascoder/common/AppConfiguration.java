package cn.welsione.ascoder.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 应用级通用 Bean 配置。集中暴露需要在多个模块复用的基础设施 Bean。
 */
@Configuration
public class AppConfiguration {

    /**
     * 用于异步线程中显式开启事务（声明式 {@code @Transactional} 在非 Spring 管理线程失效）。
     */
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
