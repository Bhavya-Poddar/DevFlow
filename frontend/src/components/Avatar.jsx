import { useEffect, useState } from "react";

const API_BASE = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const initials = name => String(name || "User").trim().split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]?.toUpperCase()).join("") || "U";

export default function Avatar({ name, src, size = 40, version = "", className = "" }) {
    const [imageUrl, setImageUrl] = useState("");
    const [refresh, setRefresh] = useState(0);
    useEffect(() => { const refreshAvatar = () => setRefresh(Date.now()); window.addEventListener("devflow-avatar-updated", refreshAvatar); return () => window.removeEventListener("devflow-avatar-updated", refreshAvatar); }, []);
    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!src || !token) return undefined;
        const controller = new AbortController();
        let objectUrl = "";
        const separator = src.includes("?") ? "&" : "?";
        fetch(`${API_BASE}${src}${separator}v=${encodeURIComponent(version || refresh || "profile")}`, {
            headers: { Authorization: `Bearer ${token}` }, signal: controller.signal
        }).then(response => {
            if (!response.ok) throw new Error("Avatar unavailable");
            return response.blob();
        }).then(blob => {
            objectUrl = URL.createObjectURL(blob);
            setImageUrl(objectUrl);
        }).catch(() => { if (!controller.signal.aborted) setImageUrl(""); });
        return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
    }, [src, version, refresh]);

    return <span aria-label={name || "User"} className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-blue-500/15 font-semibold text-blue-200 ring-1 ring-white/10 ${className}`} style={{ width: size, height: size, fontSize: Math.max(11, Math.round(size * 0.34)) }}>
        {src && localStorage.getItem("token") && imageUrl ? <img src={imageUrl} alt="" className="h-full w-full object-cover" /> : initials(name)}
    </span>;
}