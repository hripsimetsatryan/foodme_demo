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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for TEST-11 order ratings (no Spring context). */
class OrderReviewServiceTest {

    private static final String EMAIL = "ann@example.com";
    private static final String NUMBER = "FM-100001";

    private OrderRepository orderRepository;
    private OrderReviewRepository orderReviewRepository;
    private CustomerRepository customerRepository;
    private ChefRepository chefRepository;
    private OrderReviewService service;

    private Customer customer;
    private Chef chef;
    private Order order;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderReviewRepository = mock(OrderReviewRepository.class);
        customerRepository = mock(CustomerRepository.class);
        chefRepository = mock(ChefRepository.class);
        service = new OrderReviewService(orderRepository, orderReviewRepository, customerRepository, chefRepository);

        customer = new Customer();
        customer.setId(10L);
        chef = new Chef();
        chef.setId(1L);
        chef.setRating(0.0);
        order = new Order();
        order.setId(100L);
        order.setNumber(NUMBER);
        order.setStatus("DELIVERED");
        order.setCustomer(customer);
        order.setChef(chef);

        when(customerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(customer));
        when(orderRepository.findByNumber(NUMBER)).thenReturn(Optional.of(order));
        when(orderReviewRepository.existsByOrderId(100L)).thenReturn(false);
        when(orderReviewRepository.saveAndFlush(any(OrderReview.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateOrderReviewRequestDto request(String rating, String comment) {
        return new CreateOrderReviewRequestDto(rating == null ? null : new BigDecimal(rating), comment);
    }

    @Test
    void createReview_savesReviewWithOrderCustomerChefAndTimestamp() {
        when(orderReviewRepository.averageRatingForChef(1L)).thenReturn(5.0);

        OrderReviewDto dto = service.createReview(EMAIL, NUMBER, request("5", "  Great food  "));

        ArgumentCaptor<OrderReview> captor = ArgumentCaptor.forClass(OrderReview.class);
        verify(orderReviewRepository).saveAndFlush(captor.capture());
        OrderReview saved = captor.getValue();
        assertSame(order, saved.getOrder());
        assertSame(customer, saved.getCustomer());
        assertSame(chef, saved.getChef());
        assertEquals(5, saved.getRating().intValue());
        assertEquals("Great food", saved.getComment());
        assertNotNull(saved.getCreatedAt());

        assertEquals(5, dto.getRating().intValue());
        assertEquals("Great food", dto.getComment());
        assertSame(saved, order.getReview());
    }

    @Test
    void createReview_normalisesEmailBeforeLookup() {
        when(orderReviewRepository.averageRatingForChef(1L)).thenReturn(4.0);
        service.createReview("  ANN@Example.com ", NUMBER, request("4", null));
        verify(customerRepository).findByEmail(EMAIL);
    }

    @Test
    void createReview_updatesChefRatingToRoundedAverage() {
        when(orderReviewRepository.averageRatingForChef(1L)).thenReturn(13.0 / 3.0); // 4.333...

        service.createReview(EMAIL, NUMBER, request("4", null));

        assertEquals(4.3, chef.getRating().doubleValue());
        verify(chefRepository).save(chef);
    }

    @Test
    void createReview_rejectsOrderThatIsNotDelivered() {
        order.setStatus("ACCEPTED");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.createReview(EMAIL, NUMBER, request("5", null)));

        assertEquals("Only delivered orders can be reviewed.", ex.getMessage());
        verify(orderReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReview_rejectsSecondReviewOfSameOrder() {
        when(orderReviewRepository.existsByOrderId(100L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createReview(EMAIL, NUMBER, request("5", null)));

        assertEquals("Order already reviewed.", ex.getMessage());
        verify(orderReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReview_concurrentDuplicateHittingUniqueConstraintIsAConflict() {
        when(orderReviewRepository.saveAndFlush(any(OrderReview.class)))
                .thenThrow(new DataIntegrityViolationException("uk_order_review_order_id"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createReview(EMAIL, NUMBER, request("5", null)));
        assertEquals("Order already reviewed.", ex.getMessage());
        verify(chefRepository, never()).save(any());
    }

    @Test
    void createReview_someoneElsesOrderLooksLikeItDoesNotExist() {
        Customer other = new Customer();
        other.setId(99L);
        order.setCustomer(other);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.createReview(EMAIL, NUMBER, request("5", null)));
        assertEquals("Order " + NUMBER + " not found", ex.getMessage());
    }

    @Test
    void createReview_guestOrderWithoutCustomerLooksLikeItDoesNotExist() {
        order.setCustomer(null);
        assertThrows(NotFoundException.class, () -> service.createReview(EMAIL, NUMBER, request("5", null)));
    }

    @Test
    void createReview_unknownOrderIsNotFound() {
        when(orderRepository.findByNumber("FM-404")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createReview(EMAIL, "FM-404", request("5", null)));
    }

    @Test
    void createReview_unknownCustomerIsNotFound() {
        when(customerRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> service.createReview("ghost@example.com", NUMBER, request("5", null)));
    }

    @Test
    void createReview_nullBodyIsRefused() {
        assertThrows(BadRequestException.class, () -> service.createReview(EMAIL, NUMBER, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "5", "5.0", "3.00"})
    void validateRating_acceptsWholeStarsOneToFive(String value) {
        int expected = new BigDecimal(value).intValue();
        assertEquals(expected, OrderReviewService.validateRating(new BigDecimal(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "-1", "4.5", "0.9", "5.01", "10", "50"})
    void validateRating_refusesAnythingElse(String value) {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> OrderReviewService.validateRating(new BigDecimal(value)));
        assertEquals(OrderReviewService.INVALID_RATING_MESSAGE, ex.getMessage());
    }

    @Test
    void validateRating_refusesMissingRating() {
        assertThrows(BadRequestException.class, () -> OrderReviewService.validateRating(null));
    }

    @Test
    void normalizeComment_allowsExactly1000Characters() {
        String comment = "a".repeat(1000);
        assertEquals(comment, OrderReviewService.normalizeComment(comment));
    }

    @Test
    void normalizeComment_refuses1001Characters() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> OrderReviewService.normalizeComment("a".repeat(1001)));
        assertEquals(OrderReviewService.COMMENT_TOO_LONG_MESSAGE, ex.getMessage());
    }

    @Test
    void normalizeComment_blankOrMissingBecomesNoComment() {
        assertNull(OrderReviewService.normalizeComment(null));
        assertNull(OrderReviewService.normalizeComment("   "));
    }

    @Test
    void roundToOneDecimal_roundsHalfUp() {
        assertEquals(4.3, OrderReviewService.roundToOneDecimal(4.25));
        assertEquals(4.3, OrderReviewService.roundToOneDecimal(4.333333));
        assertEquals(4.7, OrderReviewService.roundToOneDecimal(4.666666));
        assertEquals(5.0, OrderReviewService.roundToOneDecimal(5.0));
        assertEquals(1.0, OrderReviewService.roundToOneDecimal(1.04));
    }

    @Test
    void refreshChefRating_leavesRatingAloneWhenChefHasNoReviews() {
        chef.setRating(4.8);
        when(orderReviewRepository.averageRatingForChef(1L)).thenReturn(null);

        service.refreshChefRating(chef);

        assertEquals(4.8, chef.getRating().doubleValue());
        verify(chefRepository, never()).save(any());
    }
}
