package com.devflow.devflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TesterAssignmentRequestDTO {
    private Long testerId;
    private IdReferenceRequestDTO tester;

    public Long getTesterId() { return testerId; }
    public void setTesterId(Long testerId) { this.testerId = testerId; }

    /** Also accepts the existing Issue JSON shape: {"tester":{"id":123}}. */
    public IdReferenceRequestDTO getTester() { return tester; }
    public void setTester(IdReferenceRequestDTO tester) { this.tester = tester; }

    public Long getRequestedTesterId() {
        Long nestedId = tester == null ? null : tester.getId();
        if (testerId != null && nestedId != null && !testerId.equals(nestedId)) {
            return null;
        }
        return testerId != null ? testerId : nestedId;
    }
}
