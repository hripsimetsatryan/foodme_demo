package am.foodme.backend;

import am.foodme.backend.model.Order;
import am.foodme.backend.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TEST-11 order ratings, end to end over HTTP (spec acceptance criteria 1-5). */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String customerToken() throws Exception {
        String email = "review-test-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ann",
                                "email", email,
                                "phoneNumber", "+37491234567",
                                "password", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String placeOrder(String token, long chefId, long dishId) throws Exception {
        Map<String, Object> body = Map.of(
                "chefId", chefId,
                "receiverName", "Ann",
                "receiverPhoneNumber", "+37491234567",
                "receiverEmail", "ann@example.com",
                "paymentType", "CASH",
                "deliveryMethod", "TAKEAWAY",
                "note", "",
                "createOrderDishes", List.of(Map.of("dishId", dishId, "quantity", 1))
        );
        String response = mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("number").asText();
    }

    /** Stands in for the kitchen moving the order NEW -> ACCEPTED -> DELIVERED in the admin panel. */
    private void markDelivered(String number) {
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findByNumber(number).orElseThrow();
            order.setStatus("DELIVERED");
        });
    }

    private String reviewBody(Object rating, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void customerRatesDeliveredOrder_andSeesItInHistoryAndTracking() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);
        markDelivered(number);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, "Lovely lavash")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Lovely lavash"))
                .andExpect(jsonPath("$.createdAt").exists());

        // R19: still there when the customer comes back
        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].number").value(number))
                .andExpect(jsonPath("$.list[0].review.rating").value(5))
                .andExpect(jsonPath("$.list[0].review.comment").value("Lovely lavash"));

        // R13: order tracking shows it too
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.rating").value(5));
    }

    @Test
    void commentIsOptional() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);
        markDelivered(number);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(3, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.comment").doesNotExist());
    }

    @Test
    void unratedOrderHasNoReview() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);

        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review").doesNotExist());
    }

    @Test
    void orderThatIsNotDeliveredCannotBeRated() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only delivered orders can be reviewed."));
    }

    @Test
    void orderCannotBeRatedTwice() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);
        markDelivered(number);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(4, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(1, "changed my mind")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order already reviewed."));
    }

    @Test
    void someoneElsesOrderBehavesAsIfItDoesNotExist() throws Exception {
        String owner = customerToken();
        String stranger = customerToken();
        String number = placeOrder(owner, 1, 1);
        markDelivered(number);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(1, "not mine")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/customer/orders/FM-DOES-NOT-EXIST/review")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(1, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedOutUserCannotRate() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);
        markDelivered(number);

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRatingsAndLongCommentsAreRefused() throws Exception {
        String token = customerToken();
        String number = placeOrder(token, 1, 1);
        markDelivered(number);

        for (Object bad : new Object[]{0, 6, 4.5, -1, null}) {
            mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reviewBody(bad, null)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, "a".repeat(1001))))
                .andExpect(status().isBadRequest());

        // nothing was saved by the refused attempts, so a valid rating still goes through
        mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(5, "a".repeat(1000))))
                .andExpect(status().isCreated());
    }

    @Test
    void chefRatingIsTheRoundedAverageOfCustomerRatings() throws Exception {
        // Chef 2 is only reviewed in this test, so its average is deterministic.
        int[] ratings = {5, 4, 4};
        double[] expectedAfterEach = {5.0, 4.5, 4.3};
        for (int i = 0; i < ratings.length; i++) {
            String token = customerToken();
            String number = placeOrder(token, 2, 3);
            markDelivered(number);
            mockMvc.perform(post("/api/customer/orders/" + number + "/review")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reviewBody(ratings[i], null)))
                    .andExpect(status().isCreated());

            // R12: the storefront sees it straight away
            mockMvc.perform(get("/api/chef/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(expectedAfterEach[i]));
        }
    }
}
