package com.qyd.product;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
public class ProductController {
    private final CatalogService service;
    public ProductController(CatalogService service) { this.service = service; }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')")
    Object createCategory(@Valid @RequestBody CategoryRequest r) { return service.createCategory(r); }
    @GetMapping("/categories") List<CategoryDto> categories() { return service.categories(); }
    @GetMapping("/categories/{id}") CategoryDto category(@PathVariable String id) { return service.categoryDto(id); }
    @PutMapping("/categories/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR')") Object updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest r) { return service.updateCategory(id, r); }
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('PLATFORM_ADMIN')") void deleteCategory(@PathVariable String id) { service.deleteCategory(id); }

    @PostMapping("/resources") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')")
    Object createResource(@Valid @RequestBody ResourceRequest r) { return service.createResource(r); }
    @GetMapping("/resources") List<ResourceDto> resources(@RequestParam(required=false) String venueId) { return service.resources(venueId); }
    @GetMapping("/resources/{id}") ResourceDto resource(@PathVariable String id) { return service.resource(id); }
    @PutMapping("/resources/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')") Object updateResource(@PathVariable String id, @Valid @RequestBody ResourceRequest r) { return service.updateResource(id, r); }
    @DeleteMapping("/resources/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('PLATFORM_ADMIN')") void deleteResource(@PathVariable String id) { service.deleteResource(id); }

    @PostMapping("/skus") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')")
    Object createSku(@Valid @RequestBody SkuRequest r) { return service.createSku(r); }
    @GetMapping("/skus") List<SkuDto> skus(@RequestParam(required=false) String resourceId) { return service.skus(resourceId); }
    @GetMapping("/skus/{id}") SkuDto sku(@PathVariable String id) { return service.sku(id); }
    @PutMapping("/skus/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')") Object updateSku(@PathVariable String id, @Valid @RequestBody SkuRequest r) { return service.updateSku(id, r); }
    @DeleteMapping("/skus/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('PLATFORM_ADMIN')") void deleteSku(@PathVariable String id) { service.deleteSku(id); }

    public record CategoryRequest(@NotBlank String name, String description) {}
    public record ResourceRequest(@NotBlank String venueId, @NotBlank String categoryId, @NotBlank String name, String description, Boolean active) {}
    public record SkuRequest(@NotBlank String resourceId, @NotBlank String name, @NotNull @DecimalMin("0.00") BigDecimal price, String currency, Boolean active) {}
    public record CategoryDto(String id,String name,String description) { static CategoryDto of(SportCategory e){return new CategoryDto(e.getId(),e.name,e.description);} }
    public record ResourceDto(String id,String venueId,String categoryId,String name,String description,boolean active) { static ResourceDto of(SportResource e){return new ResourceDto(e.getId(),e.venueId,e.categoryId,e.name,e.description,e.active);} }
    public record SkuDto(String id,String resourceId,String name,BigDecimal price,String currency,boolean active) { static SkuDto of(ProductSku e){return new SkuDto(e.getId(),e.resourceId,e.name,e.price,e.currency,e.active);} }
}

@Entity @Table(name="sport_category")
class SportCategory extends BaseEntity { @Column(nullable=false,length=100) String name; @Column(length=500) String description; protected SportCategory(){} }
@Entity @Table(name="sport_resource")
class SportResource extends BaseEntity { @Column(nullable=false,length=36) String venueId; @Column(nullable=false,length=36) String categoryId; @Column(nullable=false,length=150) String name; @Column(length=500) String description; @Column(nullable=false) boolean active; protected SportResource(){} }
@Entity @Table(name="product_sku")
class ProductSku extends BaseEntity { @Column(nullable=false,length=36) String resourceId; @Column(nullable=false,length=150) String name; @Column(nullable=false,precision=19,scale=2) BigDecimal price; @Column(nullable=false,length=3) String currency; @Column(nullable=false) boolean active; protected ProductSku(){} }
interface CategoryRepository extends JpaRepository<SportCategory,String>{}
interface ResourceRepository extends JpaRepository<SportResource,String>{ List<SportResource> findAllByVenueId(String venueId); }
interface SkuRepository extends JpaRepository<ProductSku,String>{ List<ProductSku> findAllByResourceId(String resourceId); }

