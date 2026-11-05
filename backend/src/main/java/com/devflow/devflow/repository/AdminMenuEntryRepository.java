package com.devflow.devflow.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.AdminMenuEntry;
public interface AdminMenuEntryRepository extends JpaRepository<AdminMenuEntry, String> {
    List<AdminMenuEntry> findAllByOrderBySortOrderAscRouteKeyAsc();
    List<AdminMenuEntry> findAllByEnabledTrueOrderBySortOrderAscRouteKeyAsc();
}
