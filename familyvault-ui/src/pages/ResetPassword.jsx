import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { 
    Lock, ArrowRight, ArrowLeft, Eye, EyeOff, CheckCircle2, 
    AlertCircle, Shield, RefreshCw, Check, KeyRound 
} from 'lucide-react';
import AuthLayout from '../components/AuthLayout';
import { resetPassword, verifyResetToken } from '../services/authService';
import './ForgotPassword.css';

const ResetPassword = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');

    // Form fields
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    // UI states
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [isVerifying, setIsVerifying] = useState(true);
    const [error, setError] = useState(null);
    const [isSuccess, setIsSuccess] = useState(false);
    const [isTokenExpiredOrUsed, setIsTokenExpiredOrUsed] = useState(false);

    // Verify token validity on initial page load
    useEffect(() => {
        if (!token) {
            setIsVerifying(false);
            return;
        }

        let isMounted = true;
        const checkTokenValidity = async () => {
            try {
                await verifyResetToken(token);
                if (isMounted) {
                    setIsVerifying(false);
                }
            } catch (err) {
                console.warn('Reset token verification check:', err);
                if (isMounted) {
                    setIsTokenExpiredOrUsed(true);
                    setIsVerifying(false);
                }
            }
        };

        checkTokenValidity();

        return () => {
            isMounted = false;
        };
    }, [token]);

    // Password strength rules
    const hasMinLength = newPassword.length >= 8;
    const hasLetter = /[a-zA-Z]/.test(newPassword);
    const hasNumber = /[0-9]/.test(newPassword);
    const passwordsMatch = newPassword.length > 0 && newPassword === confirmPassword;

    const getStrengthScore = () => {
        let score = 0;
        if (hasMinLength) score++;
        if (hasLetter) score++;
        if (hasNumber) score++;
        if (/[^a-zA-Z0-9]/.test(newPassword)) score++;
        return score;
    };

    const strengthScore = getStrengthScore();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);

        if (!token) {
            setIsTokenExpiredOrUsed(true);
            return;
        }

        if (!hasMinLength || !hasLetter || !hasNumber) {
            setError('Password must be at least 8 characters and contain both letters and numbers.');
            return;
        }

        if (newPassword !== confirmPassword) {
            setError('Passwords do not match.');
            return;
        }

        setLoading(true);

        try {
            await resetPassword(token, newPassword);
            setIsSuccess(true);
        } catch (err) {
            console.error('Password reset error:', err);
            const serverMsg = err.response?.data?.message || err.message || '';
            const lowerMsg = serverMsg.toLowerCase();
            const isInvalidOrExpired = 
                err.response?.status === 404 || 
                lowerMsg.includes('expired') || 
                lowerMsg.includes('already') || 
                lowerMsg.includes('used') || 
                (lowerMsg.includes('token') && !lowerMsg.includes('password'));

            if (isInvalidOrExpired) {
                setIsTokenExpiredOrUsed(true);
            } else {
                setError(serverMsg || 'Failed to reset password. Please try again.');
            }
        } finally {
            setLoading(false);
        }
    };

    // 1. Verifying Token on Initial Page Load
    if (isVerifying && !isSuccess) {
        return (
            <AuthLayout hideToggle={true}>
                <div className="forgot-card-content" style={{ textAlign: 'center', padding: '2rem 1rem' }}>
                    <div className="forgot-icon-pill">
                        <RefreshCw size={26} color="#5A45FF" className="animate-spin" />
                    </div>
                    <h2 style={{ fontSize: '1.25rem', marginTop: '1rem', color: '#0F172A', fontWeight: 700 }}>
                        Verifying Reset Link...
                    </h2>
                    <p style={{ color: '#64748B', fontSize: '0.875rem', marginTop: '0.35rem' }}>
                        Checking security credentials with FamilyVault.
                    </p>
                </div>
            </AuthLayout>
        );
    }

    // 2. Token Already Used or Expired (Prevent reopening the form)
    if (isTokenExpiredOrUsed && !isSuccess) {
        return (
            <AuthLayout hideToggle={true}>
                <div className="forgot-card-content">
                    <div className="forgot-top-bar">
                        <Link to="/login" className="forgot-back-link">
                            <ArrowLeft size={16} /> Back to Login
                        </Link>
                    </div>

                    <div className="auth-header">
                        <div className="forgot-icon-pill" style={{ background: '#FEF2F2', borderColor: '#FECACA' }}>
                            <AlertCircle size={28} color="#DC2626" />
                        </div>
                        <h2>Link Already Used or Expired</h2>
                        <p style={{ marginTop: '0.65rem', lineHeight: '1.5', color: '#64748B' }}>
                            You have already used this reset link to update your password, or the link has expired.
                        </p>
                        <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: '#475569', fontWeight: 500 }}>
                            If you want to reset your password again, please request a new link from <strong>Forgot Password</strong>.
                        </p>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1.75rem' }}>
                        <Link to="/forgot-password" className="btn-primary" style={{ textDecoration: 'none', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem', padding: '0.85rem' }}>
                            Request New Reset Link <ArrowRight size={18} />
                        </Link>
                        <Link to="/login" className="change-email-btn" style={{ textAlign: 'center', marginTop: '0.25rem' }}>
                            Return to Login
                        </Link>
                    </div>
                </div>
            </AuthLayout>
        );
    }

    // 3. If no token in URL
    if (!token && !isSuccess) {
        return (
            <AuthLayout hideToggle={true}>
                <div className="forgot-card-content">
                    <div className="forgot-top-bar">
                        <Link to="/login" className="forgot-back-link">
                            <ArrowLeft size={16} /> Back to Login
                        </Link>
                    </div>

                    <div className="auth-header">
                        <div className="forgot-icon-pill" style={{ background: '#FEF2F2', borderColor: '#FECACA' }}>
                            <AlertCircle size={26} color="#DC2626" />
                        </div>
                        <h2>Invalid Reset Link</h2>
                        <p>No reset token was found in your link. The link may be incomplete or corrupted.</p>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1.5rem' }}>
                        <Link to="/forgot-password" className="btn-primary" style={{ textDecoration: 'none', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}>
                            Request New Reset Link <ArrowRight size={18} />
                        </Link>
                        <Link to="/login" className="change-email-btn" style={{ textAlign: 'center' }}>
                            Return to Login
                        </Link>
                    </div>
                </div>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout hideToggle={true}>
            <div className="forgot-card-content">
                <div className="forgot-top-bar">
                    <Link to="/login" className="forgot-back-link">
                        <ArrowLeft size={16} /> Back to Login
                    </Link>
                </div>

                {!isSuccess ? (
                    <>
                        <div className="auth-header">
                            <div className="forgot-icon-pill">
                                <KeyRound size={26} color="#5A45FF" />
                            </div>
                            <h2>Set New Password</h2>
                            <p>Choose a strong password to secure your FamilyVault account.</p>
                        </div>

                        {isTokenExpiredOrUsed ? (
                            <div className="token-expired-box">
                                <div className="token-expired-message">
                                    <AlertCircle size={22} className="token-expired-icon" />
                                    <div className="token-expired-content">
                                        <span className="token-expired-title">
                                            This link has expired or has already been used. Please request a new one.
                                        </span>
                                        <span className="token-expired-desc">
                                            For security reasons, password reset links are single-use only and expire after 15 minutes.
                                        </span>
                                    </div>
                                </div>
                                <Link to="/forgot-password" className="btn-primary token-fallback-btn">
                                    Request a New Reset Link <ArrowRight size={16} />
                                </Link>
                            </div>
                        ) : error ? (
                            <div className="auth-alert error">
                                <AlertCircle size={18} />
                                <span>{error}</span>
                            </div>
                        ) : null}

                        <form onSubmit={handleSubmit} className="forgot-form">
                            {/* New Password */}
                            <label className="input-label">New Password</label>
                            <div className="input-group">
                                <Lock className="input-icon" size={20} />
                                <input
                                    type={showPassword ? "text" : "password"}
                                    className="input-field"
                                    placeholder="At least 8 characters (letters & numbers)"
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    required
                                    autoFocus
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(!showPassword)}
                                    className="input-eye-btn"
                                    aria-label="Toggle password visibility"
                                >
                                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                                </button>
                            </div>

                            {/* Password Strength Indicator */}
                            {newPassword.length > 0 && (
                                <div className="password-strength-box">
                                    <div className="strength-bar-track">
                                        <div 
                                            className={`strength-bar-fill score-${strengthScore}`} 
                                            style={{ width: `${(strengthScore / 4) * 100}%` }}
                                        />
                                    </div>
                                    <div className="strength-rules">
                                        <span className={`rule ${hasMinLength ? 'met' : ''}`}>
                                            <Check size={12} /> 8+ chars
                                        </span>
                                        <span className={`rule ${hasLetter ? 'met' : ''}`}>
                                            <Check size={12} /> Letter
                                        </span>
                                        <span className={`rule ${hasNumber ? 'met' : ''}`}>
                                            <Check size={12} /> Number
                                        </span>
                                    </div>
                                </div>
                            )}

                            {/* Confirm Password */}
                            <label className="input-label">Confirm New Password</label>
                            <div className="input-group">
                                <Lock className="input-icon" size={20} />
                                <input
                                    type={showConfirmPassword ? "text" : "password"}
                                    className="input-field"
                                    placeholder="Confirm new password"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    required
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                    className="input-eye-btn"
                                    aria-label="Toggle confirm password visibility"
                                >
                                    {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                                </button>
                            </div>

                            {confirmPassword && (
                                <div className={`match-hint ${passwordsMatch ? 'valid' : 'invalid'}`}>
                                    {passwordsMatch ? (
                                        <><CheckCircle2 size={14} /> Passwords match</>
                                    ) : (
                                        <><AlertCircle size={14} /> Passwords do not match</>
                                    )}
                                </div>
                            )}

                            <button 
                                type="submit" 
                                className="btn-primary" 
                                disabled={loading || isTokenExpiredOrUsed || !hasMinLength || !hasLetter || !hasNumber || !passwordsMatch}
                            >
                                {loading ? (
                                    <>
                                        <RefreshCw size={18} className="animate-spin" /> Updating Password...
                                    </>
                                ) : (
                                    <>
                                        Reset Password <ArrowRight size={18} />
                                    </>
                                )}
                            </button>
                        </form>
                    </>
                ) : (
                    <div className="forgot-success-box">
                        <div className="success-icon-wrapper">
                            <CheckCircle2 size={56} color="#10B981" />
                        </div>
                        <h2>Password Reset Complete!</h2>
                        <p>
                            Your FamilyVault password has been updated securely. You can now use your new password to sign in.
                        </p>

                        <div className="success-security-badge">
                            <Shield size={16} color="#5A45FF" />
                            <span>Your session and vault access remain strictly encrypted.</span>
                        </div>

                        <button 
                            type="button" 
                            className="btn-primary"
                            onClick={() => navigate('/login')}
                        >
                            Proceed to Login <ArrowRight size={18} />
                        </button>
                    </div>
                )}
            </div>
        </AuthLayout>
    );
};

export default ResetPassword;
