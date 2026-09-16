(function () {
    'use strict';

    var STORAGE_KEY = 'medikiosk.prefs';
    var THEMES = ['system', 'light', 'dark'];
    var MOTIONS = ['dynamic', 'standard', 'reduced'];
    var TEXT_SIZES = ['standard', 'large', 'xlarge'];

    function defaults() {
        var reducedMotion = window.matchMedia &&
            window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        return { theme: 'system', motion: reducedMotion ? 'reduced' : 'dynamic', textSize: 'standard' };
    }

    /* Server-seeded preferences (authenticated users with a stored row) are
       the base state; browser-local values override them per key. */
    function basePrefs() {
        var base = defaults();
        var seed = window.__mkPrefSeed || {};
        return {
            theme: THEMES.indexOf(seed.theme) >= 0 ? seed.theme : base.theme,
            motion: MOTIONS.indexOf(seed.motion) >= 0 ? seed.motion : base.motion,
            textSize: TEXT_SIZES.indexOf(seed.textSize) >= 0 ? seed.textSize : base.textSize
        };
    }

    function read() {
        var saved = {};
        try {
            saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') || {};
        } catch (e) {
            saved = {};
        }
        var base = basePrefs();
        return {
            theme: THEMES.indexOf(saved.theme) >= 0 ? saved.theme : base.theme,
            motion: MOTIONS.indexOf(saved.motion) >= 0 ? saved.motion : base.motion,
            textSize: TEXT_SIZES.indexOf(saved.textSize) >= 0 ? saved.textSize : base.textSize
        };
    }

    function write(prefs) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
        } catch (e) {
            /* private mode: keep the session-only state */
        }
    }

    function apply(prefs) {
        var doc = document.documentElement;
        if (prefs.theme === 'system') {
            doc.removeAttribute('data-bs-theme');
        } else {
            doc.setAttribute('data-bs-theme', prefs.theme);
        }
        if (prefs.motion === 'dynamic') {
            doc.removeAttribute('data-mk-motion');
        } else {
            doc.setAttribute('data-mk-motion', prefs.motion);
        }
        if (prefs.textSize === 'standard') {
            doc.removeAttribute('data-mk-textsize');
        } else {
            doc.setAttribute('data-mk-textsize', prefs.textSize);
        }
    }

    function syncControls(prefs) {
        document.querySelectorAll('input[name="mk-pref-theme"]').forEach(function (radio) {
            radio.checked = radio.value === prefs.theme;
        });
        document.querySelectorAll('input[name="mk-pref-motion"]').forEach(function (radio) {
            radio.checked = radio.value === prefs.motion;
        });
        document.querySelectorAll('input[name="mk-pref-textsize"]').forEach(function (radio) {
            radio.checked = radio.value === prefs.textSize;
        });
    }

    function persist(prefs) {
        if (!document.body || !document.body.hasAttribute('data-mk-authenticated')) {
            return; // anonymous users remain browser-local only
        }
        try {
            var form = new FormData();
            form.append('theme', prefs.theme);
            form.append('motion', prefs.motion);
            form.append('textSize', prefs.textSize);
            var csrfInput = document.querySelector('input[name="_csrf"]');
            var headers = { 'X-Requested-With': 'XMLHttpRequest' };
            if (csrfInput) headers['X-CSRF-TOKEN'] = csrfInput.value;
            fetch('/account/preferences', {
                method: 'POST',
                headers: headers,
                body: form,
                credentials: 'same-origin'
            }).then(function (response) {
                if (response.status === 401 || response.status === 403) {
                    window.__mkPrefPersistError = 'Your session has expired. Sign in again to keep saving preferences to your account.';
                } else if (!response.ok) {
                    window.__mkPrefPersistError = 'Preferences were applied in this browser but could not be saved to your account.';
                } else {
                    window.__mkPrefPersistError = null;
                }
            }).catch(function () {
                window.__mkPrefPersistError = 'You appear to be offline. Preferences were applied here but not saved to your account.';
            });
        } catch (e) {
            /* persistence is best-effort; the local change is already applied */
        }
    }

    function wire(root) {
        var owner = root || document;
        owner.querySelectorAll('input[name="mk-pref-theme"],input[name="mk-pref-motion"],input[name="mk-pref-textsize"]')
            .forEach(function (radio) {
                radio.addEventListener('change', function () {
                    var prefs = read();
                    var name = radio.name.replace('mk-pref-', '');
                    prefs[name] = radio.value;
                    write(prefs);
                    apply(prefs);
                    persist(prefs);
                });
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var prefs = read();
        apply(prefs);
        syncControls(prefs);
        wire();
    });
})();