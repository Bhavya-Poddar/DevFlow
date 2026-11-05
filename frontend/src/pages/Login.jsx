import { apiUrl } from "../services/apiConfig";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

function Login() {

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();

    const handleLogin = async (e) => {

        e.preventDefault();

        setError("");
        setLoading(true);

        try {

            const response = await fetch(
                apiUrl("/api/auth/login"),
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify({
                        email: email,
                        password: password
                    })
                }
            );

            if (!response.ok) {

                const message = await response.text();

                throw new Error(
                    message || "Invalid email or password"
                );
            }

            const data = await response.json();

            if (!data?.token) {
                throw new Error("The server did not return an authentication token.");
            }

            // Save JWT token
            localStorage.setItem("token", data.token);

            // Go to dashboard
            navigate("/dashboard");

        } catch (error) {

            setError(error.message);

        } finally {

            setLoading(false);

        }
    };

    return (
        <main className="df-auth-root dfx-auth-stage">
            <div className="dfx-auth-layout">
                <aside className="dfx-auth-story">
                    <Link className="dfx-brand" to="/login" aria-label="DevFlow home"><span className="dfx-brand-mark">D</span><span>Dev<span>Flow</span></span></Link>
                    <div className="dfx-auth-story-copy">
                        <p className="dfx-auth-kicker"><span /> ENGINEERING WORKSPACE</p>
                        <h1>From first report<br />to <em>shipped.</em></h1>
                        <p className="dfx-auth-description">Keep project work, task ownership, and testing progress moving in one focused place.</p>
                        <div className="dfx-auth-flow" aria-label="DevFlow process">
                            <div><span>01</span><strong>Plan</strong><small>Projects & sprints</small></div><i aria-hidden="true">→</i>
                            <div><span>02</span><strong>Build</strong><small>Tasks & reviews</small></div><i aria-hidden="true">→</i>
                            <div><span>03</span><strong>Verify</strong><small>Test outcomes</small></div>
                        </div>
                    </div>
                    <p className="dfx-auth-footnote">A clearer path from engineering intent to verified delivery.</p>
                </aside>
                <section className="df-auth-card dfx-auth-form-panel" aria-labelledby="login-title">
                    <div className="dfx-auth-form-heading"><p className="dfx-auth-kicker">WELCOME BACK</p><h2 id="login-title">Sign in to DevFlow</h2><p>Your workspace is ready when you are.</p></div>
                    {error && <div role="alert" className="dfx-auth-error"><span aria-hidden="true">!</span><p>{error}</p></div>}
                    <form onSubmit={handleLogin} className="dfx-auth-form">
                        <label htmlFor="login-email">Email address</label>
                        <input id="login-email" type="email" autoComplete="username" value={email} onChange={event => setEmail(event.target.value)} placeholder="name@company.com" required />
                        <div className="dfx-auth-label-row"><label htmlFor="login-password">Password</label><span>Use your DevFlow account password</span></div>
                        <div className="dfx-password-field"><input id="login-password" type={showPassword ? "text" : "password"} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} placeholder="Enter your password" required /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? "Hide" : "Show"}</button></div>
                        <button type="submit" disabled={loading} className="dfx-auth-submit">{loading ? <><span className="dfx-button-spinner" />Signing in…</> : <>Continue to workspace <span aria-hidden="true">→</span></>}</button>
                    </form>
                    <p className="dfx-auth-switch">New to DevFlow? <button type="button" onClick={() => navigate("/register")}>Create an account <span aria-hidden="true">↗</span></button></p>
                    <div className="dfx-auth-form-footer"><span>DEVFLOW</span><span>Engineering Management Platform</span></div>
                </section>
            </div>
        </main>
    );
}

export default Login;
