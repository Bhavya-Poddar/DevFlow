import { apiUrl } from "../services/apiConfig";
import { useEffect, useMemo, useState } from "react";
import { loadDropdownOptions, dropdownLabel } from "../services/dropdownOptions";
import "./Reports.css";

const API = apiUrl("/api");
const STATUS_META = [
  { id: "OPEN", label: "Open", color: "#7384a3" },
  { id: "IN_PROGRESS", label: "In progress", color: "#4f91ff" },
  { id: "CODE_REVIEW", label: "Code review", color: "#a17cff" },
  { id: "READY_FOR_TESTING", label: "Ready for test", color: "#35c8c0" },
  { id: "TESTING", label: "Testing", color: "#e4aa58" },
  { id: "RESOLVED", label: "Resolved", color: "#45c58c" },
];
const list = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : [];
const titleCase = value => String(value || "Unknown").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());

export default function Reports() {
  const [projects, setProjects] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [issues, setIssues] = useState([]);
  const [today, setToday] = useState(null);
  const [draftFilters, setDraftFilters] = useState({ developer: "ALL", sprint: "ALL", range: "30" });
  const [appliedFilters, setAppliedFilters] = useState({ developer: "ALL", sprint: "ALL", range: "30" });
  const [refreshKey, setRefreshKey] = useState(0);
  const [reportRows, setReportRows] = useState([]);
  const [burndownResult, setBurndownResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadedQuery, setLoadedQuery] = useState(null);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState({ tester: "ALL", status: "ALL", result: "ALL" });
  const [statusOptions, setStatusOptions] = useState(STATUS_META.map(item=>({code:item.id,displayLabel:item.label})));
  const [resultOptions, setResultOptions] = useState([{code:"PASSED",displayLabel:"Passed"},{code:"FAILED",displayLabel:"Failed"}]);
  const [priorityOptions, setPriorityOptions] = useState([]);
  const [rangeOptions, setRangeOptions] = useState([{code:"30",displayLabel:"Last 30 days"},{code:"90",displayLabel:"Last 90 days"},{code:"ALL",displayLabel:"All time"}]);
  const visibleStatusMeta = STATUS_META.map(item=>({...item,label:dropdownLabel(statusOptions,item.id,item.label)}));
  const headers = useMemo(() => ({ Authorization: `Bearer ${localStorage.getItem("token")}` }), []);

  useEffect(() => {
    let active=true;
    Promise.all([loadDropdownOptions("ISSUE_STATUS"),loadDropdownOptions("TEST_RESULT"),loadDropdownOptions("REPORTING_RANGE"),loadDropdownOptions("PRIORITY")])
      .then(([statusData,resultData,rangeData,priorityData])=>{if(active){if(statusData.length)setStatusOptions(statusData);if(resultData.length)setResultOptions(resultData);if(rangeData.length)setRangeOptions(rangeData);if(priorityData.length)setPriorityOptions(priorityData);}})
      .catch(()=>{});
    return ()=>{active=false;};
  },[]);

  useEffect(() => {
    let active = true;
    (async () => {
      const responses = await Promise.all([
        fetch(`${API}/projects`, { headers }),
        fetch(`${API}/sprints`, { headers }),
        fetch(`${API}/issues`, { headers }),
      ]);
      if (responses.some(response => !response.ok)) throw new Error("Could not load your projects, tasks, and sprints.");
      const [projectData, sprintData, issueData] = await Promise.all(responses.map(response => response.json()));
      if (!active) return;
      const projectList = list(projectData);
      const sprintList = list(sprintData);
      setProjects(projectList);
      setSprints(sprintList);
      setIssues(list(issueData));
      setToday(new Date());
      const activeSprint = sprintList.find(sprint => ["ACTIVE", "IN_PROGRESS"].includes(String(sprint.status || "").toUpperCase()));
      const initialSprint = activeSprint ? String(activeSprint.id) : "ALL";
      setDraftFilters({ developer: "ALL", sprint: initialSprint, range: "30" });
      setAppliedFilters({ developer: "ALL", sprint: initialSprint, range: "30" });
    })().catch(reason => { if (active) setError(reason.message || "Could not load report filters."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [headers]);

  useEffect(() => {
    if (!projects.length) return undefined;
    let active = true;
    const sprintId = appliedFilters.sprint;
    const selectedSprint = sprints.find(sprint => String(sprint.id) === sprintId);
    const projectIds = selectedSprint?.project?.id
      ? [selectedSprint.project.id] : projects.map(project => project.id);
    const query = sprintId === "ALL" ? "" : `?sprintId=${encodeURIComponent(sprintId)}`;
    Promise.all(projectIds.map(id => fetch(`${API}/reports/projects/${id}${query}`, { headers }).then(response => {
      if (!response.ok) throw new Error("Could not load the sprint report.");
      return response.json();
    })))
      .then(data => { if (active) setReportRows(data.flatMap(item => list(item?.issues))); })
      .catch(reason => { if (active) { setReportRows([]); setError(reason.message || "Could not load the sprint report."); } })
      .finally(() => { if (active) setLoadedQuery(`${sprintId}|${refreshKey}`); });
    return () => { active = false; };
  }, [appliedFilters.sprint, refreshKey, projects, sprints, headers]);

  useEffect(() => {
    const sprintId = appliedFilters.sprint;
    if (sprintId === "ALL") return undefined;

    let active = true;
    const query = sprintId + "|" + refreshKey;
    fetch(API + "/reports/sprints/" + encodeURIComponent(sprintId) + "/burndown", { headers })
      .then(response => {
        if (!response.ok) throw new Error("Could not load sprint burndown history.");
        return response.json();
      })
      .then(data => { if (active) setBurndownResult({ query, data, error: "" }); })
      .catch(() => { if (active) setBurndownResult({ query, data: null, error: "Could not load burndown history. Refresh to try again." }); });
    return () => { active = false; };
  }, [appliedFilters.sprint, refreshKey, headers]);
  const scopedReport = useMemo(() => reportRows.filter(row => {
    const date = row.updatedAt || row.createdAt || row.reportedAt;
    const inRange = appliedFilters.range === "ALL" || (date
      && today && new Date(date).getTime() >= today.getTime() - Number(appliedFilters.range) * 24 * 60 * 60 * 1000);
    return (appliedFilters.developer === "ALL" || String(row.developer?.id ?? "") === appliedFilters.developer) && inRange;
  }), [reportRows, appliedFilters, today]);
  const report = scopedReport;
  const filtered = useMemo(() => scopedReport.filter(row =>
    (filters.tester === "ALL" || String(row.tester?.id ?? "") === filters.tester)
    && (filters.status === "ALL" || row.status === filters.status)
    && (filters.result === "ALL" || row.testResult === filters.result)), [scopedReport, filters]);
  const counts = useMemo(() => Object.fromEntries(STATUS_META.map(status => [status.id,
    scopedReport.filter(row => row.status === status.id).length])), [scopedReport]);
  const resolved = counts.RESOLVED || 0;
  const remaining = Math.max(report.length - resolved, 0);
  const completion = report.length ? Math.round((resolved / report.length) * 100) : 0;
  const workload = useMemo(() => {
    const people = new Map();
    report.forEach(row => {
      if (!row.developer?.id) return;
      const person = people.get(row.developer.id) || { ...row.developer, count: 0 };
      person.count += 1;
      people.set(row.developer.id, person);
    });
    return [...people.values()].sort((a, b) => b.count - a.count).slice(0, 5);
  }, [report]);
  const choose = (key, value) => setFilters(current => ({ ...current, [key]: value }));
  const selectedSprint = sprints.find(sprint => String(sprint.id) === appliedFilters.sprint);
  const reportLoading = loadedQuery !== `${appliedFilters.sprint}|${refreshKey}`;
  const currentBurndownQuery = appliedFilters.sprint + "|" + refreshKey;
  const hasBurndownResult = burndownResult?.query === currentBurndownQuery;
  const burndown = hasBurndownResult ? burndownResult.data : null;
  const burndownError = hasBurndownResult ? burndownResult.error : "";
  const burndownLoading = appliedFilters.sprint !== "ALL" && !hasBurndownResult;
  const summaryIssues = issues.filter(issue => appliedFilters.sprint === "ALL"
    || String(issue.sprint?.id ?? "") === appliedFilters.sprint);
  const blockedCount = summaryIssues.filter(issue => issue.blocked).length;
  const burndownLiveRemaining = summaryIssues.filter(issue => issue.status !== "RESOLVED").length;
  const assignedDevelopers = new Set(report.map(row => row.developer?.id).filter(Boolean)).size;
  const openCount = Math.max(report.length - resolved, 0);
  const daysRemaining = selectedSprint?.endDate && today
    ? Math.max(0, Math.ceil((new Date(`${selectedSprint.endDate}T23:59:59`).getTime() - today.getTime()) / 86400000)) : null;
  const sprintTitle = selectedSprint?.name || (appliedFilters.sprint === "ALL" ? "All sprints" : `Sprint ${appliedFilters.sprint}`);

  if (loading) return <main className="df-sprint-page"><div className="df-sprint-loading">Loading sprint analytics…</div></main>;

  return <main className="df-sprint-page">
    <header className="df-sprint-titlebar"><div><h1>Engineering Team Effort Tracker</h1><p>Sprint &amp; Project Effort Overview <i /> {selectedSprint?.name || "All available sprints"}</p></div><button type="button" onClick={() => { setError(""); setRefreshKey(value => value + 1); }} disabled={reportLoading} className="df-sprint-refresh">↻ <span>Refresh</span></button></header>

    <section className="df-sprint-toolbar" aria-label="Sprint filters">
      <label aria-label="Filter developer"><select value={draftFilters.developer} onChange={event => setDraftFilters(current => ({ ...current, developer: event.target.value }))}><option value="ALL">All Developers</option>{uniqueUsers(reportRows, "developer").map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>
      <label aria-label="Select sprint"><select value={draftFilters.sprint} onChange={event => setDraftFilters(current => ({ ...current, sprint: event.target.value }))}><option value="ALL">All Sprints</option>{sprints.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name || `Sprint ${sprint.id}`}{["ACTIVE", "IN_PROGRESS"].includes(String(sprint.status || "").toUpperCase()) ? " (Current)" : ""}</option>)}</select></label>
      <label aria-label="Select reporting range"><select value={draftFilters.range} onChange={event => setDraftFilters(current => ({ ...current, range: event.target.value }))}>{rangeOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></label>
      <button type="button" className="df-sprint-apply" onClick={() => { setError(""); setAppliedFilters(draftFilters); }}>Apply</button>
      <button type="button" className="df-sprint-reset" aria-label="Reset filters" title="Reset filters" onClick={() => { const active = sprints.find(sprint => ["ACTIVE", "IN_PROGRESS"].includes(String(sprint.status || "").toUpperCase())); const reset = { developer: "ALL", sprint: active ? String(active.id) : "ALL", range: "30" }; setError(""); setDraftFilters(reset); setAppliedFilters(reset); }}>↺</button>
    </section>

    {error && <div className="df-sprint-alert" role="alert"><span>!</span><div><strong>Analytics could not be loaded</strong><p>{error}</p></div></div>}

    <section className="df-sprint-metrics" aria-label="Sprint summary">
      <Metric label="Tasks completed" value={resolved} detail={`${completion}% of current scope`} tone="green" icon="◉" loading={reportLoading} />
      <Metric label="Developers assigned" value={assignedDevelopers} detail="with work in selected scope" tone="violet" icon="♙" loading={reportLoading} />
      <Metric label="Open tickets" value={openCount} detail={`${blockedCount} currently blocked`} tone="amber" icon="▤" loading={reportLoading} />
      <Metric label="Avg sprint velocity" value="Not tracked" detail="story points are not recorded" tone="blue" icon="ϟ" />
    </section>

    <section className="df-sprint-chart-grid">
      <article className="df-sprint-panel df-sprint-burndown">
        <PanelHeading kicker="BURNDOWN" title={sprintTitle + " Burndown"} detail="Remaining tasks · observed daily history" tag={burndownLoading ? "LOADING" : burndownError ? "ERROR" : burndown?.snapshots?.length ? "TRACKING" : "NO HISTORY"} />
        <BurndownChart data={appliedFilters.sprint === "ALL" ? null : burndown} loading={appliedFilters.sprint !== "ALL" && burndownLoading} error={appliedFilters.sprint === "ALL" ? "" : burndownError} currentRemaining={burndownLiveRemaining} />
      </article>
      <article className="df-sprint-panel df-sprint-summary">
        <PanelHeading kicker="SPRINT SNAPSHOT" title={`${sprintTitle} Summary`} detail="Live values from the selected sprint" />
        <SummaryRow label="Sprint goal" value={selectedSprint?.goal || "No sprint goal set"} />
        <SummaryRow label="Total tasks" value={`${reportLoading ? "—" : report.length} tasks`} />
        <SummaryRow label="Tasks completed" value={`${reportLoading ? "—" : resolved} tasks`} />
        <SummaryRow label="Tasks remaining" value={`${reportLoading ? "—" : remaining} tasks`} />
        <SummaryRow label="Completion" value={`${reportLoading ? "—" : completion}%`} />
        <SummaryRow label="Days remaining" value={daysRemaining == null ? "Sprint dates not set" : `${daysRemaining} days`} />
        <SummaryRow label="Blockers" value={`${blockedCount} active`} />
        <SummaryRow label="Developers assigned" value={`${assignedDevelopers} assigned`} />
        <p className="df-sprint-data-note">Story-point estimates and historical burndown snapshots are not recorded by the current backend.</p>
      </article>
    </section>

    <section className="df-sprint-insight-grid">
      <article className="df-sprint-panel df-sprint-workload-panel">
        <PanelHeading kicker="TEAM EFFORT" title="Developer workload" detail="Assigned task count · selected sprint" />
        {workload.length ? <div className="df-sprint-workload-list">{workload.map(person => <div className="df-sprint-workload-row" key={person.id}><div className="df-sprint-person-avatar">{initials(person.name)}</div><div className="df-sprint-person-main"><div><strong>{person.name || "Developer"}</strong><span>{titleCase(person.role)}</span></div><div className="df-sprint-workload-track"><i style={{ width: `${Math.max(8, (person.count / Math.max(...workload.map(item => item.count))) * 100)}%` }} /></div></div><b>{person.count}<small> tasks</small></b></div>)}</div> : <Empty text="No developer assignments in this sprint." />}
      </article>
      <article className="df-sprint-panel df-sprint-status-panel">
        <PanelHeading kicker="TICKET STATUS" title="Ticket status breakdown" detail="Live task state · selected sprint" />
        <div className="df-sprint-status-layout"><StatusDonut counts={counts} total={report.length} /><ul className="df-sprint-status-list">{visibleStatusMeta.map(status => <li key={status.id}><span><i style={{ "--status-color": status.color }} />{status.label}</span><strong>{counts[status.id] || 0}</strong></li>)}</ul></div>
      </article>
    </section>

    <section className="df-sprint-tasks-section">
      <div className="df-sprint-section-heading"><div><p className="df-sprint-kicker">SPRINT WORK</p><h2>Task detail</h2><p>Filter the live task report by assignment, status, or test result.</p></div><span className="df-sprint-table-count">{filtered.length} {filtered.length === 1 ? "task" : "tasks"}</span></div>
      <div className="df-sprint-table-filters">
        <SelectFilter label="Tester" value={filters.tester} onChange={value => choose("tester", value)} options={uniqueUsers(report, "tester")} />
        <label><span>Status</span><select value={filters.status} onChange={event => choose("status", event.target.value)}><option value="ALL">All statuses</option>{visibleStatusMeta.map(status => <option key={status.id} value={status.id}>{status.label}</option>)}</select></label>
        <label><span>Test result</span><select value={filters.result} onChange={event => choose("result", event.target.value)}><option value="ALL">All results</option>{resultOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></label>
      </div>
      <div className="df-sprint-table-scroll"><table><thead><tr>{["Task", "Developer", "Tester", "Testing started", "Testing completed", "Result", "Status"].map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>
        {filtered.map((row, index) => <tr key={row.id || index}><td><strong>{row.title || `Task #${row.id || "—"}`}</strong><small>#{row.id ?? "—"} · {row.priority ? dropdownLabel(priorityOptions,row.priority,titleCase(row.priority)) : "No priority"}</small></td><td>{row.developer?.name || "Unassigned"}</td><td>{row.tester?.name || "Unassigned"}</td><td>{formatDate(row.testStartedAt)}</td><td>{formatDate(row.testCompletedAt)}</td><td><span className={`df-sprint-result ${row.testResult === "PASSED" ? "is-passed" : row.testResult === "FAILED" ? "is-failed" : ""}`}>{row.testResult ? dropdownLabel(resultOptions,row.testResult,titleCase(row.testResult)) : "Not tested"}</span></td><td><span className="df-sprint-task-status">{dropdownLabel(statusOptions,row.status,titleCase(row.status))}</span></td></tr>)}
        {!reportLoading && filtered.length === 0 && <tr><td colSpan="7"><Empty text={report.length ? "No tasks match these filters." : "No tasks are assigned to this sprint yet."} /></td></tr>}
      </tbody></table></div>
    </section>
  </main>;
}

function BurndownChart({ data, loading, error, currentRemaining }) {
  if (loading) return <div className="df-burndown-state" role="status">Loading recorded sprint snapshots…</div>;
  if (error) return <div className="df-burndown-state is-error" role="alert"><strong>Burndown history unavailable</strong><span>{error}</span></div>;
  if (!data) return <div className="df-burndown-state"><strong>Select a sprint</strong><span>Choose one sprint to view its recorded burndown history.</span></div>;
  const points = Array.isArray(data.snapshots) ? data.snapshots : [];
  if (!points.length) {
    return <div className="df-burndown-state">
      <span className="df-sprint-chart-glyph">⌁</span>
      <strong>No burndown history yet</strong>
      <span>Tracking starts at the first successful capture for an active sprint. Earlier dates are not backfilled.</span>
      <small>SPRINT START: {data.sprintStartDate || "not set"} · LIVE REMAINING: {currentRemaining} tasks</small>
    </div>;
  }

  const trackingStart = data.trackingStartedOn;
  const sprintEnd = data.sprintEndDate || points[points.length - 1].date;
  const baseline = Number(data.baselineRemainingIssues ?? points[0].remainingIssues ?? 0);
  const maxValue = Math.max(1, baseline, ...points.map(point => Number(point.totalIssues || 0)));
  const left = 62;
  const right = 780;
  const top = 18;
  const bottom = 180;
  const time = value => new Date(value + "T00:00:00Z").getTime();
  const span = Math.max(0, time(sprintEnd) - time(trackingStart));
  const x = value => span === 0 ? left : left + Math.max(0, Math.min(1, (time(value) - time(trackingStart)) / span)) * (right - left);
  const y = value => bottom - (Math.max(0, Number(value || 0)) / maxValue) * (bottom - top);
  const ticks = [...new Set([maxValue, Math.ceil(maxValue / 2), 0])];
  const actualPath = points.map((point, index) => (index === 0 ? "M" : "L") + " " + x(point.date) + " " + y(point.remainingIssues)).join(" ");

  return <div className="df-burndown-chart">
    <p className="df-burndown-tracking-note">
      Sprint start: <b>{data.sprintStartDate || "not set"}</b>
      <span>Tracking began: <b>{trackingStart}</b></span>
    </p>
    <svg className="df-burndown-svg" viewBox="0 0 800 220" role="img"
      aria-label={"Observed remaining task counts for " + (data.sprintName || "sprint") + " beginning " + trackingStart + ", with an ideal line from the first recorded count to zero by sprint end"}>
      {ticks.map(value => <g key={value}>
        <line className="df-burndown-gridline" x1={left} x2={right} y1={y(value)} y2={y(value)} />
        <text className="df-burndown-axis-label" x={left - 10} y={y(value) + 4} textAnchor="end">{value}</text>
      </g>)}
      <line className="df-burndown-axis" x1={left} x2={right} y1={bottom} y2={bottom} />
      {sprintEnd && <line className="df-burndown-ideal" x1={x(trackingStart)} y1={y(baseline)} x2={x(sprintEnd)} y2={y(0)} />}
      {points.length > 1 && <path className="df-burndown-actual" d={actualPath} />}
      {points.map(point => <circle className="df-burndown-point" key={point.date} cx={x(point.date)} cy={y(point.remainingIssues)} r="4">
        <title>{point.date + ": " + point.remainingIssues + " remaining tasks (" + point.totalIssues + " total)"}</title>
      </circle>)}
      <text className="df-burndown-date-label" x={left} y="207" textAnchor="start">{trackingStart}</text>
      <text className="df-burndown-date-label" x={right} y="207" textAnchor="end">{sprintEnd}</text>
    </svg>
    <div className="df-burndown-legend"><span><i className="is-actual" />Actual remaining tasks</span><span><i className="is-ideal" />Ideal from tracking baseline</span></div>
    <p className="df-burndown-footnote">Each marker is a saved daily observation. The ideal line starts at the first recorded remaining count; no earlier actual values are inferred.</p>
  </div>;
}
function Metric({ label, value, detail, tone, icon, loading, progress }) {
  return <article className={`df-sprint-metric df-tone-${tone}`}><div className="df-sprint-metric-top"><span>{label}</span><i>{icon}</i></div><strong>{loading ? "—" : value}</strong><div className="df-sprint-metric-bottom">{progress != null && <span className="df-sprint-mini-track"><i style={{ width: `${progress}%` }} /></span>}<small>{detail}</small></div></article>;
}
function PanelHeading({ kicker, title, detail, tag }) { return <div className="df-sprint-panel-heading"><div><p>{kicker}</p><h2>{title}</h2><span>{detail}</span></div>{tag && <em>{tag}</em>}</div>; }
function SummaryRow({ label, value }) { return <div className="df-sprint-summary-row"><span>{label}</span><strong>{value}</strong></div>; }
function StatusDonut({ counts, total }) {
  const radius = 53;
  const circumference = 2 * Math.PI * radius;
  const arcs = STATUS_META.map((status, index) => {
    const value = total ? (counts[status.id] || 0) / total : 0;
    const offset = STATUS_META.slice(0, index).reduce((sum, item) =>
      sum + (total ? (counts[item.id] || 0) / total : 0) * circumference, 0);
    return { ...status, dash: value * circumference, offset };
  });
  return <div className="df-sprint-donut-wrap"><svg viewBox="0 0 132 132" role="img" aria-label={`${total} tasks across sprint statuses`}><circle className="df-sprint-donut-track" cx="66" cy="66" r={radius} />{arcs.map(arc => arc.dash > 0 && <circle key={arc.id} cx="66" cy="66" r={radius} fill="none" stroke={arc.color} strokeWidth="10" strokeDasharray={`${arc.dash} ${circumference - arc.dash}`} strokeDashoffset={-arc.offset} transform="rotate(-90 66 66)" />)}</svg><div><strong>{total}</strong><span>TASKS</span></div></div>;
}
function SelectFilter({ label, value, onChange, options }) { return <label><span>{label}</span><select aria-label={`Filter by ${label.toLowerCase()}`} value={value} onChange={event => onChange(event.target.value)}><option value="ALL">All {label.toLowerCase()}s</option>{options.map(option => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>; }
function Empty({ text }) { return <div className="df-sprint-empty">{text}</div>; }
function uniqueUsers(rows, key) { return [...new Map(rows.filter(row => row[key]?.id).map(row => [String(row[key].id), { id: String(row[key].id), name: row[key].name || `${key} #${row[key].id}` }])).values()]; }
function initials(name) { return String(name || "D").split(/\s+/).slice(0, 2).map(part => part[0]).join("").toUpperCase(); }
function formatDate(value) { return value ? new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" }) : "—"; }
