import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, ArrowRight, ArrowLeft, CheckCircle2, AlertCircle, Shield, RefreshCw } from 'lucide-react';
import AuthLayout from '../components/AuthLayout';
import { forgotPassword } from '../services/authService';
import './ForgotPassword.css';

const ForgotPassword = () => {
    const [email, setEmail] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [isSubmitted, setIsSubmitted] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        const trimmedEmail = email.trim().toLowerCase();

        if (!trimmedEmail) {
            setError('Please enter your email address.');
            return;
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(trimmedEmail)) {
            setError('Please enter a valid email address.');
            return;
        }

        setLoading(true);
        setError(null);

        try {
            await forgotPassword(trimmedEmail);
            setIsSubmitted(true);
        } catch (err) {
            console.error('Forgot password error:', err);
            setError(err.response?.data?.message || err.message || 'Failed to send reset email. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout hideToggle={true}>
            <div className="forgot-card-content">
                {/* Back Link */}
                <div className="forgot-top-bar">
                    <Link to="/login" className="forgot-back-link">
                        <ArrowLeft size={16} /> Back to Login
                    </Link>
                </div>

                {!isSubmitted ? (
                    <>
                        <div className="auth-header">
                            <div className="forgot-icon-pill">
                                <Mail size={26} color="#5A45FF" />
                            </div>
                            <h2>Forgot Password?</h2>
                            <p>Enter your registered account email and we'll send you a secure password reset link.</p>
                        </div>

                        {error && (
                            <div className="auth-alert error">
                                <AlertCircle size={18} />
                                <span>{error}</span>
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="forgot-form">
                            <label className="input-label">Email Address</label>
                            <div className="input-group">
                                <Mail className="input-icon" size={20} />
                                <input
                                    type="email"
                                    className="input-field"
                                    placeholder="Enter your registered email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>

                            <div className="forgot-security-notice">
                                <Shield size={16} color="#10B981" />
                                <span>A secure reset link valid for 15 minutes will be emailed to you.</span>
                            </div>

                            <button type="submit" className="btn-primary" disabled={loading}>
                                {loading ? (
                                    <>
                                        <RefreshCw size={18} className="animate-spin" /> Sending Link...
                                    </>
                                ) : (
                                    <>
                                        Send Reset Link <ArrowRight size={18} />
                                    </>
                                )}
                            </button>
                        </form>
                    </>
                ) : (
                    <div className="forgot-success-box">
                        <div className="success-icon-wrapper">
                            <CheckCircle2 size={52} color="#10B981" />
                        </div>
                        <h2>Reset Link Sent!</h2>
                        <p>
                            We have emailed a secure password reset link to <span className="success-email-highlight">{email}</span>.
                        </p>
                        <p style={{ fontSize: '0.85rem', color: '#64748B', marginTop: '-0.35rem' }}>
                            Please check your inbox (and spam folder) and click the link to set your new password. The link will expire in <strong>15 minutes</strong>.
                        </p>

                        <div className="success-security-badge">
                            <Shield size={16} color="#5A45FF" />
                            <span>FamilyVault Security • End-to-End Encrypted Link</span>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', width: '100%', marginTop: '0.5rem' }}>
                            <Link to="/login" className="btn-primary" style={{ textDecoration: 'none', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}>
                                Back to Login <ArrowRight size={18} />
                            </Link>

                            <button
                                type="button"
                                className="change-email-btn"
                                onClick={() => {
                                    setIsSubmitted(false);
                                    setError(null);
                                }}
                            >
                                Try another email
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </AuthLayout>
    );
};

export default ForgotPassword;
