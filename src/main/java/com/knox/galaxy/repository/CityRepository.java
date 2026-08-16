package com.knox.galaxy.repository;

import com.knox.galaxy.model.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    /** Active cities for the order-form dropdown, grouped sensibly for display. */
    List<City> findAllByIsActiveOrderByProvinceAscDistrictAscNameAsc(boolean isActive);

    List<City> findAllByOrderByProvinceAscDistrictAscNameAsc();

    /** Backs the {@code UNIQUE (district, name)} constraint with a friendly 409. */
    Optional<City> findByDistrictIgnoreCaseAndNameIgnoreCase(String district, String name);
}
