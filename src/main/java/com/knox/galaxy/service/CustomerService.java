package com.knox.galaxy.service;

import com.knox.galaxy.dto.CustomerRequest;
import com.knox.galaxy.dto.CustomerResponse;
import com.knox.galaxy.model.City;
import com.knox.galaxy.model.Customer;
import com.knox.galaxy.repository.CityRepository;
import com.knox.galaxy.repository.CustomerRepository;
import com.knox.galaxy.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Customers, keyed by phone number (§10.3). */
@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customerRepository.findAllByOrderByNameAsc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Type-ahead for the Place Order customer field — matches name or phone. */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, int page, int size) {
        String term = query == null ? "" : query.trim();
        return customerRepository
                .findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        term, term, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest req) {
        requirePhoneAvailable(req.getPhone(), null);
        Customer customer = new Customer();
        apply(customer, req);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest req) {
        Customer customer = findOrThrow(id);
        requirePhoneAvailable(req.getPhone(), id);
        apply(customer, req);
        return toResponse(customerRepository.save(customer));
    }

    /**
     * Finds the customer with this phone number and refreshes their details, or
     * creates them. Used by order placement, where the operator types the
     * customer's details rather than picking an existing record — a repeat
     * customer must not collide with the UNIQUE(phone) index.
     */
    @Transactional
    public Customer upsertByPhone(CustomerRequest req) {
        Customer customer = customerRepository.findByPhone(req.getPhone().trim())
                .orElseGet(Customer::new);
        apply(customer, req);
        return customerRepository.save(customer);
    }

    Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer " + id + " not found"));
    }

    private void apply(Customer customer, CustomerRequest req) {
        customer.setName(req.getName().trim());
        customer.setPhone(req.getPhone().trim());
        customer.setEmail(req.getEmail() == null || req.getEmail().isBlank()
                ? null : req.getEmail().trim());
        customer.setAddress(req.getAddress());
        customer.setCity(resolveCity(req.getCityId()));
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "City " + cityId + " does not exist"));
    }

    private void requirePhoneAvailable(String phone, Long selfId) {
        customerRepository.findByPhone(phone.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A customer with phone " + phone.trim() + " already exists");
            }
        });
    }

    CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getName(), c.getPhone(), c.getEmail(),
                c.getCity() == null ? null : c.getCity().getId(),
                c.getCity() == null ? null : c.getCity().getName(),
                c.getAddress(),
                c.getId() == null ? 0 : orderRepository.countByCustomerId(c.getId()),
                c.getCreatedAt());
    }
}
