import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiUrl } from "../services/apiConfig";
import { loadDropdownOptions } from "../services/dropdownOptions";
import { permissionKind, permissionScopeLabel, permissionsGroupedByModule } from "../utils/rolePermissions";

const TABS = [
    ["overview", "Overview", "◈"], ["users", "User Management", "♟"],
    ["roles", "Manage Roles", "⚿"], ["dropdowns", "Dropdown Configuration", "⌄"],
    ["menu", "Menu Management", "☷"], ["website", "Website Configuration", "⚙"]
];
const styles = {
    panel: "rounded-2xl border border-gray-800 bg-gray-900 p-5 sm:p-7",
    input: "w-full rounded-lg border border-gray-700 bg-gray-950 px-3 py-2.5 text-sm text-white outline-none focus:border-blue-500",
    button: "rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50",
    secondary: "rounded-lg border border-gray-700 px-3 py-2 text-sm text-gray-200 hover:bg-gray-800",
    label: "block text-xs font-medium text-gray-400"
};

async function request(path, token, options = {}) {
    const response = await fetch(apiUrl(path), {
        ...options,
        headers: { Authorization: `Bearer ${token}`, ...(options.body ? { "Content-Type": "application/json" } : {}), ...options.headers }
    });
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || data?.error || `Request failed (HTTP ${response.status}).`);
    return data;
}

function AdminPanel() {
    const token = localStorage.getItem("token");
    const [permissionState, setPermissionState] = useState(() => token ? "loading" : "unauthorized");
    const [tab, setTab] = useState("overview");
    const [permissionError, setPermissionError] = useState("");

    useEffect(() => {
        if (!token) return;
        let active = true;
        request("/api/users/me/permissions", token)
            .then(data => { if (active) setPermissionState(data?.recoveryCapable ? "authorized" : "unauthorized"); })
            .catch(error => { if (active) { setPermissionError(error.message); setPermissionState("error"); } });
        return () => { active = false; };
    }, [token]);

    if (permissionState === "loading") return <StatePanel title="Checking administration access…" />;
    if (permissionState !== "authorized") return <div className={`${styles.panel} mx-auto max-w-3xl`}><p className="text-xs font-semibold uppercase tracking-widest text-violet-300">Protected administration</p><h1 className="mt-2 text-2xl font-semibold text-white">Admin Panel</h1><p role="alert" className="mt-4 text-sm text-amber-200">{permissionState === "error" ? `Could not verify protected administrator access: ${permissionError}` : "This page requires protected administrative recovery capability. Role permissions cannot grant this capability."}</p><Link to="/dashboard" className="mt-5 inline-flex text-sm text-blue-300 hover:text-blue-200">Return to dashboard</Link></div>;

    return <div className="dfx-screen dfx-screen--admin mx-auto max-w-[1500px] space-y-6">
        <header className="dfx-screen-header flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.18em] text-violet-300">SYSTEM / ACCESS CONTROL</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-white">Admin Panel</h1><p className="mt-2 text-sm text-gray-400">Manage accounts and supported workspace configuration. Protected recovery access is controlled separately.</p></div><span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-300">Recovery verified</span></header>
        <nav aria-label="Admin Panel sections" className="grid grid-cols-2 gap-2 rounded-2xl border border-gray-800 bg-gray-900 p-2 sm:grid-cols-3 xl:grid-cols-6">{TABS.map(([key,label,icon])=><button key={key} type="button" onClick={()=>setTab(key)} aria-current={tab===key?"page":undefined} className={`flex min-h-12 items-center gap-2 rounded-xl px-3 py-2 text-left text-xs font-medium transition sm:text-sm ${tab===key?"bg-violet-500/15 text-violet-200 ring-1 ring-violet-400/20":"text-gray-400 hover:bg-gray-800 hover:text-white"}`}><span aria-hidden="true">{icon}</span><span>{label}</span></button>)}</nav>
        {tab === "overview" && <AdminOverview onNavigate={setTab} />}
        {tab === "users" && <UserManagement token={token} />}
        {tab === "roles" && <RoleManagement token={token} />}
        {tab === "dropdowns" && <DropdownManagement token={token} />}
        {tab === "menu" && <MenuManagement token={token} />}
        {tab === "website" && <WebsiteConfiguration token={token} />}
    </div>;
}

function AdminOverview({onNavigate}) {
    const descriptions={users:"Search accounts, create users, update details, change global roles, and activate or deactivate accounts.",roles:"Add or edit role names, and safely remove roles that are no longer assigned.",dropdowns:"Manage every supported business option label in one place. Stable workflow values remain protected.",menu:"Rename, reorder, or disable predefined navigation routes without changing route or permission rules.",website:"Edit supported workspace name and tagline values. Deployment secrets are not exposed here."};
    return <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{TABS.filter(([key])=>key!=="overview").map(([key,label,icon])=><button key={key} type="button" onClick={()=>onNavigate(key)} className={`${styles.panel} text-left transition hover:border-violet-500/40 hover:bg-gray-900/80`}><span className="grid h-10 w-10 place-items-center rounded-xl bg-violet-500/10 text-lg text-violet-300">{icon}</span><h2 className="mt-4 text-base font-semibold text-white">{label}</h2><p className="mt-2 text-sm leading-relaxed text-gray-400">{descriptions[key]}</p><span className="mt-4 inline-flex text-xs font-medium text-blue-300">Open section →</span></button>)}</div>;
}

