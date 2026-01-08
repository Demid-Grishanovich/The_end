package com.datacrowd.core.dto;

import jakarta.validation.constraints.NotBlank;

public class SubmitTaskRequest {
    @NotBlank(message = "answerJson must not be blank")
    public String answerJson;
}
