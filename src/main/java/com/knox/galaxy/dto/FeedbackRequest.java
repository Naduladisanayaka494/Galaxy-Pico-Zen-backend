package com.knox.galaxy.dto;

import com.knox.galaxy.model.FeedbackType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** The Help page's feedback form (§14.3). */
@Data
public class FeedbackRequest {

    @NotNull(message = "Choose a feedback type")
    private FeedbackType type;

    @NotBlank(message = "Please enter a message")
    @Size(max = 4000)
    private String message;
}
