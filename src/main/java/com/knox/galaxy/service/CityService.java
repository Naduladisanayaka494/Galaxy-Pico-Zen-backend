package com.knox.galaxy.service;

import com.knox.galaxy.dto.CityRequest;
import com.knox.galaxy.dto.CityResponse;
import com.knox.galaxy.model.City;
import com.knox.galaxy.repository.CityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Province → District → Town reference data behind the delivery-address
 * dropdowns. Seeded per tenant, but editable so a business can add towns the
 * seed list missed.
 */
@Service
public class CityService {

    @Autowired
    private CityRepository cityRepository;

    /**
     * @param activeOnly when true, returns only cities still offered in the
     *                   order form; the settings screen passes false to show
     *                   deactivated rows too.
     */
    @Transactional(readOnly = true)
    public List<CityResponse> list(boolean activeOnly) {
        List<City> cities = activeOnly
                ? cityRepository.findAllByIsActiveOrderByProvinceAscDistrictAscNameAsc(true)
                : cityRepository.findAllByOrderByProvinceAscDistrictAscNameAsc();
        return cities.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CityResponse create(CityRequest req) {
        requireNotDuplicate(req.getDistrict(), req.getName(), null);
        City city = new City();
        apply(city, req);
        return toResponse(cityRepository.save(city));
    }

    @Transactional
    public CityResponse update(Long id, CityRequest req) {
        City city = findOrThrow(id);
        requireNotDuplicate(req.getDistrict(), req.getName(), id);
        apply(city, req);
        return toResponse(cityRepository.save(city));
    }

    @Transactional
    public void delete(Long id) {
        cityRepository.delete(findOrThrow(id));
    }

    private void apply(City city, CityRequest req) {
        city.setProvince(req.getProvince().trim());
        city.setDistrict(req.getDistrict().trim());
        city.setName(req.getName().trim());
        city.setActive(req.isActive());
    }

    /** Mirrors the {@code UNIQUE (district, name)} constraint with a readable 409. */
    private void requireNotDuplicate(String district, String name, Long selfId) {
        cityRepository.findByDistrictIgnoreCaseAndNameIgnoreCase(district.trim(), name.trim())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(selfId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "'" + name.trim() + "' already exists in " + district.trim());
                    }
                });
    }

    private City findOrThrow(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "City " + id + " not found"));
    }

    private CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getProvince(), c.getDistrict(), c.getName(), c.isActive());
    }
}