function UserManagement({token}) {
    const [users,setUsers]=useState([]); const [roles,setRoles]=useState([]); const [teams,setTeams]=useState([]); const [projects,setProjects]=useState([]);
    const [loading,setLoading]=useState(true); const [error,setError]=useState(""); const [notice,setNotice]=useState(""); const [confirmation,setConfirmation]=useState(null); const [search,setSearch]=useState(""); const [roleFilter,setRoleFilter]=useState("ALL"); const [statusFilter,setStatusFilter]=useState("ALL"); const [creating,setCreating]=useState(false); const [saving,setSaving]=useState(false); const [selected,setSelected]=useState(null); const [memberships,setMemberships]=useState(null); const [membershipLoading,setMembershipLoading]=useState(false); const [membershipSaving,setMembershipSaving]=useState(false); const [membershipDraft,setMembershipDraft]=useState({teamId:"",teamRoleId:"",projectId:"",projectRoleId:""});
    const [form,setForm]=useState({name:"",email:"",password:"",role:"VIEWER",teamId:"",projectId:"",projectRoleId:"",teamRoleId:""});
    const [edit,setEdit]=useState({name:"",email:"",enabled:true,role:""});
    const [accountStatusOptions,setAccountStatusOptions]=useState([{code:"ACTIVE",displayLabel:"Active"},{code:"INACTIVE",displayLabel:"Inactive"}]);
    const load=useCallback(async()=>{setLoading(true);setError("");try{const [u,r,t,p]=await Promise.all([request("/api/users",token),request("/api/admin/roles",token),request("/api/teams",token),request("/api/projects",token)]);setUsers(Array.isArray(u)?u:[]);setRoles(Array.isArray(r)?r:[]);setTeams(Array.isArray(t)?t:[]);setProjects(Array.isArray(p)?p:[]);}catch(e){setError(e.message);}finally{setLoading(false);}},[token]);
    useEffect(()=>{void Promise.resolve().then(load);},[load]);
    useEffect(()=>{loadDropdownOptions("ACCOUNT_STATUS").then(data=>{if(data.length)setAccountStatusOptions(data);}).catch(()=>{});},[]);
    const globalRoles=roles.filter(r=>r.scope==="GLOBAL"&&!r.retired); const projectRoles=roles.filter(r=>r.scope==="PROJECT"&&!r.retired); const teamRoles=roles.filter(r=>r.scope==="PROJECT_TEAM"&&!r.retired);
    const visible=users.filter(u=>`${u.name||""} ${u.email||""} ${u.role||""}`.toLowerCase().includes(search.toLowerCase())&&(roleFilter==="ALL"||u.role===roleFilter)&&(statusFilter==="ALL"||(statusFilter==="ACTIVE"?u.enabled!==false:u.enabled===false)));
    const create=async e=>{e.preventDefault();setSaving(true);setError("");setNotice("");try{const created=await request("/api/users",token,{method:"POST",body:JSON.stringify({name:form.name,email:form.email,password:form.password,role:form.role})});let membershipWarning="";
        try{if(form.teamId)await request(`/api/teams/${form.teamId}/members`,token,{method:"POST",body:JSON.stringify({developerId:created.id})});
            const project=projects.find(p=>String(p.id)===String(form.projectId));
            if(form.projectId&&form.projectRoleId){if(project?.teamId&&!form.teamId)throw new Error("Add this account to the project's team before assigning a project role.");await request(`/api/projects/${form.projectId}/members/${created.id}/role`,token,{method:"PUT",body:JSON.stringify({roleId:Number(form.projectRoleId)})});}
            if(form.teamId&&form.teamRoleId)await request(`/api/teams/${form.teamId}/members/${created.id}/role`,token,{method:"PUT",body:JSON.stringify({roleId:Number(form.teamRoleId)})});
        }catch(membershipError){membershipWarning=` Account was created, but membership setup failed: ${membershipError.message}`;}
        setNotice(`Account created. The password was sent only in the request and was not returned or saved by this page.${membershipWarning}`);setCreating(false);setForm({name:"",email:"",password:"",role:"VIEWER",teamId:"",projectId:"",projectRoleId:"",teamRoleId:""});await load();
    }catch(e){setError(e.message);}finally{setSaving(false);}};
    const refreshMemberships=async userId=>{const data=await request(`/api/users/${userId}/memberships`,token);setMemberships(data);};
    const openEdit=async user=>{setSelected(user);setMemberships(null);setMembershipDraft({teamId:"",teamRoleId:"",projectId:"",projectRoleId:""});setMembershipLoading(true);setEdit({name:user.name||"",email:user.email||"",enabled:user.enabled!==false,role:user.role||""});setError("");setNotice("");try{await refreshMemberships(user.id);}catch(e){setError(`Membership details could not be loaded: ${e.message}`);}finally{setMembershipLoading(false);}};
    const saveEdit=async()=>{if(!selected)return;setSaving(true);setError("");try{await request(`/api/users/${selected.id}`,token,{method:"PUT",body:JSON.stringify({name:edit.name,email:edit.email,enabled:edit.enabled})});if(edit.role!==selected.role)await request(`/api/users/${selected.id}/role`,token,{method:"PUT",body:JSON.stringify({role:edit.role})});setSelected(null);setNotice("Account details and role updated.");await load();}catch(e){setError(e.message);}finally{setSaving(false);}};
    const addMembership=async kind=>{if(!selected)return;setMembershipSaving(true);setError("");setNotice("");try{if(kind==="team"){if(!membershipDraft.teamId)throw new Error("Choose a team first.");await request(`/api/teams/${membershipDraft.teamId}/members`,token,{method:"POST",body:JSON.stringify({developerId:selected.id})});if(membershipDraft.teamRoleId)await request(`/api/teams/${membershipDraft.teamId}/members/${selected.id}/role`,token,{method:"PUT",body:JSON.stringify({roleId:Number(membershipDraft.teamRoleId)})});setMembershipDraft({...membershipDraft,teamId:"",teamRoleId:""});}else{if(!membershipDraft.projectId||!membershipDraft.projectRoleId)throw new Error("Choose both a project and project role.");await request(`/api/projects/${membershipDraft.projectId}/members/${selected.id}/role`,token,{method:"PUT",body:JSON.stringify({roleId:Number(membershipDraft.projectRoleId)})});setMembershipDraft({...membershipDraft,projectId:"",projectRoleId:""});}await refreshMemberships(selected.id);setNotice("Membership saved.");}catch(e){setError(e.message);}finally{setMembershipSaving(false);}};
    const saveMembershipRole=async(kind,scopeId,roleId)=>{if(!selected)return;setMembershipSaving(true);setError("");try{const base=kind==="team"?`/api/teams/${scopeId}/members/${selected.id}/role`:`/api/projects/${scopeId}/members/${selected.id}/role`;if(roleId)await request(base,token,{method:"PUT",body:JSON.stringify({roleId:Number(roleId)})});else await request(base,token,{method:"DELETE"});await refreshMemberships(selected.id);setNotice("Membership role saved.");}catch(e){setError(e.message);}finally{setMembershipSaving(false);}};
    const updateMembershipRole=(kind,scopeId,roleId)=>{if(!selected)return;if(!roleId){setConfirmation({title:"Remove scoped role?",message:"This account may lose access to the selected project or team.",confirmLabel:"Remove role",onConfirm:()=>saveMembershipRole(kind,scopeId,roleId)});return;}void saveMembershipRole(kind,scopeId,roleId);};
    const removeTeamMembership=team=>{if(!selected)return;setConfirmation({title:"Remove team membership?",message:`Remove ${selected.name} from ${team.teamName}? This may remove access to team projects.`,confirmLabel:"Remove member",onConfirm:async()=>{setMembershipSaving(true);setError("");try{await request(`/api/teams/${team.teamId}/members/${selected.id}`,token,{method:"DELETE"});await refreshMemberships(selected.id);setNotice("Team membership removed.");}catch(e){setError(e.message);}finally{setMembershipSaving(false);}}});};
    const availableTeams=teams.filter(team=>!(memberships?.teams||[]).some(item=>String(item.teamId)===String(team.id)));
    const availableProjects=projects.filter(project=>!(memberships?.projects||[]).some(item=>String(item.projectId)===String(project.id)));
    return <section className={styles.panel}><div className="flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-semibold text-white">User Management</h2><p className="mt-1 text-sm text-gray-400">Existing secure account APIs are used for creation, role assignment, profile edits, and account status.</p></div><button type="button" onClick={()=>setCreating(v=>!v)} className={styles.button}>{creating?"Close form":"Create user"}</button></div>
        {error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}</Alert>}
        {creating&&<form onSubmit={create} className="mt-5 grid gap-3 rounded-xl border border-gray-800 bg-gray-950/60 p-4 sm:grid-cols-2 xl:grid-cols-4">
            <label className={styles.label}>Initial role<select required value={form.role} onChange={e=>setForm({...form,role:e.target.value})} className={`${styles.input} mt-1`}>{globalRoles.map(r=><option key={r.id} value={r.code}>{r.name}</option>)}</select></label>
            <label className={styles.label}>Team membership<select value={form.teamId} onChange={e=>setForm({...form,teamId:e.target.value})} className={`${styles.input} mt-1`}><option value="">No team</option>{teams.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
            <label className={styles.label}>Project<select value={form.projectId} onChange={e=>setForm({...form,projectId:e.target.value})} className={`${styles.input} mt-1`}><option value="">No project role</option>{projects.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
            <label className={styles.label}>Project role<select disabled={!form.projectId} value={form.projectRoleId} onChange={e=>setForm({...form,projectRoleId:e.target.value})} className={`${styles.input} mt-1`}><option value="">No role</option>{projectRoles.map(r=><option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
            <label className={styles.label}>Name<input required maxLength="120" value={form.name} onChange={e=>setForm({...form,name:e.target.value})} className={`${styles.input} mt-1`}/></label>
            <label className={styles.label}>Email<input required type="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} className={`${styles.input} mt-1`}/></label>
            <label className={styles.label}>Initial password<input required type="password" autoComplete="new-password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})} className={`${styles.input} mt-1`}/></label>
            <label className={styles.label}>Team role<select disabled={!form.teamId} value={form.teamRoleId} onChange={e=>setForm({...form,teamRoleId:e.target.value})} className={`${styles.input} mt-1`}><option value="">Use existing role</option>{teamRoles.map(r=><option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
            <div className="sm:col-span-2 xl:col-span-4"><p className="text-xs text-gray-500">Passwords are hashed by the backend. No email invitation or administrator password-reset API exists yet; this form requires an initial password and does not retain it after submission.</p></div>
            <div className="flex justify-end gap-2 sm:col-span-2 xl:col-span-4"><button type="button" className={styles.secondary} onClick={()=>setCreating(false)}>Cancel</button><button disabled={saving} className={styles.button}>{saving?"Creating…":"Create account"}</button></div>
        </form>}
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3"><div className="flex w-full flex-wrap gap-2 sm:w-auto"><input aria-label="Search users" placeholder="Search name, email, or role" value={search} onChange={e=>setSearch(e.target.value)} className={`${styles.input} min-w-[220px] max-w-md flex-1`}/><select aria-label="Filter users by role" value={roleFilter} onChange={e=>setRoleFilter(e.target.value)} className={`${styles.input} w-auto min-w-40`}><option value="ALL">All roles</option>{globalRoles.map(r=><option key={r.id} value={r.code}>{r.name}</option>)}</select><select aria-label="Filter users by account status" value={statusFilter} onChange={e=>setStatusFilter(e.target.value)} className={`${styles.input} w-auto min-w-36`}><option value="ALL">All accounts</option>{accountStatusOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></div><span className="text-xs text-gray-500">{visible.length} of {users.length} accounts</span></div>
        {loading?<StatePanel title="Loading users…"/>:!users.length?<Empty text="No user accounts are available."/>:<div className="mt-3 divide-y divide-gray-800 rounded-xl border border-gray-800">{visible.map(user=><div key={user.id} className="flex flex-wrap items-center justify-between gap-3 p-4"><div className="min-w-0"><p className="truncate font-medium text-white">{user.name} <span className="ml-2 text-xs text-gray-500">#{user.id}</span></p><p className="truncate text-xs text-gray-400">{user.email} · {roleName(user.role,roles)}</p></div><div className="flex items-center gap-3"><span className={`rounded-full px-2.5 py-1 text-xs ${user.enabled===false?"bg-red-500/10 text-red-300":"bg-emerald-500/10 text-emerald-300"}`}>{user.enabled===false?"Inactive":"Active"}</span><button className={styles.secondary} onClick={()=>openEdit(user)}>Manage</button></div></div>)}{visible.length===0&&<Empty text="No users match this search."/>}</div>}
        {selected&&<div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/70 p-4" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)setSelected(null);}}><div role="dialog" aria-modal="true" aria-labelledby="admin-edit-user-title" className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-gray-700 bg-gray-900 p-5 shadow-2xl sm:p-7">
            <h3 id="admin-edit-user-title" className="text-lg font-semibold text-white">Manage {selected.name}</h3>
            <div className="mt-5 grid gap-3 sm:grid-cols-2"><label className={styles.label}>Global role<select value={edit.role} onChange={e=>setEdit({...edit,role:e.target.value})} className={`${styles.input} mt-1`}>{globalRoles.map(r=><option key={r.id} value={r.code}>{r.name}</option>)}</select></label><label className={styles.label}>Account status<select value={edit.enabled?"ACTIVE":"INACTIVE"} onChange={e=>setEdit({...edit,enabled:e.target.value==="ACTIVE"})} className={`${styles.input} mt-1`}>{accountStatusOptions.map(option=><option key={option.code} value={option.code}>{option.displayLabel}</option>)}</select></label><label className={styles.label}>Name<input value={edit.name} maxLength="120" onChange={e=>setEdit({...edit,name:e.target.value})} className={`${styles.input} mt-1`}/></label><label className={styles.label}>Email<input type="email" value={edit.email} onChange={e=>setEdit({...edit,email:e.target.value})} className={`${styles.input} mt-1`}/></label></div>
            <p className="mt-3 text-xs text-gray-500">Global role changes use the protected role API. Backend safeguards continue to protect the last recovery-capable account.</p>
            <div className="mt-5 border-t border-gray-800 pt-4"><h4 className="font-medium text-white">Project and team memberships</h4>
                {membershipLoading?<p className="mt-3 text-sm text-gray-400">Loading memberships…</p>:memberships?<>
                    <div className="mt-3 grid gap-4 lg:grid-cols-2">
                        <div className="rounded-xl border border-gray-800 p-3"><p className="text-xs font-semibold uppercase tracking-wide text-gray-400">Project roles</p>{memberships.projects?.length?memberships.projects.map(item=><div key={item.projectId} className="mt-3 grid gap-2 rounded-lg bg-gray-950/60 p-3 sm:grid-cols-[minmax(0,1fr)_minmax(10rem,1fr)] sm:items-center"><div><p className="text-sm text-white">{item.projectName}</p><p className="text-xs text-gray-500">{item.roleName}</p></div><select aria-label={`Role on ${item.projectName}`} disabled={membershipSaving} value={item.roleId||""} onChange={e=>void updateMembershipRole("project",item.projectId,e.target.value)} className={styles.input}><option value="">Remove project role</option>{projectRoles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select></div>):<p className="mt-2 text-sm text-gray-500">No project role assignments.</p>}
                            <div className="mt-4 grid gap-2 border-t border-gray-800 pt-3"><label className={styles.label}>Add to project<select value={membershipDraft.projectId} onChange={e=>setMembershipDraft({...membershipDraft,projectId:e.target.value})} className={styles.input+" mt-1"}><option value="">Choose project</option>{availableProjects.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select></label><label className={styles.label}>Project role<select value={membershipDraft.projectRoleId} onChange={e=>setMembershipDraft({...membershipDraft,projectRoleId:e.target.value})} className={styles.input+" mt-1"}><option value="">Choose role</option>{projectRoles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select></label><button type="button" disabled={membershipSaving||!membershipDraft.projectId||!membershipDraft.projectRoleId} className={styles.secondary} onClick={()=>void addMembership("project")}>{membershipSaving?"Saving…":"Assign project role"}</button></div>
                        </div>
                        <div className="rounded-xl border border-gray-800 p-3"><p className="text-xs font-semibold uppercase tracking-wide text-gray-400">Project teams</p>{memberships.teams?.length?memberships.teams.map(item=><div key={item.teamId} className="mt-3 grid gap-2 rounded-lg bg-gray-950/60 p-3 sm:grid-cols-[minmax(0,1fr)_minmax(10rem,1fr)_auto] sm:items-center"><div><p className="text-sm text-white">{item.teamName}{item.manager&&<span className="ml-2 text-xs text-violet-300">Manager</span>}</p><p className="text-xs text-gray-500">{item.roleName||"Member"}</p></div><select aria-label={`Role in ${item.teamName}`} disabled={membershipSaving||item.manager} value={item.roleId||""} onChange={e=>void updateMembershipRole("team",item.teamId,e.target.value)} className={styles.input}><option value="">No scoped role</option>{teamRoles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select>{item.manager?<span className="text-[11px] text-gray-500">Manager remains a member</span>:<button type="button" disabled={membershipSaving} className="rounded-lg border border-red-500/30 px-2 py-2 text-xs text-red-300" onClick={()=>void removeTeamMembership(item)}>Remove</button>}</div>):<p className="mt-2 text-sm text-gray-500">No team memberships.</p>}
                            <div className="mt-4 grid gap-2 border-t border-gray-800 pt-3"><label className={styles.label}>Add to project team<select value={membershipDraft.teamId} onChange={e=>setMembershipDraft({...membershipDraft,teamId:e.target.value})} className={styles.input+" mt-1"}><option value="">Choose team</option>{availableTeams.map(team=><option key={team.id} value={team.id}>{team.name}</option>)}</select></label><label className={styles.label}>Team role (optional)<select value={membershipDraft.teamRoleId} onChange={e=>setMembershipDraft({...membershipDraft,teamRoleId:e.target.value})} className={styles.input+" mt-1"}><option value="">No scoped role</option>{teamRoles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select></label><button type="button" disabled={membershipSaving||!membershipDraft.teamId} className={styles.secondary} onClick={()=>void addMembership("team")}>{membershipSaving?"Saving…":"Add to team"}</button></div>
                        </div>
                    </div>
                    <p className="mt-3 text-xs text-gray-500">Project and team membership operations are checked by their existing backend authorization rules. If a role is not allowed for this account or would remove required access, the backend will reject it.</p>
                </>:<p className="mt-3 text-sm text-gray-500">Membership information is unavailable.</p>}
            </div>
            <div className="mt-6 flex justify-end gap-2"><button className={styles.secondary} onClick={()=>{setSelected(null);setMemberships(null);}}>Cancel</button><button className={styles.button} disabled={saving} onClick={()=>void saveEdit()}>{saving?"Saving…":"Save account"}</button></div>
        </div></div>}
        {confirmation&&<AdminConfirmDialog {...confirmation} onCancel={()=>setConfirmation(null)} onConfirm={async()=>{const action=confirmation.onConfirm;setConfirmation(null);await action();}}/>}
    </section>;
}

function roleCodeFor(name, scope, roles) {
    const scopedName = name.trim().replace(/\s*\((project|team)\)\s*$/i, "");
    let base = scopedName.toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 48);
    if (!base || !/^[A-Z]/.test(base)) base = `ROLE_${base}`;
    const suffix = scope === "PROJECT" ? "_PROJECT" : scope === "PROJECT_TEAM" ? "_TEAM" : "";
    const candidate = `${base}${suffix}`.slice(0, 64);
    if (!roles.some(role => role.code.toUpperCase() === candidate)) return candidate;
    let number = 2;
    while (roles.some(role => role.code.toUpperCase() === `${candidate.slice(0, 61 - String(number).length)}_${number}`)) number += 1;
    return `${candidate.slice(0, 61 - String(number).length)}_${number}`;
}
const ROLE_ORDER = ["ADMIN", "PROJECT_MANAGER", "TEAM_LEAD", "DEVELOPER", "TESTER", "VIEWER"];
function RoleManagement({ token }) {
    const [roles, setRoles] = useState([]);
    const [permissionCatalogue, setPermissionCatalogue] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [confirmation, setConfirmation] = useState(null);
    const [editing, setEditing] = useState(null);
    const [formOpen, setFormOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({ name: "", description: "", scope: "GLOBAL", permissions: [] });

    const load = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            const [roleData, catalogue] = await Promise.all([
                request("/api/admin/roles", token),
                request("/api/admin/roles/permissions", token)
            ]);
            setRoles(Array.isArray(roleData) ? roleData.filter(role => !role.retired) : []);
            setPermissionCatalogue(Array.isArray(catalogue) ? catalogue.filter(permission => permission.key !== "ROLE_MANAGE") : []);
        } catch (loadError) {
            setError(loadError.message);
        } finally {
            setLoading(false);
        }
    }, [token]);
    useEffect(() => { void Promise.resolve().then(load); }, [load]);

    const begin = (role = null, scope = "GLOBAL") => {
        setEditing(role);
        setFormOpen(true);
        setForm(role ? {
            name: role.name,
            description: role.description || "",
            scope: role.scope,
            permissions: (role.permissions || []).filter(key => key !== "ROLE_MANAGE")
        } : { name: "", description: "", scope, permissions: [] });
        setError("");
        setNotice("");
    };

    const persist = async () => {
        setSaving(true);
        setError("");
        setNotice("");
        try {
            const payload = {
                code: editing ? editing.code : roleCodeFor(form.name, form.scope, roles),
                name: form.name,
                description: form.description,
                scope: form.scope,
                permissions: form.permissions
            };
            await request(editing ? `/api/admin/roles/${editing.id}` : "/api/admin/roles", token, {
                method: editing ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            setNotice(editing ? "Role details and permissions saved. Current members receive the updated access on their next API request." : "Role added with the selected permissions. Existing roles and assignments were left unchanged.");
            setFormOpen(false);
            setEditing(null);
            await load();
        } catch (saveError) {
            setError(saveError.message);
        } finally {
            setSaving(false);
        }
    };

    const save = async event => {
        event.preventDefault();
        setError("");
        const canonicalName = name => name.trim().replace(/\s*\((project|team)\)\s*$/i, "").toLocaleLowerCase();
        const duplicate = roles.find(role => role.id !== editing?.id && role.scope === form.scope && canonicalName(role.name) === canonicalName(form.name));
        if (duplicate) {
            setError(`A ${scopeLabel(form.scope).toLowerCase()} role named “${duplicate.name}” already exists.`);
            return;
        }
        const existing = new Set((editing?.permissions || []).filter(key => key !== "ROLE_MANAGE"));
        const added = form.permissions.filter(key => !existing.has(key));
        if (added.length) {
            setConfirmation({
                title: "Confirm expanded access",
                message: `This grants ${added.length} additional ${added.length === 1 ? "capability" : "capabilities"} to the “${form.name.trim()}” role. Every account assigned this role can use the new access within its ${scopeLabel(form.scope).toLowerCase()}. Continue?`,
                confirmLabel: "Save expanded access",
                onConfirm: persist
            });
            return;
        }
        await persist();
    };

    const togglePermission = (key, checked) => setForm(current => ({
        ...current,
        permissions: checked
            ? [...new Set([...current.permissions, key])]
            : current.permissions.filter(existing => existing !== key)
    }));
    const setModulePermissions = (module, checked) => {
        const keys = module.permissions.map(permission => permission.key);
        setForm(current => ({
            ...current,
            permissions: checked
                ? [...new Set([...current.permissions, ...keys])]
                : current.permissions.filter(key => !keys.includes(key))
        }));
    };
    const remove = role => setConfirmation({
        title: "Retire role?",
        message: `“${role.name}” can only be retired when no users, projects, or teams are assigned. Existing records are preserved.`,
        confirmLabel: "Retire role",
        onConfirm: async () => {
            setError(""); setNotice("");
            try {
                await request(`/api/admin/roles/${role.id}`, token, { method: "DELETE" });
                setNotice(`${role.name} was retired.`);
                await load();
            } catch (removeError) { setError(removeError.message); }
        }
    });
    const scopedSort = (a, b) => {
        const ai = ROLE_ORDER.indexOf(a.code.replace(/_(PROJECT|TEAM)$/, ""));
        const bi = ROLE_ORDER.indexOf(b.code.replace(/_(PROJECT|TEAM)$/, ""));
        return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi) || a.name.localeCompare(b.name);
    };
    const roleRows = items => <div className="mt-3 divide-y divide-gray-800 rounded-xl border border-gray-800">{items.map(role => <div key={role.id} className="flex flex-wrap items-center justify-between gap-3 p-4"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><p className="font-medium text-white">{role.name}</p>{role.code === "ADMIN" && <span className="rounded-full bg-violet-500/10 px-2 py-1 text-[10px] text-violet-200">Protected recovery role</span>}</div><p className="mt-1 text-sm text-gray-400">{role.description || "No description"}</p><p className="mt-1 text-xs text-gray-500">{role.permissions?.filter(key => key !== "ROLE_MANAGE").length || 0} configurable permissions · {role.globalUserCount || 0} assigned accounts</p></div><div className="flex shrink-0 gap-2"><button type="button" className={styles.secondary} onClick={() => begin(role)}>Edit</button>{role.code === "ADMIN" ? <span className="px-3 py-2 text-xs text-gray-500">Cannot delete</span> : <button type="button" className="rounded-lg border border-red-500/30 px-3 py-2 text-sm text-red-300 hover:bg-red-500/10" onClick={() => void remove(role)}>Delete</button>}</div></div>)}{!items.length && <Empty text="No roles in this group."/>}</div>;
    const mainRoles = roles.filter(role => role.scope === "GLOBAL").sort((a, b) => {
        const ai = ROLE_ORDER.indexOf(a.code); const bi = ROLE_ORDER.indexOf(b.code);
        return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi) || a.name.localeCompare(b.name);
    });
    const projectRoles = roles.filter(role => role.scope === "PROJECT").sort(scopedSort);
    const teamRoles = roles.filter(role => role.scope === "PROJECT_TEAM").sort(scopedSort);
    const modules = permissionsGroupedByModule(permissionCatalogue, form.scope);

    return <section className={styles.panel}>
        <div><h2 className="text-xl font-semibold text-white">Manage Roles</h2><p className="mt-1 text-sm text-gray-400">Configure what each role can view and do. Permissions are enforced by the backend within each role’s scope.</p></div>
        {error && <Alert tone="error">{error}</Alert>}{notice && <Alert>{notice}</Alert>}
        {loading ? <StatePanel title="Loading roles and supported permissions…"/> : <>
            <section className="mt-6 rounded-xl border border-violet-500/20 bg-violet-500/[.035] p-4 sm:p-5" aria-labelledby="main-roles-title">
                <div className="flex flex-wrap items-end justify-between gap-3"><div><h3 id="main-roles-title" className="text-base font-semibold text-white">Main Roles</h3><p className="mt-1 text-sm text-gray-400">Workspace-wide roles.</p></div><button type="button" className={styles.button} onClick={() => begin(null, "GLOBAL")}>Add Main Role</button></div>
                {roleRows(mainRoles)}
            </section>
            <section className="mt-6" aria-labelledby="scoped-roles-title"><div className="mb-3"><h3 id="scoped-roles-title" className="text-base font-semibold text-white">Scoped Roles</h3><p className="mt-1 text-sm text-gray-400">These grants apply only inside the assigned project or project team.</p></div>
                <details open className="mb-3 rounded-xl border border-gray-800 bg-gray-950/35 p-4"><summary className="cursor-pointer list-none font-medium text-gray-100"><span className="mr-2 text-blue-300">▸</span>Project-specific Roles <span className="ml-2 text-xs font-normal text-gray-500">({projectRoles.length})</span></summary><p className="mt-2 text-sm text-gray-500">Project Manager, Developer, Tester, Viewer, and other project-scoped roles.</p>{roleRows(projectRoles)}</details>
                <details open className="rounded-xl border border-gray-800 bg-gray-950/35 p-4"><summary className="cursor-pointer list-none font-medium text-gray-100"><span className="mr-2 text-violet-300">▸</span>Project Team-specific Roles <span className="ml-2 text-xs font-normal text-gray-500">({teamRoles.length})</span></summary><p className="mt-2 text-sm text-gray-500">Includes Team Lead and other roles scoped to a project team.</p>{roleRows(teamRoles)}</details>
                <div className="mt-4 flex justify-end"><button type="button" className={styles.secondary} onClick={() => begin(null, "PROJECT")}>Add Scoped Role</button></div>
            </section>
        </>}
        {formOpen && <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-4" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) { setFormOpen(false); setEditing(null); } }}><form role="dialog" aria-modal="true" aria-labelledby="admin-role-form-title" onSubmit={save} className="max-h-[92vh] w-full max-w-5xl overflow-y-auto rounded-2xl border border-gray-700 bg-gray-900 p-5 shadow-2xl sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 id="admin-role-form-title" className="font-semibold text-white">{editing ? "Edit role permissions" : "Add role"}</h3><p className="mt-1 text-sm text-gray-400">Scope: {scopeLabel(form.scope)}{editing ? " (cannot be changed)" : ""}. Each selected capability applies at this scope.</p></div><span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-3 py-1 text-xs text-blue-200">{form.permissions.length} enabled</span></div>
            {error && <p role="alert" className="mt-4 rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>}
            <div className="mt-4 grid gap-3 sm:grid-cols-2">{!editing && <label className={styles.label}>Role scope<select value={form.scope} onChange={event => setForm(current => ({ ...current, scope: event.target.value, permissions: [] }))} className={styles.input + " mt-1"}><option value="GLOBAL">Workspace-wide</option><option value="PROJECT">Project-specific</option><option value="PROJECT_TEAM">Project-team-specific</option></select></label>}<label className={styles.label}>Role name<input required maxLength="120" value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} className={styles.input + " mt-1"} placeholder="e.g. Project Coordinator"/></label><label className={`${styles.label} sm:col-span-2`}>Description (optional)<textarea maxLength="250" rows="2" value={form.description} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} className={styles.input + " mt-1"} placeholder="What is this role for?"/></label></div>
            <div className="mt-5 rounded-xl border border-gray-800 bg-gray-950/50 p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><h4 className="font-semibold text-white">Visibility and actions</h4><p className="mt-1 text-xs text-gray-500">Select only the capabilities this role needs. Protected recovery access is not part of the editable catalogue.</p></div><span className="text-xs text-gray-500">{scopeLabel(form.scope)} role</span></div>
                <div className="mt-4 grid gap-3 xl:grid-cols-2">{modules.map(module => {
                    const visibility = module.permissions.filter(permission => permissionKind(permission) === "visibility");
                    const actions = module.permissions.filter(permission => permissionKind(permission) === "action");
                    const keys = module.permissions.map(permission => permission.key);
                    const selectedCount = keys.filter(key => form.permissions.includes(key)).length;
                    return <details key={module.name} open={Boolean(module.permissions.length)} className="rounded-xl border border-gray-800 bg-gray-900/70 p-3"><summary className="flex cursor-pointer list-none flex-wrap items-center justify-between gap-2"><span className="font-medium text-gray-100">{module.name}<small className="ml-2 text-xs text-gray-500">{selectedCount}/{keys.length} enabled</small></span><span aria-hidden="true" className="text-xs text-gray-600">Expand or collapse</span></summary>{keys.length > 0 && <div className="mt-2 flex justify-end gap-2"><button type="button" className="rounded-md px-2 py-1 text-xs text-blue-300 hover:bg-blue-500/10" onClick={() => setModulePermissions(module, true)}>Select all</button><button type="button" className="rounded-md px-2 py-1 text-xs text-gray-400 hover:bg-gray-800" onClick={() => setModulePermissions(module, false)}>Clear all</button></div>}
                        {module.permissions.length ? <div className="mt-3 space-y-3">{[["Visibility", visibility], ["Actions", actions]].map(([groupName, entries]) => entries.length > 0 && <section key={groupName}><h5 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-500">{groupName}</h5><div className="space-y-2">{entries.map(permission => <label key={permission.key} className="flex cursor-pointer items-start gap-3 rounded-lg border border-gray-800 bg-gray-950/60 p-3 hover:border-gray-700"><input type="checkbox" checked={form.permissions.includes(permission.key)} onChange={event => togglePermission(permission.key, event.target.checked)} className="mt-0.5 h-4 w-4 shrink-0 accent-blue-500"/><span className="min-w-0"><span className="block text-sm text-gray-200">{permission.description}</span><span className="mt-1 block text-[10px] text-gray-500">{form.scope === "GLOBAL" ? "Applies workspace-wide" : permissionScopeLabel(permission.scope)}</span></span></label>)}</div></section>)}</div> : <p className="mt-3 text-xs leading-relaxed text-gray-500">{module.note || "No separate permission is defined for this module."}</p>}
                    </details>;
                })}</div>
            </div>
            {!editing && <p className="mt-3 text-xs text-gray-500">New roles start with no permissions selected. Existing roles and assignments are unchanged.</p>}
            <div className="mt-5 flex justify-end gap-2 border-t border-gray-800 pt-4"><button type="button" className={styles.secondary} onClick={() => { setFormOpen(false); setEditing(null); }}>Cancel</button><button disabled={saving || loading} className={styles.button}>{saving ? "Saving…" : "Save role and permissions"}</button></div>
        </form></div>}
        {confirmation && <AdminConfirmDialog {...confirmation} onCancel={() => setConfirmation(null)} onConfirm={async () => { const action = confirmation.onConfirm; setConfirmation(null); await action(); }} />}
    </section>;
}
function scopeLabel(scope){return scope==="PROJECT"?"Project":scope==="PROJECT_TEAM"?"Project Team":"Main workspace";}

function optionCode(label) { const code=label.trim().toUpperCase().replace(/[^A-Z0-9]+/g,"_").replace(/^_+|_+$/g,"").slice(0,64); return /^[A-Z]/.test(code) && code.length>=2 ? code : "OPTION_"+code.slice(0,56); }
function DropdownManagement({token}) {
    const [categories,setCategories]=useState([]); const [category,setCategory]=useState(""); const [label,setLabel]=useState(""); const [order,setOrder]=useState(10); const [editing,setEditing]=useState(null); const [draft,setDraft]=useState({displayLabel:"",sortOrder:0}); const [loading,setLoading]=useState(true); const [error,setError]=useState(""); const [notice,setNotice]=useState(""); const [confirmation,setConfirmation]=useState(null); const [saving,setSaving]=useState(false);
    const load=useCallback(async()=>{setLoading(true);setError("");try{const data=await request("/api/admin/config/dropdowns/catalog",token);const next=Array.isArray(data)?data:[];setCategories(next);setCategory(current=>next.some(item=>item.category===current)?current:(next[0]?.category||""));}catch(e){setError(e.message);}finally{setLoading(false);}},[token]);
    useEffect(()=>{void Promise.resolve().then(load);},[load]);
    const activeCategory=categories.find(item=>item.category===category);const visible=activeCategory?.options||[];const fullManagement=activeCategory?.managementMode==="FULL";
    const add=async event=>{event.preventDefault();setSaving(true);setError("");setNotice("");try{await request("/api/admin/config/dropdowns/"+category,token,{method:"POST",body:JSON.stringify({code:optionCode(label),displayLabel:label,sortOrder:order,enabled:true})});setLabel("");setOrder(visible.length?Math.max(...visible.map(item=>item.sortOrder))+10:10);setNotice("Option added.");await load();}catch(e){setError(e.message);}finally{setSaving(false);}};
    const beginEdit=item=>{setEditing(item.id);setDraft({displayLabel:item.displayLabel,sortOrder:item.sortOrder});setError("");};
    const saveEdit=async item=>{setSaving(true);setError("");setNotice("");try{await request("/api/admin/config/dropdowns/"+item.id,token,{method:"PUT",body:JSON.stringify({code:item.code,displayLabel:draft.displayLabel,sortOrder:Number(draft.sortOrder)})});setEditing(null);setNotice("Option saved. Existing records keep their current value.");await load();}catch(e){setError(e.message);}finally{setSaving(false);}};
    const changeEnabled=async(item,enabled)=>{const action=enabled?"enabled":"disabled";setSaving(true);setError("");setNotice("");try{await request("/api/admin/config/dropdowns/"+item.id,token,{method:"PUT",body:JSON.stringify({code:item.code,enabled})});setNotice("Option "+action+".");await load();}catch(e){setError(e.message);}finally{setSaving(false);}};
    const remove=item=>setConfirmation({title:"Delete unused option?",message:`Delete “${item.displayLabel}” permanently? This is allowed only while no saved records use it.`,confirmLabel:"Delete option",onConfirm:async()=>{setSaving(true);setError("");try{await request(`/api/admin/config/dropdowns/${item.id}`,token,{method:"DELETE"});setNotice("Unused option deleted.");await load();}catch(e){setError(e.message);}finally{setSaving(false);}}});
    const move=async(item,direction)=>{const index=visible.findIndex(option=>option.id===item.id);const other=visible[index+direction];if(!other)return;setSaving(true);setError("");try{await Promise.all([request(`/api/admin/config/dropdowns/${item.id}`,token,{method:"PUT",body:JSON.stringify({code:item.code,sortOrder:other.sortOrder})}),request(`/api/admin/config/dropdowns/${other.id}`,token,{method:"PUT",body:JSON.stringify({code:other.code,sortOrder:item.sortOrder})})]);setNotice("Option order saved.");await load();}catch(e){setError(e.message);}finally{setSaving(false);}};
    const modules=[...new Set(categories.map(item=>item.module))];
    const usageText={PRIORITY:"tasks and code reviews",PROJECT_CATEGORY:"projects",ISSUE_STATUS:"tasks",PROJECT_STATUS:"projects",SPRINT_STATUS:"sprints",TEST_RESULT:"test executions",AUTOMATION_TRIGGER:"automation rules",AUTOMATION_ACTION:"automation rules",ACCOUNT_STATUS:"accounts",REPORTING_RANGE:"report filters",WORKLOAD_LEVEL:"workload filters"};
    return <section className={styles.panel}><h2 className="text-xl font-semibold text-white">Dropdown Configuration</h2><p className="mt-1 text-sm text-gray-400">Every supported selector is inventoried by module. Business lists support full management; workflow/system lists allow safe label and order changes only.</p>{error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}</Alert>}{loading?<StatePanel title="Loading dropdown inventory…"/>:<><div className="mt-5 max-w-xl"><label className={styles.label}>Choose a list<select value={category} onChange={event=>{setCategory(event.target.value);setEditing(null);setError("");}} className={styles.input+" mt-1"}>{modules.map(module=><optgroup key={module} label={module}>{categories.filter(item=>item.module===module).map(item=><option key={item.category} value={item.category}>{item.displayName}</option>)}</optgroup>)}</select></label><p className="mt-2 text-xs text-gray-400">{activeCategory?.description}{fullManagement?" Add, rename, reorder, disable, and safely delete unused values.":" Stored codes, workflow behavior, and security meaning are protected."}</p></div><div className="mt-5 divide-y divide-gray-800 rounded-xl border border-gray-800">{visible.map((item,index)=><div key={item.id} className="grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"><div className="min-w-0">{editing===item.id?<div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_8rem]"><label className={styles.label}>Display name<input autoFocus required maxLength="120" value={draft.displayLabel} onChange={event=>setDraft({...draft,displayLabel:event.target.value})} className={styles.input+" mt-1"}/></label><label className={styles.label}>Order<input type="number" min="0" max="100000" value={draft.sortOrder} onChange={event=>setDraft({...draft,sortOrder:event.target.value})} className={styles.input+" mt-1"}/></label></div>:<><p className="font-medium text-white">{item.displayLabel}{!item.enabled&&<span className="ml-2 text-xs text-amber-300">Disabled for new assignments</span>}</p><p className="mt-1 text-xs text-gray-500">Stored value: <code>{item.code}</code>{item.usageTracked!==false&&<> · {item.usageCount} existing {usageText[category]||"records"} use this value</>}</p></>}</div><div className="flex flex-wrap items-center gap-2">{editing===item.id?<><button type="button" disabled={saving||!draft.displayLabel.trim()} className={styles.button} onClick={()=>void saveEdit(item)}>Save</button><button type="button" className={styles.secondary} onClick={()=>setEditing(null)}>Cancel</button></>:<><button type="button" aria-label="Move option up" title="Move up" disabled={saving||index===0} className={styles.secondary} onClick={()=>void move(item,-1)}>↑</button><button type="button" aria-label="Move option down" title="Move down" disabled={saving||index===visible.length-1} className={styles.secondary} onClick={()=>void move(item,1)}>↓</button><button type="button" className={styles.secondary} onClick={()=>beginEdit(item)}>Edit label</button>{fullManagement&&<><button type="button" disabled={saving} className={styles.secondary} onClick={()=>item.enabled?setConfirmation({title:"Disable option?",message:`Existing records with “${item.displayLabel}” keep their saved value; new assignments will no longer offer it.`,confirmLabel:"Disable option",onConfirm:()=>changeEnabled(item,false)}):void changeEnabled(item,true)}>{item.enabled?"Disable":"Enable"}</button><button type="button" disabled={saving||item.usageCount>0} title={item.usageCount>0?"Used by existing records; disable instead":"Delete unused option"} className="rounded-lg border border-red-500/30 px-3 py-2 text-sm text-red-300 disabled:opacity-40" onClick={()=>remove(item)}>Delete</button></>}</>}</div></div>)}{!visible.length&&fullManagement&&<Empty text="This business list has no saved options yet. Add its first option below."/>}{!visible.length&&activeCategory?.managementMode==="LABELS_ONLY"&&<Empty text="No display-label entries are available. The startup initializer or V17 migration seeds the protected values."/>}</div>{fullManagement?<form onSubmit={add} className="mt-4 rounded-xl border border-gray-800 bg-gray-950/50 p-4"><h3 className="font-medium text-white">Add an option</h3><p className="mt-1 text-xs text-gray-500">The internal value is generated once and never changes when its display label is edited.</p><div className="mt-3 grid gap-3 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-end"><label className={styles.label}>Display name<input required maxLength="120" value={label} onChange={event=>setLabel(event.target.value)} className={styles.input+" mt-1"} placeholder={category==="PRIORITY"?"e.g. Urgent":"e.g. Customer portal"}/></label><label className={styles.label}>Order<input type="number" min="0" max="100000" value={order} onChange={event=>setOrder(Number(event.target.value))} className={styles.input+" mt-1"}/></label><button disabled={saving||!optionCode(label)} className={styles.button}>{saving?"Saving…":"Add option"}</button></div></form>:activeCategory?.managementMode==="RECORD_SELECTOR"?<p className="mt-4 rounded-lg border border-gray-800 bg-gray-950/50 p-3 text-xs text-gray-400">Choices come from current records and are filtered by permissions and project context. Manage the underlying people, projects, teams, sprints, tasks, and roles in their own sections.</p>:activeCategory?.managementMode==="DATA_DERIVED"?<p className="mt-4 rounded-lg border border-gray-800 bg-gray-950/50 p-3 text-xs text-gray-400">This filter is derived from audit events. Changing historical event identifiers would make the audit trail misleading, so its choices stay data-driven.</p>:<p className="mt-4 rounded-lg border border-amber-500/20 bg-amber-500/5 p-3 text-xs text-amber-100/80">These values map to workflow/security rules. Administrators can rename or reorder them only; add, delete, and enable/disable are intentionally unavailable.</p>}</>}{confirmation&&<AdminConfirmDialog {...confirmation} onCancel={()=>setConfirmation(null)} onConfirm={async()=>{const action=confirmation.onConfirm;setConfirmation(null);await action();}}/>}</section>;
}
function MenuManagement({token}) {
    const [items,setItems]=useState([]);const [permissions,setPermissions]=useState([]);const [draft,setDraft]=useState({});const [loading,setLoading]=useState(true);const [error,setError]=useState("");const [notice,setNotice]=useState("");const [saving,setSaving]=useState("");
    const load=useCallback(async()=>{setLoading(true);setError("");try{const [data,catalogue]=await Promise.all([request("/api/admin/config/menu",token),request("/api/admin/roles/permissions",token)]);setItems(data);setPermissions(Array.isArray(catalogue)?catalogue.filter(item=>item.scope==="GLOBAL"):[]);setDraft(Object.fromEntries(data.map(i=>[i.routeKey,{displayLabel:i.displayLabel,enabled:i.enabled,sortOrder:i.sortOrder,requiredPermission:i.requiredPermission||""}])));}catch(e){setError(e.message);}finally{setLoading(false);}},[token]);useEffect(()=>{void Promise.resolve().then(load);},[load]);
    const save=async key=>{setSaving(key);setError("");setNotice("");try{await request(`/api/admin/config/menu/${encodeURIComponent(key)}`,token,{method:"PUT",body:JSON.stringify(draft[key])});setNotice("Navigation entry saved.");await load();}catch(e){setError(e.message);}finally{setSaving("");}};
    const preview=[...items].filter(item=>draft[item.routeKey]?.enabled).sort((a,b)=>(draft[a.routeKey]?.sortOrder??0)-(draft[b.routeKey]?.sortOrder??0)||a.routeKey.localeCompare(b.routeKey));
    return <section className={styles.panel}><h2 className="text-xl font-semibold text-white">Menu Management</h2><p className="mt-1 text-sm text-gray-400">Manage the approved sidebar entries. Route destinations are fixed and existing permission checks remain in force.</p>{error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}</Alert>}{loading?<StatePanel title="Loading menu entries…"/>:<>
        <div className="mt-5 space-y-3">{items.map(item=><div key={item.routeKey} className="grid gap-3 rounded-xl border border-gray-800 bg-gray-950/50 p-4 lg:grid-cols-[1fr_1fr_7rem_1fr_auto_auto] lg:items-end"><label className={styles.label}>Approved menu entry<input disabled value={draft[item.routeKey]?.displayLabel||item.displayLabel} className={`${styles.input} mt-1 opacity-70`}/><span className="mt-1 block text-[11px] text-gray-600">Route: {item.routeKey}</span></label><label className={styles.label}>Display label<input maxLength="80" value={draft[item.routeKey]?.displayLabel||""} onChange={e=>setDraft({...draft,[item.routeKey]:{...draft[item.routeKey],displayLabel:e.target.value}})} className={`${styles.input} mt-1`}/></label><label className={styles.label}>Position<input type="number" min="0" max="100000" value={draft[item.routeKey]?.sortOrder??0} onChange={e=>setDraft({...draft,[item.routeKey]:{...draft[item.routeKey],sortOrder:Number(e.target.value)}})} className={`${styles.input} mt-1 w-24`}/></label><label className={styles.label}>Show to users with<select value={draft[item.routeKey]?.requiredPermission||""} onChange={e=>setDraft({...draft,[item.routeKey]:{...draft[item.routeKey],requiredPermission:e.target.value}})} className={`${styles.input} mt-1`}><option value="">Existing permission rules only</option>{permissions.map(permission=><option key={permission.key} value={permission.key}>{permission.description}</option>)}</select></label><label className="flex items-center gap-2 pb-2 text-xs text-gray-300"><input type="checkbox" checked={Boolean(draft[item.routeKey]?.enabled)} onChange={e=>setDraft({...draft,[item.routeKey]:{...draft[item.routeKey],enabled:e.target.checked}})} className="accent-blue-500"/>Enabled</label><button disabled={saving===item.routeKey} className={styles.button} onClick={()=>void save(item.routeKey)}>{saving===item.routeKey?"Saving…":"Save"}</button></div>)}{!items.length&&<Empty text="No predefined navigation entries are available."/>}</div>
        <aside aria-label="Navigation preview" className="mt-6 rounded-xl border border-blue-500/20 bg-blue-500/[.035] p-4"><h3 className="font-medium text-white">Enabled menu preview</h3><p className="mt-1 text-xs text-gray-500">Preview of configured order and labels. Actual visibility still follows each user's permissions and existing route rules.</p><div className="mt-3 flex flex-wrap gap-2">{preview.map(item=><span key={item.routeKey} className="rounded-lg border border-gray-700 bg-gray-900 px-3 py-2 text-sm text-gray-200">{draft[item.routeKey]?.displayLabel||item.displayLabel}{draft[item.routeKey]?.requiredPermission&&<span className="ml-2 text-[10px] text-blue-300">requires permission</span>}</span>)}{!preview.length&&<span className="text-sm text-gray-500">No entries are enabled.</span>}</div></aside>
    </>}</section>;
}

function WebsiteConfiguration({token}) {
    const [values,setValues]=useState([]);const [draft,setDraft]=useState({});const [loading,setLoading]=useState(true);const [error,setError]=useState("");const [notice,setNotice]=useState("");
    const load=useCallback(async()=>{setLoading(true);setError("");try{const data=await request("/api/config/website",token);setValues(data);setDraft(Object.fromEntries(data.map(x=>[x.key,x.value])));}catch(e){setError(e.message);}finally{setLoading(false);}},[token]);useEffect(()=>{void Promise.resolve().then(load);},[load]);
    const save=async key=>{setError("");setNotice("");try{await request(`/api/admin/config/website/${encodeURIComponent(key)}`,token,{method:"PUT",body:JSON.stringify({value:draft[key]})});setNotice("Workspace setting saved.");await load();}catch(e){setError(e.message);}};
    return <section className={styles.panel}><h2 className="text-xl font-semibold text-white">Website Configuration</h2><p className="mt-1 text-sm text-gray-400">Only supported business-facing workspace values are shown. Secrets and deployment security settings are not available through this API.</p>{error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}</Alert>}{loading?<StatePanel title="Loading workspace settings…"/>:<div className="mt-5 grid gap-4 md:grid-cols-2">{values.map(setting=><div key={setting.key} className="rounded-xl border border-gray-800 bg-gray-950/50 p-4"><label className={styles.label}>{setting.key}<input maxLength="240" value={draft[setting.key]||""} onChange={e=>setDraft({...draft,[setting.key]:e.target.value})} className={`${styles.input} mt-2`}/></label><button className={`${styles.button} mt-3`} onClick={()=>void save(setting.key)}>Save setting</button></div>)}{!values.length&&<Empty text="No editable website settings are configured."/>}</div>}</section>;
}

function roleName(code,roles){return roles.find(r=>r.code===code)?.name||code;}
function StatePanel({title}){return <div className="mt-5 rounded-xl border border-gray-800 bg-gray-950/40 p-5 text-sm text-gray-400">{title}</div>;}
function Empty({text}){return <p className="p-4 text-sm text-gray-500">{text}</p>;}
function Alert({children,tone="success"}){return <div role={tone==="error"?"alert":"status"} aria-live={tone==="error"?"assertive":"polite"} className={`fixed bottom-5 right-5 z-[110] flex max-w-[min(32rem,calc(100vw-2rem))] items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-2xl ${tone==="error"?"border-red-500/30 bg-gray-950 text-red-200":"border-emerald-500/30 bg-gray-950 text-emerald-200"}`}><span>{children}</span></div>;}

function AdminConfirmDialog({title,message,confirmLabel="Confirm",onCancel,onConfirm}){const[busy,setBusy]=useState(false);const confirm=async()=>{setBusy(true);try{await onConfirm();}finally{setBusy(false);}};return <div className="fixed inset-0 z-[120] flex items-center justify-center bg-black/70 p-4" role="presentation" onMouseDown={event=>{if(event.target===event.currentTarget&&!busy)onCancel();}}><section role="alertdialog" aria-modal="true" aria-labelledby="admin-confirm-title" aria-describedby="admin-confirm-message" className="w-full max-w-md rounded-2xl border border-gray-700 bg-gray-900 p-5 shadow-2xl"><h2 id="admin-confirm-title" className="text-lg font-semibold text-white">{title}</h2><p id="admin-confirm-message" className="mt-3 text-sm leading-relaxed text-gray-300">{message}</p><div className="mt-6 flex justify-end gap-2"><button type="button" disabled={busy} onClick={onCancel} className={styles.secondary}>Cancel</button><button type="button" disabled={busy} onClick={()=>void confirm()} className="rounded-lg bg-red-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-red-500 disabled:opacity-50">{busy?"Saving…":confirmLabel}</button></div></section></div>;}

export default AdminPanel;
