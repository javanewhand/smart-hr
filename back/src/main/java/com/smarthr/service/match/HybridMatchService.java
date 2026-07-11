/**
 * 混合检索评分服务
 * 整合知识图谱技能匹配 + LLM 综合评估
 * 实现双维度综合评分
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.match;

import com.smarthr.service.ai.ModelRouter;
import com.smarthr.service.graph.SkillGraphService;
import com.smarthr.service.graph.SkillMatchResult;
// RAGService 已注释停用（2026-07-11），详情见该类 JavaDoc
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridMatchService {

    private final SkillGraphService skillGraphService;
    // RAG 评分维度已禁用，ragService 字段一并注释（2026-07-11）
    // @SuppressWarnings("unused")
    // private final RAGService ragService;
    private final ModelRouter modelRouter;

    /**
     * 权重配置（去掉 RAG，调整为知识图谱 + LLM 双维度）
     */
    // private static final float RAG_WEIGHT = 0.4f;      // 语义相似度权重 40%（已禁用）
    private static final float GRAPH_WEIGHT = 0.5f;    // 技能匹配权重 50%
    private static final float LLM_WEIGHT = 0.5f;      // LLM 评估权重 50%

    /**
     * 执行混合匹配评分
     *
     * @param resumeContent   简历内容
     * @param positionContent 岗位 JD 内容
     * @param requiredSkills  岗位要求技能列表
     * @return 匹配结果
     */
    public MatchResult match(String resumeContent, String positionContent, List<String> requiredSkills) {
        return match(resumeContent, null, positionContent, requiredSkills, null);
    }

    /**
     * 执行混合匹配评分（带候选人技能列表）
     *
     * @param resumeContent   简历内容
     * @param candidateSkills 候选人技能列表（可选，为空则从简历提取）
     * @param positionContent 岗位 JD 内容
     * @param requiredSkills  岗位要求技能列表
     * @param userId          用户 ID（用于 AI 模型路由）
     * @return 匹配结果
     */
    public MatchResult match(String resumeContent, List<String> candidateSkills,
                             String positionContent, List<String> requiredSkills,
                             Long userId) {
        log.info("Starting hybrid match");

        // 1. 计算 RAG 语义相似度得分（已禁用）
        // float ragScore = calculateRAGScore(resumeContent, positionContent);
        // log.debug("RAG score: {}", ragScore);
        float ragScore = 0.0f; // RAG 已禁用，设为 0

        // 2. 从简历中提取技能（如果未提供）并计算技能匹配得分
        List<String> skills = candidateSkills;
        if (skills == null || skills.isEmpty()) {
            skills = skillGraphService.extractSkills(resumeContent);
        }
        SkillMatchResult skillMatch = skillGraphService.calculateSkillMatch(skills, requiredSkills);
        float graphScore = skillMatch.getScore() * 100;
        log.debug("Graph score: {}, matched: {}, missing: {}", 
                graphScore, skillMatch.getMatchedSkills().size(), skillMatch.getMissingSkills().size());

        // 3. 调用 LLM 进行综合评估（传入知识图谱结果和岗位 JD）
        LLMEvaluation llmEvaluation = evaluateWithLLM(resumeContent, positionContent, 
                skills, requiredSkills, skillMatch, graphScore, userId);
        float llmScore = llmEvaluation.score;
        log.debug("LLM score: {}", llmScore);

        // 4. 计算综合得分（仅使用知识图谱 + LLM）
        float finalScore = calculateFinalScore(graphScore, llmScore);

        // 5. 确定匹配等级和推荐指数
        String matchGrade = determineMatchGrade(finalScore);
        int recommendLevel = determineRecommendLevel(finalScore);

        // 6. 构建详细评分明细
        Map<String, Object> scoreDetails = buildScoreDetails(graphScore, llmScore, finalScore);

        return MatchResult.builder()
                .finalScore(finalScore)
                .ragScore(ragScore)
                .graphScore(graphScore)
                .llmScore(llmScore)
                .matchedSkills(skillMatch.getMatchedSkills())
                .missingSkills(skillMatch.getMissingSkills())
                .extraSkills(skillMatch.getExtraSkills())
                .llmReport(llmEvaluation.report)
                .scoreDetails(scoreDetails)
                .matchGrade(matchGrade)
                .recommendLevel(recommendLevel)
                .build();
    }

    /**
     * 计算 RAG 语义相似度得分（已禁用）
     */
    // private float calculateRAGScore(String resumeContent, String positionContent) {
    //     try {
    //         float similarity = ragService.calculateSimilarity(resumeContent, positionContent);
    //         // 将相似度转换为 0-100 分
    //         return Math.max(0, Math.min(100, similarity * 100));
    //     } catch (Exception e) {
    //         log.error("Failed to calculate RAG score: {}", e.getMessage());
    //         return 50.0f; // 默认中等分数
    //     }
    // }

    /**
     * 使用 LLM 进行综合评估
     * 将知识图谱匹配结果和岗位 JD 一并发送给 LLM 进行综合评分
     */
    private LLMEvaluation evaluateWithLLM(String resumeContent, String positionContent,
                                          List<String> candidateSkills, List<String> requiredSkills,
                                          SkillMatchResult skillMatch, float graphScore, Long userId) {
        try {
            String prompt = buildLLMPrompt(resumeContent, positionContent, 
                    candidateSkills, requiredSkills, skillMatch, graphScore);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你是一位专业的HR招聘专家，擅长评估候选人与岗位的匹配度。请根据提供的岗位JD、简历内容和知识图谱技能匹配分析结果，进行客观、专业的综合评估。"),
                    Map.of("role", "user", "content", prompt)
            );

            String response = modelRouter.route(userId).chat(messages);
            return parseLLMResponse(response);
        } catch (Exception e) {
            log.error("Failed to evaluate with LLM: {}", e.getMessage());
            return new LLMEvaluation(70.0f, "LLM 评估暂时不可用，已使用默认评分。");
        }
    }

    /**
     * 构建 LLM 评估 Prompt
     * 包含岗位 JD、简历内容、知识图谱技能匹配分析结果
     */
    private String buildLLMPrompt(String resumeContent, String positionContent,
                                   List<String> candidateSkills, List<String> requiredSkills,
                                   SkillMatchResult skillMatch, float graphScore) {
        return String.format("""
                请评估以下候选人与岗位的匹配程度。
                
                ## 岗位 JD（职位描述）
                %s
                
                ## 候选人简历
                %s
                
                ## 知识图谱技能匹配分析
                - 技能匹配度得分：%.1f/100
                - 匹配的技能：%s
                - 缺失的技能：%s
                - 额外的技能：%s
                
                ## 候选人技能列表
                %s
                
                ## 岗位要求技能列表
                %s
                
                请综合以上信息，从以下维度进行评估：
                1. 工作经验匹配度：候选人的工作经历是否符合岗位要求
                2. 技术能力匹配度：结合知识图谱分析，评估技能覆盖度和深度
                3. 职业发展潜力：候选人的成长轨迹和学习能力
                4. 综合胜任度：整体评估候选人是否能胜任该岗位
                
                请按以下格式返回：
                评分：[0-100的数字]
                评估报告：[200字以内的综合评估，需要结合岗位JD和知识图谱分析结果给出具体建议]
                """,
                truncateContent(positionContent, 1500),
                truncateContent(resumeContent, 2000),
                graphScore,
                skillMatch.getMatchedSkills().isEmpty() ? "无" : String.join(", ", skillMatch.getMatchedSkills()),
                skillMatch.getMissingSkills().isEmpty() ? "无" : String.join(", ", skillMatch.getMissingSkills()),
                skillMatch.getExtraSkills().isEmpty() ? "无" : String.join(", ", skillMatch.getExtraSkills()),
                candidateSkills.isEmpty() ? "无" : String.join(", ", candidateSkills),
                requiredSkills.isEmpty() ? "无" : String.join(", ", requiredSkills)
        );
    }

    /**
     * 解析 LLM 响应
     */
    private LLMEvaluation parseLLMResponse(String response) {
        float score = 70.0f; // 默认分数
        String report = response;

        try {
            // 尝试解析评分
            if (response.contains("评分：") || response.contains("评分:")) {
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (line.contains("评分") && (line.contains("：") || line.contains(":"))) {
                        String scoreStr = line.replaceAll("[^0-9.]", "");
                        if (!scoreStr.isEmpty()) {
                            score = Float.parseFloat(scoreStr);
                            score = Math.max(0, Math.min(100, score));
                        }
                        break;
                    }
                }
            }

            // 提取评估报告
            if (response.contains("评估报告：") || response.contains("评估报告:")) {
                int idx = response.indexOf("评估报告");
                if (idx >= 0) {
                    report = response.substring(idx).replaceFirst("评估报告[：:]\\s*", "").trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
        }

        return new LLMEvaluation(score, report);
    }

    /**
     * 计算综合得分（仅使用知识图谱 + LLM）
     */
    private float calculateFinalScore(float graphScore, float llmScore) {
        return graphScore * GRAPH_WEIGHT + llmScore * LLM_WEIGHT;
    }

    /**
     * 确定匹配等级
     */
    private String determineMatchGrade(float score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        return "D";
    }

    /**
     * 确定推荐指数
     */
    private int determineRecommendLevel(float score) {
        if (score >= 90) return 5;
        if (score >= 75) return 4;
        if (score >= 60) return 3;
        if (score >= 45) return 2;
        return 1;
    }

    /**
     * 构建评分明细（去掉 RAG，仅包含知识图谱 + LLM）
     */
    private Map<String, Object> buildScoreDetails(float graphScore, float llmScore, float finalScore) {
        Map<String, Object> details = new LinkedHashMap<>();
        
        // RAG 已禁用，不再显示
        // details.put("ragScore", Map.of(
        //         "value", ragScore,
        //         "weight", RAG_WEIGHT,
        //         "weighted", ragScore * RAG_WEIGHT,
        //         "description", "语义相似度得分：基于向量检索计算简历与JD的语义匹配程度"
        // ));
        
        details.put("graphScore", Map.of(
                "value", graphScore,
                "weight", GRAPH_WEIGHT,
                "weighted", graphScore * GRAPH_WEIGHT,
                "description", "技能匹配得分：基于知识图谱分析技能匹配度和关联性"
        ));
        
        details.put("llmScore", Map.of(
                "value", llmScore,
                "weight", LLM_WEIGHT,
                "weighted", llmScore * LLM_WEIGHT,
                "description", "LLM综合评估：大模型结合岗位JD和知识图谱分析对候选人综合能力的智能评估"
        ));
        
        details.put("finalScore", finalScore);
        details.put("formula", "最终得分 = Graph×0.5 + LLM×0.5");
        
        return details;
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 批量匹配：一份简历匹配多个岗位
     */
    public List<MatchResult> matchResumeToPositions(String resumeContent, 
                                                     List<PositionInfo> positions, 
                                                     int topK) {
        List<MatchResult> results = new ArrayList<>();
        
        for (PositionInfo position : positions) {
            MatchResult result = match(resumeContent, null, 
                    position.content, position.skills, null);
            result.setPositionId(position.id);
            results.add(result);
        }
        
        // 按综合得分排序并返回 TopK
        results.sort((a, b) -> Float.compare(b.getFinalScore(), a.getFinalScore()));
        
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * 批量匹配：一个岗位匹配多份简历
     */
    public List<MatchResult> matchPositionToResumes(String positionContent, 
                                                     List<String> requiredSkills,
                                                     List<ResumeInfo> resumes, 
                                                     int topK) {
        List<MatchResult> results = new ArrayList<>();
        
        for (ResumeInfo resume : resumes) {
            MatchResult result = match(resume.content, null, 
                    positionContent, requiredSkills, null);
            result.setResumeId(resume.id);
            results.add(result);
        }
        
        // 按综合得分排序并返回 TopK
        results.sort((a, b) -> Float.compare(b.getFinalScore(), a.getFinalScore()));
        
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * LLM 评估结果
     */
    private record LLMEvaluation(float score, String report) {}

    /**
     * 岗位信息（用于批量匹配）
     */
    public record PositionInfo(String id, String content, List<String> skills) {}

    /**
     * 简历信息（用于批量匹配）
     */
    public record ResumeInfo(String id, String content) {}
}

