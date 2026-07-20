package cn.welsione.ascoder.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 代码仓库 JPA 仓库接口。
 */
public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepository, Long> {

    boolean existsByName(String name);

    Optional<CodeRepository> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CodeRepository r where r.id = :id")
    Optional<CodeRepository> findByIdForUpdate(@Param("id") Long id);
}
