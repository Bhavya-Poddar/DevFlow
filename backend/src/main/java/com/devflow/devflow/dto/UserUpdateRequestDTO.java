package com.devflow.devflow.dto;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Partial profile update input; role/password/id are deliberately not writable here. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateRequestDTO {
    private String name;
    private String email;
    private Boolean enabled;
    private final Set<String> suppliedFields = new HashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; suppliedFields.add("name"); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; suppliedFields.add("email"); }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; suppliedFields.add("enabled"); }

    @JsonIgnore public boolean hasName() { return suppliedFields.contains("name"); }
    @JsonIgnore public boolean hasEmail() { return suppliedFields.contains("email"); }
    @JsonIgnore public boolean hasEnabled() { return suppliedFields.contains("enabled"); }
}
