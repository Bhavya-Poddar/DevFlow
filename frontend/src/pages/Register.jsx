import { apiUrl } from "../services/apiConfig";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

function Register() {

    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();

    const handleRegister = async (e) => {

        e.preventDefault();

        setError("");
        setLoading(true);

        try {

            const response = await fetch(
                apiUrl("/api/auth/register"),
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify({
                        name: name,
                        email: email,
                        password: password
                    })
                }
            );

            if (!response.ok) {

                const message = await response.text();

                throw new Error(
                    message || "Registration failed"
                );
            }

            alert("Account created successfully!");

            navigate("/login");

        } catch (error) {

            setError(error.message);

        } finally {

            setLoading(false);
        }
    };

    return (
        <main className="df-auth-root dfx-auth-stage">
            <div className="dfx-auth-layout dfx-auth-layout-register">
                <aside className="dfx-auth-story">
                    <Link className="dfx-brand" to="/register" aria-label="DevFlow home"><span className="dfx-brand-mark">D</span><span>Dev<span>Flow</span></span></Link>
                    <div className="dfx-auth-story-copy">
                        <p className="dfx-auth-kicker"><span /> START WITH CLARITY</p>
                        <h1>Make good work<br /><em>flow forward.</em></h1>
                        <p className="dfx-auth-description">Bring your engineering projects, tasks, and testing handoffs into one shared workflow.</p>
                        <div className="dfx-auth-feature-list"><p><span>01</span><span><strong>Organize delivery</strong><small>Connect projects, sprints, and tasks.</small></span></p><p><span>02</span><span><strong>Keep ownership clear</strong><small>Make assignments visible to the team.</small></span></p><p><span>03</span><span><strong>Close the testing loop</strong><small>Record outcomes beside the work.</small></span></p></div>
                    </div>
                    <p className="dfx-auth-footnote">Your new account begins with viewer access.</p>
                </aside>
                <section className="df-auth-card dfx-auth-form-panel" aria-labelledby="register-title">
                    <div className="dfx-auth-form-heading"><p className="dfx-auth-kicker">CREATE YOUR ACCOUNT</p><h2 id="register-title">Join your workspace</h2><p>Set up your DevFlow identity to get started.</p></div>
                    {error && <div role="alert" className="dfx-auth-error"><span aria-hidden="true">!</span><p>{error}</p></div>}
                    <form onSubmit={handleRegister} className="dfx-auth-form">
                        <label htmlFor="register-name">Full name</label>
                        <input id="register-name" type="text" autoComplete="name" value={name} onChange={event => setName(event.target.value)} placeholder="Your name" required />
                        <label htmlFor="register-email">Email address</label>
                        <input id="register-email" type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="name@company.com" required />
                        <label htmlFor="register-password">Password</label>
                        <div className="dfx-password-field"><input id="register-password" type={showPassword ? "text" : "password"} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} placeholder="Create a password" required /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? "Hide" : "Show"}</button></div>
                        <p className="dfx-auth-help">Public sign-up creates a read-only Viewer account. Workspace administrators can update access later.</p>
                        <button type="submit" disabled={loading} className="dfx-auth-submit">{loading ? <><span className="dfx-button-spinner" />Creating account…</> : <>Create account <span aria-hidden="true">→</span></>}</button>
                    </form>
                    <p className="dfx-auth-switch">Already have an account? <button type="button" onClick={() => navigate("/login")}>Sign in <span aria-hidden="true">↗</span></button></p>
                    <div className="dfx-auth-form-footer"><span>DEVFLOW</span><span>Engineering Management Platform</span></div>
                </section>
            </div>
        </main>
    );
}

export default Register;