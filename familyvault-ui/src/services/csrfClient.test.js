import assert from 'node:assert/strict';
import test from 'node:test';
import axios, { AxiosError } from 'axios';
import { installCsrfProtection } from './csrfClient.js';

const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
};

function harness({ bootstrap, mutate } = {}) {
    const state = { cookie: null, issued: 0, bootstraps: 0, mutations: 0, effects: 0, expired: 0 };
    const response = (config, data, status = 200) => ({ config, data, status, headers: {}, statusText: String(status) });
    const reject = (config, status, data = {}) => {
        throw new AxiosError('Request rejected', AxiosError.ERR_BAD_REQUEST, config, null, response(config, data, status));
    };
    const api = axios.create({
        adapter: async (config) => {
            if (config.url === '/auth/csrf') {
                state.bootstraps++;
                if (bootstrap) await bootstrap(state, config, reject);
                state.cookie ||= `token-${++state.issued}`;
                return response(config, { token: state.cookie });
            }
            if (config.url === '/anonymous-profile') return reject(config, 401);
            if (config.method === 'get') return response(config, { ok: true });
            state.mutations++;
            if (mutate) await mutate(state, config, reject);
            if (!config.headers.get('Authorization')?.startsWith('Bearer ')
                && config.headers.get('X-XSRF-TOKEN') !== state.cookie) {
                return reject(config, 403, { code: 'CSRF_TOKEN_INVALID' });
            }
            state.effects++;
            return response(config, { ok: true });
        },
    });
    installCsrfProtection(api, () => { state.expired++; });
    return { api, state };
}

test('first mutation waits for bootstrap and succeeds with a token', async () => {
    const { api, state } = harness();
    await api.post('/documents', { name: 'Report' });
    assert.equal(state.bootstraps, 1);
    assert.equal(state.mutations, 1);
    assert.equal(state.effects, 1);
});

test('concurrent first mutations share a single pending bootstrap', async () => {
    const entered = deferred();
    const release = deferred();
    const { api, state } = harness({ bootstrap: async () => { entered.resolve(); await release.promise; } });
    const requests = Promise.all([api.post('/a'), api.put('/b'), api.delete('/c')]);
    await entered.promise;
    assert.equal(state.mutations, 0);
    release.resolve();
    await requests;
    assert.equal(state.bootstraps, 1);
    assert.equal(state.effects, 3);
});

test('an anonymous profile 401 during bootstrap cannot remove the mutation token', async () => {
    const entered = deferred();
    const release = deferred();
    const { api, state } = harness({ bootstrap: async () => { entered.resolve(); await release.promise; } });
    const login = api.post('/auth/login');
    await entered.promise;
    await assert.rejects(api.get('/anonymous-profile'));
    release.resolve();
    await login;
    assert.equal(state.expired, 1);
    assert.equal(state.bootstraps, 1);
    assert.equal(state.mutations, 1);
});

test('a 401 does not invalidate an independent, already initialized CSRF token', async () => {
    const { api, state } = harness();
    await api.post('/first');
    await assert.rejects(api.get('/anonymous-profile'));
    await api.post('/second');
    assert.equal(state.bootstraps, 1);
    assert.equal(state.effects, 2);
});

test('a cookie changed by another tab is refreshed and replayed once without duplicate effects', async () => {
    const { api, state } = harness();
    await api.post('/first');
    state.cookie = 'changed-by-another-tab';
    await api.post('/second', { name: 'Upload' });
    assert.equal(state.bootstraps, 2);
    assert.equal(state.mutations, 3);
    assert.equal(state.effects, 2);
});

test('permission-denied 403 is never retried and does not discard the CSRF token', async () => {
    const { api, state } = harness({
        mutate: async (_state, config, reject) => {
            if (config.url === '/denied') reject(config, 403, { code: 'ACCESS_DENIED' });
        },
    });
    await assert.rejects(api.delete('/denied'), (error) => error.response.data.code === 'ACCESS_DENIED');
    await api.post('/allowed');
    assert.equal(state.mutations, 2);
    assert.equal(state.bootstraps, 1);
    assert.equal(state.effects, 1);
});

test('legacy generic 403 and network failures are never automatically replayed', async () => {
    for (const networkFailure of [false, true]) {
        const { api, state } = harness({ mutate: async (_state, config, reject) => {
            if (networkFailure) throw new AxiosError('Network error', AxiosError.ERR_NETWORK, config);
            reject(config, 403, { message: 'Access denied or CSRF verification failed' });
        } });
        await assert.rejects(api.post('/documents'));
        assert.equal(state.mutations, 1);
        assert.equal(state.bootstraps, 1);
    }
});

test('persistent CSRF failure is limited to one retry', async () => {
    const { api, state } = harness({ mutate: async (_state, config, reject) => {
        reject(config, 403, { code: 'CSRF_TOKEN_INVALID' });
    } });
    await assert.rejects(api.post('/documents'), (error) => error.response.status === 403);
    assert.equal(state.mutations, 2);
    assert.equal(state.bootstraps, 2);
    assert.equal(state.effects, 0);
});

