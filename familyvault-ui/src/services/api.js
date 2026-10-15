import axios from 'axios';
import { installCsrfProtection } from './csrfClient.js';

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL
    || (import.meta.env.PROD ? '/api' : 'http://localhost:8080/api')).replace(/\/+$/, '');

const api = axios.create({
    baseURL: apiBaseUrl,
    withCredentials: true,
    // The HttpOnly CSRF cookie is read by the server, not Axios. Use /auth/csrf.
    xsrfCookieName: null,
    headers: { 'Content-Type': 'application/json' },
});

installCsrfProtection(api, () => {
    window.dispatchEvent(new Event('familyvault:session-expired'));
});

export default api;
