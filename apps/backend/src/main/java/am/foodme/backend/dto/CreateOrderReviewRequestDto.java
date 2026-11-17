package am.foodme.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Body of POST /api/customer/orders/{number}/review.
 * <p>
 * {@code rating} is a BigDecimal on purpose: with an Integer field Jackson would
 * silently truncate 4.5 to 4, and R2 requires fractional stars to be refused.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderReviewRequestDto {
    private BigDecimal rating;
    private String comment;
}
