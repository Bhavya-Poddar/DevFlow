package com.devflow.devflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Public registration input. Role and ID are deliberately not client-controlled. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistrationRequestDTO {
    private String name;
    private String email;
    private String password;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
