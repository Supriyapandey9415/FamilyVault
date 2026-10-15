const SAFE_METHODS = new Set(['get', 'head', 'options', 'trace']);
const CSRF_HEADER = 'X-XSRF-TOKEN';

async function errorCode(response) {
    const data = response?.data;
    if (typeof Blob !== 'undefined' && data instanceof Blob && data.size <= 8192) {
        // Password-protected downloads request blobs, including for JSON error responses.
        try { return JSON.parse(await data.text())?.code; }
        catch { return undefined; }
    }
    return data?.code;
}

/** Coordinate token bootstrap and recover only from a confirmed, pre-controller CSRF rejection. */
export function installCsrfProtection(api, onSessionExpired = () => {}) {
    let csrfToken = null;
    let csrfRequest = null;

    const getToken = () => {
        if (csrfToken) return Promise.resolve(csrfToken);
        if (!csrfRequest) {
            csrfRequest = api.get('/auth/csrf', { _csrfBootstrap: true })
                .then(({ data }) => {
                    if (typeof data?.token !== 'string' || !data.token.trim()) {
                        throw new Error('Unable to initialize request security. Please reload and try again.');
                    }
                    csrfToken = data.token;
                    return csrfToken;
                })
                .finally(() => { csrfRequest = null; });
        }
        return csrfRequest;
    };

    const requiresCsrf = (config) => !SAFE_METHODS.has((config.method || 'get').toLowerCase())
        && !config.headers?.get('Authorization')?.startsWith('Bearer ');

    api.interceptors.request.use(async (config) => {
        if (requiresCsrf(config)) {
            // Capture the resolved value; unrelated responses must not null out this request's header.
            const token = await getToken();
            config.headers.set(CSRF_HEADER, token);
        }
        return config;
    });

    api.interceptors.response.use((response) => response, async (error) => {
        const request = error.config;
        if (error.response?.status === 401) {
            // Authentication and the CSRF cookie have separate lifecycles. In particular,
            // an anonymous /auth/me response may arrive while a public login is starting.
            onSessionExpired();
        }

        if (error.response?.status === 403 && request && !request._csrfBootstrap && requiresCsrf(request)
            && await errorCode(error.response) === 'CSRF_TOKEN_INVALID') {
            // A late rejection for an old token must not discard a newer, already refreshed token.
            if (csrfToken === request.headers.get(CSRF_HEADER)) csrfToken = null;
            if (!request._csrfRetried && !request.signal?.aborted) {
                request._csrfRetried = true;
                // CSRF rejection happens before the controller runs, so this single replay
                // cannot repeat an upload, delete, login or other application side effect.
                await getToken();
                return api.request(request);
            }
        }
        return Promise.reject(error);
    });
}
