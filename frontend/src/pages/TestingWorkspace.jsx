import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { responseErrorMessage } from "../utils/responseError";

const API = "http://localhost:8080/api";
const date = (value) => value ? new Date(value).toLocaleString() : "Not recorded";
const canSubmit = (user, issue) => user?.role === "ADMIN"
    || (user?.role === "TESTER" && String(issue?.tester?.id) === String(user.id));

export default function TestingWorkspace() {
    const { issueId } = useParams();
    const token = localStorage.getItem("token");
    const headers = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
    const [issue, setIssue] = useState(null);
    const [history, setHistory] = useState([]);
    const [user, setUser] = useState(null);
    const [testForm, setTestForm] = useState({ testScenario: "", expectedResult: "", actualResult: "", notes: "" });
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    const load = useCallback(async () => {
        const [issueResponse, historyResponse, userResponse] = await Promise.all([
            fetch(`${API}/issues/${encodeURIComponent(issueId)}`, { headers }),
            fetch(`${API}/testing/issue/${encodeURIComponent(issueId)}/history`, { headers }),
            fetch(`${API}/users/me`, { headers }),
        ]);
        if (!issueResponse.ok) throw new Error(await responseErrorMessage(issueResponse, "Could not load issue details."));
        if (!historyResponse.ok) throw new Error(await responseErrorMessage(historyResponse, "Could not load testing history."));
        if (!userResponse.ok) throw new Error(await responseErrorMessage(userResponse, "Could not load your account."));
        const [issueData, historyData, userData] = await Promise.all([
            issueResponse.json(), historyResponse.json(), userResponse.json(),
        ]);
        if (!Array.isArray(historyData)) throw new Error("The testing history response was not a JSON array.");
        setIssue(issueData);
        setHistory(historyData);
        setUser(userData);
    }, [headers, issueId]);

    useEffect(() => {
        void Promise.resolve().then(load)
            .catch((loadError) => setError(loadError.message))
            .finally(() => setLoading(false));
    }, [load]);

    const start = async () => {
        setBusy(true);
        setError("");
        setNotice("");
        try {
            const response = await fetch(`${API}/testing/${encodeURIComponent(issueId)}/start`, {
                method: "POST", headers,
            });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not start this test."));
            await load();
            setNotice("Testing started. Record the results below, then choose Pass Test or Fail Test.");
        } catch (requestError) {
            setError(requestError.message);
        } finally {
            setBusy(false);
        }
    };

    const complete = async (result) => {
        if (result === "FAILED" && !testForm.notes.trim()) {
            setError("Add notes describing the failure before submitting a failed result.");
            return;
        }
        setBusy(true);
        setError("");
        setNotice("");
        try {
            const response = await fetch(`${API}/testing/${encodeURIComponent(issueId)}/complete`, {
                method: "POST",
                headers: { ...headers, "Content-Type": "application/json" },
                body: JSON.stringify({
                    result,
                    testScenario: testForm.testScenario,
                    expectedResult: testForm.expectedResult,
                    actualResult: testForm.actualResult,
                    notes: testForm.notes,
                }),
            });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not complete this test."));
            setTestForm({ testScenario: "", expectedResult: "", actualResult: "", notes: "" });
            await load();
            setNotice(result === "FAILED"
                ? "Test failed. The issue returned to the developer workflow."
                : "Test passed. The issue was resolved.");
        } catch (requestError) {
            setError(requestError.message);
        } finally {
            setBusy(false);
        }
    };

    const updateTestField = (event) => {
        setTestForm((current) => ({ ...current, [event.target.name]: event.target.value }));
    };

    if (loading) return <main className="text-gray-400">Loading testing workspace…</main>;

    return (
        <main className="mx-auto max-w-5xl space-y-6">
            <Link to={user?.role === "TESTER" ? "/my-tests" : "/testing"} className="text-sm text-blue-400 hover:text-blue-300">
                ← Back to testing
            </Link>
            {error && <p role="alert" className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-red-300">{error}</p>}
            {notice && <p role="status" className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-emerald-300">{notice}</p>}
            {!issue ? (
                <div className="rounded-xl border border-gray-800 bg-gray-900 p-8 text-gray-400">Issue not found or unavailable to your account.</div>
            ) : (
                <>
                    <header className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                        <div className="flex flex-wrap justify-between gap-3">
                            <div>
                                <p className="text-sm text-blue-400">ISSUE #{issue.id}</p>
                                <h1 className="mt-2 text-2xl font-bold text-white">{issue.title}</h1>
                            </div>
                            <span className="h-fit rounded-full bg-yellow-500/10 px-3 py-1 text-sm text-yellow-300">{String(issue.status || "Unknown").replaceAll("_", " ")}</span>
                        </div>
                        <p className="mt-4 whitespace-pre-wrap text-gray-300">{issue.description || "No description."}</p>
                        <div className="mt-6 rounded-lg border border-gray-800 bg-gray-950 p-4">
                            <h2 className="text-sm font-semibold text-gray-200">Acceptance criteria</h2>
                            <p className="mt-2 whitespace-pre-wrap text-sm text-gray-300">{issue.acceptanceCriteria || "No acceptance criteria have been recorded for this issue."}</p>
                        </div>
                        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                            {[["Project", issue.project?.name], ["Developer", issue.assignedUser?.name], ["Tester", issue.tester?.name], ["Sprint", issue.sprint?.name], ["Priority", issue.priority]].map(([label, value]) => (
                                <div key={label}><p className="text-xs text-gray-500">{label}</p><p className="mt-1 text-sm text-gray-200">{value || "—"}</p></div>
                            ))}
                        </div>
                    </header>

                    {issue.status === "READY_FOR_TESTING" && user?.role === "TESTER" && String(issue.tester?.id) !== String(user.id) && (
                        <section className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-5 text-sm text-amber-200">
                            This issue is assigned to {issue.tester?.name || "another tester"}. Only its assigned tester can start testing.
                        </section>
                    )}
                    {issue.status === "READY_FOR_TESTING" && canSubmit(user, issue) && (
                        <section className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                            <h2 className="text-lg font-semibold text-white">Ready to test</h2>
                            <p className="mt-1 text-sm text-gray-400">Review the acceptance criteria, then start a test execution. You can submit a Pass or Fail result once testing is active.</p>
                            <button disabled={busy} onClick={start} className="mt-4 rounded-lg bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-500 disabled:opacity-50">{busy ? "Starting…" : "Start Testing"}</button>
                        </section>
                    )}
                    {issue.status === "TESTING" && canSubmit(user, issue) && (
                        <section className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                            <h2 className="text-lg font-semibold text-white">Record test results</h2>
                            <p className="mt-1 text-sm text-gray-400">Capture the scenario and compare expected and actual behavior. Failure notes are required.</p>
                            <div className="mt-4 grid gap-4">
                                <label className="text-sm text-gray-300">Test steps or scenario<textarea name="testScenario" value={testForm.testScenario} onChange={updateTestField} rows="3" className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white placeholder:text-gray-600 focus:border-blue-500 focus:outline-none" placeholder="What did you test?" /></label>
                                <label className="text-sm text-gray-300">Expected result<textarea name="expectedResult" value={testForm.expectedResult} onChange={updateTestField} rows="2" className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white placeholder:text-gray-600 focus:border-blue-500 focus:outline-none" placeholder="What should happen?" /></label>
                                <label className="text-sm text-gray-300">Actual result<textarea name="actualResult" value={testForm.actualResult} onChange={updateTestField} rows="2" className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white placeholder:text-gray-600 focus:border-blue-500 focus:outline-none" placeholder="What happened when you tested it?" /></label>
                                <label className="text-sm text-gray-300">Notes<textarea name="notes" value={testForm.notes} onChange={updateTestField} rows="3" className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 p-3 text-white placeholder:text-gray-600 focus:border-blue-500 focus:outline-none" placeholder="Additional context; required for a failed test." /></label>
                            </div>
                            <div className="mt-4 flex flex-wrap gap-3">
                                <button disabled={busy} onClick={() => complete("PASSED")} className="rounded-lg bg-emerald-600 px-4 py-2 font-medium text-white hover:bg-emerald-500 disabled:opacity-50">{busy ? "Saving…" : "Pass Test"}</button>
                                <button disabled={busy} onClick={() => complete("FAILED")} className="rounded-lg bg-red-600 px-4 py-2 font-medium text-white hover:bg-red-500 disabled:opacity-50">{busy ? "Saving…" : "Fail Test"}</button>
                            </div>
                        </section>
                    )}
                    {issue.status === "TESTING" && !canSubmit(user, issue) && (
                        <section className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-5 text-sm text-amber-200">Testing is in progress. Results can be submitted by the assigned tester or an administrator.</section>
                    )}
                    <section className="rounded-xl border border-gray-800 bg-gray-900 p-6">
                        <h2 className="text-lg font-semibold text-white">Testing history</h2>
                        {history.length === 0 ? <p className="mt-4 text-sm text-gray-500">No previous test attempts.</p> : (
                            <ol className="mt-5 space-y-4 border-l border-gray-700 pl-5">
                                {history.map((entry, index) => (
                                    <li key={entry.id || index} className="relative rounded-lg border border-gray-800 bg-gray-950 p-4">
                                        <span className="absolute -left-[1.62rem] top-5 h-2.5 w-2.5 rounded-full bg-blue-500" />
                                        <div className="flex flex-wrap justify-between gap-2"><h3 className="font-medium text-white">Test #{history.length - index}</h3><span className={`text-sm font-semibold ${entry.result === "PASSED" ? "text-emerald-400" : entry.result === "FAILED" ? "text-red-400" : "text-yellow-300"}`}>{entry.result || entry.status || "IN PROGRESS"}</span></div>
                                        <p className="mt-2 text-sm text-gray-400">Tester / actor: {entry.tester?.name || "—"}</p>
                                        <p className="mt-1 text-xs text-gray-500">Started: {date(entry.startedAt)} · Completed: {date(entry.completedAt)}</p>
                                        {entry.testScenario && <p className="mt-3 whitespace-pre-wrap text-sm text-gray-300"><strong>Scenario:</strong> {entry.testScenario}</p>}
                                        {entry.expectedResult && <p className="mt-2 whitespace-pre-wrap text-sm text-gray-300"><strong>Expected:</strong> {entry.expectedResult}</p>}
                                        {entry.actualResult && <p className="mt-2 whitespace-pre-wrap text-sm text-gray-300"><strong>Actual:</strong> {entry.actualResult}</p>}
                                        {entry.notes && <p className="mt-2 whitespace-pre-wrap text-sm text-gray-300"><strong>Notes:</strong> {entry.notes}</p>}
                                    </li>
                                ))}
                            </ol>
                        )}
                    </section>
                </>
            )}
        </main>
    );
}
