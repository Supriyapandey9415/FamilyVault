import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Apple } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import {
    createAppleAuthSession,
    getAppleAuthConfig,
    loginWithApple,
} from '../services/authService';

const APPLE_SCRIPT_ID = 'familyvault-apple-signin-sdk';
const APPLE_SCRIPT_URL = 'https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js';

let appleScriptPromise;

const loadAppleScript = () => {
    if (window.AppleID?.auth) return Promise.resolve();
    if (appleScriptPromise) return appleScriptPromise;

    appleScriptPromise = new Promise((resolve, reject) => {
        const existing = document.getElementById(APPLE_SCRIPT_ID);
        const script = existing || document.createElement('script');
        const handleLoad = () => resolve();
        const handleError = () => {
            appleScriptPromise = undefined;
            reject(new Error('Apple Sign-In could not be loaded.'));
        };

        script.addEventListener('load', handleLoad, { once: true });
        script.addEventListener('error', handleError, { once: true });
        if (!existing) {
            script.id = APPLE_SCRIPT_ID;
            script.src = APPLE_SCRIPT_URL;
            script.async = true;
            script.defer = true;
            document.head.appendChild(script);
        }
    });
    return appleScriptPromise;
};

const readAppleError = (error) => {
    const code = error?.error || error?.message || '';
    if (String(code).includes('popup_closed') || String(code).includes('user_cancelled')) {
        return 'Apple Sign-In was cancelled.';
    }
    return 'Apple Sign-In failed. Please try again.';
};

const AppleSignInButton = ({ onError, onStartLoading, onEndLoading }) => {
    const [config, setConfig] = useState(null);
    const [authSession, setAuthSession] = useState(null);
    const [preparing, setPreparing] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const mountedRef = useRef(true);
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const prepareSession = useCallback(async () => {
        try {
            const session = await createAppleAuthSession();
            if (mountedRef.current) setAuthSession(session);
            return session;
        } catch (error) {
            if (mountedRef.current) setAuthSession(null);
            throw error;
        }
    }, []);

    useEffect(() => {
        mountedRef.current = true;
        const initialize = async () => {
            try {
                const resolvedConfig = await getAppleAuthConfig();
                if (!mountedRef.current) return;
                setConfig(resolvedConfig);
                if (resolvedConfig?.enabled) {
                    await Promise.all([loadAppleScript(), prepareSession()]);
                }
            } catch (error) {
                console.warn('Apple Sign-In initialization failed:', error);
            } finally {
                if (mountedRef.current) setPreparing(false);
            }
        };
        initialize();
        return () => { mountedRef.current = false; };
    }, [prepareSession]);

    const handleAppleSignIn = async () => {
        if (!config?.enabled) {
            onError?.('Apple Sign-In is not configured on the server.');
            return;
        }

        let session = authSession;
        const expiresSoon = !session?.expiresAt
            || new Date(session.expiresAt).getTime() < Date.now() + 30_000;

        setSubmitting(true);
        onStartLoading?.();
        try {
            if (expiresSoon) session = await prepareSession();
            await loadAppleScript();

            window.AppleID.auth.init({
                clientId: config.clientId,
                scope: 'name email',
                redirectURI: config.redirectUri,
                state: session.state,
                nonce: session.nonce,
                usePopup: true,
            });

            const appleResponse = await window.AppleID.auth.signIn();
            const authorizationCode = appleResponse?.authorization?.code;
            if (!authorizationCode) {
                throw new Error('Apple did not return an authorization code.');
            }

            const inviteToken = searchParams.get('invite');
            const response = await loginWithApple({
                authorizationCode,
                state: session.state,
                firstName: appleResponse?.user?.name?.firstName || null,
                lastName: appleResponse?.user?.name?.lastName || null,
                inviteToken: inviteToken || null,
            });

            if (!response?.success || !response?.data) {
                throw new Error(response?.message || 'Apple Sign-In failed.');
            }

            await login();
            navigate(inviteToken ? `/invite/accept?token=${encodeURIComponent(inviteToken)}` : '/');
        } catch (error) {
            console.error('Apple authentication failed:', error);
            const message = error?.response?.data?.message || readAppleError(error);
            onError?.(message);
            if (mountedRef.current) {
                setAuthSession(null);
                prepareSession().catch(() => {});
            }
        } finally {
            if (mountedRef.current) setSubmitting(false);
            onEndLoading?.();
        }
    };

    const disabled = preparing || submitting || !config?.enabled;
    const label = preparing
        ? 'Preparing Apple Sign-In...'
        : submitting
            ? 'Connecting to Apple...'
            : config?.enabled
                ? 'Continue with Apple'
                : 'Apple Sign-In unavailable';

    return (
        <button
            type="button"
            className="social-btn apple-signin-btn"
            onClick={handleAppleSignIn}
            disabled={disabled}
            title={!preparing && !config?.enabled ? 'Configure Apple Sign-In on the server to enable this option.' : undefined}
        >
            <Apple size={20} fill="currentColor" aria-hidden="true" />
            {label}
        </button>
    );
};

export default AppleSignInButton;
