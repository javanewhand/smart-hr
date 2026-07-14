package com.smarthr.dto.interview;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchQuestionIndicesRequest {

    @NotEmpty(message = "题目序号列表不能为空")
    private List<Integer> indices;
}
