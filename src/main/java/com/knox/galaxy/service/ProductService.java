package com.knox.galaxy.service;

import com.knox.galaxy.dto.*;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Default low-stock threshold applied when the request omits the field. */
final class ProductConstants {
    static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;
    private ProductConstants() {}
}

@Service
public class ProductService {

    /**
     * Ceiling on "<name> copy N". High enough never to be reached in practice,
     * and present only so a naming clash cannot spin the search forever.
     */
    private static final int MAX_COPIES = 99;

    /**
     * Date on the copy's provenance note. Fixed to English rather than the
     * server's locale so the line reads the same whoever's machine wrote it.
     */
    private static final DateTimeFormatter COPY_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Opens the provenance line, and is what identifies it again on a re-copy. */
    private static final String COPY_NOTE_PREFIX = "Copied from ";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private PlanLimitService planLimitService;

    @Autowired
    private NotificationService notificationService;

    // -------------------------------------------------------------------------
    // LIST
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of products with optional full-text search
     * (by name or code) and optional active-only filter.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String search,
                                      Boolean activeOnly,
                                      int page,
                                      int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasSearch = search != null && !search.isBlank();
        boolean filterActive = Boolean.TRUE.equals(activeOnly);

        Page<Product> products;

        if (hasSearch && filterActive) {
            // active=true AND (name ILIKE %s% OR code ILIKE %s%)
            products = productRepository
                    .findByIsActiveAndNameContainingIgnoreCaseOrIsActiveAndProductCodeContainingIgnoreCase(
                            true, search, true, search, pageable);
        } else if (hasSearch) {
            products = productRepository
                    .findByNameContainingIgnoreCaseOrProductCodeContainingIgnoreCase(
                            search, search, pageable);
        } else if (filterActive) {
            products = productRepository.findByIsActive(true, pageable);
        } else {
            products = productRepository.findAll(pageable);
        }