test('concurrent stale-token rejections share one refresh', async () => {
    const entered = deferred();
    const release = deferred();
    const { api, state } = harness({ bootstrap: async (current) => {
        if (current.bootstraps === 2) { entered.resolve(); await release.promise; }
    } });
    await api.post('/first');
    state.cookie = 'rotated';
    const requests = Promise.all([api.post('/a'), api.put('/b'), api.delete('/c')]);
    await entered.promise;
    release.resolve();
    await requests;
    assert.equal(state.bootstraps, 2);
    assert.equal(state.effects, 4);
});

test('a late rejection for an older token does not discard a newer refreshed token', async () => {
    const entered = deferred();
    const release = deferred();
    const { api, state } = harness({ mutate: async (_state, config, reject) => {
        if (config.url === '/slow' && !config._csrfRetried) {
            entered.resolve();
            await release.promise;
            reject(config, 403, { code: 'CSRF_TOKEN_INVALID' });
        }
    } });
    await api.post('/first');
    const slowRequest = api.post('/slow');
    await entered.promise;
    state.cookie = 'rotated';
    await api.post('/fast');
    release.resolve();
    await slowRequest;
    assert.equal(state.bootstraps, 2);
    assert.equal(state.effects, 3);
});

test('CSRF recovery preserves multipart uploads and does not duplicate a saved file', async () => {
    const form = new FormData();
    form.append('documentName', 'Report');
    form.append('file', new Blob(['test document'], { type: 'text/plain' }), 'report.txt');
    const { api, state } = harness({ mutate: async (_state, config) => {
        if (config.url === '/upload') {
            assert.equal(config.data, form);
            assert.equal(config.data.get('documentName'), 'Report');
            assert.equal(config.data.get('file').name, 'report.txt');
        }
    } });
    await api.post('/first');
    state.cookie = 'rotated';
    await api.post('/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } });
    assert.equal(state.mutations, 3);
    assert.equal(state.effects, 2);
});

test('protected downloads also recover when the JSON CSRF error is returned as a Blob', async () => {
    const { api, state } = harness({ mutate: async (current, config, reject) => {
        if (config.headers.get('X-XSRF-TOKEN') !== current.cookie) {
            reject(config, 403, new Blob([JSON.stringify({ code: 'CSRF_TOKEN_INVALID' })], { type: 'application/json' }));
        }
    } });
    await api.post('/first');
    state.cookie = 'rotated';
    await api.post('/documents/1/download', { password: 'fixture-password' }, { responseType: 'blob' });
    assert.equal(state.bootstraps, 2);
    assert.equal(state.effects, 2);
    assert.equal(state.mutations, 3);
});

test('permission-denied and non-JSON download blobs are not replayed', async () => {
    for (const body of [JSON.stringify({ code: 'ACCESS_DENIED' }), 'Forbidden']) {
        const { api, state } = harness({ mutate: async (_state, config, reject) => {
            reject(config, 403, new Blob([body]));
        } });
        await assert.rejects(api.post('/documents/1/download', {}, { responseType: 'blob' }));
        assert.equal(state.mutations, 1);
    }
});

test('bootstrap failure is not recursively replayed and later requests can recover', async () => {
    const { api, state } = harness({ bootstrap: async (current, config, reject) => {
        if (current.bootstraps === 1) reject(config, 403, { code: 'CSRF_TOKEN_INVALID' });
    } });
    await assert.rejects(api.post('/first'));
    assert.equal(state.mutations, 0);
    await api.post('/second');
    assert.equal(state.bootstraps, 2);
    assert.equal(state.effects, 1);
});

test('safe reads and explicit Bearer requests do not bootstrap CSRF', async () => {
    const { api, state } = harness();
    await api.get('/documents');
    await api.post('/documents', {}, { headers: { Authorization: 'Bearer access-token' } });
    assert.equal(state.bootstraps, 0);
    assert.equal(state.effects, 1);
});

test('a malformed bootstrap token fails closed without sending the mutation', async () => {
    const api = axios.create({ adapter: async (config) => ({
        config, data: { token: '' }, status: 200, statusText: 'OK', headers: {},
    }) });
    installCsrfProtection(api);
    await assert.rejects(api.post('/documents'), /Unable to initialize request security/);
});

test('an aborted mutation is not replayed after CSRF rejection', async () => {
    const controller = new AbortController();
    const { api, state } = harness({ mutate: async (_state, config, reject) => {
        controller.abort();
        reject(config, 403, { code: 'CSRF_TOKEN_INVALID' });
    } });
    await assert.rejects(api.post('/documents', {}, { signal: controller.signal }));
    assert.equal(state.mutations, 1);
    assert.equal(state.bootstraps, 1);
});
