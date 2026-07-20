package cn.welsione.ascoder.runtime.persistence;

import cn.welsione.ascoder.runtime.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemSettingJpaRepository extends JpaRepository<SystemSetting, String> {

    List<SystemSetting> findAllByCategoryOrderByKeyAsc(String category);

    void deleteByCategory(String category);
}