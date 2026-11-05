import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { cwd } from "node:process";
import { isIP } from "node:net";

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, cwd(), "VITE_");

    if (mode === "production") {
        const configuredApiBaseUrl = env.VITE_API_BASE_URL?.trim();
        let apiUrl;
        try {
            apiUrl = new URL(configuredApiBaseUrl);
        } catch {
            throw new Error("Production builds require VITE_API_BASE_URL to be set to the HTTPS DevFlow API origin.");
        }

        const hostname = apiUrl.hostname.toLowerCase().replace(/^\[|\]$/g, "");
        const addressFamily = isIP(hostname);
        const firstIpv4Octet = addressFamily === 4 ? Number(hostname.split(".")[0]) : null;
        const mappedIpv4 = hostname.match(/^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/);
        const mappedIpv4FirstOctet = mappedIpv4 ? Number.parseInt(mappedIpv4[1], 16) >> 8 : null;
        const unusableLocalTarget = hostname === "localhost" || hostname.endsWith(".localhost")
            || addressFamily === 4 && (firstIpv4Octet === 0 || firstIpv4Octet === 127)
            || addressFamily === 6 && (hostname === "::" || hostname === "::1"
                || mappedIpv4FirstOctet === 0 || mappedIpv4FirstOctet === 127);
        if (apiUrl.protocol !== "https:" || unusableLocalTarget || apiUrl.username || apiUrl.password
                || apiUrl.search || apiUrl.hash) {
            throw new Error("Production VITE_API_BASE_URL must be an HTTPS API origin and cannot target localhost.");
        }
    }

    return {
        plugins: [react(), tailwindcss()],
        define: {
            global: "globalThis"
        }
    };
});