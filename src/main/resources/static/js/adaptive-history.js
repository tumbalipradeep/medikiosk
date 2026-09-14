(function () {
    'use strict';

    function csrfHeaderName() {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.content : 'X-CSRF-TOKEN';
    }

    function csrfToken() {
        var meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.content : '';
    }

    function postJson(url, body) {
        var headers = {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        };
        headers[csrfHeaderName()] = csrfToken();
        return fetch(url, {
            method: 'POST',
            headers: headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    }

    function el(id) {
        return document.getElementById(id);
    }

    function showQuestion(view) {
        el('adv-boot').classList.add('d-none');
        el('adv-done').classList.add('d-none');
        el('adv-card').classList.remove('d-none');
        el('adv-category-label').textContent = '' + view.categoryLabel;
        el('adv-question').textContent = view.questionText;
        el('adv-question').setAttribute('data-question-id', view.questionId || '');
        el('adv-answer').value = '';
        var total = view.answeredCount + view.remainingCount;
        var pct = total > 0 ? Math.round((view.answeredCount / total) * 100) : 0;
        el('adv-progress').style.width = pct + '%';
        el('adv-progress').setAttribute('aria-valuenow', String(pct));
        el('adv-answer').focus();
    }

    function showProgress(view) {
        var total = view.answeredCount + view.remainingCount;
        var pct = total > 0 ? Math.round((view.answeredCount / total) * 100) : 0;
        el('adv-progress').style.width = pct + '%';
    }

    function showDone() {
        el('adv-boot').classList.add('d-none');
        el('adv-card').classList.add('d-none');
        el('adv-done').classList.remove('d-none');
        el('adv-progress').style.width = '100%';
    }

    function start() {
        postJson('/patient/history/adaptive/start')
            .then(function (r) { return r.json(); })
            .then(showQuestion)
            .catch(function () { alert('Sorry, I could not start the conversation. Please refresh the page.'); });
    }

    function answer() {
        var qid = el('adv-question').getAttribute('data-question-id');
        var text = el('adv-answer').value.trim();
        if (!text) {
            el('adv-answer').focus();
            return;
        }
        var submitBtn = el('adv-submit');
        submitBtn.disabled = true;
        postJson('/patient/history/adaptive/answer', {questionId: qid, answer: text})
            .then(function (r) { return r.json(); })
            .then(function (view) {
                submitBtn.disabled = false;
                if (view.completed) {
                    showDone();
                } else {
                    showQuestion(view);
                }
            })
            .catch(function () {
                submitBtn.disabled = false;
                alert('Sorry, your answer could not be saved. Please try again.');
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        el('adv-start').addEventListener('click', start);
        el('adv-submit').addEventListener('click', answer);
        el('adv-answer').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                answer();
            }
        });
        fetch('/patient/history/adaptive/state')
            .then(function (r) { return r.json(); })
            .then(function (view) {
                if (view.completed) {
                    showDone();
                } else if (view.started) {
                    showQuestion(view);
                }
            })
            .catch(function () { /* not started yet */ });
    });
})();