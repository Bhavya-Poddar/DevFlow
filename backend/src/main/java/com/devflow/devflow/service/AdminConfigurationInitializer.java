package com.devflow.devflow.service;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;

/** Seeds only missing safe business defaults; administrator edits survive restarts. */
@Component @Order(1)
public class AdminConfigurationInitializer implements ApplicationRunner {
    private final AdminDropdownOptionRepository options; private final AdminMenuEntryRepository menus;
    private final AdminWebsiteSettingRepository settings;
    public AdminConfigurationInitializer(AdminDropdownOptionRepository options,AdminMenuEntryRepository menus,AdminWebsiteSettingRepository settings){this.options=options;this.menus=menus;this.settings=settings;}
    @Override @Transactional public void run(ApplicationArguments args){
        seed("PRIORITY", new String[][]{{"LOW","Low"},{"MEDIUM","Medium"},{"HIGH","High"},{"CRITICAL","Critical"}});
        seed("ISSUE_STATUS", new String[][]{{"OPEN","Open"},{"IN_PROGRESS","In progress"},{"CODE_REVIEW","Code review"},{"READY_FOR_TESTING","Ready for testing"},{"TESTING","Testing"},{"RESOLVED","Resolved"}});
        seed("PROJECT_STATUS", new String[][]{{"ACTIVE","Active"},{"IN_PROGRESS","In progress"},{"COMPLETED","Completed"}});
        seed("SPRINT_STATUS", new String[][]{{"PLANNED","Planned"},{"ACTIVE","Active"},{"COMPLETED","Completed"}});
        seed("TEST_RESULT", new String[][]{{"PASSED","Passed"},{"FAILED","Failed"}});
        seed("AUTOMATION_TRIGGER", new String[][]{{"TASK_STATUS_CHANGED","Task status changed"},{"TESTING_PASSED","Testing passed"},{"TESTING_FAILED","Testing failed"},{"BLOCKING_TASK_RESOLVED","Blocking task resolved"}});
        seed("AUTOMATION_ACTION", new String[][]{{"NOTIFY_ASSIGNED_DEVELOPER","Notify assigned developer"},{"NOTIFY_ASSIGNED_TESTER","Notify assigned tester"},{"NOTIFY_UNBLOCKED_OWNERS","Notify owners of unblocked tasks"}});
        seed("ACCOUNT_STATUS", new String[][]{{"ACTIVE","Active"},{"INACTIVE","Inactive"}});
        seed("REPORTING_RANGE", new String[][]{{"30","Last 30 days"},{"90","Last 90 days"},{"ALL","All time"}});
        seed("WORKLOAD_LEVEL", new String[][]{{"LOW","Low"},{"MEDIUM","Medium"},{"HIGH","High"}});
        String[][] defaults={{"dashboard","Dashboard"},{"users","Users"},{"projects","Projects"},{"private-teams","Private Teams"},{"tasks","Tasks"},{"sprints","Sprints"},{"testing","Testing"},{"my-tests","My Tests"},{"code-reviews","Code Review"},{"comments","Comments"},{"reports","Reports"},{"activity","Activity"},{"audit-logs","Audit Logs"},{"notifications","Notifications"},{"automations","Automations"},{"settings","Settings"}};
        int order=10; for(String[] value:defaults){if(!menus.existsById(value[0])){AdminMenuEntry e=new AdminMenuEntry();e.setRouteKey(value[0]);e.setDisplayLabel(value[1]);e.setEnabled(true);e.setSortOrder(order);menus.save(e);}order+=10;}
        setting("workspace.name","DevFlow"); setting("workspace.tagline","Engineering OS");
        setting("maintenance.period.days","30");
    }
    private void seed(String category,String[][] values){int order=10;for(String[] value:values){if(!options.existsByCategoryAndCode(category,value[0])){AdminDropdownOption o=new AdminDropdownOption();o.setCategory(category);o.setCode(value[0]);o.setDisplayLabel(value[1]);o.setEnabled(true);o.setSortOrder(order);options.save(o);}order+=10;}}
    private void setting(String key,String value){if(!settings.existsById(key)){AdminWebsiteSetting s=new AdminWebsiteSetting();s.setKey(key);s.setValue(value);settings.save(s);}}
}
