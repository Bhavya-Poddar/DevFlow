import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { responseErrorMessage } from "../utils/responseError";

const API = "http://localhost:8080/api";
const uniqueById = issues => [...new Map(issues.filter(issue => issue?.id != null).map(issue => [String(issue.id), issue])).values()];
const valueName = value => typeof value === "string" ? value : value?.name || value?.username || "Unassigned";
const dateLabel = value => value && !Number.isNaN(new Date(value).getTime())
    ? new Date(value).toLocaleString()
    : "—";

export default function TestingQueue() {
    const [needsTesting, setNeedsTesting] = useState([]);
    const [currentlyTesting, setCurrentlyTesting] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState("");

    const load = useCallback(async ({ initial = false } = {}) => {
        if (initial) setLoading(true);
        else setRefreshing(true);
        setError("");
        const headers = { Authorization: `Bearer ${localStorage.getItem("token")}` };

        try {
            const userResponse = await fetch(`${API}/users/me`, { headers });
            if (!userResponse.ok) throw new Error(await responseErrorMessage(userResponse, "Could not load your account."));
            const user = await userResponse.json();

            if (user?.role === "TESTER") {
                const response = await fetch(`${API}/testing/my`, { headers });
                if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not load your assigned testing queue."));
                const records = await response.json();
                if (!Array.isArray(records)) throw new Error("The assigned testing queue response was not a JSON array.");
                setNeedsTesting(uniqueById(records.filter(issue => issue?.status === "READY_FOR_TESTING")));
                setCurrentlyTesting(uniqueById(records.filter(issue => issue?.status === "TESTING")));
                return;
            }

            if (!["ADMIN", "PROJECT_MANAGER"].includes(user?.role)) {
                throw new Error("The testing queue is not available for your role.");
            }

            const [queueResponse, issuesResponse] = await Promise.all([
                fetch(`${API}/testing/queue`, { headers }),
                fetch(`${API}/issues`, { headers })
            ]);
            if (!queueResponse.ok) throw new Error(await responseErrorMessage(queueResponse, "Could not load issues needing testing."));
            if (!issuesResponse.ok) throw new Error(await responseErrorMessage(issuesResponse, "Could not load active testing issues."));
            const [queueData, issueData] = await Promise.all([queueResponse.json(), issuesResponse.json()]);
            if (!Array.isArray(queueData)) throw new Error("The testing queue response was not a JSON array.");
            if (!Array.isArray(issueData)) throw new Error("The issue list response was not a JSON array.");
            setNeedsTesting(uniqueById(queueData.filter(issue => issue?.status === "READY_FOR_TESTING")));
            setCurrentlyTesting(uniqueById(issueData.filter(issue => issue?.status === "TESTING")));
        } catch (reason) {
            setNeedsTesting([]);
            setCurrentlyTesting([]);
            setError(reason.message || "Could not load the testing queue.");
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, []);

    useEffect(() => {
        void Promise.resolve().then(() => load({ initial: true }));
    }, [load]);

    return <main className="mx-auto max-w-7xl space-y-6">
        <header className="flex flex-wrap items-end justify-between gap-4">
            <div>
                <p className="text-sm font-semibold uppercase tracking-wide text-blue-400">TESTING WORKSPACE</p>
                <h1 className="mt-2 text-3xl font-bold text-white">Testing Queue</h1>
                <p className="mt-2 text-gray-400">Track issues waiting for testing and tests already underway.</p>
            </div>
            <button type="button" onClick={() => void load()} disabled={refreshing || loading} className="rounded-lg border border-gray-700 px-4 py-2 text-sm text-gray-200 hover:bg-gray-800 disabled:opacity-50">
                {refreshing ? "Refreshing…" : "Refresh"}
            </button>
        </header>
        {error && <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-red-300">{error}</p>}
        {loading ? <p className="text-gray-400">Loading testing queue…</p> : <div className="grid gap-6 xl:grid-cols-2">
            <Section title="Needs Testing" empty="No issues are waiting for testing." issues={needsTesting} />
            <Section title="Currently Testing" empty="No tests are currently underway." issues={currentlyTesting} />
        </div>}
    </main>;
}

function Section({ title, empty, issues }) {
    return <section className="rounded-xl border border-gray-800 bg-gray-900 p-5">
        <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-semibold text-white">{title}</h2><span className="rounded-full bg-gray-800 px-2.5 py-1 text-xs text-gray-300">{issues.length}</span></div>
        {issues.length === 0 ? <p className="rounded-lg border border-dashed border-gray-700 p-6 text-sm text-gray-500">{empty}</p> : <div className="space-y-3">{issues.map(issue => <article key={issue.id} className="rounded-lg border border-gray-800 bg-gray-950 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><Link to={`/testing/${issue.id}`} className="font-medium text-white hover:text-blue-400">{issue.title || `Issue #${issue.id}`}</Link><p className="mt-1 text-xs text-gray-500">Issue #{issue.id}</p></div><span className="rounded-full bg-yellow-500/10 px-3 py-1 text-xs font-medium text-yellow-300">{String(issue.status || "Unknown").replaceAll("_", " ")}</span></div>
            {issue.description && <p className="mt-3 line-clamp-3 text-sm text-gray-400">{issue.description}</p>}
            <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                <Field label="Project" value={valueName(issue.projectName || issue.project)} />
                <Field label="Developer" value={valueName(issue.developer || issue.assignedUser)} />
                <Field label="Tester" value={valueName(issue.tester)} />
                <Field label="Priority" value={issue.priority || "—"} />
                <Field label="Updated" value={dateLabel(issue.updatedAt)} />
            </dl>
            <Link to={`/testing/${issue.id}`} className="mt-4 inline-flex rounded-lg border border-gray-700 px-4 py-2 text-sm text-gray-200 hover:bg-gray-800">Open testing workspace</Link>
        </article>)}</div>}
    </section>;
}

function Field({ label, value }) {
    return <div><dt className="text-xs text-gray-500">{label}</dt><dd className="mt-1 text-gray-200">{value}</dd></div>;
}

