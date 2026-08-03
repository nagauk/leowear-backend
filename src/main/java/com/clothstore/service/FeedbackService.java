package com.clothstore.service;

import com.clothstore.dto.FeedbackDto;
import com.clothstore.entity.Feedback;
import com.clothstore.entity.Role;
import com.clothstore.entity.User;
import com.clothstore.exception.BadRequestException;
import com.clothstore.repository.FeedbackRepository;
import com.clothstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedbackDto submit(FeedbackDto dto, String usernameOrNull) {
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }

        User user = null;
        if (usernameOrNull != null && !usernameOrNull.isBlank()) {
            user = userRepository.findByUsername(usernameOrNull).orElse(null);
        }

        if (user != null && user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin can only view feedback, not submit it");
        }

        if (user == null && (dto.getGuestName() == null || dto.getGuestName().isBlank())) {
            throw new BadRequestException("Please provide your name");
        }

        Feedback f = Feedback.builder()
                .user(user)
                .guestName(user == null ? dto.getGuestName() : null)
                .guestEmail(user == null ? dto.getGuestEmail() : null)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();

        return toDto(feedbackRepository.save(f));
    }

    public Page<FeedbackDto> all(Pageable pageable) {
        return feedbackRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    private FeedbackDto toDto(Feedback f) {
        return FeedbackDto.builder()
                .id(f.getId())
                .userId(f.getUser() != null ? f.getUser().getId() : null)
                .username(f.getUser() != null ? f.getUser().getUsername() : null)
                .guestName(f.getGuestName())
                .guestEmail(f.getGuestEmail())
                .rating(f.getRating())
                .comment(f.getComment())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
