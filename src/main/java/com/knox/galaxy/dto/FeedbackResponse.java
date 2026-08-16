package com.knox.galaxy.dto;

import com.knox.galaxy.model.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {
    private Long id;
    private FeedbackType type;
    private String message;
    private String submittedBy;
    private LocalDateTime createdAt;
}
