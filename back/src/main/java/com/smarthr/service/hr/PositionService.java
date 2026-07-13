/**
 * 岗位管理服务
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthr.dto.position.PositionCreateRequest;
import com.smarthr.dto.position.PositionDTO;
import com.smarthr.entity.Position;
import com.smarthr.exception.BusinessException;
import com.smarthr.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private static final String CACHE_ALL = "position:all";
    // 基础 TTL 30 分钟，实际加随机偏移 0-4 分钟，防止缓存雪崩
    private static final Duration POSITION_TTL_BASE = Duration.ofMinutes(30);

    private final PositionRepository positionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 先更 DB 后删缓存：如果先删缓存，并发读可能查到旧 DB 再回填，造成脏缓存
    private void deleteAllCache() {
        try {
            redisTemplate.delete(CACHE_ALL);
        } catch (Exception e) {
            log.warn("Failed to delete position cache: {}", e.getMessage());
        }
    }

    /**
     * 创建岗位
     */
    @Transactional
    public PositionDTO createPosition(PositionCreateRequest request, Long createdBy) {
        Position position = Position.builder()
                .title(request.getTitle())
                .company(request.getCompany())
                .salaryRange(request.getSalaryRange())
                .experience(request.getExperience())
                .education(request.getEducation())
                .location(request.getLocation())
                .responsibilities(request.getResponsibilities())
                .requirements(request.getRequirements())
                .skills(request.getSkills())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        Position saved = positionRepository.save(position);
        deleteAllCache();
        log.info("Created position: {} by user {}", saved.getId(), createdBy);

        return toDTO(saved);
    }

    /**
     * 更新岗位
     */
    @Transactional
    public PositionDTO updatePosition(Long id, PositionCreateRequest request, Long userId) {
        Position position = positionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("岗位不存在"));

        position.setTitle(request.getTitle());
        position.setCompany(request.getCompany());
        position.setSalaryRange(request.getSalaryRange());
        position.setExperience(request.getExperience());
        position.setEducation(request.getEducation());
        position.setLocation(request.getLocation());
        position.setResponsibilities(request.getResponsibilities());
        position.setRequirements(request.getRequirements());
        position.setSkills(request.getSkills());
        position.setUpdatedAt(LocalDateTime.now());

        Position saved = positionRepository.save(position);
        deleteAllCache();
        log.info("Updated position: {} by user {}", id, userId);

        return toDTO(saved);
    }

    /**
     * 删除岗位
     */
    @Transactional
    public void deletePosition(Long id, Long userId) {
        Position position = positionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("岗位不存在或已删除"));

        position.setDeleted(true);
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);
        deleteAllCache();
        log.info("Soft deleted position: {} by user {}", id, userId);
    }

    /**
     * 获取岗位详情
     */
    public PositionDTO getPosition(Long id) {
        Position position = positionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("岗位不存在或已删除"));
        return toDTO(position);
    }

    /**
     * 分页查询岗位列表
     */
    public Page<PositionDTO> listPositions(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Position> positions;
        if (keyword != null && !keyword.isEmpty()) {
            positions = positionRepository.findByTitleContainingIgnoreCaseAndDeletedFalse(keyword, pageable);
        } else {
            positions = positionRepository.findByDeletedFalse(pageable);
        }

        return positions.map(this::toDTO);
    }

    /**
     * 获取所有岗位（用于匹配）
     */
    // 匹配页面下拉框高频调用，缓存命中可省掉全表扫描
    public List<PositionDTO> getAllPositions() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_ALL);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<PositionDTO>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to read position cache: {}", e.getMessage());
        }

        List<PositionDTO> positions = positionRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        try {
            // TTL 加随机偏移，防止大量缓存同时过期压垮 DB
            Duration ttl = POSITION_TTL_BASE.plusMinutes(ThreadLocalRandom.current().nextInt(5));
            redisTemplate.opsForValue().set(CACHE_ALL, objectMapper.writeValueAsString(positions), ttl);
        } catch (Exception e) {
            log.warn("Failed to write position cache: {}", e.getMessage());
        }

        return positions;
    }

    /**
     * 转换为 DTO
     */
    private PositionDTO toDTO(Position position) {
        return PositionDTO.builder()
                .id(position.getId())
                .title(position.getTitle())
                .company(position.getCompany())
                .salaryRange(position.getSalaryRange())
                .experience(position.getExperience())
                .education(position.getEducation())
                .location(position.getLocation())
                .responsibilities(position.getResponsibilities())
                .requirements(position.getRequirements())
                .skills(position.getSkills())
                .createdBy(position.getCreatedBy())
                .createdAt(position.getCreatedAt())
                .updatedAt(position.getUpdatedAt())
                .build();
    }
}
