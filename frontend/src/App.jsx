import { BrowserRouter, Navigate, Routes, Route } from "react-router-dom";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import Project from "./pages/Project";
import Issues from "./pages/Issues";
import Layout from "./components/Layout";
import PermissionGuard from "./components/PermissionGuard";
import Sprints from "./pages/Sprints";
import Team from "./pages/Team";
import PrivateTeams from "./pages/PrivateTeams";
import Account from "./pages/Account";
import Settings from "./pages/Settings";
import Comments from "./pages/Comments";
import Notifications from "./pages/Notifications";
import TeamWorkload from "./pages/TeamWorkload";
import AuditLogs from "./pages/AuditLogs";
import Reports from "./pages/Reports";
import CodeReviews from "./pages/CodeReviews";
import Activity from "./pages/Activity";
import Automations from "./pages/Automations";
import TestingQueue from "./pages/TestingQueue";
import MyTests from "./pages/MyTests";
import TestingWorkspace from "./pages/TestingWorkspace";
import AdminPanel from "./pages/AdminPanel";

const guarded = (permissions, page, all = []) => <PermissionGuard any={permissions} all={all}>{page}</PermissionGuard>;

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route element={<Layout />}>
                    <Route path="/dashboard" element={guarded(["DASHBOARD_READ", "DASHBOARD_READ_ALL", "ISSUE_APPROVE_TEAM_LEAD", "ISSUE_APPROVE_PROJECT_MANAGER"], <Dashboard />)} />
                    <Route path="/projects" element={guarded(["PROJECT_READ", "PROJECT_READ_ALL"], <Project />)} />
                    <Route path="/private-teams" element={guarded(["TEAM_READ", "TEAM_READ_ALL", "TEAM_CREATE"], <PrivateTeams />)} />
                    <Route path="/issues" element={guarded(["ISSUE_READ"], <Issues />)} />
                    <Route path="/team" element={guarded(["USER_READ"], <Team />)} />
                    <Route path="/sprints" element={guarded(["PROJECT_READ", "PROJECT_READ_ALL"], <Sprints />)} />
                    <Route path="/account" element={<Account />} />
                    <Route path="/settings" element={<Settings />} />
                    <Route path="/admin" element={<PermissionGuard recovery><AdminPanel /></PermissionGuard>} />
                    <Route path="/comments" element={guarded(["COMMENT_READ"], <Comments />)} />
                    <Route path="/notifications" element={<Notifications />} />
                    <Route path="/team-workload" element={guarded(["DASHBOARD_READ", "DASHBOARD_READ_ALL"], <TeamWorkload />)} />
                    <Route path="/audit-logs" element={guarded(["AUDIT_READ", "AUDIT_READ_ALL"], <AuditLogs />)} />
                    <Route path="/reports" element={guarded(["REPORT_READ"], <Reports />)} />
                    <Route path="/automations" element={guarded(["AUTOMATION_MANAGE"], <Automations />)} />
                    <Route path="/code-reviews" element={guarded(["ISSUE_READ"], <CodeReviews />)} />
                    <Route path="/activity" element={guarded(["PROJECT_READ", "PROJECT_READ_ALL"], <Activity />, ["ISSUE_READ"])} />
                    <Route path="/testing" element={guarded(["TESTING_QUEUE_READ", "TESTING_MY_READ"], <TestingQueue />)} />
                    <Route path="/testing-queue" element={guarded(["TESTING_QUEUE_READ", "TESTING_MY_READ"], <TestingQueue />)} />
                    <Route path="/my-tests" element={guarded(["TESTING_MY_READ"], <MyTests />)} />
                    <Route path="/testing/:issueId" element={guarded(["ISSUE_READ", "TEST_EXECUTE", "TESTING_HISTORY_READ"], <TestingWorkspace />)} />
                    <Route path="*" element={<Navigate to="/dashboard" replace />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;
