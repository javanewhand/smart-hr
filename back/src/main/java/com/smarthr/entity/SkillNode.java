package com.smarthr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Node("Skill")
public class SkillNode {

    @Id
    private String name;

    @Property("level")
    private Integer level;

    @Property("description")
    private String description;

    @Property("keywords")
    private List<String> keywords;

    @Relationship(type = "REQUIRES", direction = Relationship.Direction.OUTGOING)
    private List<SkillNode> requires;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<SkillNode> relatedTo;
}
