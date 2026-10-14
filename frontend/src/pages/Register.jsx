import { useState } from "react";
import { useNavigate } from "react-router-dom";

function Register() {

    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();

    const handleRegister = async (e) => {

        e.preventDefault();

        setError("");
        setLoading(true);

        try {

            const response = await fetch(
                "http://localhost:8080/api/auth/register",
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
        <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">

            <div className="w-full max-w-md">

                {/* Logo */}
                <div className="text-center mb-8">

                    <h1 className="text-4xl font-bold text-white tracking-tight">
                        Dev<span className="text-blue-500">Flow</span>
                    </h1>

                    <p className="text-gray-400 mt-2">
                        Engineering workflow, simplified.
                    </p>

                </div>


                {/* Register Card */}
                <div className="bg-gray-900 border border-gray-800 rounded-2xl p-8 shadow-2xl">

                    <div className="mb-6">

                        <h2 className="text-2xl font-semibold text-white">
                            Create an account
                        </h2>

                        <p className="text-gray-400 text-sm mt-1">
                            Join your engineering workspace
                        </p>

                    </div>


                    {/* Error */}
                    {error && (
                        <div className="mb-5 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                            {error}
                        </div>
                    )}


                    {/* Form */}
                    <form
                        onSubmit={handleRegister}
                        className="space-y-5"
                    >

                        {/* Name */}
                        <div>

                            <label className="block text-sm font-medium text-gray-300 mb-2">
                                Full Name
                            </label>

                            <input
                                type="text"
                                value={name}
                                onChange={(e) =>
                                    setName(e.target.value)
                                }
                                placeholder="Bhavya Poddar"
                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700
                                           rounded-lg text-white placeholder-gray-500
                                           focus:outline-none focus:ring-2 focus:ring-blue-500
                                           focus:border-transparent transition"
                                required
                            />

                        </div>


                        {/* Email */}
                        <div>

                            <label className="block text-sm font-medium text-gray-300 mb-2">
                                Email
                            </label>

                            <input
                                type="email"
                                value={email}
                                onChange={(e) =>
                                    setEmail(e.target.value)
                                }
                                placeholder="you@example.com"
                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700
                                           rounded-lg text-white placeholder-gray-500
                                           focus:outline-none focus:ring-2 focus:ring-blue-500
                                           focus:border-transparent transition"
                                required
                            />

                        </div>


                        {/* Password */}
                        <div>

                            <label className="block text-sm font-medium text-gray-300 mb-2">
                                Password
                            </label>

                            <input
                                type="password"
                                value={password}
                                onChange={(e) =>
                                    setPassword(e.target.value)
                                }
                                placeholder="Create a password"
                                className="w-full px-4 py-3 bg-gray-800 border border-gray-700
                                           rounded-lg text-white placeholder-gray-500
                                           focus:outline-none focus:ring-2 focus:ring-blue-500
                                           focus:border-transparent transition"
                                required
                            />

                        </div>


                        {/* Register Button */}
                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full py-3 bg-blue-600 hover:bg-blue-700
                                       disabled:bg-blue-800 disabled:cursor-not-allowed
                                       text-white font-semibold rounded-lg
                                       transition duration-200"
                        >
                            {loading ? "Creating account..." : "Create Account"}
                        </button>
                    </form>
                    {/* Login Link */}
                    <div className="text-center mt-6">
                        <p className="text-sm text-gray-400">
                            Already have an account?{" "}
                            <button
                                type="button"
                                onClick={() => navigate("/login")}
                                className="text-blue-400 hover:text-blue-300 font-medium"
                            >
                                Sign in
                            </button>
                        </p>
                    </div>
                </div>

                {/* Footer */}
                <p className="text-center text-gray-600 text-xs mt-6">
                    DevFlow • Engineering Management Platform
                </p>

            </div>

        </div>
    );
}

export default Register;