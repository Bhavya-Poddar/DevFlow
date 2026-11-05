package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity @Table(name="admin_website_setting")
public class AdminWebsiteSetting {
    @Id @Column(name="setting_key", nullable=false, length=80) private String key;
    @Column(name="setting_value", nullable=false, length=240) private String value;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void updateDate(){updatedAt=LocalDateTime.now();}
    public String getKey(){return key;} public void setKey(String v){key=v;} public String getValue(){return value;} public void setValue(String v){value=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
}
