import React, { useState } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { register as registerService } from '../services/authService';
import { Mail, Lock, User, ArrowRight, Eye, EyeOff } from 'lucide-react';
import AuthLayout from '../components/AuthLayout';
import GoogleSignInButton from '../components/GoogleSignInButton';
import AppleSignInButton from '../components/AppleSignInButton';

const Register = () => {
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        try {
            const data = await registerService({ firstName, lastName, email, password });
            if (data && data.success) {
                const invitationToken = searchParams.get('invite');
                navigate(invitationToken ? `/login?invite=${encodeURIComponent(invitationToken)}` : '/login');
            } else {
                setError(data.message || 'Registration failed');
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
                <h2>Create Account</h2>
                <p>Join FamilyVault and secure your memories</p>
            </div>

            {error && <p style={{ color: 'red', textAlign: 'center', marginBottom: '1rem', fontSize: '0.9rem' }}>{error}</p>}

            <form onSubmit={handleSubmit}>
                <div className="input-group half-width">
                    <div>
                        <User className="input-icon" size={20} />
                        <input 
                            type="text" 
                            className="input-field" 
                            placeholder="First Name" 
                            value={firstName} 
                            onChange={(e) => setFirstName(e.target.value)} 
                            required 
                        />
                    </div>
                    <div>
                        <User className="input-icon" size={20} />
                        <input 
                            type="text" 
                            className="input-field" 
                            placeholder="Last Name" 
                            value={lastName} 
                            onChange={(e) => setLastName(e.target.value)} 
                            required 
                        />
                    </div>
                </div>

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
                        placeholder="Create a password" 
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

                <button type="submit" className="btn-primary" disabled={loading} style={{ marginTop: '1.5rem' }}>
                    {loading ? 'Registering...' : (
                        <>Create Account <ArrowRight size={18} /></>
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
                Already have an account? <Link to="/login">Login here</Link>
            </div>
        </AuthLayout>
    );
};

export default Register;
