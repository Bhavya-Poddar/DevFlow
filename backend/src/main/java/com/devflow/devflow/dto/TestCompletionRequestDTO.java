package com.devflow.devflow.dto;

public class TestCompletionRequestDTO {
    private String result;
    private String testScenario;
    private String expectedResult;
    private String actualResult;
    private String notes;
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getTestScenario() { return testScenario; }
    public void setTestScenario(String testScenario) { this.testScenario = testScenario; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
