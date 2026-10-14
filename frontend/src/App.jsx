import { BrowserRouter, Navigate, Routes, Route } from "react-router-dom";

import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import Project from "./pages/Project";
import Issues from "./pages/Issues";
import Layout from "./components/Layout";
import Sprints from "./pages/Sprints";
import Team from "./pages/Team";
import Account from "./pages/Account";
import Settings from "./pages/Settings";
import Comments from "./pages/Comments";
import Notifications from "./pages/Notifications";
import TeamWorkload from "./pages/TeamWorkload";
import AuditLogs from "./pages/AuditLogs";
import Reports from "./pages/Reports";
import CodeReviews from "./pages/CodeReviews";
import Activity from "./pages/Activity";
import TestingQueue from "./pages/TestingQueue";
import MyTests from "./pages/MyTests";
import TestingWorkspace from "./pages/TestingWorkspace";
function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route element={<Layout />}>
                    <Route path="/dashboard" element={<Dashboard />} />
                    <Route path="/projects" element={<Project />} />
                    <Route path="/issues" element={<Issues />} />
                    <Route path="/team" element={<Team />} />
                    <Route path="/sprints" element={<Sprints/>}></Route>
                    <Route path="/account" element={<Account />} />
                    <Route path="/settings" element={<Settings />} />
                    <Route path="/comments" element={<Comments />} />
                    <Route path="/notifications" element={<Notifications />} />
                    <Route path="/team-workload" element={<TeamWorkload />} />
                    <Route path="/audit-logs" element={<AuditLogs />} />
                    <Route path="/reports" element={<Reports />} />
                    <Route path="/code-reviews" element={<CodeReviews />} />
                    <Route path="/activity" element={<Activity />} />
                    <Route path="/testing" element={<TestingQueue />} />
                    <Route path="/testing-queue" element={<TestingQueue />} />
                    <Route path="/my-tests" element={<MyTests />} />
                    <Route path="/testing/:issueId" element={<TestingWorkspace />} />
                    <Route path="*" element={<Navigate to="/dashboard" replace />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}

export default App;
