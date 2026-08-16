package com.knox.galaxy.service;

import com.knox.galaxy.dto.FeedbackRequest;
import com.knox.galaxy.dto.FeedbackResponse;
import com.knox.galaxy.model.Feedback;
import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.FeedbackRepository;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Messages sent from the Help page's feedback form (§14.3). */
@Service
public class FeedbackService {

    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    public FeedbackResponse submit(FeedbackRequest req, String actingUsername) {
        Feedback feedback = new Feedback();
        feedback.setType(req.getType());
        feedback.setMessage(req.getMessage().trim());
        if (actingUsername != null) {
            userRepository.findByUsernameIgnoreCase(actingUsername).ifPresent(feedback::setSubmittedBy);
        }
        return toResponse(feedbackRepository.save(feedback));
    }

    /** Newest first. Everyone in the tenant can read what's been submitted. */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> list() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private FeedbackResponse toResponse(Feedback f) {
        User author = f.getSubmittedBy();
        return new FeedbackResponse(
                f.getId(), f.getType(), f.getMessage(),
                author == null ? null : author.getFirstName() + " " + author.getLastName(),
                f.getCreatedAt());
    }
}
