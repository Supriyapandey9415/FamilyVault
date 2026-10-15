import React, { createContext, useCallback, useState, useEffect } from 'react';
import { getProfile, logoutSession } from '../services/authService';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [authLoading, setAuthLoading] = useState(true);

    const refreshProfile = useCallback(async () => {
        const profile = await getProfile();
        setUser(profile || null);
        return profile;
    }, []);

    useEffect(() => {
        // One-time cleanup of credentials cached by older versions.
        try {
            ['token', 'userEmail', 'userProfile'].forEach((name) => localStorage.removeItem(name));
        } catch { /* Restricted browser storage must not prevent cookie authentication. */ }
        let active = true;
        getProfile().then((profile) => { if (active) setUser(profile || null); })
            .catch(() => { if (active) setUser(null); })
            .finally(() => { if (active) setAuthLoading(false); });
        const expire = () => setUser(null);
        window.addEventListener('familyvault:session-expired', expire);
        return () => {
            active = false;
            window.removeEventListener('familyvault:session-expired', expire);
        };
    }, []);

    // The server sets an HttpOnly cookie. Login responses are never persisted in JS storage.
    const login = async () => {
        const profile = await refreshProfile();
        if (!profile) throw new Error('Unable to verify the signed-in session');
    };

    const logout = async () => {
        try { await logoutSession(); }
        catch (error) { if (error.response?.status !== 401) throw error; }
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ token: user ? 'cookie-session' : null, user, setUser,
            authLoading, login, logout, refreshProfile }}>
            {children}
        </AuthContext.Provider>
    );
};
