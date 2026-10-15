import React, { useEffect, useState, useRef, useContext } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getAuthConfig, loginWithGoogle } from '../services/authService';
import { AuthContext } from '../context/AuthContext';
import { AlertCircle, X, ShieldCheck } from 'lucide-react';

const GoogleSignInButton = ({ onError, onStartLoading, onEndLoading }) => {
    const [clientId, setClientId] = useState('');
    const [isConfigured, setIsConfigured] = useState(false);
    const [showConfigModal, setShowConfigModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const googleButtonRef = useRef(null);
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // 1. Fetch backend dynamic OAuth configuration
    useEffect(() => {
        let isMounted = true;
        const initConfig = async () => {
            try {
                const config = await getAuthConfig();
                const resolvedClientId = config?.googleClientId || import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
                if (isMounted) {
                    if (resolvedClientId && resolvedClientId.trim()) {
                        setClientId(resolvedClientId.trim());
                        setIsConfigured(true);
                    }
                }
            } catch (err) {
                console.warn('Could not fetch Google auth configuration from backend:', err);
                const envClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
                if (isMounted && envClientId && envClientId.trim()) {
                    setClientId(envClientId.trim());
                    setIsConfigured(true);
                }
            }
        };
        initConfig();
        return () => { isMounted = false; };
    }, []);

    // 2. Initialize Google Identity Services when script is ready and clientId is available
    useEffect(() => {
        if (!clientId || !isConfigured) return;

        const setupGIS = () => {
            if (!window.google?.accounts?.id) return false;

            try {
                window.google.accounts.id.initialize({
                    client_id: clientId,
                    callback: handleCredentialResponse,
                    auto_select: false,
                    cancel_on_tap_outside: true,
                });

                if (googleButtonRef.current) {
                    // Render the official Google sign-in button
                    window.google.accounts.id.renderButton(googleButtonRef.current, {
                        type: 'standard',
                        theme: 'outline',
                        size: 'large',
                        text: 'continue_with',
                        shape: 'rectangular',
                        logo_alignment: 'left',
                        width: googleButtonRef.current.offsetWidth || 340,
                    });
                }
                return true;
            } catch (e) {
                console.error('Failed to initialize Google Identity Services:', e);
                return false;
            }
        };

        if (!setupGIS()) {
            const interval = setInterval(() => {
                if (setupGIS()) clearInterval(interval);
            }, 300);
            return () => clearInterval(interval);
        }
    }, [clientId, isConfigured]);

    // 3. Handle credential token returned by Google
    const handleCredentialResponse = async (response) => {
        if (!response || !response.credential) {
            if (onError) onError('Google did not return valid credentials.');
            return;
        }

        setSubmitting(true);
        if (onStartLoading) onStartLoading();

        try {
            const inviteToken = searchParams.get('invite');
            const data = await loginWithGoogle({
                idToken: response.credential,
                inviteToken: inviteToken || null,
            });

            if (data && data.success && data.data) {
                await login();

                if (inviteToken) {
                    navigate(`/invite/accept?token=${encodeURIComponent(inviteToken)}`);
                } else {
                    navigate('/');
                }
            } else {
                if (onError) onError(data?.message || 'Google Sign-in failed');
            }
        } catch (err) {
            console.error('Error during Google authentication exchange:', err);
            const msg = err.response?.data?.message || 'Failed to authenticate with Google. Please try again.';
            if (onError) onError(msg);
        } finally {
            setSubmitting(false);
            if (onEndLoading) onEndLoading();
        }
    };

    const handleClickFallback = () => {
        if (isConfigured && window.google?.accounts?.id) {
            try {
                window.google.accounts.id.prompt();
            } catch (e) {
                console.warn('One-tap prompt error:', e);
            }
        } else {
            setShowConfigModal(true);
        }
    };

    return (
        <div style={{ width: '100%', marginBottom: '1.2vh' }}>
            {/* If configured and GIS renders the official button */}
            {isConfigured ? (
                <div 
                    ref={googleButtonRef} 
                    style={{ 
                        width: '100%', 
                        display: 'flex', 
                        justifyContent: 'center',
                        minHeight: '44px' 
                    }} 
                />
            ) : (
                <button
                    type="button"
                    className="social-btn"
                    onClick={handleClickFallback}
                    disabled={submitting}
                    title="Sign in with Google"
                >
                    <img
                        src="https://www.svgrepo.com/show/475656/google-color.svg"
                        alt="Google"
                        width="20"
                    />
                    {submitting ? 'Connecting to Google...' : 'Continue with Google'}
                </button>
            )}

            {/* Informational Guidance Modal if GOOGLE_CLIENT_ID is not configured yet */}
            {showConfigModal && (
                <div 
                    style={{
                        position: 'fixed',
                        inset: 0,
                        backgroundColor: 'rgba(0,0,0,0.5)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        zIndex: 9999,
                        padding: '1rem',
                        backdropFilter: 'blur(3px)'
                    }}
                    onClick={() => setShowConfigModal(false)}
                >
                    <div 
                        style={{
                            background: 'white',
                            borderRadius: '16px',
                            maxWidth: '460px',
                            width: '100%',
                            padding: '1.75rem',
                            boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
                            color: '#1E293B',
                            position: 'relative'
                        }}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <button
                            type="button"
                            onClick={() => setShowConfigModal(false)}
                            style={{
                                position: 'absolute',
                                top: '1rem',
                                right: '1rem',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer',
                                color: '#64748B'
                            }}
                        >
                            <X size={20} />
                        </button>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
                            <div style={{
                                width: '42px',
                                height: '42px',
                                borderRadius: '10px',
                                background: '#EEF2FF',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                color: '#4F46E5'
                            }}>
                                <ShieldCheck size={24} />
                            </div>
                            <div>
                                <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700 }}>
                                    Google Sign-In Architecture Active
                                </h3>
                                <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748B' }}>
                                    OAuth2 Backend & GIS Ready
                                </p>
                            </div>
                        </div>

                        <p style={{ fontSize: '0.92rem', lineHeight: '1.5', color: '#334155', marginBottom: '1rem' }}>
                            The dynamic Google Sign-In pipeline is fully wired up on both the frontend and Spring Boot backend!
                        </p>

                        <div style={{
                            background: '#F8FAFC',
                            border: '1px solid #E2E8F0',
                            borderRadius: '10px',
                            padding: '0.9rem',
                            fontSize: '0.85rem',
                            fontFamily: 'monospace',
                            color: '#0F172A',
                            marginBottom: '1.25rem'
                        }}>
                            <strong>Backend:</strong> Set <code>GOOGLE_CLIENT_ID</code> in <code>application.yml</code> or environment variable.<br /><br />
                            <strong>Frontend:</strong> Or set <code>VITE_GOOGLE_CLIENT_ID</code> in <code>familyvault-ui/.env</code>.
                        </div>

                        <button
                            type="button"
                            onClick={() => setShowConfigModal(false)}
                            style={{
                                width: '100%',
                                padding: '0.75rem',
                                borderRadius: '10px',
                                border: 'none',
                                background: 'linear-gradient(135deg, #4F46E5, #6366F1)',
                                color: 'white',
                                fontWeight: 600,
                                cursor: 'pointer'
                            }}
                        >
                            Got It
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default GoogleSignInButton;
