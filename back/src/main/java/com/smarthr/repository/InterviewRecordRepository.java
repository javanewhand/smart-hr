/**
 * 面试题记录数据访问接口
 * 由于继承自JPA，而JPA主数据源配置配置为pgSQL，所以默认查询为pgSQL
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.repository;

import com.smarthr.entity.InterviewRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRecordRepository extends JpaRepository<InterviewRecord, Long> {

    Page<InterviewRecord> findByUserId(Long userId, Pageable pageable);

    List<InterviewRecord> findByUserId(Long userId);

    List<InterviewRecord> findByPositionId(Long positionId);

    Page<InterviewRecord> findByPositionId(Long positionId, Pageable pageable);
}
