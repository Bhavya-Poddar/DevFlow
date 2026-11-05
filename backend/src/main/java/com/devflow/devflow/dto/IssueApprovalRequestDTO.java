package com.devflow.devflow.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueApprovalRequestDTO {
    private String decision;
    private String remark;
    public String getDecision(){return decision;} public void setDecision(String decision){this.decision=decision;}
    public String getRemark(){return remark;} public void setRemark(String remark){this.remark=remark;}
}
