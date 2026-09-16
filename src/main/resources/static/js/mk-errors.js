/*
 * MediKiosk shared fetch/error feedback.
 *
 * MkFetch wraps fetch and converts transport/auth failures into typed errors:
 *   - 401/403 → {kind:'session'}   (session expired or not authorized)
 *   - network failure → {kind:'network'}
 *   - other non-OK responses resolve normally so callers can read their
 *     own JSON error bodies (existing contracts unchanged).
 *
 * MkFeedback renders honest, recoverable inline feedback:
 *   - a single aria-live region per page (role=alert)
 *   - a session-expiry bar with a sign-in path — never a silent restart
 * No raw server error text is ever shown to users.
 */
(function (global) {
    'use strict';

    function liveRegion() {
        var region = document.getElementById('mk-feedback-region');
        if (region) {
            return region;
        }
        region = document.createElement('div');
        region.id = 'mk-feedback-region';
        region.setAttribute('role', 'alert');
        region.setAttribute('aria-live', 'assertive');
        document.body.appendChild(region);
        return region;
    }

    function makeBox(kind) {
        var box = document.createElement('div');
        box.className = 'mk-feedback mk-feedback--' + kind;
        box.setAttribute('role', 'alert');
        var text = document.createElement('span');
        text.className = 'mk-feedback__text';
        box.appendChild(text);
        var dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.className = 'mk-feedback__dismiss';
        dismiss.setAttribute('aria-label', 'Dismiss message');
        dismiss.textContent = '\u00d7';
        dismiss.addEventListener('click', function () {
            box.remove();
        });
        box.appendChild(dismiss);
        return box;
    }

    function signInLink() {
        var link = document.createElement('a');
        link.className = 'mk-feedback__action';
        link.href = '/login';
        link.textContent = 'Sign in again';
        return link;
    }

    // Appends one dismissible message box to the shared aria-live region.
    // Replaces only previous feedback boxes in that container — never other
    // content. opts: { container (Element, optional), kind: 'session'|'network'|'error' }
    function show(message, opts) {
        opts = opts || {};
        var parent = opts.container || liveRegion();
        parent.querySelectorAll(':scope > .mk-feedback').forEach(function (previous) {
            previous.remove();
        });
        var box = makeBox(opts.kind || 'error');
        box.querySelector('.mk-feedback__text').textContent = String(message || 'Something went wrong. Please try again.');
        if (opts.kind === 'session') {
            box.querySelector('.mk-feedback__text').appendChild(signInLink());
        }
        parent.appendChild(box);
        return box;
    }

    // Fixed, prominent session-expiry bar. Does not navigate away or restart
    // anything — the user chooses when to sign in again.
    function showSessionBar(message) {
        var existing = document.getElementById('mk-session-bar');
        if (existing) {
            return existing;
        }
        var bar = document.createElement('div');
        bar.id = 'mk-session-bar';
        bar.className = 'mk-session-bar';
        bar.setAttribute('role', 'alert');
        var text = document.createElement('span');
        text.textContent = message || 'Your session has expired. Nothing was deleted, but you need to sign in again to continue.';
        bar.appendChild(text);
        bar.appendChild(signInLink());
        var dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.className = 'mk-feedback__dismiss';
        dismiss.setAttribute('aria-label', 'Dismiss session message');
        dismiss.textContent = '\u00d7';
        dismiss.addEventListener('click', function () {
            bar.remove();
        });
        bar.appendChild(dismiss);
        document.body.appendChild(bar);
        return bar;
    }

    var SESSION_MESSAGE = 'Your session has expired or you are signed out. Nothing was deleted \u2014 sign in again to continue.';
    var NETWORK_MESSAGE = 'You appear to be offline or the network is unreachable. Check your connection and try again.';
    var SERVER_MESSAGE = 'Something went wrong on our side. Please try again \u2014 if it keeps failing, contact the clinic staff.';

    function isSessionError(error) {
        return !!(error && error.kind === 'session');
    }

    // Maps a MkFetch typed error (or plain Error) to an honest user message.
    function messageFor(error) {
        if (!error) {
            return SERVER_MESSAGE;
        }
        if (error.kind === 'session') {
            return SESSION_MESSAGE;
        }
        if (error.kind === 'network') {
            return NETWORK_MESSAGE;
        }
        if (error.message && error.message.indexOf('HTTP ') !== 0) {
            // Application-specific message from the caller's own error path.
            return error.message;
        }
        return SERVER_MESSAGE;
    }

    // Show feedback for a caught error; session errors additionally get the
    // prominent recovery bar. Returns nothing.
    function handle(error, opts) {
        if (isSessionError(error)) {
            showSessionBar();
            show(SESSION_MESSAGE, {kind: 'session', container: opts && opts.container});
        } else if (error && error.kind === 'network') {
            show(NETWORK_MESSAGE, opts);
        } else {
            show(messageFor(error), opts);
        }
    }

    // fetch wrapper: same signature as fetch, typed rejections for
    // auth/network failures. Non-OK non-auth responses RESOLVE so callers
    // keep their existing response.ok / JSON-error-body handling.
    function MkFetch(url, options) {
        return fetch(url, options).catch(function (networkError) {
            var typed = new Error(NETWORK_MESSAGE);
            typed.kind = 'network';
            typed.cause = networkError;
            throw typed;
        }).then(function (response) {
            if (response.status === 401 || response.status === 403) {
                var typed = new Error(SESSION_MESSAGE);
                typed.kind = 'session';
                typed.status = response.status;
                throw typed;
            }
            return response;
        });
    }

    global.MkFeedback = {
        show: show,
        handle: handle,
        showSessionBar: showSessionBar,
        isSessionError: isSessionError,
        messages: {
            session: SESSION_MESSAGE,
            network: NETWORK_MESSAGE,
            server: SERVER_MESSAGE
        }
    };
    global.MkFetch = MkFetch;
})(window);
