(function () {
    'use strict';

    var body = document.body;
    var CSRF_TOKEN = body.dataset.csrf || '';
    var CSRF_HEADER = body.dataset.csrfHeader || 'X-CSRF-TOKEN';
    var CASE_ID = body.dataset.caseId || '';

    function request(url, method, payload) {
        var headers = {'X-Requested-With': 'XMLHttpRequest'};
        if (method === 'POST') {
            headers[CSRF_HEADER] = CSRF_TOKEN;
        }
        var options = {method: method, headers: headers};
        if (payload !== undefined) {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(payload);
        }
        return fetch(url, options);
    }

    function caseUrl(path) {
        return '/physician/cases/' + encodeURIComponent(CASE_ID) + path;
    }

    function showToast(message, isError) {
        var box = document.createElement('div');
        box.className = 'position-fixed bottom-0 end-0 p-3';
        box.style.zIndex = 1080;
        box.innerHTML = '<div class="toast align-items-center ' + (isError ? 'text-bg-danger' : 'text-bg-success') + ' border-0">' +
            '<div class="d-flex"><div class="toast-body">' + message + '</div>' +
            '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>' +
            '</div></div>';
        document.body.appendChild(box);
        var toast = box.querySelector('.toast');
        var bsToast = new bootstrap.Toast(toast, {delay: 2500});
        bsToast.show();
        toast.addEventListener('hidden.bs.toast', function () { box.remove(); });
    }

    // ─── Red-flag triage actions ──────────────────────────────────────

    document.addEventListener('click', function (event) {
        var btn = event.target.closest('.triage-action');
        if (!btn) {
            return;
        }
        var card = btn.closest('.triage-card');
        var flagId = card.dataset.flagId;
        var note = card.querySelector('.triage-note').value || '';
        var action = btn.dataset.action;
        btn.disabled = true;
        request(caseUrl('/flags/' + encodeURIComponent(flagId) + '/action'), 'POST', {action: action, note: note})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('action failed');
                }
                return response.json();
            })
            .then(function (result) {
                var badge = card.querySelector('[data-triage-badge]');
                badge.className = 'badge text-bg-success';
                badge.textContent = result.action;
                showToast('Flag ' + result.action + '.');
            })
            .catch(function () {
                showToast('Could not record the triage action.', true);
            })
            .finally(function () {
                btn.disabled = false;
            });
    });

    // ─── Clinical summary ─────────────────────────────────────────────

    function summaryAction(btn, action, payload) {
        var section = btn.closest('.summary-section');
        var sectionName = section.dataset.section;
        btn.disabled = true;
        request(caseUrl('/summary/' + encodeURIComponent(sectionName) + '/' + action), 'POST', payload)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('summary action failed');
                }
                return response.json();
            })
            .then(function (result) {
                var badge = section.querySelector('.summary-status');
                badge.className = 'badge ' + (result.status === 'ACCEPTED' ? 'text-bg-success'
                    : result.status === 'REJECTED' ? 'text-bg-danger' : 'text-bg-warning');
                badge.textContent = result.status;
                var provenance = section.querySelector('.small.text-muted');
                if (provenance) {
                    provenance.textContent = 'Provenance: PHYSICIAN_ENTERED';
                }
                showToast('Summary ' + result.status.toLowerCase() + '.');
            })
            .catch(function () {
                showToast('Could not update the summary.', true);
            })
            .finally(function () {
                btn.disabled = false;
            });
    }

    document.addEventListener('click', function (event) {
        var saveBtn = event.target.closest('.summary-save-btn');
        if (saveBtn) {
            var section = saveBtn.closest('.summary-section');
            summaryAction(saveBtn, 'amend', {content: section.querySelector('.summary-content').value});
        }
        var acceptBtn = event.target.closest('.summary-accept-btn');
        if (acceptBtn) {
            var secA = acceptBtn.closest('.summary-section');
            summaryAction(acceptBtn, 'accept', {content: secA.querySelector('.summary-content').value});
        }
        var rejectBtn = event.target.closest('.summary-reject-btn');
        if (rejectBtn) {
            summaryAction(rejectBtn, 'reject', {});
        }
    });

    // ─── Consultation ─────────────────────────────────────────────────

    document.addEventListener('submit', function (event) {
        var form = event.target.closest('.consultation-form');
        if (!form) {
            return;
        }
        event.preventDefault();
        var btn = form.querySelector('.consultation-save-btn');
        btn.disabled = true;
        request(caseUrl('/consultation'), 'POST', {
            assessment: form.cs_assessment ? form.cs_assessment.value : form.elements.assessment.value,
            plan: form.elements.plan.value,
            advice: form.elements.advice.value,
            followUp: form.elements.followUp.value
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('consultation failed');
                }
                return response.json();
            })
            .then(function () {
                showToast('Consultation draft saved.');
            })
            .catch(function () {
                showToast('Could not save the consultation.', true);
            })
            .finally(function () {
                btn.disabled = false;
            });
    });

    document.addEventListener('click', function (event) {
        var btn = event.target.closest('.consultation-finalize-btn');
        if (!btn) {
            return;
        }
        btn.disabled = true;
        request(caseUrl('/consultation/finalize'), 'POST')
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('finalize failed');
                }
                return response.json();
            })
            .then(function () {
                showToast('Clinical record finalized.');
                location.reload();
            })
            .catch(function () {
                showToast('Could not finalize the record. Save a draft first.', true);
            })
            .finally(function () {
                btn.disabled = false;
            });
    });
})();