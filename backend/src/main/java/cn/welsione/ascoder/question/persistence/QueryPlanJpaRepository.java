package cn.welsione.ascoder.question.persistence;

import cn.welsione.ascoder.question.domain.QueryPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 查询规划 JPA 仓库接口。
 */
public interface QueryPlanJpaRepository extends JpaRepository<QueryPlan, Long> {

    Optional<QueryPlan> findByQuestionId(Long questionId);

    List<QueryPlan> findByQuestionIdIn(List<Long> questionIds);

    /**
     * 批量删除指定问题关联的查询规划。
     */
    @Modifying
    @Query("delete from QueryPlan qp where qp.question.id in :questionIds")
    int deleteByQuestionIdIn(@Param("questionIds") List<Long> questionIds);
}
