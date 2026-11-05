-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: devflow
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin_dropdown_option`
--

DROP TABLE IF EXISTS `admin_dropdown_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_dropdown_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(40) NOT NULL,
  `code` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `display_label` varchar(120) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `sort_order` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_dropdown_option` (`category`,`code`),
  KEY `ix_admin_dropdown_category_order` (`category`,`enabled`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_dropdown_option`
--

LOCK TABLES `admin_dropdown_option` WRITE;
/*!40000 ALTER TABLE `admin_dropdown_option` DISABLE KEYS */;
INSERT INTO `admin_dropdown_option` VALUES (1,'PRIORITY','LOW','2026-11-02 10:51:39.231172','Low',_binary '',10,'2026-11-02 10:51:39.231172'),(2,'PRIORITY','MEDIUM','2026-11-02 10:51:39.369608','Medium',_binary '',20,'2026-11-02 10:51:39.369608'),(3,'PRIORITY','HIGH','2026-11-02 10:51:39.380140','High',_binary '',30,'2026-11-02 10:51:39.380140'),(4,'PRIORITY','CRITICAL','2026-11-02 10:51:39.390145','Critical',_binary '',40,'2026-11-02 10:51:39.390145'),(5,'ISSUE_STATUS','OPEN','2026-11-02 14:17:53.561624','Open',_binary '',10,'2026-11-02 14:17:53.561624'),(6,'ISSUE_STATUS','IN_PROGRESS','2026-11-02 14:17:53.624326','In progress',_binary '',20,'2026-11-02 14:17:53.624326'),(7,'ISSUE_STATUS','CODE_REVIEW','2026-11-02 14:17:53.633119','Code review',_binary '',30,'2026-11-02 14:17:53.633119'),(8,'ISSUE_STATUS','READY_FOR_TESTING','2026-11-02 14:17:53.639115','Ready for testing',_binary '',40,'2026-11-02 14:17:53.639115'),(9,'ISSUE_STATUS','TESTING','2026-11-02 14:17:53.645569','Testing',_binary '',50,'2026-11-02 14:17:53.645569'),(10,'ISSUE_STATUS','RESOLVED','2026-11-02 14:17:53.652327','Resolved',_binary '',60,'2026-11-02 14:17:53.652327'),(11,'PROJECT_STATUS','ACTIVE','2026-11-02 14:17:53.657502','Active',_binary '',10,'2026-11-02 14:17:53.657502'),(12,'PROJECT_STATUS','IN_PROGRESS','2026-11-02 14:17:53.664207','In progress',_binary '',20,'2026-11-02 14:17:53.664207'),(13,'PROJECT_STATUS','COMPLETED','2026-11-02 14:17:53.669206','Completed',_binary '',30,'2026-11-02 14:17:53.669206'),(14,'SPRINT_STATUS','PLANNED','2026-11-02 14:17:53.675209','Planned',_binary '',10,'2026-11-02 14:17:53.675209'),(15,'SPRINT_STATUS','ACTIVE','2026-11-02 14:17:53.681514','Active',_binary '',20,'2026-11-02 14:17:53.681514'),(16,'SPRINT_STATUS','COMPLETED','2026-11-02 14:17:53.687960','Completed',_binary '',30,'2026-11-02 14:17:53.687960'),(17,'TEST_RESULT','PASSED','2026-11-02 14:17:53.693512','Passed',_binary '',10,'2026-11-02 14:17:53.693512'),(18,'TEST_RESULT','FAILED','2026-11-02 14:17:53.699506','Failed',_binary '',20,'2026-11-02 14:17:53.699506'),(19,'AUTOMATION_TRIGGER','TASK_STATUS_CHANGED','2026-11-02 14:17:53.705153','Task status changed',_binary '',10,'2026-11-02 14:17:53.705153'),(20,'AUTOMATION_TRIGGER','TESTING_PASSED','2026-11-02 14:17:53.711747','Testing passed',_binary '',20,'2026-11-02 14:17:53.711747'),(21,'AUTOMATION_TRIGGER','TESTING_FAILED','2026-11-02 14:17:53.717724','Testing failed',_binary '',30,'2026-11-02 14:17:53.717724'),(22,'AUTOMATION_TRIGGER','BLOCKING_TASK_RESOLVED','2026-11-02 14:17:53.721835','Blocking task resolved',_binary '',40,'2026-11-02 14:17:53.721835'),(23,'AUTOMATION_ACTION','NOTIFY_ASSIGNED_DEVELOPER','2026-11-02 14:17:53.727406','Notify assigned developer',_binary '',10,'2026-11-02 14:17:53.727406'),(24,'AUTOMATION_ACTION','NOTIFY_ASSIGNED_TESTER','2026-11-02 14:17:53.732689','Notify assigned tester',_binary '',20,'2026-11-02 14:17:53.732689'),(25,'AUTOMATION_ACTION','NOTIFY_UNBLOCKED_OWNERS','2026-11-02 14:17:53.738326','Notify owners of unblocked tasks',_binary '',30,'2026-11-02 14:17:53.738326'),(26,'ACCOUNT_STATUS','ACTIVE','2026-11-02 14:17:53.743737','Active',_binary '',10,'2026-11-02 14:17:53.743737'),(27,'ACCOUNT_STATUS','INACTIVE','2026-11-02 14:17:53.748185','Inactive',_binary '',20,'2026-11-02 14:17:53.748185'),(28,'REPORTING_RANGE','30','2026-11-02 14:17:53.753958','Last 30 days',_binary '',10,'2026-11-02 14:17:53.753958'),(29,'REPORTING_RANGE','90','2026-11-02 14:17:53.759758','Last 90 days',_binary '',20,'2026-11-02 14:17:53.759758'),(30,'REPORTING_RANGE','ALL','2026-11-02 14:17:53.765757','All time',_binary '',30,'2026-11-02 14:17:53.765757'),(31,'WORKLOAD_LEVEL','LOW','2026-11-02 14:17:53.771265','Low',_binary '',10,'2026-11-02 14:17:53.771265'),(32,'WORKLOAD_LEVEL','MEDIUM','2026-11-02 14:17:53.777269','Medium',_binary '',20,'2026-11-02 14:17:53.777269'),(33,'WORKLOAD_LEVEL','HIGH','2026-11-02 14:17:53.784274','High',_binary '',30,'2026-11-02 14:17:53.784274');
/*!40000 ALTER TABLE `admin_dropdown_option` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin_menu_entry`
--

DROP TABLE IF EXISTS `admin_menu_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_menu_entry` (
  `route_key` varchar(64) NOT NULL,
  `display_label` varchar(80) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `sort_order` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `required_permission` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`route_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_menu_entry`
--

LOCK TABLES `admin_menu_entry` WRITE;
/*!40000 ALTER TABLE `admin_menu_entry` DISABLE KEYS */;
INSERT INTO `admin_menu_entry` VALUES ('activity','Activity',_binary '',120,'2026-11-02 10:51:39.579061',NULL),('audit-logs','Audit Logs',_binary '',130,'2026-11-02 10:51:39.594069',NULL),('automations','Automations',_binary '',150,'2026-11-02 10:51:39.619053',NULL),('code-reviews','Code Review',_binary '',90,'2026-11-02 10:51:39.543061',NULL),('comments','Comments',_binary '',100,'2026-11-02 10:51:39.554053',NULL),('dashboard','Dashboard',_binary '',10,'2026-11-02 10:51:39.442068',NULL),('my-tests','My Tests',_binary '',80,'2026-11-02 10:51:39.533053',NULL),('notifications','Notifications',_binary '',140,'2026-11-02 10:51:39.606058',NULL),('private-teams','Private Teams',_binary '',40,'2026-11-02 10:51:39.488055',NULL),('projects','Projects',_binary '',30,'2026-11-02 10:51:39.476054',NULL),('reports','Reports',_binary '',110,'2026-11-02 10:51:39.567055',NULL),('settings','Settings',_binary '',160,'2026-11-02 10:51:39.632058',NULL),('sprints','Sprints',_binary '',60,'2026-11-02 10:51:39.511051',NULL),('tasks','Tasks',_binary '',50,'2026-11-02 10:51:39.500062',NULL),('testing','Testing',_binary '',70,'2026-11-02 10:51:39.523067',NULL),('users','User',_binary '',20,'2026-11-02 11:36:21.394847',NULL);
/*!40000 ALTER TABLE `admin_menu_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin_website_setting`
--

DROP TABLE IF EXISTS `admin_website_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_website_setting` (
  `setting_key` varchar(80) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `setting_value` varchar(240) NOT NULL,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_website_setting`
--

LOCK TABLES `admin_website_setting` WRITE;
/*!40000 ALTER TABLE `admin_website_setting` DISABLE KEYS */;
INSERT INTO `admin_website_setting` VALUES ('maintenance.period.days','2026-11-02 15:26:56.797834','30'),('workspace.name','2026-11-02 10:51:39.654061','DevFlow'),('workspace.tagline','2026-11-02 10:51:39.671052','Engineering OS');
/*!40000 ALTER TABLE `admin_website_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `details` varchar(255) DEFAULT NULL,
  `issue_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKo0vhv2vfluxgrqkf5gd7p4x39` (`issue_id`),
  KEY `FKqir5ob5q1x1w30jk4j3e4p58c` (`user_id`),
  CONSTRAINT `FKo0vhv2vfluxgrqkf5gd7p4x39` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`),
  CONSTRAINT `FKqir5ob5q1x1w30jk4j3e4p58c` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,'ISSUE_CREATED','2026-10-12 17:24:26.238403','Issue \'Issue section testing\' was created',NULL,1),(2,'ISSUE_DELETED','2026-10-12 17:24:44.972259','Issue \'Issue part testing\' was deleted',NULL,1),(3,'ISSUE_CREATED','2026-10-12 17:27:59.088445','Issue \'issue #1\' was created',NULL,1),(4,'ISSUE_CREATED','2026-10-12 17:28:06.704149','Issue \'issue #2\' was created',NULL,1),(5,'ISSUE_CREATED','2026-10-12 17:28:15.497411','Issue \'issue #3\' was created',NULL,1),(6,'ISSUE_DELETED','2026-10-12 17:28:29.600197','Issue \'issue #33\' was deleted',NULL,1),(7,'ISSUE_DELETED','2026-10-12 17:28:31.646468','Issue \'issue #2\' was deleted',NULL,1),(8,'ISSUE_DELETED','2026-10-12 17:28:33.523841','Issue \'issue #3\' was deleted',NULL,1),(9,'ISSUE_CREATED','2026-10-14 11:39:02.344631','Issue \'issue#1\' was created',5,1),(10,'STATUS_CHANGED','2026-10-14 11:39:19.829890','Status changed from \'OPEN\' to \'READY_FOR_TESTING\'',5,1),(11,'ROLE_CHANGED','2026-10-14 11:44:03.302194','User \'Harsh\' role changed from \'VIEWER\' to \'DEVELOPER\'',NULL,1),(12,'ROLE_CHANGED','2026-10-14 11:44:08.166033','User \'hardik\' role changed from \'VIEWER\' to \'TESTER\'',NULL,1),(13,'TESTER_ASSIGNED','2026-10-14 11:44:25.339315','Tester changed from \'Unassigned\' to \'hardik (#3)\'',5,1),(14,'ASSIGNED_USER_CHANGED','2026-10-14 11:44:25.355587','Assignee changed from \'Unassigned\' to \'Harsh (#2)\'',5,1),(15,'COMMENT_ADDED','2026-10-14 12:07:35.254137','A comment was added to issue',5,1),(16,'COMMENT_ADDED','2026-10-14 12:09:32.510788','A comment was added to issue',5,1),(17,'ROLE_CHANGED','2026-10-14 12:11:37.940835','User \'Hardik\' role changed from \'VIEWER\' to \'TESTER\'',NULL,1),(18,'TESTER_ASSIGNED','2026-10-14 12:12:22.214101','Tester changed from \'hardik (#3)\' to \'Hardik (#4)\'',5,1),(19,'TEST_STARTED','2026-10-14 12:12:40.954849','Testing started for issue \'issue#1\'',5,4),(20,'TEST_PASSED','2026-10-14 12:12:51.729509','Issue \'issue#1\' passed testing',5,4),(21,'ISSUE_CREATED','2026-10-14 17:08:48.849727','Issue \'login not working\' was created with status \'OPEN\'',6,1),(22,'STATUS_CHANGED','2026-10-14 17:09:07.274574','Status changed from \'RESOLVED\' to \'READY_FOR_TESTING\'',5,1),(23,'TESTER_ASSIGNED','2026-10-14 17:09:22.958822','Tester changed from \'Unassigned\' to \'Hardik (#4)\'',6,1),(24,'ASSIGNED_USER_CHANGED','2026-10-14 17:09:22.970326','Assignee changed from \'Unassigned\' to \'Harsh (#2)\'',6,1),(25,'SPRINT_CHANGED','2026-10-14 17:09:22.978069','Sprint changed from \'Unassigned\' to \'Complete Project #2 (#1)\'',6,1),(26,'STATUS_CHANGED','2026-10-14 17:09:37.092810','Status changed from \'OPEN\' to \'CODE_REVIEW\'',6,1),(27,'TEST_STARTED','2026-10-16 11:25:26.128850','Testing started for issue \'issue#1\'',5,1),(28,'TEST_PASSED','2026-10-16 11:26:16.352247','Issue \'issue#1\' PASSED testing; Scenario: login page; Expected: user can access the site; Actual: user got to access the site',5,1),(29,'STATUS_CHANGED','2026-10-16 11:26:34.537195','Status changed from \'CODE_REVIEW\' to \'READY_FOR_TESTING\'',6,1),(30,'TEST_STARTED','2026-10-16 11:27:22.184178','Testing started for issue \'login not working\'',6,1),(31,'TEST_PASSED','2026-10-16 11:27:25.968659','Issue \'login not working\' PASSED testing',6,1),(32,'ISSUE_CREATED','2026-10-16 11:27:59.349893','Issue \'issue page\' was created with status \'OPEN\'',NULL,1),(33,'STATUS_CHANGED','2026-10-16 11:28:17.422080','Status changed from \'OPEN\' to \'CODE_REVIEW\'',NULL,1),(34,'TESTER_ASSIGNED','2026-10-16 11:28:17.429024','Tester changed from \'Unassigned\' to \'hardik (#3)\'',NULL,1),(35,'ASSIGNED_USER_CHANGED','2026-10-16 11:28:17.438622','Assignee changed from \'Unassigned\' to \'Harsh (#2)\'',NULL,1),(36,'SPRINT_CHANGED','2026-10-16 11:28:17.445982','Sprint changed from \'Unassigned\' to \'Complete Project #2 (#1)\'',NULL,1),(37,'TESTER_ASSIGNED','2026-10-16 14:23:17.710469','Tester changed from \'hardik (#3)\' to \'Hardik (#4)\'',NULL,1),(38,'ISSUE_DELETED','2026-10-16 14:42:17.826279','targetType=ISSUE; targetId=7; issue deleted',NULL,1),(39,'ISSUE_CREATED','2026-10-16 14:42:57.472073','targetType=ISSUE; targetId=8; issue created with status \'OPEN\'',8,1),(40,'STATUS_CHANGED','2026-10-16 14:43:20.311296','targetType=ISSUE; targetId=8; status changed from \'OPEN\' to \'IN_PROGRESS\'',8,1),(41,'TESTER_ASSIGNED','2026-10-16 14:43:20.327956','targetType=ISSUE; targetId=8; testerId changed from null to 3',8,1),(42,'ASSIGNED_USER_CHANGED','2026-10-16 14:43:20.338387','targetType=ISSUE; targetId=8; assigneeId changed from null to 2',8,1),(43,'SPRINT_CHANGED','2026-10-16 14:43:20.346392','targetType=ISSUE; targetId=8; sprintId changed from null to 1',8,1),(44,'LOGIN_SUCCEEDED','2026-10-16 14:43:35.372121','outcome=SUCCESS; authentication succeeded',NULL,4),(45,'LOGIN_SUCCEEDED','2026-10-16 14:43:57.058128','outcome=SUCCESS; authentication succeeded',NULL,1),(46,'PROJECT_CREATED','2026-10-16 15:09:40.769079','targetType=PROJECT; targetId=3; project created; githubRepository=unlinked',NULL,1),(47,'ISSUE_CREATED','2026-10-16 15:10:31.258101','targetType=ISSUE; targetId=9; issue created with status \'OPEN\'',9,1),(48,'TESTER_ASSIGNED','2026-10-16 15:11:31.193292','targetType=ISSUE; targetId=9; testerId changed from null to 3',9,1),(49,'ASSIGNED_USER_CHANGED','2026-10-16 15:11:31.211145','targetType=ISSUE; targetId=9; assigneeId changed from null to 2',9,1),(50,'COMMENT_ADDED','2026-10-16 15:12:17.553161','A comment was added to issue',9,1),(51,'LOGIN_SUCCEEDED','2026-10-16 15:12:43.152721','outcome=SUCCESS; authentication succeeded',NULL,1),(52,'LOGIN_SUCCEEDED','2026-10-16 15:12:53.202272','outcome=SUCCESS; authentication succeeded',NULL,4),(53,'LOGIN_SUCCEEDED','2026-10-16 15:13:23.790783','outcome=SUCCESS; authentication succeeded',NULL,1),(54,'STATUS_CHANGED','2026-10-16 15:13:37.756636','targetType=ISSUE; targetId=9; status changed from \'OPEN\' to \'READY_FOR_TESTING\'',9,1),(55,'TEST_STARTED','2026-10-16 15:13:49.129579','targetType=ISSUE; targetId=9; testing started',9,1),(56,'TEST_PASSED','2026-10-16 15:14:14.738908','targetType=ISSUE; targetId=9; testOutcome=PASSED',9,1),(57,'LOGIN_SUCCEEDED','2026-10-21 12:23:04.287866','outcome=SUCCESS; authentication succeeded',NULL,1),(58,'LOGIN_FAILED','2026-10-21 12:50:42.208862','outcome=FAILURE; login credentials rejected',NULL,NULL),(59,'USER_REGISTERED','2026-10-21 12:51:05.537444','targetType=USER; targetId=5; public account registered with VIEWER role',NULL,5),(60,'LOGIN_FAILED','2026-10-21 12:51:25.677711','outcome=FAILURE; login credentials rejected',NULL,NULL),(61,'LOGIN_SUCCEEDED','2026-10-21 13:46:03.335037','outcome=SUCCESS; authentication succeeded',NULL,1),(62,'STATUS_CHANGED','2026-10-21 14:35:44.325396','targetType=ISSUE; targetId=6; status changed from \'RESOLVED\' to \'CODE_REVIEW\'',6,1),(63,'STATUS_CHANGED','2026-10-21 14:36:49.319535','targetType=ISSUE; targetId=5; status changed from \'RESOLVED\' to \'READY_FOR_TESTING\'',5,1),(64,'TEST_STARTED','2026-10-21 14:36:57.995269','targetType=ISSUE; targetId=5; testing started',5,1),(65,'LOGIN_SUCCEEDED','2026-10-21 14:51:11.604448','outcome=SUCCESS; authentication succeeded',NULL,1),(66,'AUTOMATION_RULE_CREATED','2026-10-21 14:56:19.906311','targetType=AUTOMATION_RULE; targetId=5',NULL,1),(67,'LOGIN_SUCCEEDED','2026-10-21 17:18:16.614160','outcome=SUCCESS; authentication succeeded',NULL,1),(68,'LOGIN_SUCCEEDED','2026-10-23 14:13:38.675843','outcome=SUCCESS; authentication succeeded',NULL,1),(69,'LOGIN_SUCCEEDED','2026-10-23 15:59:43.692075','outcome=SUCCESS; authentication succeeded',NULL,1),(70,'LOGIN_SUCCEEDED','2026-10-28 10:58:54.769751','outcome=SUCCESS; authentication succeeded',NULL,1),(71,'LOGIN_SUCCEEDED','2026-10-28 12:04:37.492505','outcome=SUCCESS; authentication succeeded',NULL,1),(72,'LOGIN_SUCCEEDED','2026-10-28 15:40:13.129846','outcome=SUCCESS; authentication succeeded',NULL,1),(73,'LOGIN_SUCCEEDED','2026-10-28 16:16:06.245908','outcome=SUCCESS; authentication succeeded',NULL,1),(74,'LOGIN_SUCCEEDED','2026-10-28 16:44:22.588032','outcome=SUCCESS; authentication succeeded',NULL,4),(75,'USER_REGISTERED','2026-10-28 16:56:58.005056','targetType=USER; targetId=6; public account registered with VIEWER role',NULL,6),(76,'LOGIN_SUCCEEDED','2026-10-28 16:57:20.607220','outcome=SUCCESS; authentication succeeded',NULL,6),(77,'ROLE_CHANGED','2026-10-28 16:58:45.586420','targetType=USER; targetId=6; role changed from \'VIEWER\' to \'DEVELOPER\'',NULL,1),(78,'PROJECT_CREATED','2026-10-28 16:59:47.021566','targetType=PROJECT; targetId=4; project created; githubRepository=unlinked',NULL,1),(79,'LOGIN_SUCCEEDED','2026-10-28 17:25:04.748705','outcome=SUCCESS; authentication succeeded',NULL,1),(80,'ASSIGNED_USER_CHANGED','2026-10-28 17:29:24.543244','targetType=ISSUE; targetId=5; assigneeId changed from 2 to 6',5,1),(81,'LOGIN_SUCCEEDED','2026-10-30 11:08:58.352668','outcome=SUCCESS; authentication succeeded',NULL,1),(82,'LOGIN_SUCCEEDED','2026-10-30 11:09:41.919469','outcome=SUCCESS; authentication succeeded',NULL,1),(83,'LOGIN_SUCCEEDED','2026-10-30 11:39:38.101653','outcome=SUCCESS; authentication succeeded',NULL,1),(84,'USER_REGISTERED','2026-10-30 11:42:06.280418','targetType=USER; targetId=7; public account registered with VIEWER role',NULL,7),(85,'LOGIN_SUCCEEDED','2026-10-30 11:42:13.580882','outcome=SUCCESS; authentication succeeded',NULL,1),(86,'ROLE_CHANGED','2026-10-30 11:42:31.620769','targetType=USER; targetId=7; role changed from \'VIEWER\' to \'PROJECT_MANAGER\'',NULL,1),(87,'PROJECT_CREATED','2026-10-30 11:51:19.519035','targetType=PROJECT; targetId=5; project created; githubRepository=unlinked',NULL,1),(88,'LOGIN_SUCCEEDED','2026-10-30 17:00:16.468214','outcome=SUCCESS; authentication succeeded',NULL,1),(89,'LOGIN_SUCCEEDED','2026-11-02 10:47:06.218037','outcome=SUCCESS; authentication succeeded',NULL,1),(90,'LOGIN_SUCCEEDED','2026-11-02 11:52:23.864682','outcome=SUCCESS; authentication succeeded',NULL,1),(91,'LOGIN_SUCCEEDED','2026-11-02 13:13:44.953457','outcome=SUCCESS; authentication succeeded',NULL,1),(92,'LOGIN_SUCCEEDED','2026-11-02 14:17:03.391490','outcome=SUCCESS; authentication succeeded',NULL,1),(93,'LOGIN_SUCCEEDED','2026-11-02 15:27:09.932200','outcome=SUCCESS; authentication succeeded',NULL,1),(94,'LOGIN_SUCCEEDED','2026-11-02 16:27:13.842776','outcome=SUCCESS; authentication succeeded',NULL,1),(95,'USER_CREATED','2026-11-02 17:06:14.510816','targetType=USER; targetId=8; administrator created account with role TESTER',NULL,1),(96,'LOGIN_SUCCEEDED','2026-11-02 17:06:35.730940','outcome=SUCCESS; authentication succeeded',NULL,8),(97,'USER_CREATED','2026-11-02 17:07:25.827323','targetType=USER; targetId=9; administrator created account with role DEVELOPER',NULL,1),(98,'USER_CREATED','2026-11-02 17:07:57.109709','targetType=USER; targetId=10; administrator created account with role PROJECT_MANAGER',NULL,1),(99,'LOGIN_SUCCEEDED','2026-11-02 17:08:18.180370','outcome=SUCCESS; authentication succeeded',NULL,10),(100,'LOGIN_SUCCEEDED','2026-11-02 17:08:39.745375','outcome=SUCCESS; authentication succeeded',NULL,9),(101,'PROJECT_DELETED','2026-11-02 17:09:04.224316','targetType=PROJECT; targetId=5; project deleted',NULL,1),(102,'ROLE_CREATED','2026-11-02 17:11:14.258222','targetType=ROLE; roleId=16; code=TEAM_LEAD',NULL,1),(103,'ROLE_CHANGED','2026-11-02 17:11:27.680259','targetType=USER; targetId=7; oldRole=PROJECT_MANAGER; newRole=TEAM_LEAD',NULL,1),(104,'LOGIN_FAILED','2026-11-02 17:12:02.253186','outcome=FAILURE; login credentials rejected',NULL,7),(105,'LOGIN_FAILED','2026-11-02 17:12:19.005378','outcome=FAILURE; login credentials rejected',NULL,7),(106,'LOGIN_FAILED','2026-11-02 17:12:20.011757','outcome=FAILURE; login credentials rejected',NULL,7),(107,'LOGIN_SUCCEEDED','2026-11-02 17:12:39.141487','outcome=SUCCESS; authentication succeeded',NULL,7),(108,'PROJECT_CREATED','2026-11-02 17:14:15.325823','targetType=PROJECT; targetId=6; project created',NULL,10),(109,'ISSUE_CREATED','2026-11-02 17:15:54.935251','targetType=ISSUE; targetId=10; task created with status \'IN_PROGRESS\'',10,10),(110,'ASSIGNED_USER_CHANGED','2026-11-02 17:15:54.938674','targetType=ISSUE; targetId=10; assigneeId=9',10,10),(111,'STATUS_CHANGED','2026-11-02 17:17:12.705370','targetType=ISSUE; targetId=10; status changed from \'IN_PROGRESS\' to \'CODE_REVIEW\'',10,9),(112,'STATUS_CHANGED','2026-11-02 17:19:59.871150','targetType=ISSUE; targetId=10; status changed from \'CODE_REVIEW\' to \'READY_FOR_TESTING\'',10,10),(113,'TEST_STARTED','2026-11-02 17:20:11.217924','targetType=ISSUE; targetId=10; testing started',10,8),(114,'TEST_PASSED','2026-11-02 17:20:16.571483','targetType=ISSUE; targetId=10; testOutcome=PASSED',10,8),(115,'ISSUE_APPROVAL_GRANTED','2026-11-02 17:20:48.659904','targetType=ISSUE; targetId=10; stage=TEAM_LEAD',10,7),(116,'ISSUE_APPROVAL_GRANTED','2026-11-02 17:20:52.237487','targetType=ISSUE; targetId=10; stage=PROJECT_MANAGER',10,7),(117,'LOGIN_SUCCEEDED','2026-11-02 17:58:37.674004','outcome=SUCCESS; authentication succeeded',NULL,10),(118,'LOGIN_SUCCEEDED','2026-11-02 17:59:20.510247','outcome=SUCCESS; authentication succeeded',NULL,1),(119,'LOGIN_SUCCEEDED','2026-11-04 12:03:20.900313','outcome=SUCCESS; authentication succeeded',NULL,1),(120,'LOGIN_SUCCEEDED','2026-11-04 16:28:03.086291','outcome=SUCCESS; authentication succeeded',NULL,1),(121,'LOGIN_SUCCEEDED','2026-11-04 16:31:13.039235','outcome=SUCCESS; authentication succeeded',NULL,9),(122,'LOGIN_SUCCEEDED','2026-11-05 10:57:49.445472','outcome=SUCCESS; authentication succeeded',NULL,1),(123,'LOGIN_SUCCEEDED','2026-11-05 11:21:44.315390','outcome=SUCCESS; authentication succeeded',NULL,1),(124,'LOGIN_SUCCEEDED','2026-11-05 12:06:18.652699','outcome=SUCCESS; authentication succeeded',NULL,1);
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `authorization_invariant_lock`
--

DROP TABLE IF EXISTS `authorization_invariant_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `authorization_invariant_lock` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `authorization_invariant_lock`
--

LOCK TABLES `authorization_invariant_lock` WRITE;
/*!40000 ALTER TABLE `authorization_invariant_lock` DISABLE KEYS */;
INSERT INTO `authorization_invariant_lock` VALUES (1);
/*!40000 ALTER TABLE `authorization_invariant_lock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `automation_event`
--

DROP TABLE IF EXISTS `automation_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `automation_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_summary` varchar(300) DEFAULT NULL,
  `issue_id` bigint DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `previous_status` varchar(40) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `resulting_status` varchar(40) DEFAULT NULL,
  `source_key` varchar(160) NOT NULL,
  `state` varchar(20) NOT NULL,
  `trigger_event` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_automation_event_source_key` (`source_key`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `automation_event`
--

LOCK TABLES `automation_event` WRITE;
/*!40000 ALTER TABLE `automation_event` DISABLE KEYS */;
INSERT INTO `automation_event` VALUES (1,0,'2026-11-02 17:17:12.725823',NULL,10,'2026-11-02 17:17:12.725823','2026-11-02 17:17:12.725823','IN_PROGRESS','2026-11-02 17:17:13.770951','CODE_REVIEW','TASK_STATUS_CHANGED:issue-update:10:451da4d4-8f0f-4be9-bdbf-77e818709435','PROCESSED','TASK_STATUS_CHANGED'),(2,0,'2026-11-02 17:19:59.885465',NULL,10,'2026-11-02 17:19:59.885465','2026-11-02 17:19:59.885465','CODE_REVIEW','2026-11-02 17:20:00.742293','READY_FOR_TESTING','TASK_STATUS_CHANGED:issue-update:10:3b8b6fa0-6961-40f9-b947-88d9b9afd663','PROCESSED','TASK_STATUS_CHANGED'),(3,0,'2026-11-02 17:20:11.215924',NULL,10,'2026-11-02 17:20:11.215924','2026-11-02 17:20:11.215924','READY_FOR_TESTING','2026-11-02 17:20:11.886481','TESTING','TASK_STATUS_CHANGED:test-start:6','PROCESSED','TASK_STATUS_CHANGED'),(4,0,'2026-11-02 17:20:16.560542',NULL,10,'2026-11-02 17:20:16.560542','2026-11-02 17:20:16.560542','TESTING','2026-11-02 17:20:16.976823','TEAM_LEAD_APPROVAL','TASK_STATUS_CHANGED:test-complete:6','PROCESSED','TASK_STATUS_CHANGED'),(5,0,'2026-11-02 17:20:16.570028',NULL,10,'2026-11-02 17:20:16.570028','2026-11-02 17:20:16.570028','TESTING','2026-11-02 17:20:16.992891','TEAM_LEAD_APPROVAL','TESTING_PASSED:test-execution:6','PROCESSED','TESTING_PASSED'),(6,0,'2026-11-02 17:20:48.764962',NULL,10,'2026-11-02 17:20:48.764962','2026-11-02 17:20:48.764962','TEAM_LEAD_APPROVAL','2026-11-02 17:20:49.389397','PROJECT_MANAGER_APPROVAL','TASK_STATUS_CHANGED:approval:1','PROCESSED','TASK_STATUS_CHANGED'),(7,0,'2026-11-02 17:20:52.246608',NULL,10,'2026-11-02 17:20:52.246608','2026-11-02 17:20:52.246608','PROJECT_MANAGER_APPROVAL','2026-11-02 17:20:52.456197','RESOLVED','BLOCKING_TASK_RESOLVED:manager-approval:2','PROCESSED','BLOCKING_TASK_RESOLVED'),(8,0,'2026-11-02 17:20:52.258408',NULL,10,'2026-11-02 17:20:52.258408','2026-11-02 17:20:52.258408','PROJECT_MANAGER_APPROVAL','2026-11-02 17:20:52.476307','RESOLVED','TASK_STATUS_CHANGED:approval:2','PROCESSED','TASK_STATUS_CHANGED');
/*!40000 ALTER TABLE `automation_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `automation_execution`
--

DROP TABLE IF EXISTS `automation_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `automation_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` varchar(40) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `outcome` varchar(20) NOT NULL,
  `rule_name` varchar(120) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `target_issue_id` bigint DEFAULT NULL,
  `trigger_event` varchar(40) NOT NULL,
  `event_id` bigint NOT NULL,
  `rule_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_automation_execution_event_rule` (`event_id`,`rule_id`),
  KEY `FKpae52uhwm1y8ogx91nu4p2bn2` (`rule_id`),
  CONSTRAINT `FKayxv11l9l2uof5ttkebjl1gux` FOREIGN KEY (`event_id`) REFERENCES `automation_event` (`id`),
  CONSTRAINT `FKpae52uhwm1y8ogx91nu4p2bn2` FOREIGN KEY (`rule_id`) REFERENCES `automation_rule` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `automation_execution`
--

LOCK TABLES `automation_execution` WRITE;
/*!40000 ALTER TABLE `automation_execution` DISABLE KEYS */;
INSERT INTO `automation_execution` VALUES (1,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:17:13.768936','SKIPPED','Ready for testing — notify tester','The configured status condition did not match',10,'TASK_STATUS_CHANGED',1,1),(2,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:20:00.739842','SUCCESS','Ready for testing — notify tester','Created 1 notification(s)',10,'TASK_STATUS_CHANGED',2,1),(3,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:20:11.884478','SKIPPED','Ready for testing — notify tester','The configured status condition did not match',10,'TASK_STATUS_CHANGED',3,1),(4,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:20:16.975738','SKIPPED','Ready for testing — notify tester','The configured status condition did not match',10,'TASK_STATUS_CHANGED',4,1),(5,'NOTIFY_ASSIGNED_DEVELOPER','2026-11-02 17:20:16.991616','SUCCESS','Testing passed — notify developer','Created 1 notification(s)',10,'TESTING_PASSED',5,4),(6,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:20:49.388397','SKIPPED','Ready for testing — notify tester','The configured status condition did not match',10,'TASK_STATUS_CHANGED',6,1),(7,'NOTIFY_UNBLOCKED_OWNERS','2026-11-02 17:20:52.453198','SKIPPED','Blocker resolved — notify unblocked owners','No eligible recipient was available',10,'BLOCKING_TASK_RESOLVED',7,3),(8,'NOTIFY_ASSIGNED_TESTER','2026-11-02 17:20:52.473851','SKIPPED','Ready for testing — notify tester','The configured status condition did not match',10,'TASK_STATUS_CHANGED',8,1);
/*!40000 ALTER TABLE `automation_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `automation_rule`
--

DROP TABLE IF EXISTS `automation_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `automation_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` varchar(40) NOT NULL,
  `built_in_key` varchar(80) DEFAULT NULL,
  `condition_status` varchar(40) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(120) NOT NULL,
  `trigger_event` varchar(40) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkln7ibyvkhfv0nsqx994ekdcw` (`built_in_key`),
  KEY `FKknlp6swmdj1lfd4i0l34wkhus` (`created_by_user_id`),
  CONSTRAINT `FKknlp6swmdj1lfd4i0l34wkhus` FOREIGN KEY (`created_by_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `automation_rule`
--

LOCK TABLES `automation_rule` WRITE;
/*!40000 ALTER TABLE `automation_rule` DISABLE KEYS */;
INSERT INTO `automation_rule` VALUES (1,'NOTIFY_ASSIGNED_TESTER','READY_FOR_TESTING_TESTER','READY_FOR_TESTING','2026-10-21 14:53:34.694740',_binary '\0','Notify the assigned tester when a task enters the testing queue.',_binary '','Ready for testing — notify tester','TASK_STATUS_CHANGED','2026-10-21 14:53:34.694740',NULL),(2,'NOTIFY_ASSIGNED_DEVELOPER','TEST_FAILED_DEVELOPER',NULL,'2026-10-21 14:53:34.839724',_binary '\0','Notify the assigned developer when a test fails.',_binary '','Testing failed — notify developer','TESTING_FAILED','2026-10-21 14:53:34.839724',NULL),(3,'NOTIFY_UNBLOCKED_OWNERS','BLOCKER_RESOLVED_OWNERS',NULL,'2026-10-21 14:53:34.851726',_binary '\0','Notify owners when all blockers for their task are resolved.',_binary '','Blocker resolved — notify unblocked owners','BLOCKING_TASK_RESOLVED','2026-10-21 14:53:34.851726',NULL),(4,'NOTIFY_ASSIGNED_DEVELOPER','TEST_PASSED_DEVELOPER',NULL,'2026-10-21 14:53:34.862715',_binary '\0','Notify the assigned developer when a test passes.',_binary '','Testing passed — notify developer','TESTING_PASSED','2026-10-21 14:53:34.862715',NULL),(5,'NOTIFY_ASSIGNED_DEVELOPER',NULL,'READY_FOR_TESTING','2026-10-21 14:56:19.902304',_binary '\0',NULL,_binary '','Test failed','TESTING_FAILED','2026-10-21 14:56:19.902304',1);
/*!40000 ALTER TABLE `automation_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `issue_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKomjg70m9sundkar1el2rtonrn` (`issue_id`),
  KEY `FK8kcum44fvpupyw6f5baccx25c` (`user_id`),
  CONSTRAINT `FK8kcum44fvpupyw6f5baccx25c` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKomjg70m9sundkar1el2rtonrn` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comment`
--

LOCK TABLES `comment` WRITE;
/*!40000 ALTER TABLE `comment` DISABLE KEYS */;
INSERT INTO `comment` VALUES (1,'hello','2026-10-14 12:07:35.207694',5,1),(2,'hw','2026-10-14 12:09:32.507498',5,1),(3,'i am working','2026-10-16 15:12:17.539866',9,1);
/*!40000 ALTER TABLE `comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `github_issue_activity`
--

DROP TABLE IF EXISTS `github_issue_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `github_issue_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_type` varchar(24) NOT NULL,
  `author` varchar(255) DEFAULT NULL,
  `event_at` datetime(6) DEFAULT NULL,
  `external_id` varchar(100) NOT NULL,
  `html_url` varchar(2048) NOT NULL,
  `summary` text NOT NULL,
  `delivery_id` bigint NOT NULL,
  `issue_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_github_activity_delivery_object_issue` (`delivery_id`,`external_id`,`issue_id`),
  KEY `fk_github_activity_issue` (`issue_id`),
  CONSTRAINT `fk_github_activity_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `github_webhook_delivery` (`id`),
  CONSTRAINT `fk_github_activity_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `github_issue_activity`
--

LOCK TABLES `github_issue_activity` WRITE;
/*!40000 ALTER TABLE `github_issue_activity` DISABLE KEYS */;
/*!40000 ALTER TABLE `github_issue_activity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `github_issue_link`
--

DROP TABLE IF EXISTS `github_issue_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `github_issue_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `github_issue_number` bigint DEFAULT NULL,
  `html_url` varchar(2048) DEFAULT NULL,
  `repository_name` varchar(100) NOT NULL,
  `repository_owner` varchar(39) NOT NULL,
  `state` varchar(16) NOT NULL,
  `title` varchar(512) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `issue_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_github_issue_link_task` (`issue_id`),
  UNIQUE KEY `uk_github_issue_link_repo_number` (`repository_owner`,`repository_name`,`github_issue_number`),
  KEY `ix_github_issue_link_repository` (`repository_owner`,`repository_name`,`state`),
  CONSTRAINT `fk_github_issue_link_task` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `github_issue_link`
--

LOCK TABLES `github_issue_link` WRITE;
/*!40000 ALTER TABLE `github_issue_link` DISABLE KEYS */;
/*!40000 ALTER TABLE `github_issue_link` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `github_pull_request`
--

DROP TABLE IF EXISTS `github_pull_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `github_pull_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(255) DEFAULT NULL,
  `base_branch` varchar(255) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `head_branch` varchar(255) DEFAULT NULL,
  `html_url` varchar(2048) NOT NULL,
  `merged` bit(1) NOT NULL,
  `merged_at` datetime(6) DEFAULT NULL,
  `pull_request_number` bigint NOT NULL,
  `repository_name` varchar(100) NOT NULL,
  `repository_owner` varchar(39) NOT NULL,
  `state` varchar(16) NOT NULL,
  `title` varchar(512) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_github_pull_request_repository_number` (`repository_owner`,`repository_name`,`pull_request_number`),
  KEY `fk_github_pr_project` (`project_id`),
  CONSTRAINT `fk_github_pr_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `github_pull_request`
--

LOCK TABLES `github_pull_request` WRITE;
/*!40000 ALTER TABLE `github_pull_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `github_pull_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `github_pull_request_issue`
--

DROP TABLE IF EXISTS `github_pull_request_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `github_pull_request_issue` (
  `pull_request_id` bigint NOT NULL,
  `issue_id` bigint NOT NULL,
  UNIQUE KEY `uk_github_pr_issue` (`pull_request_id`,`issue_id`),
  KEY `fk_github_pr_issue_issue` (`issue_id`),
  CONSTRAINT `fk_github_pr_issue_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`),
  CONSTRAINT `fk_github_pr_issue_pr` FOREIGN KEY (`pull_request_id`) REFERENCES `github_pull_request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `github_pull_request_issue`
--

LOCK TABLES `github_pull_request_issue` WRITE;
/*!40000 ALTER TABLE `github_pull_request_issue` DISABLE KEYS */;
/*!40000 ALTER TABLE `github_pull_request_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `github_webhook_delivery`
--

DROP TABLE IF EXISTS `github_webhook_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `github_webhook_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_id` varchar(64) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `received_at` datetime(6) NOT NULL,
  `repository_name` varchar(100) NOT NULL,
  `repository_owner` varchar(39) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_github_webhook_delivery_id` (`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `github_webhook_delivery`
--

LOCK TABLES `github_webhook_delivery` WRITE;
/*!40000 ALTER TABLE `github_webhook_delivery` DISABLE KEYS */;
/*!40000 ALTER TABLE `github_webhook_delivery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue`
--

DROP TABLE IF EXISTS `issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `priority` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `assigned_user_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `reported_by_id` bigint DEFAULT NULL,
  `sprint_id` bigint DEFAULT NULL,
  `tester_id` bigint DEFAULT NULL,
  `acceptance_criteria` text,
  `due_date` date DEFAULT NULL,
  `workflow_cycle` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb0wvgskd5hpvafdfwcbdic5bx` (`assigned_user_id`),
  KEY `FKcombytcpeogaqi2012phvvvhy` (`project_id`),
  KEY `FKbur9fc5x79i2i7j5j5ftfigva` (`reported_by_id`),
  KEY `FK5k39vkuty9g6w3n7iwye1nh0i` (`sprint_id`),
  KEY `FKfn1wr6jjgmx39qs20cpf4ji93` (`tester_id`),
  CONSTRAINT `FK5k39vkuty9g6w3n7iwye1nh0i` FOREIGN KEY (`sprint_id`) REFERENCES `sprint` (`id`),
  CONSTRAINT `FKb0wvgskd5hpvafdfwcbdic5bx` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKbur9fc5x79i2i7j5j5ftfigva` FOREIGN KEY (`reported_by_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKcombytcpeogaqi2012phvvvhy` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `FKfn1wr6jjgmx39qs20cpf4ji93` FOREIGN KEY (`tester_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue`
--

LOCK TABLES `issue` WRITE;
/*!40000 ALTER TABLE `issue` DISABLE KEYS */;
INSERT INTO `issue` VALUES (5,'2026-10-14 11:39:02.275011','','HIGH','TESTING','issue#1','2026-10-28 17:29:24.583233',6,1,1,NULL,4,NULL,NULL,0),(6,'2026-10-14 17:08:48.797113','login page has an issue','HIGH','CODE_REVIEW','login not working','2026-10-21 14:35:44.391446',2,2,1,1,4,'',NULL,0),(8,'2026-10-16 14:42:57.468014','','HIGH','IN_PROGRESS','issue #4','2026-10-16 14:43:20.351386',2,2,1,1,3,'pass it when the notifications work properly',NULL,0),(9,'2026-10-16 15:10:31.251090','desc','HIGH','RESOLVED','login page','2026-10-16 15:14:14.747389',2,3,1,NULL,3,'',NULL,0),(10,'2026-11-02 17:15:54.907459','this is a login error','HIGH','RESOLVED','Login page','2026-11-02 17:20:52.267547',9,6,10,NULL,8,'','2026-11-11',1);
/*!40000 ALTER TABLE `issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue_approval`
--

DROP TABLE IF EXISTS `issue_approval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `decided_at` datetime(6) NOT NULL,
  `decision` varchar(16) NOT NULL,
  `remark` text,
  `stage` varchar(32) NOT NULL,
  `workflow_cycle` int NOT NULL,
  `actor_id` bigint NOT NULL,
  `issue_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_approval_cycle_stage` (`issue_id`,`workflow_cycle`,`stage`),
  KEY `FKaychs69q277n9e1u6kpfetmap` (`actor_id`),
  CONSTRAINT `FKaychs69q277n9e1u6kpfetmap` FOREIGN KEY (`actor_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKshypf69op4f6d98bugrsyd1c1` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue_approval`
--

LOCK TABLES `issue_approval` WRITE;
/*!40000 ALTER TABLE `issue_approval` DISABLE KEYS */;
INSERT INTO `issue_approval` VALUES (1,'2026-11-02 17:20:48.654479','APPROVED','','TEAM_LEAD',1,7,10),(2,'2026-11-02 17:20:52.220526','APPROVED','','PROJECT_MANAGER',1,7,10);
/*!40000 ALTER TABLE `issue_approval` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue_dependency`
--

DROP TABLE IF EXISTS `issue_dependency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `blocked_issue_id` bigint NOT NULL,
  `blocking_issue_id` bigint NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_dependency_pair` (`blocked_issue_id`,`blocking_issue_id`),
  KEY `ix_issue_dependency_blocker` (`blocking_issue_id`),
  KEY `FKgcbni79rrfs9opfuvv0cwuey1` (`created_by_user_id`),
  CONSTRAINT `FK5hyiqu5cxvgsftkykahy4qtak` FOREIGN KEY (`blocking_issue_id`) REFERENCES `issue` (`id`),
  CONSTRAINT `FKgcbni79rrfs9opfuvv0cwuey1` FOREIGN KEY (`created_by_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKrlkmnnl4hiy8jv6l1s0j3y3k6` FOREIGN KEY (`blocked_issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue_dependency`
--

LOCK TABLES `issue_dependency` WRITE;
/*!40000 ALTER TABLE `issue_dependency` DISABLE KEYS */;
/*!40000 ALTER TABLE `issue_dependency` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue_maintenance`
--

DROP TABLE IF EXISTS `issue_maintenance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_maintenance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `issue_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_maintenance_issue` (`issue_id`),
  CONSTRAINT `FKdfxbwc8epenhb9a30eh2cqeoo` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue_maintenance`
--

LOCK TABLES `issue_maintenance` WRITE;
/*!40000 ALTER TABLE `issue_maintenance` DISABLE KEYS */;
INSERT INTO `issue_maintenance` VALUES (1,NULL,'2026-12-02 17:20:52.232239','2026-11-02 17:20:52.232239','ACTIVE',10);
/*!40000 ALTER TABLE `issue_maintenance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `issue_maintenance_follow_up`
--

DROP TABLE IF EXISTS `issue_maintenance_follow_up`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_maintenance_follow_up` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reason` text NOT NULL,
  `reported_at` datetime(6) NOT NULL,
  `follow_up_issue_id` bigint NOT NULL,
  `original_issue_id` bigint NOT NULL,
  `reporter_id` bigint NOT NULL,
  `responsible_user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_maintenance_followup_issue` (`follow_up_issue_id`),
  KEY `FKsnmiai0w0h9s41fa84kvsuqcu` (`original_issue_id`),
  KEY `FKayaucwilp25299hxnxcqxhd5i` (`reporter_id`),
  KEY `FKfi9qd57jn0m23351mmvxk9tni` (`responsible_user_id`),
  CONSTRAINT `FK5m8ste73swoactqk978eko0mg` FOREIGN KEY (`follow_up_issue_id`) REFERENCES `issue` (`id`),
  CONSTRAINT `FKayaucwilp25299hxnxcqxhd5i` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKfi9qd57jn0m23351mmvxk9tni` FOREIGN KEY (`responsible_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKsnmiai0w0h9s41fa84kvsuqcu` FOREIGN KEY (`original_issue_id`) REFERENCES `issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `issue_maintenance_follow_up`
--

LOCK TABLES `issue_maintenance_follow_up` WRITE;
/*!40000 ALTER TABLE `issue_maintenance_follow_up` DISABLE KEYS */;
/*!40000 ALTER TABLE `issue_maintenance_follow_up` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `target_path` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb0yvoep4h4k92ipon31wmdf7e` (`user_id`),
  CONSTRAINT `FKb0yvoep4h4k92ipon31wmdf7e` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notification`
--

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
INSERT INTO `notification` VALUES (1,'2026-10-14 11:44:25.346149','You have been assigned to test issue \'issue#1\'',_binary '\0','TESTER_ASSIGNED',3,NULL),(2,'2026-10-14 11:44:25.359828','You were assigned issue \'issue#1\'',_binary '\0','ISSUE_ASSIGNED',2,NULL),(3,'2026-10-14 12:07:35.271920','New comment added to issue \'issue#1\'',_binary '\0','COMMENT_ADDED',2,NULL),(4,'2026-10-14 12:09:32.513826','New comment added to issue \'issue#1\'',_binary '\0','COMMENT_ADDED',2,NULL),(5,'2026-10-14 12:12:22.219428','You have been assigned to test issue \'issue#1\'',_binary '','TESTER_ASSIGNED',4,NULL),(6,'2026-10-14 12:12:40.958308','Testing started for issue \'issue#1\'',_binary '\0','TEST_STARTED',2,NULL),(7,'2026-10-14 12:12:51.731508','Issue \'issue#1\' passed testing',_binary '\0','TEST_PASSED',2,NULL),(8,'2026-10-14 17:09:07.292657','Issue \'issue#1\' changed status to READY_FOR_TESTING',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(9,'2026-10-14 17:09:07.323898','Issue \'issue#1\' is ready for testing',_binary '','READY_FOR_TESTING',4,NULL),(10,'2026-10-14 17:09:22.963819','You have been assigned to test issue \'login not working\'',_binary '','TESTER_ASSIGNED',4,NULL),(11,'2026-10-14 17:09:22.974475','You were assigned issue \'login not working\'',_binary '\0','ISSUE_ASSIGNED',2,NULL),(12,'2026-10-14 17:09:37.100446','Issue \'login not working\' changed status to CODE_REVIEW',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(13,'2026-10-16 11:25:26.136400','Testing started for issue \'issue#1\'',_binary '\0','TEST_STARTED',2,NULL),(14,'2026-10-16 11:26:16.356561','Issue \'issue#1\' PASSED testing; Scenario: login page; Expected: user can access the site; Actual: user got to access the site',_binary '\0','TEST_PASSED',2,NULL),(15,'2026-10-16 11:26:34.541197','Issue \'login not working\' changed status to READY_FOR_TESTING',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(16,'2026-10-16 11:26:34.544199','Issue \'login not working\' is ready for testing',_binary '','READY_FOR_TESTING',4,NULL),(17,'2026-10-16 11:27:22.190508','Testing started for issue \'login not working\'',_binary '\0','TEST_STARTED',2,NULL),(18,'2026-10-16 11:27:25.972276','Issue \'login not working\' PASSED testing',_binary '\0','TEST_PASSED',2,NULL),(19,'2026-10-16 11:28:17.426668','Issue \'issue page\' changed status to CODE_REVIEW',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(20,'2026-10-16 11:28:17.433453','You have been assigned to test issue \'issue page\'',_binary '\0','TESTER_ASSIGNED',3,NULL),(21,'2026-10-16 11:28:17.441836','You were assigned issue \'issue page\'',_binary '\0','ISSUE_ASSIGNED',2,NULL),(22,'2026-10-16 14:23:17.878006','You have been assigned to test issue \'issue page\'',_binary '','TESTER_ASSIGNED',4,NULL),(23,'2026-10-16 14:43:20.318594','Issue \'issue #4\' changed status to IN_PROGRESS',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(24,'2026-10-16 14:43:20.333399','You have been assigned to test issue \'issue #4\'',_binary '\0','TESTER_ASSIGNED',3,NULL),(25,'2026-10-16 14:43:20.342391','You were assigned issue \'issue #4\'',_binary '\0','ISSUE_ASSIGNED',2,NULL),(26,'2026-10-16 15:11:31.202110','You have been assigned to test issue \'login page\'',_binary '\0','TESTER_ASSIGNED',3,NULL),(27,'2026-10-16 15:11:31.218139','You were assigned issue \'login page\'',_binary '\0','ISSUE_ASSIGNED',2,NULL),(28,'2026-10-16 15:12:17.560252','New comment added to issue \'login page\'',_binary '\0','COMMENT_ADDED',2,NULL),(29,'2026-10-16 15:13:37.767482','Issue \'login page\' changed status to READY_FOR_TESTING',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(30,'2026-10-16 15:13:37.779797','Issue \'login page\' is ready for testing',_binary '\0','READY_FOR_TESTING',3,NULL),(31,'2026-10-16 15:13:49.134729','Testing started for issue \'login page\'',_binary '\0','TEST_STARTED',2,NULL),(32,'2026-10-16 15:14:14.742222','targetType=ISSUE; targetId=9; testOutcome=PASSED',_binary '\0','TEST_PASSED',2,NULL),(33,'2026-10-21 14:35:44.368980','Task \'login not working\' changed status to CODE_REVIEW',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(34,'2026-10-21 14:36:49.324691','Task \'issue#1\' changed status to READY_FOR_TESTING',_binary '\0','ISSUE_STATUS_CHANGED',2,NULL),(35,'2026-10-21 14:36:49.331007','Task \'issue#1\' is ready for testing',_binary '','READY_FOR_TESTING',4,NULL),(36,'2026-10-21 14:36:57.998251','Testing started for task \'issue#1\'',_binary '\0','TEST_STARTED',2,NULL),(37,'2026-10-28 17:29:24.563222','You were assigned task \'issue#1\'',_binary '','ISSUE_ASSIGNED',6,NULL),(38,'2026-11-02 17:15:54.917713','You were assigned task \'Login page\'',_binary '','ISSUE_ASSIGNED',9,NULL),(39,'2026-11-02 17:17:12.731200','Task \'Login page\' changed status to CODE_REVIEW',_binary '','ISSUE_STATUS_CHANGED',9,NULL),(40,'2026-11-02 17:19:59.890776','Task \'Login page\' changed status to READY_FOR_TESTING',_binary '','ISSUE_STATUS_CHANGED',9,NULL),(41,'2026-11-02 17:20:00.737232','Task \'Login page\' is ready for testing',_binary '\0','READY_FOR_TESTING',8,NULL),(42,'2026-11-02 17:20:11.219924','Testing started for task \'Login page\'',_binary '','TEST_STARTED',9,NULL),(43,'2026-11-02 17:20:16.704369','Testing passed. This task is waiting for your Team Lead approval.',_binary '','TEST_PASSED_AWAITING_TEAM_LEAD',7,NULL),(44,'2026-11-02 17:20:16.989577','Testing passed for task \'Login page\'',_binary '','TEST_PASSED',9,NULL),(45,'2026-11-02 17:20:48.759857','Team Lead approved this task. It is waiting for your final approval.',_binary '\0','PROJECT_MANAGER_APPROVAL',7,NULL),(46,'2026-11-02 17:20:52.249810','Final approval completed. Task \'Login page\' is resolved.',_binary '','APPROVAL_RESOLVED',9,NULL),(47,'2026-11-02 17:20:52.253417','Final approval completed. Task \'Login page\' is resolved.',_binary '\0','APPROVAL_RESOLVED',8,NULL);
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permission_catalogue`
--

DROP TABLE IF EXISTS `permission_catalogue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission_catalogue` (
  `permission_key` varchar(80) NOT NULL,
  `description` varchar(180) NOT NULL,
  `scope_type` varchar(20) NOT NULL,
  PRIMARY KEY (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permission_catalogue`
--

LOCK TABLES `permission_catalogue` WRITE;
/*!40000 ALTER TABLE `permission_catalogue` DISABLE KEYS */;
INSERT INTO `permission_catalogue` VALUES ('ASSIGNABLE_DEVELOPER','May be assigned as accountable developer','GLOBAL'),('ASSIGNABLE_TESTER','May be assigned as tester','GLOBAL'),('AUDIT_DELETE','Delete audit records','GLOBAL'),('AUDIT_READ','Read project audit history','PROJECT'),('AUDIT_READ_ALL','Read unscoped audit history','GLOBAL'),('AUTOMATION_MANAGE','Manage project workflow automation','PROJECT'),('COMMENT_CREATE','Create task comments','PROJECT'),('COMMENT_DELETE','Delete comments','PROJECT'),('COMMENT_READ','Read task comments','PROJECT'),('DASHBOARD_READ','Read project dashboard metrics','PROJECT'),('DASHBOARD_READ_ALL','Read unscoped dashboard metrics','GLOBAL'),('GITHUB_ISSUE_CREATE','Create or reconcile linked GitHub issues','PROJECT'),('ISSUE_APPROVE_PROJECT_MANAGER','Approve or reject Team Lead-approved tasks','PROJECT'),('ISSUE_APPROVE_TEAM_LEAD','Approve or reject tested tasks at Team Lead review','PROJECT'),('ISSUE_ASSIGN_DEVELOPER','Assign task developers','PROJECT'),('ISSUE_CREATE','Create tasks','PROJECT'),('ISSUE_DELETE','Delete tasks','PROJECT'),('ISSUE_DEPENDENCY_MANAGE','Create and remove task dependencies','PROJECT'),('ISSUE_READ','Read tasks','PROJECT'),('ISSUE_STATUS_ADVANCE','Advance assigned tasks through developer states','PROJECT'),('ISSUE_STATUS_MANAGE','Set permitted manager-level task statuses','PROJECT'),('ISSUE_UPDATE','Update tasks within assigned ownership rules','PROJECT'),('ISSUE_UPDATE_ALL','Update any task in this scope','PROJECT'),('ISSUE_UPDATE_ASSIGNED','Update tasks assigned to the current user','PROJECT'),('PROJECT_CREATE','Create projects','GLOBAL'),('PROJECT_DELETE','Delete projects','PROJECT'),('PROJECT_READ','Read projects','PROJECT'),('PROJECT_READ_ALL','Read all projects, including private projects','GLOBAL'),('PROJECT_ROLE_MANAGE','Assign roles to members of this project','PROJECT'),('PROJECT_UPDATE','Update project details','PROJECT'),('REPORT_READ','Read project and sprint reports','PROJECT'),('TEAM_ACTIVITY_READ','Read team activity','PROJECT_TEAM'),('TEAM_CREATE','Create teams','GLOBAL'),('TEAM_MANAGE','Manage team membership','PROJECT_TEAM'),('TEAM_READ','Read team details','PROJECT_TEAM'),('TEAM_READ_ALL','Read all teams','GLOBAL'),('TEAM_ROLE_MANAGE','Assign roles to existing team members','PROJECT_TEAM'),('TEST_EXECUTE','Start and complete assigned testing','PROJECT'),('TESTER_ASSIGN','Assign testers to tasks','PROJECT'),('TESTING_HISTORY_READ','Read task testing history','PROJECT'),('TESTING_MY_READ','Read assigned testing work','PROJECT'),('TESTING_OVERRIDE','Override assigned-tester ownership checks','GLOBAL'),('TESTING_QUEUE_READ','Read the testing queue','PROJECT'),('USER_MANAGE','Create and manage user accounts','GLOBAL'),('USER_READ','Read the administrative user directory','GLOBAL');
/*!40000 ALTER TABLE `permission_catalogue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project`
--

DROP TABLE IF EXISTS `project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `github_repository_name` varchar(100) DEFAULT NULL,
  `github_repository_owner` varchar(39) DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_team` (`team_id`),
  CONSTRAINT `fk_project_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project`
--

LOCK TABLES `project` WRITE;
/*!40000 ALTER TABLE `project` DISABLE KEYS */;
INSERT INTO `project` VALUES (1,'A project management platform','2026-12-17','Devflow','2026-09-29','IN_PROGRESS',NULL,NULL,NULL,NULL),(2,'A new project','2026-11-11','Project #2','2026-10-05','IN_PROGRESS',NULL,NULL,NULL,NULL),(3,'this is a full stack pr','2026-10-16','Project #3','2026-10-06','ACTIVE',NULL,NULL,NULL,NULL),(4,'Team Projexct','2026-12-03','Team Project','2026-10-05','IN_PROGRESS',NULL,NULL,1,NULL),(6,'DCR is a test project','2027-01-21','DCR','2026-10-21','ACTIVE',NULL,NULL,4,NULL);
/*!40000 ALTER TABLE `project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_role_assignment`
--

DROP TABLE IF EXISTS `project_role_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_role_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_role_assignment` (`project_id`,`user_id`),
  KEY `FKj2h1j08v4njslwr9lul02mro3` (`role_id`),
  KEY `FKishp79rdj4wtinqsnmt5jt9sj` (`user_id`),
  CONSTRAINT `FKishp79rdj4wtinqsnmt5jt9sj` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKj2h1j08v4njslwr9lul02mro3` FOREIGN KEY (`role_id`) REFERENCES `role_definition` (`id`),
  CONSTRAINT `FKnbywgbmhqynx2mtvpa4xt2grh` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_role_assignment`
--

LOCK TABLES `project_role_assignment` WRITE;
/*!40000 ALTER TABLE `project_role_assignment` DISABLE KEYS */;
INSERT INTO `project_role_assignment` VALUES (1,6,6,10),(2,6,15,7),(3,6,7,9),(4,6,8,8);
/*!40000 ALTER TABLE `project_role_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_definition`
--

DROP TABLE IF EXISTS `role_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `name` varchar(120) NOT NULL,
  `retired` bit(1) NOT NULL,
  `scope_type` varchar(20) NOT NULL,
  `system_role` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(250) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_definition`
--

LOCK TABLES `role_definition` WRITE;
/*!40000 ALTER TABLE `role_definition` DISABLE KEYS */;
INSERT INTO `role_definition` VALUES (1,'ADMIN','2026-10-30 17:00:01.568195','Administrator',_binary '\0','GLOBAL',_binary '','2026-10-30 17:00:01.568195',NULL),(2,'PROJECT_MANAGER','2026-10-30 17:00:01.604097','Project Manager',_binary '\0','GLOBAL',_binary '','2026-10-30 17:00:01.604097',NULL),(3,'DEVELOPER','2026-10-30 17:00:01.611091','Developer',_binary '\0','GLOBAL',_binary '','2026-10-30 17:00:01.611091',NULL),(4,'TESTER','2026-10-30 17:00:01.617167','Tester',_binary '\0','GLOBAL',_binary '','2026-10-30 17:00:01.617167',NULL),(5,'VIEWER','2026-10-30 17:00:01.625174','Viewer',_binary '\0','GLOBAL',_binary '','2026-10-30 17:00:01.625174',NULL),(6,'PROJECT_MANAGER_PROJECT','2026-10-30 17:00:01.633307','Project Manager (Project)',_binary '\0','PROJECT',_binary '','2026-10-30 17:00:01.633307',NULL),(7,'DEVELOPER_PROJECT','2026-10-30 17:00:01.640512','Developer (Project)',_binary '\0','PROJECT',_binary '','2026-10-30 17:00:01.640512',NULL),(8,'TESTER_PROJECT','2026-10-30 17:00:01.648912','Tester (Project)',_binary '\0','PROJECT',_binary '','2026-10-30 17:00:01.648912',NULL),(9,'VIEWER_PROJECT','2026-10-30 17:00:01.656010','Viewer (Project)',_binary '\0','PROJECT',_binary '','2026-10-30 17:00:01.656010',NULL),(10,'PROJECT_MANAGER_TEAM','2026-10-30 17:00:01.661743','Project Manager (Team)',_binary '\0','PROJECT_TEAM',_binary '','2026-10-30 17:00:01.661743',NULL),(11,'TEAM_LEAD_TEAM','2026-10-30 17:00:01.667273','Team Lead',_binary '\0','PROJECT_TEAM',_binary '','2026-10-30 17:00:01.667273',NULL),(12,'DEVELOPER_TEAM','2026-10-30 17:00:01.674264','Developer (Team)',_binary '\0','PROJECT_TEAM',_binary '','2026-10-30 17:00:01.674264',NULL),(13,'TESTER_TEAM','2026-10-30 17:00:01.679570','Tester (Team)',_binary '\0','PROJECT_TEAM',_binary '','2026-10-30 17:00:01.679570',NULL),(14,'VIEWER_TEAM','2026-10-30 17:00:01.685659','Viewer (Team)',_binary '\0','PROJECT_TEAM',_binary '','2026-10-30 17:00:01.685659',NULL),(15,'TEAM_LEAD_PROJECT','2026-11-02 15:26:56.534639','Team Lead (Project)',_binary '\0','PROJECT',_binary '','2026-11-02 15:26:56.534639',NULL),(16,'TEAM_LEAD','2026-11-02 17:11:14.176359','Team-Lead',_binary '\0','GLOBAL',_binary '\0','2026-11-02 17:11:14.176359',NULL);
/*!40000 ALTER TABLE `role_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permission`
--

DROP TABLE IF EXISTS `role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission` (
  `role_id` bigint NOT NULL,
  `permission_key` varchar(80) NOT NULL,
  PRIMARY KEY (`role_id`,`permission_key`),
  KEY `FKf1u4jlg5rdhd0vde8kbu6ost9` (`permission_key`),
  CONSTRAINT `FKe6onrsc6iwvq3c12jl300ge6b` FOREIGN KEY (`role_id`) REFERENCES `role_definition` (`id`),
  CONSTRAINT `FKf1u4jlg5rdhd0vde8kbu6ost9` FOREIGN KEY (`permission_key`) REFERENCES `permission_catalogue` (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permission`
--

LOCK TABLES `role_permission` WRITE;
/*!40000 ALTER TABLE `role_permission` DISABLE KEYS */;
INSERT INTO `role_permission` VALUES (1,'ASSIGNABLE_DEVELOPER'),(3,'ASSIGNABLE_DEVELOPER'),(16,'ASSIGNABLE_DEVELOPER'),(1,'ASSIGNABLE_TESTER'),(4,'ASSIGNABLE_TESTER'),(16,'ASSIGNABLE_TESTER'),(1,'AUDIT_DELETE'),(1,'AUDIT_READ'),(2,'AUDIT_READ'),(6,'AUDIT_READ'),(1,'AUDIT_READ_ALL'),(1,'AUTOMATION_MANAGE'),(2,'AUTOMATION_MANAGE'),(6,'AUTOMATION_MANAGE'),(16,'AUTOMATION_MANAGE'),(1,'COMMENT_CREATE'),(2,'COMMENT_CREATE'),(3,'COMMENT_CREATE'),(6,'COMMENT_CREATE'),(7,'COMMENT_CREATE'),(16,'COMMENT_CREATE'),(1,'COMMENT_DELETE'),(2,'COMMENT_DELETE'),(6,'COMMENT_DELETE'),(16,'COMMENT_DELETE'),(1,'COMMENT_READ'),(2,'COMMENT_READ'),(3,'COMMENT_READ'),(4,'COMMENT_READ'),(5,'COMMENT_READ'),(6,'COMMENT_READ'),(7,'COMMENT_READ'),(8,'COMMENT_READ'),(9,'COMMENT_READ'),(15,'COMMENT_READ'),(16,'COMMENT_READ'),(1,'DASHBOARD_READ'),(2,'DASHBOARD_READ'),(3,'DASHBOARD_READ'),(4,'DASHBOARD_READ'),(5,'DASHBOARD_READ'),(6,'DASHBOARD_READ'),(7,'DASHBOARD_READ'),(8,'DASHBOARD_READ'),(9,'DASHBOARD_READ'),(16,'DASHBOARD_READ'),(1,'DASHBOARD_READ_ALL'),(16,'DASHBOARD_READ_ALL'),(1,'GITHUB_ISSUE_CREATE'),(2,'GITHUB_ISSUE_CREATE'),(6,'GITHUB_ISSUE_CREATE'),(16,'ISSUE_APPROVE_PROJECT_MANAGER'),(15,'ISSUE_APPROVE_TEAM_LEAD'),(16,'ISSUE_APPROVE_TEAM_LEAD'),(1,'ISSUE_ASSIGN_DEVELOPER'),(2,'ISSUE_ASSIGN_DEVELOPER'),(6,'ISSUE_ASSIGN_DEVELOPER'),(16,'ISSUE_ASSIGN_DEVELOPER'),(1,'ISSUE_CREATE'),(2,'ISSUE_CREATE'),(3,'ISSUE_CREATE'),(6,'ISSUE_CREATE'),(7,'ISSUE_CREATE'),(16,'ISSUE_CREATE'),(1,'ISSUE_DELETE'),(2,'ISSUE_DELETE'),(6,'ISSUE_DELETE'),(16,'ISSUE_DELETE'),(1,'ISSUE_DEPENDENCY_MANAGE'),(2,'ISSUE_DEPENDENCY_MANAGE'),(6,'ISSUE_DEPENDENCY_MANAGE'),(16,'ISSUE_DEPENDENCY_MANAGE'),(1,'ISSUE_READ'),(2,'ISSUE_READ'),(3,'ISSUE_READ'),(4,'ISSUE_READ'),(5,'ISSUE_READ'),(6,'ISSUE_READ'),(7,'ISSUE_READ'),(8,'ISSUE_READ'),(9,'ISSUE_READ'),(15,'ISSUE_READ'),(16,'ISSUE_READ'),(1,'ISSUE_STATUS_ADVANCE'),(3,'ISSUE_STATUS_ADVANCE'),(7,'ISSUE_STATUS_ADVANCE'),(16,'ISSUE_STATUS_ADVANCE'),(1,'ISSUE_STATUS_MANAGE'),(2,'ISSUE_STATUS_MANAGE'),(6,'ISSUE_STATUS_MANAGE'),(16,'ISSUE_STATUS_MANAGE'),(1,'ISSUE_UPDATE'),(2,'ISSUE_UPDATE'),(3,'ISSUE_UPDATE'),(6,'ISSUE_UPDATE'),(7,'ISSUE_UPDATE'),(16,'ISSUE_UPDATE'),(1,'ISSUE_UPDATE_ALL'),(2,'ISSUE_UPDATE_ALL'),(6,'ISSUE_UPDATE_ALL'),(16,'ISSUE_UPDATE_ALL'),(1,'ISSUE_UPDATE_ASSIGNED'),(3,'ISSUE_UPDATE_ASSIGNED'),(7,'ISSUE_UPDATE_ASSIGNED'),(16,'ISSUE_UPDATE_ASSIGNED'),(1,'PROJECT_CREATE'),(2,'PROJECT_CREATE'),(16,'PROJECT_CREATE'),(1,'PROJECT_DELETE'),(2,'PROJECT_DELETE'),(6,'PROJECT_DELETE'),(16,'PROJECT_DELETE'),(1,'PROJECT_READ'),(2,'PROJECT_READ'),(3,'PROJECT_READ'),(4,'PROJECT_READ'),(5,'PROJECT_READ'),(6,'PROJECT_READ'),(7,'PROJECT_READ'),(8,'PROJECT_READ'),(9,'PROJECT_READ'),(15,'PROJECT_READ'),(16,'PROJECT_READ'),(1,'PROJECT_READ_ALL'),(16,'PROJECT_READ_ALL'),(1,'PROJECT_ROLE_MANAGE'),(6,'PROJECT_ROLE_MANAGE'),(16,'PROJECT_ROLE_MANAGE'),(1,'PROJECT_UPDATE'),(2,'PROJECT_UPDATE'),(6,'PROJECT_UPDATE'),(16,'PROJECT_UPDATE'),(1,'REPORT_READ'),(2,'REPORT_READ'),(3,'REPORT_READ'),(4,'REPORT_READ'),(5,'REPORT_READ'),(6,'REPORT_READ'),(7,'REPORT_READ'),(8,'REPORT_READ'),(9,'REPORT_READ'),(16,'REPORT_READ'),(1,'TEAM_ACTIVITY_READ'),(10,'TEAM_ACTIVITY_READ'),(11,'TEAM_ACTIVITY_READ'),(12,'TEAM_ACTIVITY_READ'),(13,'TEAM_ACTIVITY_READ'),(14,'TEAM_ACTIVITY_READ'),(16,'TEAM_ACTIVITY_READ'),(1,'TEAM_CREATE'),(2,'TEAM_CREATE'),(16,'TEAM_CREATE'),(1,'TEAM_MANAGE'),(10,'TEAM_MANAGE'),(16,'TEAM_MANAGE'),(1,'TEAM_READ'),(10,'TEAM_READ'),(11,'TEAM_READ'),(12,'TEAM_READ'),(13,'TEAM_READ'),(14,'TEAM_READ'),(16,'TEAM_READ'),(1,'TEAM_READ_ALL'),(16,'TEAM_READ_ALL'),(1,'TEAM_ROLE_MANAGE'),(10,'TEAM_ROLE_MANAGE'),(16,'TEAM_ROLE_MANAGE'),(1,'TEST_EXECUTE'),(4,'TEST_EXECUTE'),(8,'TEST_EXECUTE'),(16,'TEST_EXECUTE'),(1,'TESTER_ASSIGN'),(2,'TESTER_ASSIGN'),(6,'TESTER_ASSIGN'),(16,'TESTER_ASSIGN'),(1,'TESTING_HISTORY_READ'),(2,'TESTING_HISTORY_READ'),(3,'TESTING_HISTORY_READ'),(4,'TESTING_HISTORY_READ'),(5,'TESTING_HISTORY_READ'),(6,'TESTING_HISTORY_READ'),(7,'TESTING_HISTORY_READ'),(8,'TESTING_HISTORY_READ'),(9,'TESTING_HISTORY_READ'),(16,'TESTING_HISTORY_READ'),(1,'TESTING_MY_READ'),(4,'TESTING_MY_READ'),(8,'TESTING_MY_READ'),(16,'TESTING_MY_READ'),(1,'TESTING_OVERRIDE'),(16,'TESTING_OVERRIDE'),(1,'TESTING_QUEUE_READ'),(2,'TESTING_QUEUE_READ'),(4,'TESTING_QUEUE_READ'),(6,'TESTING_QUEUE_READ'),(8,'TESTING_QUEUE_READ'),(16,'TESTING_QUEUE_READ'),(1,'USER_MANAGE'),(16,'USER_MANAGE'),(1,'USER_READ'),(16,'USER_READ');
/*!40000 ALTER TABLE `role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sprint`
--

DROP TABLE IF EXISTS `sprint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sprint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_date` date DEFAULT NULL,
  `goal` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKerwve0blrvfhqm1coxo69f0xr` (`project_id`),
  CONSTRAINT `FKerwve0blrvfhqm1coxo69f0xr` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sprint`
--

LOCK TABLES `sprint` WRITE;
/*!40000 ALTER TABLE `sprint` DISABLE KEYS */;
INSERT INTO `sprint` VALUES (1,'2026-11-11','Complete within the deadine','Complete Project #2','2026-10-05','ACTIVE',2);
/*!40000 ALTER TABLE `sprint` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sprint_burndown_snapshot`
--

DROP TABLE IF EXISTS `sprint_burndown_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sprint_burndown_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `captured_at` datetime(6) NOT NULL,
  `remaining_issues` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  `total_issues` bigint NOT NULL,
  `sprint_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sprint_burndown_date` (`sprint_id`,`snapshot_date`),
  CONSTRAINT `fk_sprint_burndown_sprint` FOREIGN KEY (`sprint_id`) REFERENCES `sprint` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sprint_burndown_snapshot`
--

LOCK TABLES `sprint_burndown_snapshot` WRITE;
/*!40000 ALTER TABLE `sprint_burndown_snapshot` DISABLE KEYS */;
INSERT INTO `sprint_burndown_snapshot` VALUES (1,'2026-10-16 09:11:27.689424',1,'2026-10-16',2,1),(2,'2026-10-21 06:09:57.109142',1,'2026-10-21',2,1),(3,'2026-10-23 05:26:32.356553',2,'2026-10-23',2,1),(4,'2026-10-24 16:52:11.338818',2,'2026-10-24',2,1),(5,'2026-10-28 05:28:50.345976',2,'2026-10-28',2,1),(6,'2026-10-29 18:06:45.342617',2,'2026-10-29',2,1),(7,'2026-10-30 05:31:02.540787',2,'2026-10-30',2,1),(8,'2026-11-02 05:16:36.113579',2,'2026-11-02',2,1),(9,'2026-11-04 05:11:03.694616',2,'2026-11-04',2,1),(10,'2026-11-05 05:08:38.342417',2,'2026-11-05',2,1);
/*!40000 ALTER TABLE `sprint_burndown_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team`
--

DROP TABLE IF EXISTS `team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) NOT NULL,
  `manager_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_name` (`name`),
  KEY `fk_team_manager` (`manager_id`),
  CONSTRAINT `fk_team_manager` FOREIGN KEY (`manager_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team`
--

LOCK TABLES `team` WRITE;
/*!40000 ALTER TABLE `team` DISABLE KEYS */;
INSERT INTO `team` VALUES (1,'Devlopers',1),(2,'Project',1),(3,'DCR project',1),(4,'DCR TEAM',10);
/*!40000 ALTER TABLE `team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_member`
--

DROP TABLE IF EXISTS `team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_member` (
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  UNIQUE KEY `pk_team_member` (`team_id`,`user_id`),
  KEY `fk_team_member_user` (`user_id`),
  CONSTRAINT `fk_team_member_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `fk_team_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_member`
--

LOCK TABLES `team_member` WRITE;
/*!40000 ALTER TABLE `team_member` DISABLE KEYS */;
INSERT INTO `team_member` VALUES (1,1),(2,1),(3,1),(1,2),(2,2),(3,2),(1,3),(1,4),(3,4),(3,6),(4,7),(4,8),(4,9),(4,10);
/*!40000 ALTER TABLE `team_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_role_assignment`
--

DROP TABLE IF EXISTS `team_role_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_role_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_role_assignment` (`team_id`,`user_id`),
  KEY `FKpdy1k42nr87glnqgfpuwsu3e8` (`role_id`),
  KEY `FKhweojxlxopc8r1tltx0hs8gbw` (`user_id`),
  CONSTRAINT `FKhweojxlxopc8r1tltx0hs8gbw` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `FKk2idic2rgbmmw9dge13rx7ali` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKpdy1k42nr87glnqgfpuwsu3e8` FOREIGN KEY (`role_id`) REFERENCES `role_definition` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_role_assignment`
--

LOCK TABLES `team_role_assignment` WRITE;
/*!40000 ALTER TABLE `team_role_assignment` DISABLE KEYS */;
INSERT INTO `team_role_assignment` VALUES (1,11,4,7),(2,12,4,9),(3,13,4,8),(4,10,4,10);
/*!40000 ALTER TABLE `team_role_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_execution`
--

DROP TABLE IF EXISTS `test_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `result` varchar(255) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `issue_id` bigint DEFAULT NULL,
  `tester_id` bigint DEFAULT NULL,
  `actual_result` text,
  `expected_result` text,
  `test_scenario` text,
  `workflow_cycle` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7bwhean30io7jrnd2ijlmhq8p` (`issue_id`),
  KEY `FKgr8copxavlgx2s4a9wh3rc9ii` (`tester_id`),
  CONSTRAINT `FK7bwhean30io7jrnd2ijlmhq8p` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`),
  CONSTRAINT `FKgr8copxavlgx2s4a9wh3rc9ii` FOREIGN KEY (`tester_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_execution`
--

LOCK TABLES `test_execution` WRITE;
/*!40000 ALTER TABLE `test_execution` DISABLE KEYS */;
INSERT INTO `test_execution` VALUES (1,'2026-10-14 12:12:51.728505','2026-10-14 12:12:40.946461','','PASSED','2026-10-14 12:12:40.946461','COMPLETED',5,4,NULL,NULL,NULL,0),(2,'2026-10-16 11:26:16.323824','2026-10-16 11:25:26.057038','','PASSED','2026-10-16 11:25:26.057038','COMPLETED',5,1,'user got to access the site','user can access the site','login page',0),(3,'2026-10-16 11:27:25.968659','2026-10-16 11:27:22.179745','','PASSED','2026-10-16 11:27:22.179745','COMPLETED',6,1,NULL,NULL,NULL,0),(4,'2026-10-16 15:14:14.737385','2026-10-16 15:13:49.123917','','PASSED','2026-10-16 15:13:49.123917','COMPLETED',9,1,NULL,NULL,NULL,0),(5,NULL,'2026-10-21 14:36:57.984272',NULL,NULL,'2026-10-21 14:36:57.984272','IN_PROGRESS',5,1,NULL,NULL,NULL,0),(6,'2026-11-02 17:20:16.557523','2026-11-02 17:20:11.208579','','PASSED','2026-11-02 17:20:11.208579','COMPLETED',10,8,NULL,NULL,NULL,1);
/*!40000 ALTER TABLE `test_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `role` varchar(255) DEFAULT NULL,
  `avatar_key` varchar(64) DEFAULT NULL,
  `admin_recovery` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin@devflow.com',1,'DevFlow Admin','$2a$10$QHdSCCIqWbvgNBMdrnvcXOj1lzt1I7jimAVvkACQXFXcXTiPTn8Wa','ADMIN','95286649-9cbd-4abb-b84e-b358f98759bb.jpg',_binary '\0'),(2,'harsh@devflow.com',1,'Harsh','$2a$10$rEumptNP6kLqznbHPK.r6.bKU972h3VdwFIFKF2XBN.uokCMgS7QG','DEVELOPER',NULL,_binary '\0'),(3,'hardik@devflow.com',1,'hardik','$2a$10$hrpCBNx5L1vvvDjaMegMrOx.HcP43JUYrc.kHwmrSeZPCKkYjvAB6','TESTER',NULL,_binary '\0'),(4,'hardik@gmail.com',1,'Hardik','$2a$10$fHzaNhtmHuQ58j4.Uz4gQOUbxTNXw6r7LASnixtkwNg4zbeD/roaO','TESTER',NULL,_binary '\0'),(5,'harsh@gmail.com',1,'harsh','$2a$10$EJ8IOcS7EmRStKUcKjBpReb/8bFT7dB2ojCrs/iK2P4LsOyxZ6yjO','VIEWER',NULL,_binary '\0'),(6,'ishan@gmail.com',1,'Ishan','$2a$10$94n4JJEV3DqmHG.mTeHHm.Om/B5CYLgmwUhlMxL3mZ1D8B8hyeF1O','DEVELOPER',NULL,_binary '\0'),(7,'aditya@gmail.com',1,'Aditya','$2a$10$y1xLc5GLJTC71NAgq54CW.OCrpwJMHhrZFN7kQH.8vv2m22EkEd5G','TEAM_LEAD',NULL,_binary '\0'),(8,'laksh@gmail.com',1,'Laksh','$2a$10$19OGpQR2e60BWCkZ16FZHeIzOCt80psEVoLhf67UZIAtXu8bR6Aim','TESTER',NULL,_binary '\0'),(9,'maanit@gmail.com',1,'Maanit','$2a$10$GHyvYV5E4P4iYCV3y4Cxd.kM7uey8JM58SV4oi0x.9WDAAf8COXm.','DEVELOPER','392b08c4-5cb8-456a-b95c-b86a684b5be8.jpg',_binary '\0'),(10,'mridul@gmail.com',1,'Mridul','$2a$10$XB6XMnuQpJpJMzKgqUN.ceISbZDxCw76VXf9yAOXvMYfTkEzyaeuG','PROJECT_MANAGER','7d807089-8aa1-4cae-bc4c-78f8024528a9.jpg',_binary '\0');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 12:47:00
