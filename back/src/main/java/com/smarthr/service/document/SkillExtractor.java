/**
 * 技能提取服务 - 从简历文本中提取技能
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.document;

import com.smarthr.service.ai.ModelRouter;
import com.smarthr.service.graph.SkillGraphService;
import com.smarthr.entity.SkillNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExtractor {

    private final SkillGraphService skillGraphService;
    private final ModelRouter modelRouter;

    /**
     * 从文本中提取技能（基于知识图谱 + AI 辅助）
     */
    public List<String> extractSkills(String content, Long userId) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> extractedSkills = new LinkedHashSet<>();

        // 1. 基于知识图谱关键词匹配
        List<String> graphBasedSkills = extractFromKnowledgeGraph(content);
        extractedSkills.addAll(graphBasedSkills);

        // 2. 使用 AI 提取更多技能
        try {
            List<String> aiExtractedSkills = extractWithAI(content, userId);
            extractedSkills.addAll(aiExtractedSkills);
        } catch (Exception e) {
            log.warn("AI 技能提取失败，使用知识图谱结果: {}", e.getMessage());
        }

        return new ArrayList<>(extractedSkills);
    }

    /**
     * 基于知识图谱提取技能
     */
    private List<String> extractFromKnowledgeGraph(String content) {
        List<String> skills = new ArrayList<>();
        String lowerContent = content.toLowerCase();

        // 获取所有技能节点
        List<SkillNode> allSkills = skillGraphService.getAllSkills();
        
        for (SkillNode skill : allSkills) {
            // 检查技能名称
            if (containsSkill(lowerContent, skill.getName())) {
                skills.add(skill.getName());
                continue;
            }
            
            // 检查技能关键词
            if (skill.getKeywords() != null) {
                for (String keyword : skill.getKeywords()) {
                    if (containsSkill(lowerContent, keyword)) {
                        skills.add(skill.getName());
                        break;
                    }
                }
            }
        }

        return skills;
    }

    /**
     * 检查文本是否包含技能（考虑边界）
     */
    private boolean containsSkill(String content, String skill) {
        if (skill == null || skill.isEmpty()) {
            return false;
        }
        
        // 对于特殊字符的技能名，直接包含检查
        String lowerSkill = skill.toLowerCase();
        
        // 使用正则匹配单词边界
        String regex = "(?i)\\b" + Pattern.quote(lowerSkill) + "\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return true;
        }
        
        // 对于中文或特殊格式的技能，直接包含检查
        return content.contains(lowerSkill);
    }

    /**
     * 使用 AI 提取技能
     */
    private List<String> extractWithAI(String content, Long userId) {
        String prompt = """
            请从以下简历内容中提取技术技能和软技能。
            只返回技能名称列表，用逗号分隔，不要包含其他内容。
            
            简历内容：
            %s
            
            提取的技能（用逗号分隔）：
            """.formatted(truncateContent(content, 2000));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        String response = modelRouter.route(userId).chat(messages);
        
        return Arrays.stream(response.split("[,，]"))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .filter(s -> s.length() <= 50)  // 过滤异常长度
                     .collect(Collectors.toList());
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 验证并标准化技能名称
     */
    public List<String> normalizeSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> normalized = new LinkedHashSet<>();
        
        for (String skill : skills) {
            // 尝试在知识图谱中找到标准名称
            Optional<SkillNode> found = skillGraphService.findByName(skill);
            if (found.isPresent()) {
                normalized.add(found.get().getName());
            } else {
                // 尝试通过关键词查找
                Optional<SkillNode> byKeyword = skillGraphService.findByKeyword(skill);
                if (byKeyword.isPresent()) {
                    normalized.add(byKeyword.get().getName());
                } else {
                    // 保留原始技能名
                    normalized.add(skill);
                }
            }
        }

        return new ArrayList<>(normalized);
    }
}