        return products.map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = findOrThrow(id);
        return toResponse(product);
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Transactional
    public ProductResponse create(ProductRequest req) {
        planLimitService.requireProductSlot(productRepository.count());
        // Validate product code uniqueness
        if (productRepository.existsByProductCodeIgnoreCase(req.getProductCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product code '" + req.getProductCode() + "' already exists");
        }

        // Validate warehouse quantities: at least one warehouse must have qty > 0
        // Map<Long, Integer> wqMap = req.getWarehouseQuantities();
        // if (wqMap == null || wqMap.values().stream().noneMatch(q -> q != null && q > 0)) {
        //     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        //             "Total quantity is 0 - please add stock to at least one warehouse.");
        // }

        // Default low-stock threshold
        if (req.getLowStockThreshold() == null) {
            req.setLowStockThreshold(ProductConstants.DEFAULT_LOW_STOCK_THRESHOLD);
        }

        Product product = new Product();
        applyFields(product, req);
        product.setAddedDate(LocalDate.now());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        product = productRepository.save(product);

        saveImages(product, req.getImageUrls());
        saveInventory(product, req.getWarehouseQuantities());

        notificationService.raise(NotificationType.product_added,
                product.getName() + " was added to the catalogue",
                product.getProductCode(), null, product, null);

        return toResponse(product);
    }

    // -------------------------------------------------------------------------
    // COPY
    // -------------------------------------------------------------------------

    /**
     * Duplicates a product as a separate catalogue entry — "Test" becomes
     * "Test copy 1" — so a batch bought at a different price can carry that
     * price on its own row instead of overwriting the original's.
     *
     * <p><strong>Stock is deliberately not copied.</strong> The copy starts at
     * zero in every warehouse and you refill it at the new price. Copying the
     * stock too would double the quantity the business believes it owns, which
     * is the one mistake this feature could make that money depends on.
     *
     * <p>Images are shared by URL rather than re-uploaded: the file on disk is
     * identical, and nothing in the app deletes an image file when a product
     * goes, so pointing two rows at one file is safe.
     *
     * <p>The copy records the original in {@code parentProduct} — a column that
     * has been in the schema since V1, indexed and unused. Copying a copy links
     * to the same original rather than chaining, so every sibling of a product
     * is one query away.
     */
    @Transactional
    public ProductResponse copy(Long id) {
        Product source = findOrThrow(id);
        planLimitService.requireProductSlot(productRepository.count());

        Product copy = new Product();
        copy.setName(nextCopyName(source.getName()));
        copy.setProductCode(nextCopyCode(source.getProductCode()));
        copy.setDescription(describeCopy(source));
        copy.setCategory(source.getCategory());
        copy.setParentProduct(source.getParentProduct() != null ? source.getParentProduct() : source);
        copy.setPurchasePrice(source.getPurchasePrice());
        copy.setSellingPrice(source.getSellingPrice());
        copy.setLowStockThreshold(source.getLowStockThreshold());
        copy.setActive(source.isActive());
        copy.setAddedDate(LocalDate.now());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy = productRepository.save(copy);

        copyImages(source, copy);

        notificationService.raise(NotificationType.product_added,
                copy.getName() + " was copied from " + source.getName(),
                copy.getProductCode(), null, copy, null);

        return toResponse(copy);
    }

    /**
     * The copy's description: the original's text, then a line recording what
     * it was copied from and when.
     *
     * <p>Without it nothing on the product says it is a copy — the name says
     * "copy 1" and {@code addedDate} says today, but neither survives a rename,
     * and neither names the original. This does both, in a field people
     * already read.
     *
     * <p>Any previous provenance line is stripped before the new one is added,
     * so copying a copy leaves one line naming its immediate source rather than
     * a growing stack of them.
     */
    private String describeCopy(Product source) {
        String note = COPY_NOTE_PREFIX + source.getName()
                + " (" + source.getProductCode() + ") on " + LocalDate.now().format(COPY_DATE);

        String existing = source.getDescription() == null ? ""
                : source.getDescription()
                        .replaceAll("(?m)^" + COPY_NOTE_PREFIX + ".*$", "")
                        .trim();

        return existing.isEmpty() ? note : existing + "\n\n" + note;
    }

    /**
     * "Test" and "Test copy 2" both lead to "Test copy 3" once two copies
     * exist — the suffix is stripped first, so copying a copy never produces
     * "Test copy 2 copy 1".
     */
    private String nextCopyName(String sourceName) {
        String base = sourceName.replaceAll("(?i)\\s+copy\\s+\\d+$", "").trim();
        for (int n = 1; n <= MAX_COPIES; n++) {
            String candidate = base + " copy " + n;
            if (!productRepository.existsByNameIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "'" + base + "' already has " + MAX_COPIES + " copies");
    }

    /** Product codes are unique, so the copy needs its own: ABC123 -> ABC123-C1. */
    private String nextCopyCode(String sourceCode) {
        String base = sourceCode.replaceAll("(?i)-C\\d+$", "");
        for (int n = 1; n <= MAX_COPIES; n++) {
            String candidate = base + "-C" + n;
            if (!productRepository.existsByProductCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Product code '" + base + "' has no free copy suffix left");
    }

    private void copyImages(Product source, Product copy) {
        List<ProductImage> originals = productImageRepository.findByProductOrderByPosition(source);
        if (originals.isEmpty()) return;

        List<ProductImage> copies = new ArrayList<>();
        for (ProductImage original : originals) {
            ProductImage img = new ProductImage();
            img.setProduct(copy);
            img.setUrl(original.getUrl());
            img.setPosition(original.getPosition());
            img.setDefault(original.isDefault());
            copies.add(img);
        }
        productImageRepository.saveAll(copies);
    }

    // -------------------------------------------------------------------------
    // UPDATE (full replace)
    // -------------------------------------------------------------------------

    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product product = findOrThrow(id);

        // If the code changed, check it isn't taken by another product
        if (!product.getProductCode().equalsIgnoreCase(req.getProductCode())
                && productRepository.existsByProductCodeIgnoreCase(req.getProductCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product code '" + req.getProductCode() + "' already exists");
        }

        // Default low-stock threshold
        if (req.getLowStockThreshold() == null) {
            req.setLowStockThreshold(ProductConstants.DEFAULT_LOW_STOCK_THRESHOLD);
        }

        applyFields(product, req);
        product.setUpdatedAt(LocalDateTime.now());
        product = productRepository.save(product);

        // Replace images: delete old, insert new
        productImageRepository.deleteByProduct(product);
        productImageRepository.flush();
        saveImages(product, req.getImageUrls());

        // Update warehouse quantities if provided
        if (req.getWarehouseQuantities() != null && !req.getWarehouseQuantities().isEmpty()) {
            updateInventory(product, req.getWarehouseQuantities());
        }

        return toResponse(product);
    }

    // -------------------------------------------------------------------------
    // SOFT-DELETE (single)
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);
        inventoryRepository.deleteByProduct(product);
        productImageRepository.deleteByProduct(product);
        productImageRepository.flush();
        productRepository.delete(product);
    }

    // -------------------------------------------------------------------------
    // BULK SOFT-DELETE
    // -------------------------------------------------------------------------

    @Transactional
    public void bulkDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<Product> products = productRepository.findAllById(ids);
        for (Product p : products) {
            inventoryRepository.deleteByProduct(p);
            productImageRepository.deleteByProduct(p);
        }
        productRepository.deleteAll(products);
    }

    // -------------------------------------------------------------------------
    // TOGGLE ACTIVE STATUS
    // -------------------------------------------------------------------------

    @Transactional
    public ProductResponse toggleActive(Long id, boolean active) {
        Product product = findOrThrow(id);
        product.setActive(active);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        return toResponse(product);
    }



    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    /** Copy request fields onto the product entity (does NOT set timestamps). */
    private void applyFields(Product product, ProductRequest req) {
        product.setName(req.getName());
        product.setProductCode(req.getProductCode());
        product.setDescription(req.getDescription());
        product.setPurchasePrice(req.getPurchasePrice());
        product.setSellingPrice(req.getSellingPrice());
        product.setLowStockThreshold(req.getLowStockThreshold());
        product.setActive(req.isActive());

        product.setCategory(null);
    }

    /**
     * Persist up to 5 image URLs as ProductImage rows.
     * Position is 1-indexed. First image is marked as default.
     */
    private void saveImages(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < urls.size() && i < 5; i++) {
            String url = urls.get(i);
            if (url == null || url.isBlank()) continue;
            
            // Store on disk if base64 encoded; an existing URL passes through.
            String processedUrl = storageService.uploadIfBase64(url);
            
            ProductImage img = new ProductImage();
            img.setProduct(product);
            img.setUrl(processedUrl.trim());
            img.setPosition((short) (i + 1));
            img.setDefault(i == 0);
            images.add(img);
        }
        productImageRepository.saveAll(images);
    }

