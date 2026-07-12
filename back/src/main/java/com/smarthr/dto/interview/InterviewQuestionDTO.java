/**
 * 面试题 DTO
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionDTO {

    /**
     * 题目内容
     */
    private String question;

    /**
     * 题目类型（技术题/行为题/情景题）
     */
    private String type;

    /**
     * 难度等级（初级/中级/高级）
     */
    private String difficulty;

    /**
     * 相关技能
     */
    private String skill;

    /**
     * 参考答案要点
     */
    private String answerPoints;

    /**
     * 评估维度
     */
    private String evaluationDimension;

    /**
     * 题目状态：DRAFT（默认）/ APPROVED（已入库）/ REJECTED（已弃用）
     */
    private String status;
}


