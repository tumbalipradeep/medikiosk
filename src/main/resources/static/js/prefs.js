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

    function read() {
        var saved = {};
        try {
            saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') || {};
        } catch (e) {
            saved = {};
        }
        var base = defaults();
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