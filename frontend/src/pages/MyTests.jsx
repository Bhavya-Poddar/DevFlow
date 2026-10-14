import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { responseErrorMessage } from "../utils/responseError";

const API = "http://localhost:8080/api";

export default function MyTests() {
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        const headers = { Authorization: `Bearer ${localStorage.getItem("token")}` };
        fetch(`${API}/testing/my`, { headers })
            .then(async response => {
                if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not load your assigned tests."));
                const issueData = await response.json();
                if (!Array.isArray(issueData)) throw new Error("Your assigned testing response was not a JSON array.");
                if (!active) return;
                setItems(issueData.filter(issue => ["READY_FOR_TESTING", "TESTING"].includes(issue.status)));
            })
            .catch(reason => { if (active) setError(reason.message || "Could not load your assigned tests."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const ready = items.filter(issue => issue.status === "READY_FOR_TESTING");
    const active = items.filter(issue => issue.status === "TESTING");

    return <main className="mx-auto max-w-7xl space-y-7">
        <header><p className="text-sm font-semibold uppercase tracking-wide text-blue-400">PERSONAL WORKSPACE</p><h1 className="mt-2 text-3xl font-bold text-white">My Tests</h1><p className="mt-2 text-gray-400">Testing work assigned to your account.</p></header>
        {error && <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-red-300">{error}</p>}
        {loading ? <p className="text-gray-400">Loading your tests…</p> : <div className="grid gap-6 xl:grid-cols-2"><Section title="Ready for Testing" items={ready} /><Section title="In Testing" items={active} /></div>}
    </main>;
}

function Section({ title, items }) {
    return <section className="rounded-xl border border-gray-800 bg-gray-900 p-5">
        <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-semibold text-white">{title}</h2><span className="rounded-full bg-gray-800 px-2.5 py-1 text-xs text-gray-300">{items.length}</span></div>
        {items.length === 0 ? <p className="rounded-lg border border-dashed border-gray-700 p-6 text-sm text-gray-500">No assigned issues in this stage.</p> : <div className="space-y-3">{items.map(issue => <Link key={issue.id} to={`/testing/${issue.id}`} className="block rounded-lg border border-gray-800 bg-gray-950 p-4 hover:border-blue-500/60"><p className="font-medium text-white">{issue.title || `Issue #${issue.id}`}</p><p className="mt-1 text-sm text-gray-400">{issue.projectName || issue.project?.name || "Project unavailable"} · {issue.priority || "Priority unavailable"}</p><p className="mt-2 text-xs text-gray-500">Issue #{issue.id}</p><p className="mt-3 text-xs text-blue-300">Open test workspace →</p></Link>)}</div>}
    </section>;
}

