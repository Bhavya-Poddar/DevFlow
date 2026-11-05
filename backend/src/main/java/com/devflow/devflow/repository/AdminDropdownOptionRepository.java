package com.devflow.devflow.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.devflow.devflow.entity.AdminDropdownOption;
public interface AdminDropdownOptionRepository extends JpaRepository<AdminDropdownOption, Long> {
    List<AdminDropdownOption> findAllByCategoryOrderBySortOrderAscCodeAsc(String category);
    List<AdminDropdownOption> findAllByCategoryAndEnabledTrueOrderBySortOrderAscCodeAsc(String category);
    Optional<AdminDropdownOption> findByCategoryAndCode(String category, String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select option from AdminDropdownOption option where option.category = :category and option.code = :code")
    Optional<AdminDropdownOption> findByCategoryAndCodeForUpdate(@Param("category") String category, @Param("code") String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select option from AdminDropdownOption option where option.id = :id")
    Optional<AdminDropdownOption> findByIdForUpdate(@Param("id") Long id);
    boolean existsByCategoryAndCode(String category, String code);
    long countByCategory(String category);
}
