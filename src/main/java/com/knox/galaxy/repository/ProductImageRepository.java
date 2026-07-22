package com.knox.galaxy.repository;

import com.knox.galaxy.model.Product;
import com.knox.galaxy.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Images for one product in display order. */
    List<ProductImage> findByProductOrderByPosition(Product product);

    /** Bulk delete — used when replacing all images on an update. */
    void deleteByProduct(Product product);
}
