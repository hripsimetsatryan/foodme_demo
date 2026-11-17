package am.foodme.backend.repository;

import am.foodme.backend.model.OrderReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReviewRepository extends JpaRepository<OrderReview, Long> {

    boolean existsByOrderId(Long orderId);

    /** Average stars across every review the chef has received, or null if none. */
    @Query("select avg(r.rating) from OrderReview r where r.chef.id = :chefId")
    Double averageRatingForChef(@Param("chefId") Long chefId);
}
