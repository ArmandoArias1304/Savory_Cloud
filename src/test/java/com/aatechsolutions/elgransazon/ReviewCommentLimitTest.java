package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.Review;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewCommentLimitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void accepts150CharacterCommentsAndRejectsLongerComments() {
        Review review = Review.builder()
                .rating(5)
                .comment("a".repeat(150))
                .build();

        assertTrue(commentViolations(validator.validate(review)).isEmpty());

        review.setComment("a".repeat(151));
        assertTrue(commentViolations(validator.validate(review)).size() > 0);
    }

    private Set<ConstraintViolation<Review>> commentViolations(Set<ConstraintViolation<Review>> violations) {
        return violations.stream()
                .filter(violation -> "comment".equals(violation.getPropertyPath().toString()))
                .collect(java.util.stream.Collectors.toSet());
    }
}
