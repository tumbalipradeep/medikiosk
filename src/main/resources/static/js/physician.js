(function () {
    'use strict';

    var body = document.body;
    var CSRF_TOKEN = body.dataset.csrf || '';
    var CSRF_HEADER = body.dataset.csrfHeader || 'X-CSRF-TOKEN';
    var CASE_ID = body.dataset.caseId || '';

    function extractionUrl(caseId, documentId, action) {
        return '/physician/cases/' + encodeURIComponent(caseId)
            + '/documents/' + encodeURIComponent(documentId)
            + '/extraction' + action;
    }

    function request(url, method, body) {
        var headers = {
            'X-Requested-With': 'XMLHttpRequest'
        };
        if (method === 'POST') {
            headers[CSRF_HEADER] = CSRF_TOKEN;
        }
        var options = {method: method, headers: headers};
        if (body) {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }
        return fetch(url, options);
    }

    // ─── Review decisions ─────────────────────────────────────────────

    function reviewUrl(caseId, answerOrder) {
        return '/physician/cases/' + encodeURIComponent(caseId)
            + '/answers/' + encodeURIComponent(answerOrder) + '/review';
    }

    function decisionBadge(entry, decision) {
        var badge = entry.querySelector('[data-review-badge]');
        badge.className = 'badge text-bg-success';
        badge.textContent = 'Accepted';
        if (decision === 'AMENDED') {
            badge.className = 'badge text-bg-warning';
            badge.textContent = 'Amended';
        } else if (decision === 'REJECTED') {
            badge.className = 'badge text-bg-danger';
            badge.textContent = 'Rejected';
        }
    }

    function applyDecision(entry, result) {
        entry.dataset.reviewed = 'true';
        decisionBadge(entry, result.decision);
        var amendBox = entry.querySelector('[data-amended-box]');
        var amendText = entry.querySelector('[data-amended-text]');
        if (amendBox && amendText) {
            if (result.decision === 'AMENDED' && result.amendedText) {
                amendText.textContent = result.amendedText;
                amendBox.classList.remove('d-none');
            } else {
                amendBox.classList.add('d-none');
            }
        }
        var reviewer = entry.querySelector('[data-review-meta]');
        if (reviewer) {
            reviewer.textContent = 'Reviewed by ' + result.reviewer;
        }
        updateReviewedCount();
    }

    function updateReviewedCount() {
        var counter = document.getElementById('reviewedCount');
        if (!counter) {
            return;
        }
        counter.textContent = document.querySelectorAll('.answer-entry[data-reviewed="true"]').length;
    }

    function submitDecision(entry, decision, amendedText) {
        var answerOrder = entry.dataset.answerOrder;
        var payload = {decision: decision};
        if (decision === 'AMENDED') {
            payload.amendedText = amendedText;
        }
        return request(reviewUrl(CASE_ID, answerOrder), 'POST', payload)
            .then(function (response) {
                if (!response.ok) {
                    return response.json().then(function (err) {
                        throw new Error(err.error || 'Review could not be saved.');
                    });
                }
                return response.json();
            })
            .then(function (result) {
                applyDecision(entry, result);
            });
    }

    // Serializes review submissions per answer so a fast double-click cannot
    // fire two concurrent back-end writes (and two audit events) for the same
    // decision. The server treats it as an idempotent upsert either way.
    function guardedSubmit(entry, decision, amendedText) {
        if (entry.dataset.submitting === 'true') {
            return Promise.resolve();
        }
        entry.dataset.submitting = 'true';
        var buttons = entry.querySelectorAll('button');
        buttons.forEach(function (btn) {
            btn.disabled = true;
        });
        return submitDecision(entry, decision, amendedText)
            .then(function (result) {
                entry.dataset.submitting = 'false';
                buttons.forEach(function (btn) {
                    btn.disabled = false;
                });
                return result;
            })
            .catch(function (e) {
                entry.dataset.submitting = 'false';
                buttons.forEach(function (btn) {
                    btn.disabled = false;
                });
                throw e;
            });
    }

    document.querySelectorAll('.answer-entry').forEach(function (entry) {
        var acceptBtn = entry.querySelector('.review-accept-btn');
        var rejectBtn = entry.querySelector('.review-reject-btn');
        var amendToggle = entry.querySelector('.review-amend-toggle');
        var amendSave = entry.querySelector('.review-amend-save');
        var amendInput = entry.querySelector('.review-amend-input');
        var amendForm = entry.querySelector('[data-amend-form]');

        if (acceptBtn) {
            acceptBtn.addEventListener('click', function () {
                guardedSubmit(entry, 'ACCEPTED').catch(function (e) {
                    alert(e.message);
                });
            });
        }
        if (rejectBtn) {
            rejectBtn.addEventListener('click', function () {
                guardedSubmit(entry, 'REJECTED').catch(function (e) {
                    alert(e.message);
                });
            });
        }
        if (amendToggle && amendForm) {
            amendToggle.addEventListener('click', function () {
                amendForm.classList.toggle('d-none');
            });
        }
        if (amendSave && amendInput) {
            amendSave.addEventListener('click', function () {
                if (!amendInput.value.trim()) {
                    alert('Enter the amended text before saving.');
                    return;
                }
                guardedSubmit(entry, 'AMENDED', amendInput.value.trim()).then(function () {
                    amendForm.classList.add('d-none');
                }).catch(function (e) {
                    alert(e.message);
                });
            });
        }
    });

    updateReviewedCount();

    // ─── Medication interaction screening ──────────────────────

    var SEVERITY_CLASS = {
        'CONTRAINDICATED': 'text-bg-danger',
        'MAJOR':           'text-bg-warning',
        'MODERATE':        'text-bg-info',
        'MINOR':           'text-bg-secondary'
    };

    function medUrl(caseId) {
        return '/physician/cases/' + encodeURIComponent(caseId) + '/medications';
    }

    function medActionUrl(caseId, name, action) {
        return medUrl(caseId) + '/' + encodeURIComponent(name) + '/' + action;
    }

    function severityBadgeClass(severity) {
        return SEVERITY_CLASS[severity] || 'text-bg-secondary';
    }

    function renderMedicationReport(report) {
        var badge = document.getElementById('overallSeverityBadge');
        if (badge) {
            if (report.overallSeverity) {
                badge.className = 'badge ' + severityBadgeClass(report.overallSeverity);
                badge.textContent = report.overallSeverity;
            } else {
                badge.className = 'badge text-bg-success';
                badge.textContent = 'No interactions';
            }
        }

        var list = document.getElementById('medList');
        var interactions = document.getElementById('interactionsList');
        if (list) {
            list.innerHTML = '';
            if (!report.medications || report.medications.length === 0) {
                var empty = document.createElement('p');
                empty.className = 'small text-muted mb-0';
                empty.textContent = 'No medications on the case screen.';
                list.appendChild(empty);
            } else {
                var ul = document.createElement('ul');
                ul.className = 'list-unstyled mb-0';
                report.medications.forEach(function (med) {
                    var li = document.createElement('li');
                    li.className = 'd-flex flex-wrap align-items-center justify-content-between gap-2 border-bottom py-2';
                    var left = document.createElement('div');
                    var name = document.createElement('span');
                    name.className = 'fw-semibold';
                    name.textContent = med.displayName;
                    left.appendChild(name);
                    if (med.classKey) {
                        var cls = document.createElement('span');
                        cls.className = 'badge text-bg-light border text-muted ms-2';
                        cls.textContent = med.classKey;
                        left.appendChild(cls);
                    }
                    if (med.dose || med.frequency) {
                        var detail = document.createElement('div');
                        detail.className = 'small text-muted mt-1';
                        var parts = [];
                        if (med.dose) parts.push(med.dose);
                        if (med.frequency) parts.push(med.frequency);
                        detail.textContent = parts.join(' \u00b7 ');
                        left.appendChild(detail);
                    }
                    var source = document.createElement('div');
                    source.className = 'small text-muted';
                    var srcLabels = {
                        'PATIENT_HISTORY': 'Patient history',
                        'DOCUMENT_FINDING': 'Document finding',
                        'PHYSICIAN_ENTERED': 'Physician added'
                    };
                    source.textContent = srcLabels[med.source] || med.source;
                    left.appendChild(source);
                    li.appendChild(left);

                    var btnGroup = document.createElement('div');
                    btnGroup.className = 'd-flex gap-1';
                    if (med.physicianAdded) {
                        var supBtn = document.createElement('button');
                        supBtn.type = 'button';
                        supBtn.className = 'btn btn-sm btn-outline-danger';
                        supBtn.textContent = 'Remove';
                        supBtn.addEventListener('click', function () {
                            request(medActionUrl(CASE_ID, med.displayName, 'suppress'), 'POST')
                                .then(function (res) {
                                    if (!res.ok) return Promise.reject();
                                    return res.json();
                                })
                                .then(renderMedicationReport)
                                .catch(function () { alert('Could not remove medicine.'); });
                        });
                        btnGroup.appendChild(supBtn);
                    } else {
                        var exclBtn = document.createElement('button');
                        exclBtn.type = 'button';
                        exclBtn.className = 'btn btn-sm btn-outline-secondary';
                        exclBtn.textContent = 'Exclude';
                        exclBtn.title = 'Remove this sourced medicine from the case screen.';
                        exclBtn.addEventListener('click', function () {
                            request(medActionUrl(CASE_ID, med.displayName, 'suppress'), 'POST')
                                .then(function (res) {
                                    if (!res.ok) return Promise.reject();
                                    return res.json();
                                })
                                .then(renderMedicationReport)
                                .catch(function () { alert('Could not exclude medicine.'); });
                        });
                        btnGroup.appendChild(exclBtn);
                    }
                    li.appendChild(btnGroup);
                    ul.appendChild(li);
                });
                list.appendChild(ul);
            }
        }

        if (interactions) {
            interactions.innerHTML = '';
            if (!report.interactions || report.interactions.length === 0) {
                var noInt = document.createElement('p');
                noInt.className = 'small text-muted mb-0';
                noInt.textContent = 'No screened interactions found between the listed medicines.';
                interactions.appendChild(noInt);
            } else {
                report.interactions.forEach(function (intx) {
                    var card = document.createElement('div');
                    card.className = 'mb-2 p-2 border rounded';
                    var head = document.createElement('div');
                    head.className = 'd-flex flex-wrap align-items-center gap-2 mb-1';
                    var sevBadge = document.createElement('span');
                    sevBadge.className = 'badge ' + severityBadgeClass(intx.severity);
                    sevBadge.textContent = intx.severity;
                    head.appendChild(sevBadge);
                    var pair = document.createElement('span');
                    pair.className = 'fw-semibold';
                    pair.textContent = intx.drugA + ' + ' + intx.drugB;
                    head.appendChild(pair);
                    card.appendChild(head);
                    var desc = document.createElement('div');
                    desc.className = 'small text-muted mb-1';
                    desc.textContent = intx.description;
                    card.appendChild(desc);
                    var guid = document.createElement('div');
                    guid.className = 'small';
                    guid.innerHTML = '<strong>Guidance:</strong> ' + intx.guidance;
                    card.appendChild(guid);
                    interactions.appendChild(card);
                });
            }
        }
    }

    if (CASE_ID && document.getElementById('medList')) {
        request(medUrl(CASE_ID), 'GET')
            .then(function (res) {
                if (!res.ok) throw new Error();
                return res.json();
            })
            .then(renderMedicationReport)
            .catch(function () {
                var badge = document.getElementById('overallSeverityBadge');
                if (badge) { badge.className = 'badge text-bg-secondary'; badge.textContent = 'Not available'; }
            });
    }

    var addMedForm = document.getElementById('addMedicationForm');
    if (addMedForm) {
        addMedForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var name = document.getElementById('medNameInput').value.trim();
            if (!name) return;
            request(medUrl(CASE_ID), 'POST', {
                name: name,
                dose: document.getElementById('medDoseInput').value.trim(),
                frequency: document.getElementById('medFreqInput').value.trim()
            }).then(function (res) {
                if (!res.ok) return res.json().then(function (b) { throw new Error(b.message || b.error || 'Add failed'); });
                return res.json();
            }).then(function (report) {
                renderMedicationReport(report);
                addMedForm.reset();
            }).catch(function (err) { alert(err.message || 'Could not add medicine.'); });
        });
    }

    // ─── Document extraction / findings / detail ──────────────────────

    function plainTextDiv(text) {
        var div = document.createElement('div');
        div.className = 'text-muted';
        div.textContent = text;
        return div;
    }

    function showExtractedText(data) {
        var title = document.getElementById('extractedTextModalTitle');
        var body = document.getElementById('extractedTextModalBody');
        body.innerHTML = '';
        title.textContent = data.status === 'EXTRACTED' ? 'Extracted text (' + data.pageCount + ' page' + (data.pageCount === 1 ? '' : 's') + ')' : 'Extraction status';

        if (data.status === 'PENDING') {
            body.appendChild(plainTextDiv('Extraction has not been run for this document yet.'));
        } else if (data.status === 'NO_TEXT') {
            body.appendChild(plainTextDiv(data.errorMessage || 'No extractable text found.'));
        } else if (data.status === 'UNSUPPORTED') {
            body.appendChild(plainTextDiv(data.errorMessage || 'This document type cannot be extracted.'));
        } else if (data.status === 'FAILED') {
            body.appendChild(plainTextDiv(data.errorMessage || 'Extraction failed.'));
        } else {
            var pages = data.pages || [];
            var rendered = pages.filter(function (page) { return page.text && page.text.trim().length > 0; });
            if (rendered.length === 0) {
                body.appendChild(plainTextDiv('The document contains no extractable text.'));
            } else {
                rendered.forEach(function (page) {
                    var heading = document.createElement('h6');
                    heading.className = 'mt-3 mb-1 text-muted';
                    heading.textContent = 'Page ' + page.pageNumber;
                    var pre = document.createElement('pre');
                    pre.className = 'p-3 bg-light border rounded mb-0';
                    pre.style.whiteSpace = 'pre-wrap';
                    pre.textContent = page.text;
                    body.appendChild(heading);
                    body.appendChild(pre);
                });
            }
        }

        new bootstrap.Modal(document.getElementById('extractedTextModal')).show();
    }

    function findingsUrl(caseId, documentId) {
        return '/physician/cases/' + encodeURIComponent(caseId)
            + '/documents/' + encodeURIComponent(documentId)
            + '/findings';
    }

    function timelineUrl(caseId) {
        return '/physician/cases/' + encodeURIComponent(caseId) + '/timeline';
    }

    function documentDetailUrl(caseId, documentId) {
        return '/physician/cases/' + encodeURIComponent(caseId)
            + '/documents/' + encodeURIComponent(documentId)
            + '/detail';
    }

    function eventTypeBadge(type) {
        var badge = document.createElement('span');
        badge.className = 'badge badge-pill badge-event tl-' + type;
        badge.textContent = type.replace(/_/g, ' ').toLowerCase();
        return badge;
    }

    function renderTimeline(events) {
        var timelineBody = document.getElementById('timelineBody');
        timelineBody.innerHTML = '';

        if (!events || events.length === 0) {
            timelineBody.appendChild(plainTextDiv('No timeline events could be derived for this case.'));
            return;
        }

        var list = document.createElement('ul');
        list.className = 'list-group list-group-flush';

        events.forEach(function (event) {
            var li = document.createElement('li');
            li.className = 'list-group-item px-0 d-flex flex-wrap align-items-start gap-3';

            var dateCol = document.createElement('div');
            dateCol.className = 'timeline-date-col pt-1';
            if (event.eventDate) {
                var dateBadge = document.createElement('span');
                dateBadge.className = 'badge text-bg-white border';
                dateBadge.textContent = event.eventDate;
                dateCol.appendChild(dateBadge);
            } else {
                var dash = document.createElement('span');
                dash.className = 'text-muted';
                dash.textContent = 'No date';
                dateCol.appendChild(dash);
            }
            li.appendChild(dateCol);

            var content = document.createElement('div');
            content.className = 'flex-grow-1';

            var headRow = document.createElement('div');
            headRow.className = 'd-flex flex-wrap align-items-center gap-2';
            headRow.appendChild(eventTypeBadge(event.eventType));
            var label = document.createElement('span');
            label.className = 'fw-semibold';
            label.textContent = event.label;
            headRow.appendChild(label);
            if (event.details) {
                var value = document.createElement('span');
                value.className = 'text-muted';
                value.textContent = event.details;
                headRow.appendChild(value);
            }
            content.appendChild(headRow);

            var source = document.createElement('div');
            source.className = 'small text-muted mt-1';
            source.textContent = 'Source: ' + event.sourceDocumentFilename;
            content.appendChild(source);

            if (event.sourceSnippet) {
                var snippet = document.createElement('div');
                snippet.className = 'timeline-snippet mt-1';
                snippet.textContent = '\u201C' + event.sourceSnippet + '\u201D';
                content.appendChild(snippet);
            }

            li.appendChild(content);
            list.appendChild(li);
        });

        timelineBody.appendChild(list);
    }

    function findingsCard(title) {
        var card = document.createElement('div');
        card.className = 'card shadow-sm mb-3';
        var header = document.createElement('div');
        header.className = 'card-header bg-white py-2';
        var heading = document.createElement('h6');
        heading.className = 'fw-bold mb-0';
        heading.textContent = title;
        header.appendChild(heading);
        card.appendChild(header);
        var cardBody = document.createElement('div');
        cardBody.className = 'card-body py-3';
        card.appendChild(cardBody);
        return {card: card, body: cardBody};
    }

    function descriptionRow(label, value) {
        var row = document.createElement('div');
        row.className = 'mb-2';
        var name = document.createElement('span');
        name.className = 'text-muted me-2';
        name.textContent = label + ':';
        row.appendChild(name);
        var text = document.createElement('span');
        text.className = 'fw-semibold';
        text.textContent = value || 'Not available';
        row.appendChild(text);
        return row;
    }

    function tableWithColumns(columns) {
        var table = document.createElement('table');
        table.className = 'table table-sm table-striped align-middle mb-0';
        var thead = document.createElement('thead');
        var tr = document.createElement('tr');
        columns.forEach(function (col) {
            var th = document.createElement('th');
            th.className = 'text-muted';
            th.textContent = col;
            tr.appendChild(th);
        });
        thead.appendChild(tr);
        table.appendChild(thead);
        var tbody = document.createElement('tbody');
        table.appendChild(tbody);
        return {table: table, tbody: tbody};
    }

    function addTableRow(tbody, cells) {
        var tr = document.createElement('tr');
        cells.forEach(function (text) {
            var td = document.createElement('td');
            td.textContent = text || '-';
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    }

    function addEmptyRow(tbody, span) {
        var tr = document.createElement('tr');
        var td = document.createElement('td');
        td.colSpan = span;
        td.className = 'text-muted';
        td.textContent = 'None found in the extracted text.';
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    function addCompletenessBadgeCell(tr, med) {
        var td = document.createElement('td');
        var badge = document.createElement('span');
        badge.className = 'badge badge-pill badge-' + (med.completeness || 'incomplete').toLowerCase();
        badge.textContent = med.completeness || 'INCOMPLETE';
        td.appendChild(badge);
        if (med.missingFields && med.missingFields.length > 0) {
            var missing = document.createElement('div');
            missing.className = 'small text-muted';
            missing.textContent = 'Missing: ' + med.missingFields.map(function (f) {
                return f.charAt(0) + f.slice(1).toLowerCase();
            }).join(', ');
            td.appendChild(missing);
        }
        tr.appendChild(td);
    }

    function addDuplicateBadgeCell(tr, med) {
        var td = document.createElement('td');
        if (med.duplicateCount > 1) {
            var badge = document.createElement('span');
            badge.className = 'badge badge-pill badge-duplicate';
            badge.textContent = med.duplicateCount + ' occurrences';
            td.appendChild(badge);
        } else {
            td.textContent = '-';
        }
        tr.appendChild(td);
    }

    function renderEncounter(cardBody, encounter) {
        if (!encounter) {
            cardBody.appendChild(plainTextDiv('No encounter/report metadata could be derived from the extracted text.'));
            return;
        }
        cardBody.appendChild(descriptionRow('Report date', encounter.reportDate));
        cardBody.appendChild(descriptionRow('Encounter date', encounter.encounterDate));
        cardBody.appendChild(descriptionRow('Facility', encounter.facility));
        cardBody.appendChild(descriptionRow('Clinician', encounter.clinician));
        cardBody.appendChild(descriptionRow('Report type', encounter.reportType));
    }

    function renderFindings(data) {
        var findingsBody = document.getElementById('findingsModalBody');
        findingsBody.innerHTML = '';

        if (!data.patient && !data.encounter && data.vitals.length === 0
                && data.labResults.length === 0 && data.medications.length === 0) {
            findingsBody.appendChild(plainTextDiv('No structured findings could be derived from the extracted document text.'));
            return;
        }

        var patientSection = findingsCard('Patient');
        if (data.patient) {
            patientSection.body.appendChild(descriptionRow('Name', data.patient.name));
            patientSection.body.appendChild(descriptionRow('Date of birth', data.patient.dateOfBirth));
            patientSection.body.appendChild(descriptionRow('Sex', data.patient.sex));
            patientSection.body.appendChild(descriptionRow('MRN', data.patient.mrn));
        } else {
            patientSection.body.appendChild(plainTextDiv('Not available.'));
        }
        findingsBody.appendChild(patientSection.card);

        var reportSection = findingsCard('Report / Encounter');
        renderEncounter(reportSection.body, data.encounter);
        findingsBody.appendChild(reportSection.card);

        var vitalsSection = findingsCard('Vitals');
        var vitalsTable = tableWithColumns(['#', 'Vital', 'Value', 'Unit']);
        data.vitals.forEach(function (v) {
            addTableRow(vitalsTable.tbody, [v.occurrenceIndex + 1, v.type.replace(/_/g, ' '), v.value, v.unit]);
        });
        if (data.vitals.length === 0) {
            addEmptyRow(vitalsTable.tbody, 4);
        }
        vitalsSection.body.appendChild(vitalsTable.table);
        findingsBody.appendChild(vitalsSection.card);

        var labsSection = findingsCard('Laboratory Results');
        var labsTable = tableWithColumns(['#', 'Test', 'Value', 'Unit', 'Reference range', 'Status', 'Source']);
        data.labResults.forEach(function (lab) {
            addTableRow(labsTable.tbody, [lab.occurrenceIndex + 1, lab.testName, lab.value, lab.unit, lab.referenceRange, null, lab.sourceSnippet]);
            var cells = labsTable.tbody.lastElementChild.querySelectorAll('td');
            var statusCell = cells[cells.length - 2];
            statusCell.innerHTML = '';
            var badge = document.createElement('span');
            badge.className = 'badge badge-pill badge-' + (lab.abnormalityStatus || 'unknown').toLowerCase();
            badge.textContent = lab.abnormalityStatus || 'UNKNOWN';
            statusCell.appendChild(badge);
        });
        if (data.labResults.length === 0) {
            addEmptyRow(labsTable.tbody, 7);
        }
        labsSection.body.appendChild(labsTable.table);
        findingsBody.appendChild(labsSection.card);

        var medsSection = findingsCard('Medications');
        var medsTable = tableWithColumns(['#', 'Medication', 'Strength', 'Dose', 'Route', 'Frequency', 'Duration', 'Completeness', 'Duplicate']);
        data.medications.forEach(function (med) {
            addTableRow(medsTable.tbody, [med.occurrenceIndex + 1, med.name, med.strength, med.dose, med.route, med.frequency, med.duration]);
            var tr = medsTable.tbody.lastElementChild;
            addCompletenessBadgeCell(tr, med);
            addDuplicateBadgeCell(tr, med);
        });
        if (data.medications.length === 0) {
            addEmptyRow(medsTable.tbody, 9);
        }
        medsSection.body.appendChild(medsTable.table);
        findingsBody.appendChild(medsSection.card);

        var interactionSection = findingsCard('Drug Interaction Analysis');
        var interactionStatus = data.interactionAnalysisStatus || 'NOT_AVAILABLE';
        if (interactionStatus === 'NOT_AVAILABLE') {
            interactionSection.body.appendChild(plainTextDiv(
                'Interaction analysis is not available: the deployment ships no reliable drug-interaction dataset, so no interaction claims are made for this extraction.'));
        } else {
            interactionSection.body.appendChild(plainTextDiv(interactionStatus));
        }
        findingsBody.appendChild(interactionSection.card);

        findingsBody.appendChild(plainTextDiv(
            'Findings are extracted from the uploaded document and require clinician verification.'));
    }

    function renderDocumentDetail(data) {
        var detailBody = document.getElementById('documentDetailModalBody');
        var title = document.getElementById('documentDetailModalTitle');
        detailBody.innerHTML = '';
        title.textContent = 'Details: ' + data.originalFilename;

        var meta = findingsCard('Document');
        meta.body.appendChild(descriptionRow('Filename', data.originalFilename));
        meta.body.appendChild(descriptionRow('Type', data.contentType));
        meta.body.appendChild(descriptionRow('Size', data.fileSize + ' bytes'));
        meta.body.appendChild(descriptionRow('Uploaded', data.uploadedAt));
        detailBody.appendChild(meta.card);

        var extraction = findingsCard('Extraction');
        var statusBadge = document.createElement('span');
        statusBadge.className = 'badge ' + data.extractionBadgeClass;
        statusBadge.textContent = data.extractionStatusLabel;
        var statusRow = document.createElement('div');
        statusRow.className = 'mb-2';
        statusRow.appendChild(statusBadge);
        extraction.body.appendChild(statusRow);
        extraction.body.appendChild(descriptionRow('Extracted at', data.extractedAt));
        extraction.body.appendChild(descriptionRow('Pages', data.pageCount > 0 ? data.pageCount : 'Not available'));
        if (data.extractionErrorMessage) {
            extraction.body.appendChild(plainTextDiv(data.extractionErrorMessage));
        }
        if (data.extractionStatus === 'EXTRACTED') {
            var pages = data.pages || [];
            var rendered = pages.filter(function (page) { return page.text && page.text.trim().length > 0; });
            if (rendered.length === 0) {
                extraction.body.appendChild(plainTextDiv('The document contains no extractable text.'));
            } else {
                rendered.forEach(function (page) {
                    var heading = document.createElement('h6');
                    heading.className = 'mt-3 mb-1 text-muted';
                    heading.textContent = 'Page ' + page.pageNumber;
                    var pre = document.createElement('pre');
                    pre.className = 'p-3 bg-light border rounded mb-0';
                    pre.style.whiteSpace = 'pre-wrap';
                    pre.textContent = page.text;
                    extraction.body.appendChild(heading);
                    extraction.body.appendChild(pre);
                });
            }
        } else if (data.extractionStatus === 'NO_TEXT') {
            extraction.body.appendChild(plainTextDiv(
                'No extractable text found - the PDF may contain only scanned images. OCR is not available in this deployment.'));
        } else if (data.extractionStatus === 'UNSUPPORTED') {
            extraction.body.appendChild(plainTextDiv('This document type cannot be extracted.'));
        } else if (data.extractionStatus === 'FAILED') {
            extraction.body.appendChild(plainTextDiv('Extraction failed.'));
        } else {
            extraction.body.appendChild(plainTextDiv('Extraction has not been run for this document yet.'));
        }
        detailBody.appendChild(extraction.card);

        var patientSection = findingsCard('Patient');
        if (data.patient) {
            patientSection.body.appendChild(descriptionRow('Name', data.patient.name));
            patientSection.body.appendChild(descriptionRow('Date of birth', data.patient.dateOfBirth));
            patientSection.body.appendChild(descriptionRow('Sex', data.patient.sex));
            patientSection.body.appendChild(descriptionRow('MRN', data.patient.mrn));
        } else {
            patientSection.body.appendChild(plainTextDiv('No patient identity could be derived.'));
        }
        detailBody.appendChild(patientSection.card);

        var reportSection = findingsCard('Report / Encounter');
        renderEncounter(reportSection.body, data.encounter);
        detailBody.appendChild(reportSection.card);

        var vitalsSection = findingsCard('Vitals');
        var vitalsTable = tableWithColumns(['#', 'Vital', 'Value', 'Unit', 'Source']);
        data.vitals.forEach(function (v) {
            addTableRow(vitalsTable.tbody, [v.occurrenceIndex + 1, v.type.replace(/_/g, ' '), v.value, v.unit, v.sourceSnippet]);
        });
        if (data.vitals.length === 0) {
            addEmptyRow(vitalsTable.tbody, 5);
        }
        vitalsSection.body.appendChild(vitalsTable.table);
        detailBody.appendChild(vitalsSection.card);

        var labsSection = findingsCard('Laboratory Results');
        var labsTable = tableWithColumns(['#', 'Test', 'Value', 'Unit', 'Reference range', 'Status', 'Source']);
        data.labResults.forEach(function (lab) {
            addTableRow(labsTable.tbody, [lab.occurrenceIndex + 1, lab.testName, lab.value, lab.unit, lab.referenceRange, null, lab.sourceSnippet]);
            var cells = labsTable.tbody.lastElementChild.querySelectorAll('td');
            var statusCell = cells[cells.length - 2];
            statusCell.innerHTML = '';
            var badge = document.createElement('span');
            badge.className = 'badge badge-pill badge-' + (lab.abnormalityStatus || 'unknown').toLowerCase();
            badge.textContent = lab.abnormalityStatus || 'UNKNOWN';
            statusCell.appendChild(badge);
        });
        if (data.labResults.length === 0) {
            addEmptyRow(labsTable.tbody, 7);
        }
        labsSection.body.appendChild(labsTable.table);
        detailBody.appendChild(labsSection.card);

        var medsSection = findingsCard('Medications');
        var medsTable = tableWithColumns(['#', 'Medication', 'Strength', 'Dose', 'Route', 'Frequency', 'Duration', 'Completeness', 'Duplicate', 'Source']);
        data.medications.forEach(function (med) {
            addTableRow(medsTable.tbody, [med.occurrenceIndex + 1, med.name, med.strength, med.dose, med.route, med.frequency, med.duration, null, null, med.sourceSnippet]);
            var tr = medsTable.tbody.lastElementChild;
            addCompletenessBadgeCell(tr, med);
            addDuplicateBadgeCell(tr, med);
            var sourceCell = tr.querySelectorAll('td')[9];
            var sourceText = document.createElement('div');
            sourceText.className = 'timeline-snippet';
            sourceText.textContent = '\u201C' + med.sourceSnippet + '\u201D';
            sourceCell.textContent = '';
            sourceCell.appendChild(sourceText);
        });
        if (data.medications.length === 0) {
            addEmptyRow(medsTable.tbody, 10);
        }
        medsSection.body.appendChild(medsTable.table);
        detailBody.appendChild(medsSection.card);

        var interactionSection = findingsCard('Drug Interaction Analysis');
        var interactionStatus = data.interactionAnalysisStatus || 'NOT_AVAILABLE';
        if (interactionStatus === 'NOT_AVAILABLE') {
            interactionSection.body.appendChild(plainTextDiv(
                'Interaction analysis is not available: the deployment ships no reliable drug-interaction dataset, so no interaction claims are made for this extraction.'));
        } else {
            interactionSection.body.appendChild(plainTextDiv(interactionStatus));
        }
        detailBody.appendChild(interactionSection.card);

        detailBody.appendChild(plainTextDiv(
            'Findings are extracted from the uploaded document and require clinician verification.'));
    }

    document.querySelectorAll('.findings-view-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var url = findingsUrl(btn.dataset.caseId, btn.dataset.documentId);
            var original = btn.textContent;
            btn.disabled = true;
            request(url, 'GET')
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Could not load structured findings.');
                    }
                    return response.json();
                })
                .then(renderFindings)
                .then(function () {
                    new bootstrap.Modal(document.getElementById('findingsModal')).show();
                })
                .catch(function () {
                    alert('Could not load structured findings for this document.');
                })
                .finally(function () {
                    btn.disabled = false;
                    btn.textContent = original;
                });
        });
    });

    document.querySelectorAll('.extract-view-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var url = extractionUrl(btn.dataset.caseId, btn.dataset.documentId, '');
            var original = btn.textContent;
            btn.disabled = true;
            request(url, 'GET')
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Could not load extracted text.');
                    }
                    return response.json();
                })
                .then(showExtractedText)
                .catch(function () {
                    alert('Could not load extracted text for this document.');
                })
                .finally(function () {
                    btn.disabled = false;
                    btn.textContent = original;
                });
        });
    });

    document.querySelectorAll('.extract-run-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var url = extractionUrl(btn.dataset.caseId, btn.dataset.documentId, '');
            var original = btn.textContent;
            btn.disabled = true;
            request(url, 'POST')
                .then(function (response) {
                    if (!response.ok) {
                        return response.json().then(function (errBody) {
                            throw new Error(errBody.error || 'Extraction failed.');
                        });
                    }
                    window.location.reload();
                })
                .catch(function (error) {
                    alert(error.message || 'Extraction failed. Please try again.');
                    btn.disabled = false;
                    btn.textContent = original;
                });
        });
    });

    document.querySelectorAll('.document-detail-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var url = documentDetailUrl(btn.dataset.caseId, btn.dataset.documentId);
            var original = btn.textContent;
            btn.disabled = true;
            request(url, 'GET')
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Could not load document details.');
                    }
                    return response.json();
                })
                .then(renderDocumentDetail)
                .then(function () {
                    new bootstrap.Modal(document.getElementById('documentDetailModal')).show();
                })
                .catch(function () {
                    alert('Could not load document details for this document.');
                })
                .finally(function () {
                    btn.disabled = false;
                    btn.textContent = original;
                });
        });
    });

    if (CASE_ID && document.getElementById('timelineBody')) {
        request(timelineUrl(CASE_ID), 'GET')
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Could not load the clinical timeline.');
                }
                return response.json();
            })
            .then(function (data) {
                renderTimeline(data.events);
            })
            .catch(function () {
                var timelineBody = document.getElementById('timelineBody');
                if (timelineBody) {
                    timelineBody.innerHTML = '';
                    timelineBody.appendChild(plainTextDiv('Could not load the clinical timeline for this case.'));
                }
            });
    }

    var releaseBtn = document.getElementById('releaseCaseBtn');
    if (releaseBtn) {
        releaseBtn.addEventListener('click', function () {
            if (!window.confirm('Release this case back to the shared pool?')) {
                return;
            }
            request('/physician/cases/' + encodeURIComponent(CASE_ID) + '/unassign', 'POST')
                .then(function (res) {
                    if (!res.ok) {
                        throw new Error('Could not release case.');
                    }
                    window.location.href = '/physician/home';
                })
                .catch(function () {
                    alert('Could not release this case back to the queue.');
                });
        });
    }
})();
