/**
 * 面试题生成控制器
 *
 * @author QinFeng Luo
 * @date 2026/01/12
 */
package com.smarthr.controller.interview;

import com.smarthr.dto.ApiResponse;
import com.smarthr.dto.interview.GenerateQuestionsRequest;
import com.smarthr.dto.interview.InterviewRecordDTO;
import com.smarthr.security.UserPrincipal;
import com.smarthr.service.interview.InterviewQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERVIEWER')")
public class InterviewController {

    private final InterviewQuestionService interviewQuestionService;

    /**
     * 生成面试题（完整参数）
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<InterviewRecordDTO>> generateQuestions(
            @Valid @RequestBody GenerateQuestionsRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        InterviewRecordDTO result = interviewQuestionService.generateQuestions(request, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "面试题生成成功"));
    }

    /**
     * 根据岗位快速生成面试题
     */
    @PostMapping("/generate/position/{positionId}")
    public ResponseEntity<ApiResponse<InterviewRecordDTO>> generateByPosition(
            @PathVariable Long positionId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false, defaultValue = "5") Integer count,
            @AuthenticationPrincipal UserPrincipal user) {
        InterviewRecordDTO result = interviewQuestionService.generateByPosition(
                positionId, difficulty, count, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "面试题生成成功"));
    }

    /**
     * 根据技能列表快速生成面试题
     */
    @PostMapping("/generate/skills")
    public ResponseEntity<ApiResponse<InterviewRecordDTO>> generateBySkills(
            @RequestBody List<String> skills,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false, defaultValue = "5") Integer count,
            @AuthenticationPrincipal UserPrincipal user) {
        InterviewRecordDTO result = interviewQuestionService.generateBySkills(
                skills, difficulty, count, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "面试题生成成功"));
    }

    /**
     * 获取面试记录详情
     */
    @GetMapping("/records/{id}")
    public ResponseEntity<ApiResponse<InterviewRecordDTO>> getRecord(@PathVariable Long id) {
        InterviewRecordDTO result = interviewQuestionService.getRecord(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 分页查询我的面试记录
     */
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<Page<InterviewRecordDTO>>> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal user) {
        Page<InterviewRecordDTO> records = interviewQuestionService.listRecords(user.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    /**
     * 删除面试记录
     */
    @DeleteMapping("/records/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {
        interviewQuestionService.deleteRecord(id, user.getId());
        return ResponseEntity.ok(ApiResponse.successMessage("面试记录删除成功"));
    }

    /**
     * 题目入库
     */
    @PostMapping("/records/{id}/questions/{index}/approve")
    public ResponseEntity<ApiResponse<Void>> approveQuestion(
            @PathVariable Long id,
            @PathVariable int index,
            @AuthenticationPrincipal UserPrincipal user) {
        interviewQuestionService.approveQuestion(id, index, user.getId());
        return ResponseEntity.ok(ApiResponse.successMessage("题目已入库"));
    }

    /**
     * 弃用题目
     */
    @PostMapping("/records/{id}/questions/{index}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectQuestion(
            @PathVariable Long id,
            @PathVariable int index,
            @AuthenticationPrincipal UserPrincipal user) {
        interviewQuestionService.rejectQuestion(id, index, user.getId());
        return ResponseEntity.ok(ApiResponse.successMessage("题目已弃用"));
    }
}


