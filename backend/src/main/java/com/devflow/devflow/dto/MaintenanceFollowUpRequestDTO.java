package com.devflow.devflow.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenanceFollowUpRequestDTO {
    private String title;
    private String reason;
    private Long responsibleDeveloperId;
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
    public Long getResponsibleDeveloperId(){return responsibleDeveloperId;} public void setResponsibleDeveloperId(Long id){this.responsibleDeveloperId=id;}
}
