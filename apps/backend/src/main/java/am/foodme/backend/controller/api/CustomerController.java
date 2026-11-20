package am.foodme.backend.controller.api;

import am.foodme.backend.dto.CreateOrderReviewRequestDto;
import am.foodme.backend.dto.CustomerProfileDto;
import am.foodme.backend.dto.OrderListResponseDto;
import am.foodme.backend.dto.OrderReviewDto;
import am.foodme.backend.dto.PromoApplyRequestDto;
import am.foodme.backend.dto.PromoApplyResponseDto;
import am.foodme.backend.service.CustomerAuthService;
import am.foodme.backend.service.OrderReviewService;
import am.foodme.backend.service.OrderService;
import am.foodme.backend.utils.ControllerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ControllerUtil.API_CUSTOMER_CONTROLLER)
public class CustomerController {

    private final CustomerAuthService customerAuthService;
    private final OrderService orderService;
    private final OrderReviewService orderReviewService;

    public CustomerController(CustomerAuthService customerAuthService, OrderService orderService,
                              OrderReviewService orderReviewService) {
        this.customerAuthService = customerAuthService;
        this.orderService = orderService;
        this.orderReviewService = orderReviewService;
    }

    @GetMapping("/me")
    public CustomerProfileDto me(Authentication authentication) {
        return customerAuthService.me(authentication.getName());
    }

    @GetMapping("/orders")
    public OrderListResponseDto orders(Authentication authentication,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return orderService.listForCustomer(authentication.getName(), page, size);
    }

    /** TEST-11: rate one of your own delivered orders. /api/customer/** requires ROLE_CUSTOMER (R6). */
    @PostMapping("/orders/{number}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReviewDto reviewOrder(Authentication authentication,
                                      @PathVariable String number,
                                      @RequestBody CreateOrderReviewRequestDto request) {
        return orderReviewService.createReview(authentication.getName(), number, request);
    }

    @PostMapping("/promo/apply")
    public PromoApplyResponseDto applyPromo(@RequestBody PromoApplyRequestDto request, Authentication authentication) {
        return orderService.applyPromo(request, authentication.getName());
    }
}