    /**
     * Create Inventory rows for each warehouse in the map.
     * Used on product creation - existing rows (if any) are deleted first.
     */
    private void saveInventory(Product product, Map<Long, Integer> warehouseQuantities) {
        if (warehouseQuantities == null || warehouseQuantities.isEmpty()) return;
        inventoryRepository.deleteByProduct(product);
        List<Inventory> rows = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : warehouseQuantities.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) continue;
            Warehouse wh = warehouseRepository.findById(entry.getKey()).orElse(null);
            if (wh == null) continue;
            Inventory inv = new Inventory();
            inv.setProduct(product);
            inv.setWarehouse(wh);
            inv.setOnHand(entry.getValue());
            inv.setReserved(0);
            rows.add(inv);
        }
        inventoryRepository.saveAllAndFlush(rows);
    }

    /**
     * Update existing Inventory rows without resetting others.
     * Used on product update - only the provided warehouses are touched.
     */
    private void updateInventory(Product product, Map<Long, Integer> warehouseQuantities) {
        // Load existing rows into a map keyed by warehouse id
        Map<Long, Inventory> existing = new HashMap<>();
        for (Inventory inv : inventoryRepository.findByProduct(product)) {
            existing.put(inv.getWarehouse().getId(), inv);
        }

        List<Inventory> toSave = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : warehouseQuantities.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) continue;
            Warehouse wh = warehouseRepository.findById(entry.getKey()).orElse(null);
            if (wh == null) continue;

            Inventory inv = existing.getOrDefault(entry.getKey(), new Inventory());
            inv.setProduct(product);
            inv.setWarehouse(wh);
            inv.setOnHand(entry.getValue());
            if (inv.getReserved() == 0) inv.setReserved(0); // keep reserved unchanged
            toSave.add(inv);
        }
        inventoryRepository.saveAllAndFlush(toSave);
    }

    /** Map a Product entity (+ its relations) to a ProductResponse DTO. */
    private ProductResponse toResponse(Product product) {
        ProductResponse resp = new ProductResponse();
        resp.setId(product.getId());
        resp.setProductCode(product.getProductCode());
        resp.setName(product.getName());
        resp.setDescription(product.getDescription());
        resp.setActive(product.isActive());
        resp.setPurchasePrice(product.getPurchasePrice());
        resp.setSellingPrice(product.getSellingPrice());
        resp.setLowStockThreshold(product.getLowStockThreshold());
        resp.setAddedDate(product.getAddedDate());
        resp.setCreatedAt(product.getCreatedAt());
        resp.setUpdatedAt(product.getUpdatedAt());
        // Straight off the lazy proxy — an id needs no query to read.
        resp.setParentProductId(
                product.getParentProduct() == null ? null : product.getParentProduct().getId());



        // Images
        List<ProductImage> imgs = productImageRepository.findByProductOrderByPosition(product);
        List<ProductImageDto> imageDtos = imgs.stream()
                .map(img -> {
                    ProductImageDto d = new ProductImageDto();
                    d.setId(img.getId());
                    d.setUrl(img.getUrl());
                    d.setPosition(img.getPosition());
                    d.setDefault(img.isDefault());
                    return d;
                })
                .collect(Collectors.toList());
        resp.setImages(imageDtos);

        // Total stock across all warehouses
        resp.setTotalStock(productRepository.sumOnHandByProductId(product.getId()));

        // Units sold, for the Item Stock table's SALES QUANTITY column
        resp.setSalesQuantity(productRepository.sumDeliveredQuantityByProductId(product.getId()));

        List<String> warehouses = inventoryRepository.findByProduct(product).stream()
                .filter(inv -> inv.getOnHand() > 0)
                .map(inv -> inv.getWarehouse().getName() + " (" + inv.getOnHand() + ")")
                .collect(Collectors.toList());
        resp.setWarehouses(warehouses);

        return resp;
    }
}


