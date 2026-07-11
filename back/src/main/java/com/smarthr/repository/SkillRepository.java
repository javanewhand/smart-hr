/**
 * 技能节点数据访问接口
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.repository;

import com.smarthr.entity.SkillNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 注意：由于其中关系非常简单，即关系里没有任何属性，所以将节点与关系用的是同一个类，即都用的skillNode类
 */
@Repository
public interface SkillRepository extends Neo4jRepository<SkillNode, String> {

    /**
     * 根据技能名称查找
     */
    Optional<SkillNode> findByName(String name);

    /**
     * 根据关键词模糊查找
     */
    @Query("MATCH (s:Skill) WHERE s.name CONTAINS $keyword OR ANY(k IN s.keywords WHERE k CONTAINS $keyword) RETURN s")
    List<SkillNode> findByKeyword(String keyword);

    /**
     * 查找技能的前置依赖
     */
    @Query("MATCH (s:Skill {name: $skillName})-[:REQUIRES]->(prereq:Skill) RETURN prereq")
    List<SkillNode> findPrerequisites(String skillName);

    /**
     * 查找技能的后续技能（以此技能为前置的技能）
     */
    @Query("MATCH (s:Skill {name: $skillName})<-[:REQUIRES]-(advanced:Skill) RETURN advanced")
    List<SkillNode> findAdvancedSkills(String skillName);

    /**
     * 查找相关技能
     */
    @Query("MATCH (s:Skill {name: $skillName})-[:RELATED_TO]-(related:Skill) RETURN related")
    List<SkillNode> findRelatedSkills(String skillName);

    /**
     * 查找某分类下的所有技能
     */
    @Query("MATCH (s:Skill)-[:BELONGS_TO]->(c:SkillCategory {code: $categoryCode}) RETURN s")
    List<SkillNode> findByCategory(String categoryCode);

    /**
     * 查找技能的完整依赖树（深度优先，最多 5 层）
     */
    @Query("""
            MATCH path = (s:Skill {name: $skillName})-[:REQUIRES*0..5]->(prereq:Skill)
            RETURN prereq
            """)
    List<SkillNode> findDependencyTree(String skillName);

    /**
     * 计算两个技能之间的路径距离
     */
    @Query("""
            MATCH path = shortestPath((s1:Skill {name: $skill1})-[:REQUIRES|RELATED_TO*]-(s2:Skill {name: $skill2}))
            RETURN length(path) as distance
            """)
    Integer findDistanceBetweenSkills(String skill1, String skill2);

    /**
     * 查找所有技能
     */
    @Query("MATCH (s:Skill) RETURN s")
    List<SkillNode> findAllSkills();

    /**
     * 统计技能总数
     */
    @Query("MATCH (s:Skill) RETURN count(s)")
    Long countSkills();
}


