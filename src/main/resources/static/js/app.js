(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var meta = document.getElementById('kioskMeta');
        if (meta) {
            meta.textContent =
                'Modules: ' +
                ['Auth', 'Patient', 'Clinical', 'AI', 'OCR', 'Voice', 'AYUSH', 'Red Flags', 'Appointments', 'Physician', 'FHIR', 'ABDM', 'HIS', 'Documents'].join(' \u00b7 ') +
                ' \u00b7 M5.2';
        }
    });
})();
