/**
 * 面试题生成服务
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.service.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthr.dto.interview.GenerateQuestionsRequest;
import com.smarthr.dto.interview.InterviewQuestionDTO;
import com.smarthr.dto.interview.InterviewRecordDTO;
import com.smarthr.entity.InterviewRecord;
import com.smarthr.entity.Position;
import com.smarthr.exception.BusinessException;
import com.smarthr.repository.InterviewRecordRepository;
import com.smarthr.repository.PositionRepository;
import com.smarthr.service.ai.ModelRouter;
import com.smarthr.service.rag.EmbeddingService;
import com.smarthr.service.vector.QuestionBankVectorStore;
import com.smarthr.service.vector.VectorDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionService {

    private final ModelRouter modelRouter;
    private final PositionRepository positionRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final ObjectMapper objectMapper;
    private final QuestionBankService questionBankService;
    private final EmbeddingService embeddingService;
    private final Optional<QuestionBankVectorStore> questionBankVectorStore;

    /**
     * 根据请求生成面试题
     */
    @Transactional
    public InterviewRecordDTO generateQuestions(GenerateQuestionsRequest request, Long userId) {
        log.info("Generating interview questions for user {}", userId);

        // 补齐默认值，避免空值导致提示构造异常
        if (request.getDifficulty() == null) {
            request.setDifficulty("MIDDLE");
        }
        if (request.getCount() == null) {
            request.setCount(5);
        }
        if (request.getQuestionType() == null) {
            request.setQuestionType("MIXED");
        }
        if (request.getIncludeAnswers() == null) {
            request.setIncludeAnswers(true);
        }
        if (request.getBusinessDomain() == null) {
            request.setBusinessDomain("企业金融/支付");
        }

        // 获取技能列表
        List<String> skills = getSkillsForGeneration(request);
        if (skills.isEmpty()) {
            throw new BusinessException("请指定岗位或技能列表");
        }

        // 调用 AI 生成面试题
        List<InterviewQuestionDTO> questions = generateWithAI(skills, request, userId);

        // 保存记录
        InterviewRecord record = saveRecord(request, questions, userId);

        // 获取岗位标题
        String positionTitle = null;
        if (request.getPositionId() != null) {
            positionTitle = positionRepository.findByIdAndDeletedFalse(request.getPositionId())
                    .map(Position::getTitle)
                    .orElse(null);
        }

        return toDTO(record, questions, positionTitle);
    }

    /**
     * 根据岗位 ID 快速生成面试题
     */
    @Transactional
    public InterviewRecordDTO generateByPosition(Long positionId, String difficulty, Integer count, Long userId) {
        GenerateQuestionsRequest request = new GenerateQuestionsRequest();
        request.setPositionId(positionId);
        request.setDifficulty(difficulty != null ? difficulty : "MIDDLE");
        request.setCount(count != null ? count : 5);
        request.setQuestionType("MIXED");
        request.setIncludeAnswers(true);
        
        return generateQuestions(request, userId);
    }

    /**
     * 根据技能列表快速生成面试题
     */
    @Transactional
    public InterviewRecordDTO generateBySkills(List<String> skills, String difficulty, Integer count, Long userId) {
        GenerateQuestionsRequest request = new GenerateQuestionsRequest();
        request.setSkills(skills);
        request.setDifficulty(difficulty != null ? difficulty : "MIDDLE");
        request.setCount(count != null ? count : 5);
        request.setQuestionType("TECHNICAL");
        request.setIncludeAnswers(true);
        
        return generateQuestions(request, userId);
    }

    /**
     * 获取面试记录详情
     */
    public InterviewRecordDTO getRecord(Long id) {
        InterviewRecord record = interviewRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        
        List<InterviewQuestionDTO> questions = convertToQuestionDTOs(record.getQuestions());
        String positionTitle = null;
        if (record.getPositionId() != null) {
            positionTitle = positionRepository.findById(record.getPositionId())
                    .map(Position::getTitle)
                    .orElse(null);
        }
        
        return toDTO(record, questions, positionTitle);
    }

    /**
     * 分页查询面试记录
     */
    public Page<InterviewRecordDTO> listRecords(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InterviewRecord> records = interviewRecordRepository.findByUserId(userId, pageable);
        
        return records.map(record -> {
            List<InterviewQuestionDTO> questions = convertToQuestionDTOs(record.getQuestions());
            String positionTitle = null;
            if (record.getPositionId() != null) {
                positionTitle = positionRepository.findById(record.getPositionId())
                        .map(Position::getTitle)
                        .orElse(null);
            }
            return toDTO(record, questions, positionTitle);
        });
    }

    /**
     * 删除面试记录
     */
    @Transactional
    public void deleteRecord(Long id, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此记录");
        }
        
        interviewRecordRepository.deleteById(id);
        log.info("Deleted interview record {} by user {}", id, userId);
    }

    /**
     * 将指定题目入库到向量题库
     */
    @Transactional
    public void approveQuestion(Long recordId, int questionIndex, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }

        List<Map<String, Object>> questions = record.getQuestions();
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw new BusinessException("题目序号无效");
        }

        Map<String, Object> question = questions.get(questionIndex);

        if ("APPROVED".equals(getStringValue(question, "status"))
                && question.containsKey("milvusDocId")) {
            log.info("Question {}/{} already in bank, skipping", recordId, questionIndex);
            return;
        }

        String milvusDocId = writeToVectorStore(question);
        if (milvusDocId != null) {
            question.put("milvusDocId", milvusDocId);
        }
        question.put("status", "APPROVED");
        record.setQuestions(questions);
        interviewRecordRepository.save(record);

        log.info("Question {}/{} approved to bank by user {}", recordId, questionIndex, userId);
    }

    /**
     * 弃用指定题目
     */
    @Transactional
    public void rejectQuestion(Long recordId, int questionIndex, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }

        List<Map<String, Object>> questions = record.getQuestions();
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw new BusinessException("题目序号无效");
        }

        Map<String, Object> question = questions.get(questionIndex);
        question.put("status", "REJECTED");
        record.setQuestions(questions);
        interviewRecordRepository.save(record);

        log.info("Question {}/{} rejected by user {}", recordId, questionIndex, userId);
    }

    /**
     * 取消入库：从向量题库移除并恢复状态为 DRAFT
     */
    @Transactional
    public void unapproveQuestion(Long recordId, int questionIndex, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }

        List<Map<String, Object>> questions = record.getQuestions();
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw new BusinessException("题目序号无效");
        }

        Map<String, Object> question = questions.get(questionIndex);
        if (!"APPROVED".equals(getStringValue(question, "status"))) {
            throw new BusinessException("该题目未入库，无法取消入库");
        }

        String milvusDocId = getStringValue(question, "milvusDocId");
        if (milvusDocId != null && questionBankVectorStore.isPresent()) {
            try {
                questionBankVectorStore.get().deleteDocument(milvusDocId);
                log.info("Deleted question doc from Milvus: {}", milvusDocId);
            } catch (Exception e) {
                log.error("Failed to delete from Milvus: {}", e.getMessage(), e);
            }
        } else if (milvusDocId == null) {
            log.warn("Question {}/{} has no milvusDocId, cannot delete from vector store", recordId, questionIndex);
        }

        question.put("status", "DRAFT");
        question.remove("milvusDocId");
        record.setQuestions(questions);
        interviewRecordRepository.save(record);
        log.info("Question {}/{} unapproved from bank by user {}", recordId, questionIndex, userId);
    }

    /**
     * 批量入库：将多条题目同时写入向量题库
     */
    @Transactional
    public void batchApproveQuestions(Long recordId, List<Integer> indices, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }

        List<Map<String, Object>> questions = record.getQuestions();
        if (questions == null) {
            throw new BusinessException("该记录无题目");
        }

        for (int index : indices) {
            if (index < 0 || index >= questions.size()) {
                throw new BusinessException("题目序号无效: " + index);
            }
        }

        int successCount = 0;
        for (int index : indices) {
            Map<String, Object> question = questions.get(index);

            if ("APPROVED".equals(getStringValue(question, "status"))
                    && question.containsKey("milvusDocId")) {
                log.info("Question {}/{} already in bank, skipping", recordId, index);
                continue;
            }

            String milvusDocId = writeToVectorStore(question);
            if (milvusDocId != null) {
                question.put("milvusDocId", milvusDocId);
            }
            question.put("status", "APPROVED");
            successCount++;
        }

        record.setQuestions(questions);
        interviewRecordRepository.save(record);
        log.info("Batch approved {}/{} questions for record {} by user {}",
                successCount, indices.size(), recordId, userId);
    }

    /**
     * 批量取消入库：将多条题目从向量题库移除并恢复为 DRAFT
     */
    @Transactional
    public void batchUnapproveQuestions(Long recordId, List<Integer> indices, Long userId) {
        InterviewRecord record = interviewRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("面试记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此记录");
        }

        List<Map<String, Object>> questions = record.getQuestions();
        if (questions == null) {
            throw new BusinessException("该记录无题目");
        }

        for (int index : indices) {
            if (index < 0 || index >= questions.size()) {
                throw new BusinessException("题目序号无效: " + index);
            }
            if (!"APPROVED".equals(getStringValue(questions.get(index), "status"))) {
                throw new BusinessException("序号 " + index + " 的题目未入库，无法取消入库");
            }
        }

        List<String> docIdsToDelete = new ArrayList<>();
        for (int index : indices) {
            String milvusDocId = getStringValue(questions.get(index), "milvusDocId");
            if (milvusDocId != null) {
                docIdsToDelete.add(milvusDocId);
            }
        }

        if (!docIdsToDelete.isEmpty() && questionBankVectorStore.isPresent()) {
            try {
                questionBankVectorStore.get().deleteDocuments(docIdsToDelete);
                log.info("Batch deleted {} question docs from Milvus", docIdsToDelete.size());
            } catch (Exception e) {
                log.error("Failed to batch delete from Milvus: {}", e.getMessage(), e);
            }
        }

        for (int index : indices) {
            Map<String, Object> question = questions.get(index);
            question.put("status", "DRAFT");
            question.remove("milvusDocId");
        }
        record.setQuestions(questions);
        interviewRecordRepository.save(record);
        log.info("Batch unapproved {} questions for record {} by user {}", indices.size(), recordId, userId);
    }

    /**
     * 将题目写入 Milvus 向量题库
     */
    private String writeToVectorStore(Map<String, Object> question) {
        if (questionBankVectorStore.isEmpty()) {
            log.warn("Question bank Milvus unavailable, skip vector write");
            return null;
        }
        try {
            String questionText = getStringValue(question, "question");
            String answer = getStringValue(question, "answerPoints");
            String skill = getStringValue(question, "skill");
            String domain = getStringValue(question, "domain");

            String embedText = String.format("Smart-HR 科技 面试题 %s 企业金融/支付 %s %s 答案要点: %s",
                    skill != null ? skill : "",
                    questionText != null ? questionText : "",
                    domain != null ? domain : "企业金融/支付",
                    answer != null ? answer : "");

            float[] embedding = embeddingService.embed(embedText);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("question", questionText);
            metadata.put("answer", answer != null ? answer : "");
            metadata.put("difficulty", getStringValue(question, "difficulty"));
            metadata.put("type", getStringValue(question, "type"));
            metadata.put("domain", domain != null ? domain : "企业金融/支付");
            metadata.put("updatedAt", LocalDateTime.now().toString());

            String docId = UUID.randomUUID().toString();
            VectorDocument doc = VectorDocument.builder()
                    .id(docId)
                    .content(questionText)
                    .embedding(embedding)
                    .metadata(metadata)
                    .build();

            questionBankVectorStore.get().addDocument(doc);
            log.info("Question written to vector store: {}", docId);
            return docId;
        } catch (Exception e) {
            log.error("Failed to write question to vector store: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取用于生成的技能列表
     */
    private List<String> getSkillsForGeneration(GenerateQuestionsRequest request) {
        List<String> skills = new ArrayList<>();
        
        // 从岗位获取技能
        if (request.getPositionId() != null) {
            Position position = positionRepository.findByIdAndDeletedFalse(request.getPositionId())
                    .orElseThrow(() -> new BusinessException("岗位不存在或已删除"));
            if (position.getSkills() != null) {
                skills.addAll(position.getSkills());
            }
        }
        
        // 添加请求中指定的技能
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            skills.addAll(request.getSkills());
        }
        
        // 去重
        return skills.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 调用 AI 生成面试题
     */
    private List<InterviewQuestionDTO> generateWithAI(List<String> skills,
                                                       GenerateQuestionsRequest request,
                                                       Long userId) {
        String businessDomain = request.getBusinessDomain() != null ? request.getBusinessDomain() : "企业金融/支付";

        List<QuestionBankEntry> kbContext = questionBankService.searchSimilarQuestions(
                skills,
                request.getDifficulty(),
                request.getQuestionType(),
                businessDomain,
                8
        );

        String prompt = buildPrompt(skills, request, questionBankService.formatContext(kbContext));
        
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "你是 Smart-HR 科技的面试官助手，行业=企业金融/支付。"
                                + "需优先复用/改写公司内部题库，保持题干中体现公司名或“我们公司”"
                                + "请仅输出 JSON 数组。"),
                Map.of("role", "user", "content", prompt)
        );
        
        try {
            String response = modelRouter.route(userId).chat(messages);
            return parseQuestions(response, Boolean.TRUE.equals(request.getIncludeAnswers()));
        } catch (Exception e) {
            log.error("Failed to generate questions with AI: {}", e.getMessage());
            // 返回预设的默认题目
            return generateDefaultQuestions(skills, request.getCount());
        }
    }

    /**
     * 构建生成题目的 Prompt
     */
    private String buildPrompt(List<String> skills, GenerateQuestionsRequest request, String kbContext) {
        String difficulty = request.getDifficulty().toUpperCase();
        String difficultyDesc = switch (difficulty) {
            case "JUNIOR" -> "初级（0-3年经验）";
            case "SENIOR" -> "高级（5年以上经验）";
            default -> "中级（3-5年经验）";
        };

        String typeDesc = switch (request.getQuestionType().toUpperCase()) {
            case "TECHNICAL" -> "技术题（概念、原理、算法等纯知识问答）";
            case "BEHAVIORAL" -> "行为题（过往经历、团队协作）";
            case "SCENARIO" -> "情景题（假设业务场景，给出解决方案）";
            default -> "混合题型（技术题、行为题、情景题均衡分布）";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("请为以下技能栈生成面试题：\n\n");
        sb.append("## 技能要求\n");
        sb.append(String.join(", ", skills)).append("\n\n");
        sb.append("## 公司与业务背景\n");
        sb.append("- 公司：Smart-HR 科技，行业=企业金融/支付（企业账户、付款/收款、对账、清结算、限额、风控、幂等与重试、通道路由等）\n");
        sb.append("- 主业务域：").append(request.getBusinessDomain() != null ? request.getBusinessDomain() : "企业金融/支付").append("\n\n");

        sb.append("## 内部题库命中（优先复用/改写，保持公司名）：\n");
        sb.append(kbContext).append("\n");
        sb.append("- 若未命中或相关度低，请生成贴合上述业务背景的新题，并在输出中保持公司名。\n\n");

        sb.append("## 题型区分（严格遵循）\n");
        sb.append("- 技术题：纯知识问答，直接问概念、原理、算法。题干中不要出现具体业务场景、不要提公司名。\n");
        sb.append("  正确示例：「MySQL 的 MVCC 机制是如何实现的？」「Redis 的过期删除策略有哪些？」\n");
        sb.append("  错误示例：「在 Smart-HR 科技的对账场景中，MySQL 如何保证事务一致性？请写出 SQL。」\n");
        sb.append("- 情景题：嵌入具体业务场景，描述一个实际问题和约束条件，让候选人设计或实现方案。题干中可出现公司名和业务细节。\n");
        sb.append("  正确示例：「Smart-HR 科技日均对账100万笔，MySQL 单表查询变慢，请设计方案解决。」\n\n");

        sb.append("## 生成要求\n");
        sb.append("- 难度级别：").append(difficultyDesc).append("\n");
        sb.append("- 题目数量：").append(request.getCount()).append(" 道\n");
        sb.append("- 题目类型：").append(typeDesc).append("\n");
        sb.append("- 技术题不要提公司名；情景题/行为题若自然涉及公司可提「Smart-HR 科技」或「我们公司」。\n");
        sb.append("- 优先沿用/改写上方题库的题目/答案要点；未命中时再生成新题，并保持业务一致。\n");

        sb.append(buildDifficultyConstraints(difficulty));

        if (Boolean.TRUE.equals(request.getIncludeAnswers())) {
            sb.append("- 需要包含参考答案要点\n");
        }

        sb.append("\n## 输出格式\n");
        sb.append("请以JSON数组格式返回，每个题目包含以下字段：\n");
        sb.append("```json\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"question\": \"题目内容\",\n");
        sb.append("    \"type\": \"TECHNICAL/BEHAVIORAL/SCENARIO\",\n");
        sb.append("    \"difficulty\": \"JUNIOR/MIDDLE/SENIOR\",\n");
        sb.append("    \"skill\": \"相关技能\",\n");
        if (Boolean.TRUE.equals(request.getIncludeAnswers())) {
            sb.append("    \"answerPoints\": \"参考答案要点\",\n");
        }
        sb.append("    \"evaluationDimension\": \"评估维度\"\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("```\n");
        sb.append("\n只返回JSON数组，不要有其他内容。");

        return sb.toString();
    }

    private String buildDifficultyConstraints(String difficulty) {
        switch (difficulty) {
            case "JUNIOR":
                return "\n- 【初级难度约束，请严格遵循】\n"
                    + "  * 只考察基础概念、常用 API 和简单应用场景。\n"
                    + "  * 题干简短直白，用一句话说清场景即可，不要堆砌复杂背景\n"
                    + "  * 参考答案只需判断候选人「是否理解这个基础概念」，不要求深入展开\n"
                    + "  * 如果出技术题，聚焦在「是什么、怎么用」，不要问「为什么这么设计、如何优化」\n"
                    + "  * 如果出行为题，聚焦在「你遇到过什么问题、怎么解决的」，场景贴近日常工作\n";
            case "SENIOR":
                return "\n- 【高级难度约束】\n"
                    + "  * 考察架构设计、深度原理、故障排查和技术选型\n"
                    + "  * 题干可包含复杂分布式场景和 trade-off 权衡\n"
                    + "  * 参考答案需体现方案完整性和多维度思考\n"
                    + "  * 可以涉及：分布式系统设计、源码级理解、大规模调优、团队技术规划\n";
            default:
                return "\n- 【中级难度约束】\n"
                    + "  * 考察原理理解、常见方案选型和简单设计能力\n"
                    + "  * 题干可包含具体业务场景，但不要过度复杂\n"
                    + "  * 参考答案以「能否讲清原理并给出合理方案」为标准\n"
                    + "  * 可以涉及：框架核心原理、中间件使用场景、常见设计模式、基础性能优化\n";
        }
    }

    /**
     * 解析 AI 返回的题目
     */
    private List<InterviewQuestionDTO> parseQuestions(String response, boolean includeAnswers) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        
        try {
            // 提取 JSON 数组
            String jsonStr = extractJson(response);
            
            List<Map<String, Object>> questionMaps = objectMapper.readValue(
                    jsonStr, new TypeReference<List<Map<String, Object>>>() {});
            
            for (Map<String, Object> map : questionMaps) {
                InterviewQuestionDTO dto = InterviewQuestionDTO.builder()
                        .question(getStringValue(map, "question"))
                        .type(getStringValue(map, "type"))
                        .difficulty(getStringValue(map, "difficulty"))
                        .skill(getStringValue(map, "skill"))
                        .answerPoints(includeAnswers ? getStringValue(map, "answerPoints") : null)
                        .evaluationDimension(getStringValue(map, "evaluationDimension"))
                        .build();
                questions.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
        }
        
        return questions;
    }

    /**
     * 提取 JSON 内容
     */
    private String extractJson(String response) {
        // 尝试找到 JSON 数组
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return response;
    }

    /**
     * 安全获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 生成默认题目（AI 失败时的兜底）
     */
    private List<InterviewQuestionDTO> generateDefaultQuestions(List<String> skills, int count) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        
        String[] templates = {
                "在 Smart-HR 科技的企业支付场景下，如何使用 %s 实现高并发下的幂等与重试？",
                "Smart-HR 科技的清结算批处理需要用到 %s，如何拆分长事务并保证数据一致性？",
                "使用 %s 设计企业账户的限额与风控规则时，你会怎样实现实时统计与穿透防护？",
                "Smart-HR 科技在跨通道路由时，如何用 %s 设计可灰度、可回滚的路由策略？",
                "结合 %s，描述一次在企业支付/对账场景中排查生产事故的过程。"
        };
        
        for (int i = 0; i < Math.min(count, skills.size() * templates.length); i++) {
            String skill = skills.get(i % skills.size());
            String template = templates[i % templates.length];
            
            questions.add(InterviewQuestionDTO.builder()
                    .question(String.format(template, skill))
                    .type("TECHNICAL")
                    .difficulty("MIDDLE")
                    .skill(skill)
                    .evaluationDimension("技术深度与实践经验")
                    .build());
        }
        
        return questions;
    }

    /**
     * 保存面试记录
     */
    private InterviewRecord saveRecord(GenerateQuestionsRequest request, 
                                        List<InterviewQuestionDTO> questions, 
                                        Long userId) {
        List<Map<String, Object>> questionMaps = questions.stream()
                .map(q -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("question", q.getQuestion());
                    map.put("type", q.getType());
                    map.put("difficulty", q.getDifficulty());
                    map.put("skill", q.getSkill());
                    map.put("answerPoints", q.getAnswerPoints());
                    map.put("evaluationDimension", q.getEvaluationDimension());
                    map.put("status", "DRAFT");
                    map.put("domain", request.getBusinessDomain());
                    return map;
                })
                .collect(Collectors.toList());

        InterviewRecord record = InterviewRecord.builder()
                .positionId(request.getPositionId())
                .userId(userId)
                .difficulty(request.getDifficulty())
                .questionType(request.getQuestionType())
                .questions(questionMaps)
                .createdAt(LocalDateTime.now())
                .build();

        return interviewRecordRepository.save(record);
    }

    /**
     * 将存储的题目转换为 DTO
     */
    private List<InterviewQuestionDTO> convertToQuestionDTOs(List<Map<String, Object>> questions) {
        if (questions == null) {
            return Collections.emptyList();
        }
        
        return questions.stream()
                .map(map -> InterviewQuestionDTO.builder()
                        .question(getStringValue(map, "question"))
                        .type(getStringValue(map, "type"))
                        .difficulty(getStringValue(map, "difficulty"))
                        .skill(getStringValue(map, "skill"))
                        .answerPoints(getStringValue(map, "answerPoints"))
                        .evaluationDimension(getStringValue(map, "evaluationDimension"))
                        .status(getStringValue(map, "status"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 转换为 DTO
     */
    private InterviewRecordDTO toDTO(InterviewRecord record, List<InterviewQuestionDTO> questions, 
                                      String positionTitle) {
        return InterviewRecordDTO.builder()
                .id(record.getId())
                .positionId(record.getPositionId())
                .positionTitle(positionTitle)
                .userId(record.getUserId())
                .difficulty(record.getDifficulty())
                .questionType(record.getQuestionType())
                .questions(questions)
                .createdAt(record.getCreatedAt())
                .build();
    }
}
