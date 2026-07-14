/**
 * 技能知识图谱控制器
 * 提供技能搜索、分类浏览、学习路径和技能推荐等接口
 *
 * @author Javanewhand
 * @date 2026/01/09
 */
package com.smarthr.controller.hr;

import com.smarthr.dto.ApiResponse;
import com.smarthr.entity.SkillNode;
import com.smarthr.service.graph.SkillGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/hr/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillGraphService skillGraphService;

    /**
     * 搜索或全量查询技能列表
     * 传入 keyword 时按名称和关键词模糊匹配，不传则返回全部
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")//访问权限控制，下面类似
    public ResponseEntity<ApiResponse<List<SkillNode>>> searchSkills(
            @RequestParam(required = false) String keyword) {
        List<SkillNode> skills;
        if (keyword != null && !keyword.isEmpty()) {
            skills = skillGraphService.searchSkills(keyword);
        } else {
            skills = skillGraphService.getAllSkills();
        }
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    /**
     * 获取知识图谱中技能节点的总数
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<Long>> getSkillCount() {
        long count = skillGraphService.getSkillCount();
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * 获取技能详情
     * 返回技能的基本信息及其前置依赖（REQUIRES）和相关技能（RELATED_TO）关系
     */
    @GetMapping("/{skillName}")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<SkillNode>> getSkillDetail(@PathVariable String skillName) {
        Optional<SkillNode> skill = skillGraphService.getSkillDetail(skillName);
        return skill.map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElse(ResponseEntity.ok(ApiResponse.notFound("技能不存在: " + skillName)));
    }

    /**
     * 按八大分类获取技能列表
     * 返回 Map<分类编码, 技能名称列表>，用于前端分类 Tab 展示
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getSkillsByCategory() {
        Map<String, List<String>> categories = skillGraphService.getSkillsByCategory();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * 获取技能学习路径
     * 从目标技能出发，沿 REQUIRES 关系逆向查询最多 5 层依赖树，按技能等级升序排列
     */
    @GetMapping("/{skillName}/learning-path")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<List<String>>> getLearningPath(@PathVariable String skillName) {
        List<String> path = skillGraphService.getSkillLearningPath(skillName);
        return ResponseEntity.ok(ApiResponse.success(path));
    }

    /**
     * 根据当前技能和目标技能推荐学习清单
     * 对每个未掌握的目标技能，沿 REQUIRES 关系链找出缺失的前置依赖一并纳入推荐
     */
    @PostMapping("/recommend")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<List<String>>> recommendSkillsToLearn(
            @RequestBody Map<String, List<String>> request) {
        List<String> currentSkills = request.getOrDefault("currentSkills", List.of());
        List<String> targetSkills = request.getOrDefault("targetSkills", List.of());
        List<String> toLearn = skillGraphService.recommendSkillsToLearn(currentSkills, targetSkills);
        return ResponseEntity.ok(ApiResponse.success(toLearn));
    }
}
