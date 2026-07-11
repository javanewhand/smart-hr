/**
 * 技能图谱服务
 * 提供技能匹配、路径分析等功能
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.graph;

import com.smarthr.entity.SkillNode;
import com.smarthr.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGraphService {

    private final SkillRepository skillRepository;

    /**
     * 从文本中提取技能
     */
    public List<String> extractSkills(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<SkillNode> allSkills = skillRepository.findAllSkills();
        List<String> extractedSkills = new ArrayList<>();
        String lowerText = text.toLowerCase();//全部转换为小写字母

        for (SkillNode skill : allSkills) {
            // 检查技能名称
            if (lowerText.contains(skill.getName().toLowerCase())) {
                extractedSkills.add(skill.getName());
                continue;
            }

            // 检查关键词
            if (skill.getKeywords() != null) {
                for (String keyword : skill.getKeywords()) {
                    if (lowerText.contains(keyword.toLowerCase())) {
                        extractedSkills.add(skill.getName());
                        break;
                    }
                }
            }
        }

        log.debug("Extracted {} skills from text", extractedSkills.size());
        return extractedSkills.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 计算技能匹配度
     *
     * @param candidateSkills 候选人技能列表
     * @param requiredSkills  岗位要求技能列表
     * @return 匹配度（0-1）
     */
    public SkillMatchResult calculateSkillMatch(List<String> candidateSkills, List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return new SkillMatchResult(1.0f, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        if (candidateSkills == null || candidateSkills.isEmpty()) {
            return new SkillMatchResult(0.0f, Collections.emptyList(), requiredSkills, Collections.emptyList());
        }

        Set<String> candidateSet = new HashSet<>(candidateSkills);
        Set<String> requiredSet = new HashSet<>(requiredSkills);

        // 直接匹配的技能
        List<String> matchedSkills = new ArrayList<>();
        // 缺失的技能
        List<String> missingSkills = new ArrayList<>();
        // 候选人的额外技能
        List<String> extraSkills = new ArrayList<>();

        float totalScore = 0.0f;
        float maxScore = requiredSet.size();

        for (String required : requiredSet) {
            if (candidateSet.contains(required)) {
                // 直接匹配
                matchedSkills.add(required);
                totalScore += 1.0f;
            } else {
                // 检查是否有相关技能或高级技能
                float partialScore = calculatePartialMatch(required, candidateSkills);
                if (partialScore > 0) {
                    matchedSkills.add(required + " (部分匹配)");
                    totalScore += partialScore;
                } else {
                    missingSkills.add(required);
                }
            }
        }

        // 计算额外技能
        for (String candidate : candidateSet) {
            if (!requiredSet.contains(candidate)) {
                extraSkills.add(candidate);
            }
        }

        float score = maxScore > 0 ? totalScore / maxScore : 0.0f;
        log.debug("Skill match score: {}, matched: {}, missing: {}", score, matchedSkills.size(), missingSkills.size());

        return new SkillMatchResult(score, matchedSkills, missingSkills, extraSkills);
    }

    /**
     * 计算部分匹配分数
     * 如果候选人有相关技能或更高级技能，给予部分分数
     */
    private float calculatePartialMatch(String requiredSkill, List<String> candidateSkills) {
        try {
            // 查找要求技能的前置技能
            List<SkillNode> prerequisites = skillRepository.findPrerequisites(requiredSkill);
            for (SkillNode prereq : prerequisites) {
                if (candidateSkills.contains(prereq.getName())) {
                    // 有前置技能，给予 0.3 分
                    return 0.3f;
                }
            }

            // 查找要求技能的相关技能
            List<SkillNode> relatedSkills = skillRepository.findRelatedSkills(requiredSkill);
            for (SkillNode related : relatedSkills) {
                if (candidateSkills.contains(related.getName())) {
                    // 有相关技能，给予 0.5 分
                    return 0.5f;
                }
            }

            // 查找以要求技能为前置的高级技能
            List<SkillNode> advancedSkills = skillRepository.findAdvancedSkills(requiredSkill);
            for (SkillNode advanced : advancedSkills) {
                if (candidateSkills.contains(advanced.getName())) {
                    // 有更高级的技能，给予 0.8 分
                    return 0.8f;
                }
            }

        } catch (Exception e) {
            log.warn("Failed to calculate partial match for skill {}: {}", requiredSkill, e.getMessage());
        }

        return 0.0f;
    }

    /**
     * 获取技能学习路径
     */
    public List<String> getSkillLearningPath(String targetSkill) {
        List<SkillNode> dependencies = skillRepository.findDependencyTree(targetSkill);
        
        // 按层级排序（先学基础，再学高级）
        return dependencies.stream()
                .sorted(Comparator.comparingInt(s -> s.getLevel() != null ? s.getLevel() : 0))
                .map(SkillNode::getName)
                .collect(Collectors.toList());
    }

    /**
     * 推荐需要学习的技能
     * 先将用户输入的原始技能名通过图谱关键词映射为标准名，避免大小写/别名导致的误判
     * 结果按技能等级从低到高排序，确保先学基础再学进阶
     */
    public List<String> recommendSkillsToLearn(List<String> currentSkills, List<String> targetSkills) {
        Set<String> current = normalizeSkillNames(currentSkills);
        Set<String> targets = normalizeSkillNames(targetSkills);
        List<String> toLearn = new ArrayList<>();

        for (String target : targets) {
            if (!current.contains(target)) {
                // 获取该技能的前置依赖
                List<SkillNode> prerequisites = skillRepository.findPrerequisites(target);
                for (SkillNode prereq : prerequisites) {
                    if (!current.contains(prereq.getName()) && !toLearn.contains(prereq.getName())) {
                        toLearn.add(prereq.getName());
                    }
                }
                if (!toLearn.contains(target)) {
                    toLearn.add(target);
                }
            }
        }
        // 按技能等级升序排列（低等级=基础，先学）
        toLearn.sort(Comparator.comparingInt(name -> {
            SkillNode node = skillRepository.findByName(name).orElse(null);
            return node != null && node.getLevel() != null ? node.getLevel() : Integer.MAX_VALUE;
        }));
        return toLearn;
    }

    /**
     * 将原始技能名映射为知识图谱中的标准名
     * 通过搜索技能名称和关键词模糊匹配，取第一个命中结果
     */
    private Set<String> normalizeSkillNames(List<String> rawNames) {
        Set<String> normalized = new HashSet<>();
        for (String raw : rawNames) {
            List<SkillNode> matched = skillRepository.findByKeyword(raw.trim());
            if (!matched.isEmpty()) {
                normalized.add(matched.get(0).getName());
            } else {
                normalized.add(raw.trim());
            }
        }
        return normalized;
    }

    /**
     * 获取所有技能分类
     */
    public Map<String, List<String>> getSkillsByCategory() {
        Map<String, List<String>> result = new HashMap<>();
        String[] categories = {"BACKEND", "FRONTEND", "DATABASE", "DEVOPS", "AI", "TESTING", "MANAGEMENT", "GENERAL"};

        for (String category : categories) {
            List<SkillNode> skills = skillRepository.findByCategory(category);
            result.put(category, skills.stream().map(SkillNode::getName).collect(Collectors.toList()));
        }

        return result;
    }

    /**
     * 搜索技能
     */
    public List<SkillNode> searchSkills(String keyword) {
        return skillRepository.findByKeyword(keyword);
    }

    /**
     * 获取技能详情
     */
    public Optional<SkillNode> getSkillDetail(String skillName) {
        return skillRepository.findByName(skillName);
    }

    /**
     * 获取技能统计
     */
    public long getSkillCount() {
        Long count = skillRepository.countSkills();
        return count != null ? count : 0;
    }

    /**
     * 获取所有技能节点
     */
    public List<SkillNode> getAllSkills() {
        return skillRepository.findAllSkills();
    }

    /**
     * 根据名称查找技能
     */
    public Optional<SkillNode> findByName(String name) {
        return skillRepository.findByName(name);
    }

    /**
     * 根据关键词查找技能
     */
    public Optional<SkillNode> findByKeyword(String keyword) {
        List<SkillNode> skills = skillRepository.findByKeyword(keyword);
        return skills.isEmpty() ? Optional.empty() : Optional.of(skills.get(0));
    }
}
