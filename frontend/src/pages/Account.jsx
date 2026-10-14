import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Avatar from "../components/Avatar";
import { apiUrl } from "../services/issueApi";
import { responseErrorMessage } from "../utils/responseError";

const roleLabels = { ADMIN: "Administrator", PROJECT_MANAGER: "Project Manager", DEVELOPER: "Developer", TESTER: "Tester", VIEWER: "Viewer" };
const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem("token") || ""}` });

function Account() {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [avatarVersion, setAvatarVersion] = useState("");
    const [status, setStatus] = useState(() => localStorage.getItem("token") ? "loading" : "error");
    const [error, setError] = useState(() => localStorage.getItem("token") ? "" : "You need to sign in to view your account.");
    const [notice, setNotice] = useState("");
    const [savingProfile, setSavingProfile] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [uploading, setUploading] = useState(false);

    const load = useCallback(async () => {
        const response = await fetch(apiUrl("/api/users/me"), { headers: authHeaders() });
        if (!response.ok) throw new Error(response.status === 401 ? "Your session has expired. Please sign in again." : "We couldn't load your account right now.");
        const data = await response.json();
        setUser(data); setName(data.name || ""); setEmail(data.email || ""); setStatus("ready");
    }, []);

    useEffect(() => {
        if (!localStorage.getItem("token")) return;
        let active = true;
        void Promise.resolve().then(() => load()).catch(reason => { if (active) { setError(reason.message); setStatus("error"); } });
        return () => { active = false; };
    }, [load]);

    const logoutToLogin = message => { localStorage.removeItem("token"); navigate("/login", { replace: true, state: { message } }); };
    const saveProfile = async event => {
        event.preventDefault(); setError(""); setNotice(""); setSavingProfile(true);
        try {
            const response = await fetch(apiUrl("/api/users/me/profile"), { method: "PUT", headers: { ...authHeaders(), "Content-Type": "application/json" }, body: JSON.stringify({ name, email }) });
            const data = await response.json().catch(() => null);
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not save profile details."));
            if (data.email !== user.email) { logoutToLogin("Your email changed. Sign in again with the updated address."); return; }
            setUser(data); setNotice("Profile details saved.");
        } catch (reason) { setError(reason.message); } finally { setSavingProfile(false); }
    };

    const uploadAvatar = async event => {
        const file = event.target.files?.[0]; event.target.value = "";
        if (!file) return;
        if (!["image/png", "image/jpeg"].includes(file.type)) { setError("Choose a PNG or JPEG image."); return; }
        if (file.size > 5 * 1024 * 1024) { setError("Profile images must be 5 MB or smaller."); return; }
        setError(""); setNotice(""); setUploading(true);
        try {
            const body = new FormData(); body.append("file", file);
            const response = await fetch(apiUrl("/api/users/me/avatar"), { method: "POST", headers: authHeaders(), body });
            const data = await response.json().catch(() => null);
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not upload profile image."));
            setUser(data); setAvatarVersion(String(Date.now())); window.dispatchEvent(new Event("devflow-avatar-updated")); setNotice("Profile picture updated.");
        } catch (reason) { setError(reason.message); } finally { setUploading(false); }
    };

    const changePassword = async event => {
        event.preventDefault(); setError(""); setNotice("");
        if (newPassword.length < 12) { setError("Use at least 12 characters for your new password."); return; }
        if (newPassword !== confirmPassword) { setError("The new passwords do not match."); return; }
        setSavingPassword(true);
        try {
            const response = await fetch(apiUrl("/api/users/me/password"), { method: "PUT", headers: { ...authHeaders(), "Content-Type": "application/json" }, body: JSON.stringify({ currentPassword, newPassword }) });
            if (!response.ok) throw new Error(await responseErrorMessage(response, "Could not change password."));
            logoutToLogin("Password changed. Sign in again with your new password.");
        } catch (reason) { setError(reason.message); } finally { setSavingPassword(false); }
    };

    return <div className="mx-auto max-w-5xl space-y-8">
        <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-blue-400">YOUR PROFILE</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-white">My account</h1><p className="mt-2 text-sm text-gray-400">Manage your identity and account security.</p></div><Link to="/settings" className="rounded-lg border border-gray-700 px-4 py-2.5 text-sm text-gray-300 hover:bg-gray-800">Workspace settings</Link></header>
        {error && <div role="alert" className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">{error}</div>}
        {notice && <div role="status" className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm text-emerald-300">{notice}</div>}
        {status === "loading" && <div role="status" className="rounded-2xl border border-gray-800 bg-gray-900 p-8 text-sm text-gray-400">Loading your account…</div>}
        {status === "error" && !user && <div className="rounded-2xl border border-gray-800 bg-gray-900 p-6 text-sm text-gray-400">Account unavailable. <Link to="/login" className="text-blue-400">Sign in</Link></div>}
        {user && <>
            <section className="rounded-2xl border border-gray-800 bg-gradient-to-br from-gray-900 to-gray-900/70 p-6 sm:p-8"><div className="flex flex-wrap items-center gap-5"><Avatar name={user.name} src={user.avatarUrl} version={avatarVersion} size={88} className="text-2xl" /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-3"><h2 className="text-2xl font-semibold text-white">{user.name || "Unnamed user"}</h2><span className="rounded-full border border-blue-400/25 bg-blue-400/10 px-3 py-1 text-xs font-medium text-blue-300">{roleLabels[user.role] || user.role}</span></div><p className="mt-1 text-sm text-gray-400">{user.email}</p></div><label className="cursor-pointer rounded-lg border border-gray-700 px-4 py-2.5 text-sm font-medium text-gray-200 transition hover:border-blue-500 hover:bg-gray-800">{uploading ? "Uploading…" : "Change picture"}<input type="file" accept="image/png,image/jpeg" disabled={uploading} onChange={uploadAvatar} className="sr-only" /></label></div><p className="mt-4 text-xs text-gray-500">PNG or JPEG · up to 5 MB</p></section>
            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-6 sm:p-7"><div className="mb-6"><h2 className="text-lg font-semibold text-white">Profile details</h2><p className="mt-1 text-sm text-gray-500">Your role is managed by your workspace administrator.</p></div><form onSubmit={saveProfile} className="grid gap-5 sm:grid-cols-2"><label className="text-sm text-gray-300">Display name<input required value={name} onChange={event => setName(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-3 text-white outline-none focus:border-blue-500" /></label><label className="text-sm text-gray-300">Email address<input required type="email" value={email} onChange={event => setEmail(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-3 text-white outline-none focus:border-blue-500" /></label><div className="text-sm text-gray-400">Workspace role<span className="mt-2 block rounded-lg border border-gray-800 bg-gray-950 px-3.5 py-3 text-gray-300">{roleLabels[user.role] || user.role}</span></div><div className="flex items-end"><button disabled={savingProfile} className="rounded-lg bg-blue-600 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60">{savingProfile ? "Saving…" : "Save profile"}</button></div></form></section>
            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-6 sm:p-7"><div className="mb-6"><h2 className="text-lg font-semibold text-white">Change password</h2><p className="mt-1 text-sm text-gray-500">Confirm your current password. You’ll sign in again after a successful change.</p></div><form onSubmit={changePassword} className="grid gap-5 sm:grid-cols-2"><label className="text-sm text-gray-300 sm:col-span-2">Current password<input required type="password" autoComplete="current-password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-3 text-white outline-none focus:border-blue-500" /></label><label className="text-sm text-gray-300">New password<input required type="password" autoComplete="new-password" value={newPassword} onChange={event => setNewPassword(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-3 text-white outline-none focus:border-blue-500" /></label><label className="text-sm text-gray-300">Confirm new password<input required type="password" autoComplete="new-password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} className="mt-2 w-full rounded-lg border border-gray-700 bg-gray-950 px-3.5 py-3 text-white outline-none focus:border-blue-500" /></label><div className="sm:col-span-2"><button disabled={savingPassword} className="rounded-lg border border-gray-700 px-5 py-3 text-sm font-semibold text-gray-200 hover:bg-gray-800 disabled:opacity-60">{savingPassword ? "Updating…" : "Update password"}</button></div></form></section>
        </>}
    </div>;
}
export default Account;