import React, { useState, useContext } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { login as loginService } from '../services/authService';
import { AuthContext } from '../context/AuthContext';
import { Mail, Lock, ArrowRight, Eye, EyeOff } from 'lucide-react';
import AuthLayout from '../components/AuthLayout';
import GoogleSignInButton from '../components/GoogleSignInButton';
import AppleSignInButton from '../components/AppleSignInButton';

const Login = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        try {
            const data = await loginService({ email, password });
            if (data && data.success && data.data) {
                await login();
                const invitationToken = searchParams.get('invite');
                navigate(invitationToken ? `/invite/accept?token=${encodeURIComponent(invitationToken)}` : '/');
            } else {
                setError(data.message || 'Login failed');
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Failed to connect to the server');
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout>
            <div className="auth-header">
                <h2>Welcome Back</h2>
                <p>Sign in to your FamilyVault account</p>
            </div>

            {error && <p style={{ color: 'red', textAlign: 'center', marginBottom: '1rem', fontSize: '0.9rem' }}>{error}</p>}

            <form onSubmit={handleSubmit}>
                <div className="input-group">
                    <Mail className="input-icon" size={20} />
                    <input 
                        type="email" 
                        className="input-field" 
                        placeholder="Enter your email" 
                        value={email} 
                        onChange={(e) => setEmail(e.target.value)} 
                        required 
                    />
                </div>

                <div className="input-group">
                    <Lock className="input-icon" size={20} />
                    <input 
                        type={showPassword ? "text" : "password"} 
                        className="input-field" 
                        placeholder="Enter your password" 
                        value={password} 
                        onChange={(e) => setPassword(e.target.value)} 
                        required 
                    />
                    <button 
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        style={{ position: 'absolute', right: '1rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }}
                    >
                        {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                    </button>
                </div>

                <div className="auth-actions">
                    <label className="checkbox-group">
                        <input type="checkbox" /> Remember me
                    </label>
                    <Link to="/forgot-password" style={{ fontWeight: 600 }}>Forgot password?</Link>
                </div>

                <button type="submit" className="btn-primary" disabled={loading}>
                    {loading ? 'Logging in...' : (
                        <>Login <ArrowRight size={18} /></>
                    )}
                </button>
            </form>

            <div className="divider">OR</div>

            <GoogleSignInButton 
                onError={(msg) => setError(msg)}
                onStartLoading={() => setLoading(true)}
                onEndLoading={() => setLoading(false)}
            />

            <AppleSignInButton
                onError={(msg) => setError(msg)}
                onStartLoading={() => setLoading(true)}
                onEndLoading={() => setLoading(false)}
            />

            <div className="auth-footer">
                Don't have an account? <Link to="/register">Register here</Link>
            </div>
        </AuthLayout>
    );
};

export default Login;
