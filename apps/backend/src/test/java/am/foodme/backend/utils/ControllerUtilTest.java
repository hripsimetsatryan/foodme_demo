package am.foodme.backend.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilTest {
    @Test
    void apiPathsAreBuiltCorrectly() {
        assertEquals("/api/dish", ControllerUtil.API_DISH_CONTROLLER);
        assertEquals("/admin/auth", ControllerUtil.ADMIN_AUTH_CONTROLLER);
    }
}
