import { useState } from "react";
import { Link } from "react-router-dom";
import { applyTheme, getTheme } from "../theme";

const STORAGE_KEY = "devflow.preferences";

function readPreferences() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}") || {};
    } catch {
        return {};
    }
}

function Settings() {
    const [preferences, setPreferences] = useState(readPreferences);
    const [saved, setSaved] = useState(false);
    const [theme, setTheme] = useState(getTheme);

    const update = changes => {
        const next = { ...preferences, ...changes };
        setPreferences(next);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        window.dispatchEvent(new Event("devflow-preferences-change"));
        setSaved(true);
        window.setTimeout(() => setSaved(false), 1800);
    };

    const reset = () => {
        localStorage.removeItem(STORAGE_KEY);
        setPreferences({});
        setTheme(applyTheme("dark"));
        window.dispatchEvent(new Event("devflow-preferences-change"));
        setSaved(false);
    };

    const toggle = (title, description, checked, onChange) => (
        <label className="flex cursor-pointer items-center justify-between gap-5 rounded-xl border border-gray-800 bg-gray-950/60 p-4">
            <span><span className="block text-sm font-medium text-gray-200">{title}</span><span className="mt-1 block text-xs leading-relaxed text-gray-500">{description}</span></span>
            <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} className="h-4 w-4 shrink-0 accent-blue-500" />
        </label>
    );

    return (
        <div className="mx-auto max-w-5xl space-y-8">
            <div className="flex flex-wrap items-end justify-between gap-4">
                <div><p className="text-sm font-medium text-blue-400">PREFERENCES</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-white">Settings</h1><p className="mt-2 text-sm text-gray-400">Tune your DevFlow workspace experience.</p></div>
                <div className="flex items-center gap-3">{saved && <span role="status" className="text-xs text-emerald-400">Saved on this device</span>}<button onClick={reset} className="rounded-lg border border-gray-700 px-4 py-2.5 text-sm font-medium text-gray-300 transition hover:bg-gray-800">Reset preferences</button></div>
            </div>

            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-6 sm:p-7">
                <div className="mb-5"><h2 className="text-lg font-semibold text-white">Appearance</h2><p className="mt-1 text-sm text-gray-500">Small interface adjustments for this device.</p></div>
                <div className="space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-800 bg-gray-950/60 p-4"><div><p className="text-sm font-medium text-gray-200">Color theme</p><p className="mt-1 text-xs text-gray-500">Choose the appearance used across DevFlow on this device.</p></div><div className="inline-flex rounded-lg border border-gray-700 bg-gray-900 p-1" role="group" aria-label="Color theme">{["dark", "light"].map(option => <button key={option} type="button" aria-pressed={theme === option} onClick={() => setTheme(applyTheme(option))} className={`rounded-md px-4 py-2 text-sm font-medium capitalize transition ${theme === option ? "bg-blue-600 text-white shadow" : "text-gray-400 hover:bg-gray-800 hover:text-white"}`}>{option}</button>)}</div></div>
                    {toggle("Compact content", "Use tighter spacing in the workspace content area.", Boolean(preferences.compactMode), value => update({ compactMode: value }))}
                </div>
            </section>

            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-6 sm:p-7">
                <div className="mb-5"><h2 className="text-lg font-semibold text-white">Notifications</h2><p className="mt-1 text-sm text-gray-500">Choose whether the notification control appears in your header.</p></div>
                {toggle("Show notification bell", "Hides or shows the in-app notification bell. Notification delivery preferences are not changed.", preferences.showNotificationBell !== false, value => update({ showNotificationBell: value }))}
            </section>

            <section className="rounded-2xl border border-gray-800 bg-gray-900 p-6 sm:p-7">
                <div className="mb-5"><h2 className="text-lg font-semibold text-white">Workspace & security</h2><p className="mt-1 text-sm text-gray-500">Profile details and password are managed on your account page.</p></div>
                <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-800 bg-gray-950/60 p-4"><div><p className="text-sm font-medium text-gray-200">Profile & account security</p><p className="mt-1 text-xs leading-relaxed text-gray-500">Update your display name, email, profile picture, and password securely from your account page.</p></div><Link to="/account" className="rounded-lg border border-gray-700 px-3.5 py-2 text-sm text-gray-200 hover:bg-gray-800">Manage account</Link></div>
                <p className="mt-4 text-xs text-gray-600">Preferences are stored only in this browser and are not synced to the server.</p>
            </section>
        </div>
    );
}

export default Settings;
