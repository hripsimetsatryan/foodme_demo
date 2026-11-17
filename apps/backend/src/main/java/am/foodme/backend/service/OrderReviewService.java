package am.foodme.backend.service;

import am.foodme.backend.dto.CreateOrderReviewRequestDto;
import am.foodme.backend.dto.OrderReviewDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.ConflictException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Chef;
import am.foodme.backend.model.Customer;
import am.foodme.backend.model.Order;
import am.foodme.backend.model.OrderReview;
import am.foodme.backend.repository.ChefRepository;
import am.foodme.backend.repository.CustomerRepository;
import am.foodme.backend.repository.OrderRepository;
import am.foodme.backend.repository.OrderReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Order ratings (TEST-11, spec: order-ratings.spec.md). */
@Service
public class OrderReviewService {

    public static final String NOT_DELIVERED_MESSAGE = "Only delivered orders can be reviewed.";
    public static final String ALREADY_REVIEWED_MESSAGE = "Order already reviewed.";
    public static final String INVALID_RATING_MESSAGE = "Rating must be a whole number from 1 to 5.";
    public static final String COMMENT_TOO_LONG_MESSAGE = "Comment must be at most 1000 characters.";

    static final int MIN_RATING = 1;
    static final int MAX_RATING = 5;
    static final int MAX_COMMENT_LENGTH = 1000;
    static final String DELIVERED = "DELIVERED";

    private final OrderRepository orderRepository;
    private final OrderReviewRepository orderReviewRepository;
    private final CustomerRepository customerRepository;
    private final ChefRepository chefRepository;

    public OrderReviewService(OrderRepository orderRepository,
                              OrderReviewRepository orderReviewRepository,
                              CustomerRepository customerRepository,
                              ChefRepository chefRepository) {
        this.orderRepository = orderRepository;
        this.orderReviewRepository = orderReviewRepository;
        this.customerRepository = customerRepository;
        this.chefRepository = chefRepository;
    }

    @Transactional
    public OrderReviewDto createReview(String customerEmail, String orderNumber, CreateOrderReviewRequestDto request) {
        Customer customer = customerRepository
                .findByEmail(customerEmail == null ? "" : customerEmail.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        // R7: someone else's order behaves exactly like a missing one.
        Order order = orderRepository.findByNumber(orderNumber)
                .filter(o -> o.getCustomer() != null && Objects.equals(o.getCustomer().getId(), customer.getId()))
                .orElseThrow(() -> new NotFoundException("Order " + orderNumber + " not found"));

        if (request == null) {
            throw new BadRequestException(INVALID_RATING_MESSAGE);
        }
        int rating = validateRating(request.getRating());
        String comment = normalizeComment(request.getComment());

        if (!DELIVERED.equals(order.getStatus())) {
            throw new BadRequestException(NOT_DELIVERED_MESSAGE); // R8
        }
        if (orderReviewRepository.existsByOrderId(order.getId())) {
            throw new ConflictException(ALREADY_REVIEWED_MESSAGE); // R9
        }

        OrderReview review = new OrderReview();
        review.setOrder(order);
        review.setCustomer(customer);
        review.setChef(order.getChef());
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());

        OrderReview saved;
        try {
            saved = orderReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent submissions: the unique order_id constraint is the last line of defence (R4).
            throw new ConflictException(ALREADY_REVIEWED_MESSAGE);
        }
        order.setReview(saved);

        refreshChefRating(order.getChef()); // R10-R12
        return OrderReviewDto.mapEntityToDto(saved);
    }

    /** R10/R11: chef rating = average of all their reviews, rounded to one decimal. */
    void refreshChefRating(Chef chef) {
        if (chef == null) {
            return;
        }
        Double average = orderReviewRepository.averageRatingForChef(chef.getId());
        if (average == null) {
            return;
        }
        chef.setRating(roundToOneDecimal(average));
        chefRepository.save(chef);
    }

    static double roundToOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** R2: whole number 1..5 only; 4.5, 0, 6 and missing values are refused. */
    static int validateRating(BigDecimal rating) {
        if (rating == null) {
            throw new BadRequestException(INVALID_RATING_MESSAGE);
        }
        boolean whole = rating.signum() == 0 || rating.stripTrailingZeros().scale() <= 0;
        if (!whole
                || rating.compareTo(BigDecimal.valueOf(MIN_RATING)) < 0
                || rating.compareTo(BigDecimal.valueOf(MAX_RATING)) > 0) {
            throw new BadRequestException(INVALID_RATING_MESSAGE);
        }
        return rating.intValueExact();
    }

    /** R3: optional comment, max 1000 characters; blank becomes no comment. */
    static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException(COMMENT_TOO_LONG_MESSAGE);
        }
        return trimmed;
    }
}
