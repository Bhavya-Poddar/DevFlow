package com.devflow.devflow.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.dto.*;
import com.devflow.devflow.service.AdminConfigurationService;

@RestController
public class AdminConfigurationController {
    private final AdminConfigurationService service;
    public AdminConfigurationController(AdminConfigurationService service){this.service=service;}

    @GetMapping("/api/config/dropdowns/{category}")
    public List<AdminDropdownOptionDTO> options(@PathVariable String category){return service.publicOptions(category);}
    @GetMapping("/api/config/menu")
    public List<AdminMenuEntryDTO> menu(){return service.publicMenu();}
    @GetMapping("/api/config/website")
    public List<AdminWebsiteSettingDTO> website(){return service.publicSettings();}
    @GetMapping("/api/config/statuses")
    public List<String> statuses(){return service.statuses();}

    @GetMapping("/api/admin/config/dropdowns")
    public List<AdminDropdownOptionDTO> adminOptions(Authentication auth){return service.adminOptions(auth.getName());}
    @GetMapping("/api/admin/config/dropdowns/catalog")
    public List<AdminDropdownCategoryDTO> dropdownCatalog(Authentication auth){return service.adminDropdownCatalog(auth.getName());}
    @PostMapping("/api/admin/config/dropdowns/{category}")
    public ResponseEntity<AdminDropdownOptionDTO> createOption(@PathVariable String category,
            @RequestBody AdminDropdownOptionRequest request, Authentication auth){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOption(category,request,auth.getName()));
    }
    @PutMapping("/api/admin/config/dropdowns/{id}")
    public AdminDropdownOptionDTO updateOption(@PathVariable Long id,@RequestBody AdminDropdownOptionRequest request,Authentication auth){
        return service.updateOption(id,request,auth.getName());
    }
    @DeleteMapping("/api/admin/config/dropdowns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOption(@PathVariable Long id,Authentication auth){service.deleteOption(id,auth.getName());}
    @GetMapping("/api/admin/config/menu")
    public List<AdminMenuEntryDTO> adminMenu(Authentication auth){return service.adminMenu(auth.getName());}
    @PutMapping("/api/admin/config/menu/{routeKey}")
    public AdminMenuEntryDTO updateMenu(@PathVariable String routeKey,@RequestBody AdminMenuEntryRequest request,Authentication auth){
        return service.updateMenu(routeKey,request,auth.getName());
    }
    @PutMapping("/api/admin/config/website/{key}")
    public AdminWebsiteSettingDTO updateSetting(@PathVariable String key,@RequestBody AdminWebsiteSettingRequest request,Authentication auth){
        return service.updateWebsiteSetting(key,request,auth.getName());
    }
}