@Service @Transactional
class CatalogService {
    private final CategoryRepository categories; private final ResourceRepository resources; private final SkuRepository skus;
    CatalogService(CategoryRepository c,ResourceRepository r,SkuRepository s){categories=c;resources=r;skus=s;}
    ProductController.CategoryDto createCategory(ProductController.CategoryRequest r){SportCategory e=new SportCategory();e.name=r.name();e.description=r.description();return ProductController.CategoryDto.of(categories.save(e));}
    List<ProductController.CategoryDto> categories(){return categories.findAll().stream().map(ProductController.CategoryDto::of).toList();}
    ProductController.CategoryDto categoryDto(String id){return ProductController.CategoryDto.of(category(id));}
    ProductController.CategoryDto updateCategory(String id,ProductController.CategoryRequest r){SportCategory e=category(id);e.name=r.name();e.description=r.description();return ProductController.CategoryDto.of(e);}
    void deleteCategory(String id){categories.delete(category(id));}
    ProductController.ResourceDto createResource(ProductController.ResourceRequest r){category(r.categoryId());SportResource e=new SportResource();e.venueId=r.venueId();e.categoryId=r.categoryId();e.name=r.name();e.description=r.description();e.active=r.active()==null||r.active();return ProductController.ResourceDto.of(resources.save(e));}
    List<ProductController.ResourceDto> resources(String venueId){return(venueId==null?resources.findAll():resources.findAllByVenueId(venueId)).stream().map(ProductController.ResourceDto::of).toList();}
    ProductController.ResourceDto resource(String id){return ProductController.ResourceDto.of(resourceEntity(id));}
    ProductController.ResourceDto updateResource(String id,ProductController.ResourceRequest r){category(r.categoryId());SportResource e=resourceEntity(id);e.venueId=r.venueId();e.categoryId=r.categoryId();e.name=r.name();e.description=r.description();if(r.active()!=null)e.active=r.active();return ProductController.ResourceDto.of(e);}
    void deleteResource(String id){resources.delete(resourceEntity(id));}
    ProductController.SkuDto createSku(ProductController.SkuRequest r){resourceEntity(r.resourceId());ProductSku e=new ProductSku();apply(e,r);return ProductController.SkuDto.of(skus.save(e));}
    List<ProductController.SkuDto> skus(String resourceId){return(resourceId==null?skus.findAll():skus.findAllByResourceId(resourceId)).stream().map(ProductController.SkuDto::of).toList();}
    ProductController.SkuDto sku(String id){return ProductController.SkuDto.of(skuEntity(id));}
    ProductController.SkuDto updateSku(String id,ProductController.SkuRequest r){resourceEntity(r.resourceId());ProductSku e=skuEntity(id);apply(e,r);return ProductController.SkuDto.of(e);}
    void deleteSku(String id){skus.delete(skuEntity(id));}
    private void apply(ProductSku e,ProductController.SkuRequest r){e.resourceId=r.resourceId();e.name=r.name();e.price=r.price();e.currency=r.currency()==null?"CNY":r.currency().toUpperCase();if(r.active()!=null)e.active=r.active();else e.active=true;}
    private SportCategory category(String id){return categories.findById(id).orElseThrow(()->notFound("category"));}
    private SportResource resourceEntity(String id){return resources.findById(id).orElseThrow(()->notFound("resource"));}
    ProductSku skuEntity(String id){return skus.findById(id).orElseThrow(()->notFound("sku"));}
    private ResponseStatusException notFound(String type){return new ResponseStatusException(HttpStatus.NOT_FOUND,type+" not found");}
}
