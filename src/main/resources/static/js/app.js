(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        try {
            var prefs = JSON.parse(localStorage.getItem('medikiosk.prefs') || '{}');
            var doc = document.documentElement;
            if (prefs.theme) doc.setAttribute('data-bs-theme', prefs.theme);
            if (prefs.motion) doc.setAttribute('data-mk-motion', prefs.motion);
            if (prefs.textSize) doc.setAttribute('data-mk-textsize', prefs.textSize);
        } catch (e) {
            /* preferences are cosmetic; never block rendering */
        }
        var meta = document.getElementById('kioskMeta');
        if (meta) {
            meta.textContent =
                'Modules: ' +
                ['Auth', 'Patient', 'Consent', 'Clinical', 'AI', 'OCR', 'Voice', 'AYUSH', 'Audit', 'Physician', 'FHIR', 'Documents'].join(' \u00b7 ') +
                ' \u00b7 local demo \u00b7 no ABHA / ABDM / HIS transmission';
        }
        var statusElement = document.getElementById('appStatus');
        if (statusElement) {
            fetch('actuator/health', {headers: {'Accept': 'application/vnd.spring-boot.actuator.v3+json'}})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('health check failed');
                    }
                    return response.json();
                })
                .then(function (health) {
                    var up = health && (health.status === 'UP');
                    statusElement.className = 'badge ' + (up ? 'bg-success' : 'bg-warning text-dark');
                    statusElement.textContent = up ? 'UP' : 'Degraded';
                })
                .catch(function () {
                    statusElement.className = 'badge bg-danger';
                    statusElement.textContent = 'Unreachable';
                });
        }
    });
})();