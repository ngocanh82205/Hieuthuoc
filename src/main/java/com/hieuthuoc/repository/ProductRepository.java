package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    List<Product> findAllByOrderByNameAsc();

    List<Product> findByActiveTrueOrderByNameAsc();

    long countByCategory(Category category);

    @Query("select distinct p.manufacturer from Product p where p.active = true and p.manufacturer is not null order by p.manufacturer")
    List<String> distinctManufacturers();

    @Query("select distinct p.country from Product p where p.active = true and p.country is not null order by p.country")
    List<String> distinctCountries();

    @Query("select distinct p.dosageForm from Product p where p.active = true and p.dosageForm is not null order by p.dosageForm")
    List<String> distinctDosageForms();

    @Query("select p from Product p where p.active = true and p.drugType <> com.hieuthuoc.entity.DrugType.SPECIAL order by p.name")
    List<Product> findSellable();

    List<Product> findTop4ByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(String activeIngredient, Long id);

    List<Product> findTop4ByActiveTrueAndCategoryAndIdNotAndDrugTypeNot(Category category, Long id, DrugType drugType);

    List<Product> findByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(String activeIngredient, Long id);

    /** Các sản phẩm đã khai báo p là thuốc tương đương của chúng (chiều ngược). */
    @Query("select x from Product x join x.equivalents e where e = :p")
    List<Product> findEquivalentOf(@Param("p") Product p);

    Optional<Product> findFirstByRegistrationNoIgnoreCase(String registrationNo);

    Optional<Product> findFirstByNameIgnoreCase(String name);
}
