(function () {
    'use strict';

    var LANGUAGES = {
        'en-IN': {
            name: 'English',
            greeting: 'Hello! I can help you describe what has been troubling you. ' +
                'Please tell me about your problem in your own words.',
            placeholder: 'Type your message...',
            progress: 'answered',
            of: 'of',
            sections: {
                HISTORY: 'History',
                DASHAVIDHA: 'AYUSH Dashavidha',
                AHARA_VIHARA: 'AYUSH Ahara-Vihara'
            },
            steps: ['History', 'AYUSH Assessment', 'Lifestyle', 'Review'],
            redFlagTitle: 'Flagged for clinical review',
            redFlagNote: 'Some of your answers matched signs that should be reviewed carefully by your physician. ' +
                'If you feel worse right now, please seek urgent care immediately.',
            complete: 'The conversation is complete. Please review your answers below.',
            startFailed: 'Sorry, I could not start the conversation. Please refresh the page.'
        },
        'hi-IN': {
            name: 'Hindi',
            greeting: 'नमस्ते! मैं आपकी समस्या समझने में मदद कर सकता हूँ। ' +
                'कृपया अपनी समस्या अपने शब्दों में बताइए।',
            placeholder: 'अपना संदेश लिखिए...',
            progress: 'उत्तर पूरे हुए',
            of: 'में से',
            sections: {
                HISTORY: 'इतिहास',
                DASHAVIDHA: 'आयुष दशविध',
                AHARA_VIHARA: 'आयुष आहार-विहार'
            },
            steps: ['इतिहास', 'आयुष मूल्यांकन', 'जीवनशैली', 'समीक्षा'],
            redFlagTitle: 'चिकित्सकीय समीक्षा के लिए चिह्नित',
            redFlagNote: 'आपके कुछ उत्तर ऐसे संकेतों से मेल खाते हैं जिन्हें आपके डॉक्टर को ध्यान से देखना चाहिए। ' +
                'अगर अभी आपकी हालत बिगड़ रही है, तो कृपया तुरंत आपातकालीन सहायता लें।',
            complete: 'बातचीत पूरी हो गई है। कृपया नीचे अपने उत्तरों की समीक्षा करें।',
            startFailed: 'क्षमा करें, बातचीत शुरू नहीं हो सकी। कृपया पेज रीफ़्रेश करें।'
        },
        'te-IN': {
            name: 'Telugu',
            greeting: 'నమస్కారం! మీ సమస్యను వివరించడంలో నేను సహాయపడగలను. ' +
                'దయచేసి మీ సమస్యను మీ స్వంత మాటల్లో చెప్పండి.',
            placeholder: 'మీ సందేశాన్ని టైప్ చేయండి...',
            progress: 'ప్రశ్నలకు సమాధానం ఇచ్చారు',
            of: 'లో',
            sections: {
                HISTORY: 'చరిత్ర',
                DASHAVIDHA: 'ఆయుష్ దశవిధ',
                AHARA_VIHARA: 'ఆయుష్ ఆహార-విహార'
            },
            steps: ['చరిత్ర', 'ఆయుష్ అంచనా', 'జీవనశైలి', 'సమీక్ష'],
            redFlagTitle: 'వైద్య సమీక్ష కోసం గుర్తించబడింది',
            redFlagNote: 'మీ కొన్ని సమాధానాలు మీ వైద్యుడు జాగ్రత్తగా పరిశీలించాల్సిన సంకేతాలకు సరిపోలాయి. ' +
                'ప్రస్తుతం మీ పరిస్థితి మరింత అధ్వాన్నంగా ఉంటే, వెంటనే అత్యవసర సహాయం పొందండి.',
            complete: 'సంభాషణ పూర్తయింది. దయచేసి మీ సమాధానాలను క్రింద సమీక్షించండి.',
            startFailed: 'క్షమించండి, సంభాషణను ప్రారంభించలేకపోయాము. దయచేసి పేజీని రీఫ్రెష్ చేయండి.'
        },
        'ta-IN': {
            name: 'Tamil',
            greeting: 'வணக்கம்! உங்கள் பிரச்சனையை விவரிக்க நான் உதவ முடியும். ' +
                'தயவுசெய்து உங்கள் பிரச்சனையை உங்கள் வார்த்தைகளில் சொல்லுங்கள்.',
            placeholder: 'உங்கள் செய்தியைத் தட்டச்சு செய்க...',
            progress: 'கேள்விகளுக்கு பதிலளித்துள்ளீர்கள்',
            of: 'இல்',
            sections: {
                HISTORY: 'வரலாறு',
                DASHAVIDHA: 'ஆயுஷ் தசவித',
                AHARA_VIHARA: 'ஆயுஷ் ஆகார-விஹார'
            },
            steps: ['வரலாறு', 'ஆயுஷ் மதிப்பீடு', 'வாழ்க்கை முறை', 'மதிப்பாய்வு'],
            redFlagTitle: 'மருத்துவ மதிப்பாய்வுக்காகக் குறிக்கப்பட்டது',
            redFlagNote: 'உங்கள் சில பதில்கள் உங்கள் மருத்துவர் கவனமாகப் பார்க்க வேண்டிய அறிகுறிகளுடன் பொருந்தின. ' +
                'இப்போது உங்கள் நிலை மோசமாகி வருகிறது என்றால், உடனடியாக அவசர சிகிச்சை பெறுங்கள்.',
            complete: 'உரையாடல் முடிந்தது. உங்கள் பதில்களை கீழே மதிப்பாய்வு செய்யவும்.',
            startFailed: 'மன்னிக்கவும், உரையாடலைத் தொடங்க முடியவில்லை. பக்கத்தைப் புதுப்பிக்கவும்.'
        },
        'kn-IN': {
            name: 'Kannada',
            greeting: 'ನಮಸ್ಕಾರ! ನಿಮ್ಮ ಸಮಸ್ಯೆಯನ್ನು ವಿವರಿಸಲು ನಾನು ಸಹಾಯ ಮಾಡಬಹುದು. ' +
                'ದಯವಿಟ್ಟು ನಿಮ್ಮ ಸಮಸ್ಯೆಯನ್ನು ನಿಮ್ಮ ಮಾತುಗಳಲ್ಲಿ ಹೇಳಿ.',
            placeholder: 'ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ಟೈಪ್ ಮಾಡಿ...',
            progress: 'ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರಿಸಿದ್ದೀರಿ',
            of: 'ರಲ್ಲಿ',
            sections: {
                HISTORY: 'ಇತಿಹಾಸ',
                DASHAVIDHA: 'ಆಯುಷ್ ದಶವಿಧ',
                AHARA_VIHARA: 'ಆಯುಷ್ ಅಹಾರ-ವಿಹಾರ'
            },
            steps: ['ಇತಿಹಾಸ', 'ಆಯುಷ್ ಮೌಲ್ಯಮಾಪನ', 'ಜೀವನಶೈಲಿ', 'ವಿಮರ್ಶೆ'],
            redFlagTitle: 'ವೈದ್ಯಕೀಯ ವಿಮರ್ಶೆಗಾಗಿ ಗುರುತಿಸಲಾಗಿದೆ',
            redFlagNote: 'ನಿಮ್ಮ ಕೆಲವು ಉತ್ತರಗಳು ನಿಮ್ಮ ವೈದ್ಯರು ಎಚ್ಚರಿಕೆಯಿಂದ ಪರಿಶೀಲಿಸಬೇಕಾದ ಚಿಹ್ನೆಗಳಿಗೆ ಹೊಂದಿಕೆಯಾಗಿವೆ. ' +
                'ಈಗ ನಿಮ್ಮ ಸ್ಥಿತಿ ಹದಗೆಡುತ್ತಿದ್ದರೆ, ತಕ್ಷಣ ತುರ್ತು ಆರೈಕೆ ಪಡೆಯಿರಿ.',
            complete: 'ಸಂಭಾಷಣೆ ಪೂರ್ಣಗೊಂಡಿದೆ. ದಯವಿಟ್ಟು ನಿಮ್ಮ ಉತ್ತರಗಳನ್ನು ಕೆಳಗೆ ಪರಿಶೀಲಿಸಿ.',
            startFailed: 'ಕ್ಷಮಿಸಿ, ಸಂಭಾಷಣೆಯನ್ನು ಪ್ರಾರಂಭಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಪುಟವನ್ನು ರಿಫ್ರೆಶ್ ಮಾಡಿ.'
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        var badge = document.getElementById('currentLangBadge');
        var selector = document.getElementById('languageSelect');
        var greeting = document.getElementById('assistantGreeting');
        var input = document.getElementById('messageInput');
        var sendButton = document.getElementById('sendButton');
        var chatWindow = document.getElementById('chatWindow');
        var chatMessages = document.getElementById('chatMessages');
        var hint = document.getElementById('uiHint');
        var micButton = document.getElementById('micButton');
        var speakButton = document.getElementById('speakQuestion');
        var statusRegion = document.getElementById('statusRegion');
        var progressText = document.getElementById('progressText');
        var stepper = document.getElementById('journeyStepper');
        var reviewPanel = document.getElementById('reviewPanel');

        var currentLang = 'en-IN';
        var currentBcp47 = 'en-IN';
        var currentQuestionId = null;
        var currentDisplayedQuestionText = '';
        var conversationActive = true;
        var inFlight = false;
        var currentCaseId = null;
        var voiceUsed = false;
        var progressState = {answered: 0, total: 25, section: null};

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
            return MkFetch(url, {
                method: 'POST',
                headers: headers,
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        }

        // Mid-intake expiry must be visible, not silent: an assistant message
        // explains what happened and offers the recovery path. No answer text
        // is kept or replayed; the server remains the source of truth.
        function announceSessionExpired() {
            var message = (window.MkFeedback && window.MkFeedback.messages.session)
                || 'Your session has expired. Please sign in again to continue your intake.';
            appendMessage('assistant', message);
            conversationActive = false;
            sendButton.disabled = true;
            input.disabled = true;
            if (window.MkFeedback) {
                window.MkFeedback.showSessionBar();
            }
        }

        function bcp47For(value) {
            if (selector && selector.querySelector('option[value="' + value + '"]')) {
                var option = selector.querySelector('option[value="' + value + '"]');
                return option.getAttribute('data-bcp47') || value;
            }
            return value;
        }

        function announce(message) {
            if (statusRegion) {
                statusRegion.textContent = message || '';
            }
        }

        function setHint(message) {
            if (hint) {
                hint.textContent = message || '';
            }
        }

        function updatePlaceholder() {
            if (!conversationActive) {
                input.placeholder = LANGUAGES[currentLang].name + ' \u00b7 ' +
                    LANGUAGES[currentLang].complete.toLowerCase();
                return;
            }
            input.placeholder = LANGUAGES[currentLang].placeholder;
        }

        function applyLanguage(lang) {
            currentLang = LANGUAGES[lang] ? lang : 'en-IN';
            var resolved = LANGUAGES[currentLang];
            currentBcp47 = bcp47For(currentLang);
            badge.textContent = resolved.name;
            badge.setAttribute('data-lang', currentLang);
            badge.textContent = resolved.name + ' \u00b7 ' +
                (selector && selector.selectedOptions.length
                    ? (selector.selectedOptions[0].getAttribute('data-native') || '')
                    : '');
            if (greeting) {
                greeting.textContent = resolved.greeting;
            }
            updatePlaceholder();
            // Re-render stepper labels AND the last known progress in the new language.
            setProgress(progressState.answered, progressState.total, progressState.section);
        }

        function persistLanguage(lang) {
            return postJson('/patient/intake/language', {language: lang})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('language update failed');
                    }
                    return response.json();
                });
        }

        function scrollToBottom() {
            if (chatWindow) {
                chatWindow.scrollTop = chatWindow.scrollHeight;
            }
        }

        function appendMessage(role, text) {
            var template = document.createElement('div');
            template.className = 'chat-message ' + role;
            var bubble = document.createElement('div');
            bubble.className = 'chat-bubble';
            var span = document.createElement('span');
            span.className = 'chat-text';
            span.textContent = text;
            bubble.appendChild(span);
            template.appendChild(bubble);
            chatMessages.appendChild(template);
            scrollToBottom();
        }

        function sectionLabel(section) {
            var labels = LANGUAGES[currentLang].sections;
            if (labels && labels[section]) {
                return labels[section];
            }
            return section;
        }

        function setProgress(answered, total, section) {
            progressState.answered = answered;
            progressState.total = total;
            progressState.section = section || null;
            if (progressText) {
                var lang = LANGUAGES[currentLang];
                var answeredLabel = answered + ' ' + lang.progress + ' ' + lang.of + ' ' + total;
                progressText.textContent = answered + ' / ' + total + ' \u00b7 ' + answeredLabel;
            }
            if (stepper) {
                var active = section === 'CHIEF_COMPLAINT' || section === 'HISTORY_OF_PRESENT_ILLNESS'
                    ? 'HISTORY'
                    : (section || '');
                if (!conversationActive) {
                    active = 'REVIEW';
                }
                var steps = stepper.querySelectorAll('.progress-step');
                for (var i = 0; i < steps.length; i++) {
                    var match = steps[i].getAttribute('data-step') === active;
                    steps[i].classList.toggle('active', match);
                    steps[i].setAttribute('aria-current', match ? 'step' : 'false');
                }
                if (LANGUAGES[currentLang].steps) {
                    for (var j = 0; j < steps.length; j++) {
                        steps[j].textContent = LANGUAGES[currentLang].steps[j];
                    }
                }
            }
        }

        function showQuestion(question) {
            var text = question.text;
            if (question.section === 'DASHAVIDHA' || question.section === 'AHARA_VIHARA') {
                text = sectionLabel(question.section) + ' \u00b7 ' + question.type + '\n' + text;
            }
            appendMessage('assistant', text);
            currentQuestionId = question.id;
            currentDisplayedQuestionText = text;
            updatePlaceholder();
            if (input) {
                input.focus();
            }
        }

        function appendReviewFlagBanner(redFlags) {
            var lang = LANGUAGES[currentLang];
            var text = redFlags.map(function (flag) {
                return flag.title + ': ' + flag.message;
            }).join('\n');
            var template = document.createElement('div');
            template.className = 'chat-message urgent';
            var bubble = document.createElement('div');
            bubble.className = 'chat-bubble';
            var title = document.createElement('div');
            title.className = 'urgent-title';
            title.textContent = lang.redFlagTitle;
            var span = document.createElement('span');
            span.className = 'chat-text';
            span.textContent = text + '\n\n' + lang.redFlagNote;
            bubble.appendChild(title);
            bubble.appendChild(span);
            template.appendChild(bubble);
            chatMessages.appendChild(template);
            announce(lang.redFlagTitle);
            scrollToBottom();
        }

        function handleStep(data) {
            if (data.redFlags && data.redFlags.length > 0) {
                appendReviewFlagBanner(data.redFlags);
            }
            setProgress(data.answeredCount, data.totalCount,
                data.question ? data.question.section : null);
            if (data.completed || !data.question) {
                currentCaseId = data.caseId || null;
                completeConversation();
            } else {
                showQuestion(data.question);
            }
        }

        function disableConversationControls() {
            conversationActive = false;
            currentQuestionId = null;
            currentDisplayedQuestionText = '';
            stopListening();
            input.disabled = true;
            sendButton.disabled = true;
            updatePlaceholder();
        }

        function completeConversation() {
            disableConversationControls();
            if (currentCaseId) {
                appendMessage('assistant', LANGUAGES[currentLang].complete);
                showReviewPanel(currentCaseId);
            } else {
                appendMessage('assistant', LANGUAGES[currentLang].complete);
            }
        }

        function showReviewPanel(caseId) {
            if (!reviewPanel) {
                return;
            }
            reviewPanel.classList.remove('d-none');
            var progress = document.getElementById('reviewProgressText');
            if (progress) {
                progress.textContent = progressState.answered + ' / ' + progressState.total;
            }
            var finish = document.getElementById('finishButton');
            if (finish) {
                // No case reference is placed in the URL; the completion page reads it
                // from the server-side session, keeping PHI out of query strings and
                // the browser history.
                finish.href = '/patient/intake/complete';
            }
            showDocumentUpload();
            loadReviewSummary(caseId);
        }

        function loadReviewSummary(caseId) {
            var container = document.getElementById('reviewAnswers');
            if (!container) {
                return;
            }
            return MkFetch('/patient/cases/' + encodeURIComponent(caseId) + '/summary', {
                headers: {
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                }
            }).then(function (response) {
                if (!response.ok) {
                    throw new Error('summary failed');
                }
                return response.json();
            }).then(function (summary) {
                container.innerHTML = '';
                if (!summary.answers || summary.answers.length === 0) {
                    var empty = document.createElement('div');
                    empty.className = 'text-muted py-1';
                    empty.textContent = 'No answers were recorded for this case.';
                    container.appendChild(empty);
                    return;
                }
                var redFlagBox = document.getElementById('redFlagReviewBox');
                var noFlagBox = document.getElementById('noRedFlagReviewBox');
                if (summary.flaggedCount > 0) {
                    if (redFlagBox) {
                        redFlagBox.classList.remove('d-none');
                    }
                    if (noFlagBox) {
                        noFlagBox.classList.add('d-none');
                    }
                } else {
                    if (redFlagBox) {
                        redFlagBox.classList.add('d-none');
                    }
                    if (noFlagBox) {
                        noFlagBox.classList.remove('d-none');
                    }
                }
                var correctionsByOrder = {};
                (summary.corrections || []).forEach(function (c) {
                    correctionsByOrder[c.answerOrder] = c;
                });
                summary.answers.forEach(function (item) {
                    var wrapper = document.createElement('div');
                    wrapper.className = 'review-item';
                    var q = document.createElement('div');
                    q.className = 'review-question fw-semibold';
                    q.textContent = item.displayedText;
                    var a = document.createElement('div');
                    a.className = 'review-answer';
                    a.textContent = item.answer || '(no answer given)';
                    wrapper.appendChild(q);
                    wrapper.appendChild(a);
                    if (correctionsByOrder[item.answerOrder]) {
                        wrapper.appendChild(correctionBadge(correctionsByOrder[item.answerOrder]));
                    } else {
                        wrapper.appendChild(correctButton(item, currentCaseId, container, summary));
                    }
                    container.appendChild(wrapper);
                });
            }).catch(function () {
                if (window.MkFeedback) {
                    window.MkFeedback.show(window.MkFeedback.messages.server, {container: container});
                } else {
                    container.innerHTML = '<div class="text-danger py-1">Could not load your review.</div>';
                }
            });
        }

        // ─── Patient correction loop ─────────────────────────────────────
        // Additive evidence only: the server keeps the original answer and the
        // correction side by side; nothing here rewrites what was captured.

        function correctButton(item, caseId, container, summary) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-outline-secondary mt-1 mk-correction-btn';
            btn.setAttribute('aria-label', 'Correct your answer to: ' + (item.displayedText || ''));
            btn.textContent = "This isn't right";
            btn.addEventListener('click', function () {
                openCorrectionDialog(item, caseId, container, summary, btn);
            });
            return btn;
        }

        function openCorrectionDialog(item, caseId, container, summary, trigger) {
            var overlay = document.createElement('div');
            overlay.className = 'mk-correction-overlay';
            var dialog = document.createElement('div');
            dialog.className = 'mk-correction-dialog';
            dialog.setAttribute('role', 'dialog');
            dialog.setAttribute('aria-modal', 'true');
            dialog.setAttribute('aria-labelledby', 'mkCorrectionTitle');

            var title = document.createElement('h3');
            title.id = 'mkCorrectionTitle';
            title.className = 'h6 fw-bold mb-2';
            title.textContent = 'Correct this answer';

            var q = document.createElement('p');
            q.className = 'small text-muted mb-2';
            q.textContent = item.displayedText || '';

            var originalLabel = document.createElement('div');
            originalLabel.className = 'small fw-semibold mb-1';
            originalLabel.textContent = 'What was recorded:';
            var originalValue = document.createElement('div');
            originalValue.className = 'mk-correction-original';
            originalValue.textContent = item.answer || '(no answer given)';

            var formLabel = document.createElement('label');
            formLabel.className = 'form-label small fw-semibold mt-3 mb-1';
            formLabel.setAttribute('for', 'mkCorrectionInput');
            formLabel.textContent = 'What it should say:';
            var input = document.createElement('textarea');
            input.id = 'mkCorrectionInput';
            input.className = 'form-control form-control-sm';
            input.rows = 2;
            input.maxLength = 4000;

            var reasonLabel = document.createElement('label');
            reasonLabel.className = 'form-label small fw-semibold mt-2 mb-1';
            reasonLabel.setAttribute('for', 'mkCorrectionReason');
            reasonLabel.textContent = 'Why (optional):';
            var reason = document.createElement('input');
            reason.id = 'mkCorrectionReason';
            reason.type = 'text';
            reason.className = 'form-control form-control-sm';
            reason.maxLength = 1000;

            var errBox = document.createElement('div');
            errBox.className = 'mk-feedback mk-feedback--error mt-2 d-none';
            errBox.setAttribute('aria-live', 'assertive');

            var btnRow = document.createElement('div');
            btnRow.className = 'd-flex gap-2 mt-3';
            var save = document.createElement('button');
            save.type = 'button';
            save.className = 'btn btn-primary btn-sm flex-grow-1';
            save.textContent = 'Submit correction';
            var cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.className = 'btn btn-outline-secondary btn-sm';
            cancel.textContent = 'Cancel';
            btnRow.appendChild(save);
            btnRow.appendChild(cancel);

            dialog.appendChild(title);
            dialog.appendChild(q);
            dialog.appendChild(originalLabel);
            dialog.appendChild(originalValue);
            dialog.appendChild(formLabel);
            dialog.appendChild(input);
            dialog.appendChild(reasonLabel);
            dialog.appendChild(reason);
            dialog.appendChild(errBox);
            dialog.appendChild(btnRow);
            overlay.appendChild(dialog);
            document.body.appendChild(overlay);

            var previouslyFocused = document.activeElement;
            input.focus();

            function close() {
                document.body.removeChild(overlay);
                if (previouslyFocused && previouslyFocused.focus) {
                    previouslyFocused.focus();
                }
            }
            cancel.addEventListener('click', close);
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    close();
                }
            });
            document.addEventListener('keydown', function esc(e) {
                if (e.key === 'Escape') {
                    close();
                    document.removeEventListener('keydown', esc);
                }
            });

            save.addEventListener('click', function () {
                var value = input.value.trim();
                if (!value) {
                    errBox.textContent = 'Please enter what your answer should say.';
                    errBox.classList.remove('d-none');
                    input.focus();
                    return;
                }
                if (value === (item.answer || '')) {
                    errBox.textContent = 'That is the same as what was recorded. Enter a different answer.';
                    errBox.classList.remove('d-none');
                    input.focus();
                    return;
                }
                errBox.classList.add('d-none');
                save.disabled = true;
                var headers = {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                };
                headers[csrfHeaderName()] = csrfToken();
                MkFetch('/patient/cases/' + encodeURIComponent(caseId) + '/corrections', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({
                        answerOrder: item.answerOrder,
                        correctedAnswer: value,
                        reason: reason.value.trim() || null
                    })
                }).then(function (response) {
                    if (response.status === 400 || response.status === 404 || response.status === 409) {
                        return response.json().catch(function () {
                            return {};
                        }).then(function (body) {
                            throw new Error(body.message || 'The correction could not be saved.');
                        });
                    }
                    if (!response.ok) {
                        throw new Error('server');
                    }
                    return response.json();
                }).then(function () {
                    close();
                    // Re-render the review list so original vs corrected stays truthful.
                    loadReviewSummary(caseId).catch(function () { /* already surfaced */ });
                }).catch(function (error) {
                    save.disabled = false;
                    if (window.MkFeedback && window.MkFeedback.isSessionError(error)) {
                        // Prominent recovery bar; the dialog stays open so the
                        // patient can sign in again and resubmit.
                        window.MkFeedback.showSessionBar();
                        errBox.textContent = window.MkFeedback.messages.session;
                    } else if (error && error.kind === 'network') {
                        errBox.textContent = window.MkFeedback ? window.MkFeedback.messages.network : 'You appear to be offline. Please check the connection and try again.';
                    } else {
                        errBox.textContent = error && error.message && error.message !== 'server'
                            ? error.message
                            : (window.MkFeedback ? window.MkFeedback.messages.server : 'Could not save the correction. Please try again.');
                    }
                    errBox.classList.remove('d-none');
                    input.focus();
                });
            });
        }

        function correctionBadge(c) {
            var box = document.createElement('div');
            box.className = 'mk-correction-badge mt-1';
            var label = document.createElement('span');
            label.className = 'badge text-bg-info';
            label.textContent = 'Corrected by you';
            box.appendChild(label);
            var detail = document.createElement('div');
            detail.className = 'small mt-1';
            var now = document.createElement('div');
            now.innerHTML = '<strong>Now says:</strong> ';
            now.appendChild(document.createTextNode(c.correctedAnswer));
            detail.appendChild(now);
            if (c.reason) {
                var why = document.createElement('div');
                why.className = 'text-muted';
                why.textContent = 'Reason: ' + c.reason;
                detail.appendChild(why);
            }
            box.appendChild(detail);
            return box;
        }

        function startConversation() {
            postJson('/patient/intake/conversation/start', undefined)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('start failed');
                    }
                    return response.json();
                })
                .then(function (data) {
                    if (data && data.question) {
                        handleStep(data);
                    } else if (data.caseId) {
                        currentCaseId = data.caseId;
                        completeConversation();
                    } else {
                        completeConversation();
                    }
                })
                .catch(function (error) {
                    if (error && error.kind === 'session') {
                        announceSessionExpired();
                    } else {
                        appendMessage('assistant', LANGUAGES[currentLang].startFailed);
                    }
                });
        }

        function submitAnswer() {
            var text = input.value.trim();
            if (!conversationActive || inFlight || !text || !currentQuestionId) {
                return;
            }
            inFlight = true;
            sendButton.disabled = true;
            input.disabled = true;

            var answeredId = currentQuestionId;
            var enteredByVoice = voiceUsed;
            voiceUsed = false;
            appendMessage('patient', text);
            input.value = '';
            scrollToBottom();

            postJson('/patient/intake/conversation/answer', {
                questionId: answeredId,
                answer: text,
                answerSource: enteredByVoice ? 'VOICE' : 'TEXT'
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('answer failed');
                    }
                    return response.json();
                })
                .then(function (data) {
                    handleStep(data);
                })
                .catch(function (error) {
                    if (error && error.kind === 'session') {
                        announceSessionExpired();
                    } else {
                        appendMessage('assistant', 'Sorry, something went wrong. Please try again.');
                    }
                })
                .finally(function () {
                    inFlight = false;
                    if (conversationActive) {
                        sendButton.disabled = false;
                        input.disabled = false;
                        input.focus();
                    }
                });
        }

        function installBeforeUnloadGuard() {
            window.addEventListener('beforeunload', beforeUnloadHandler);
        }

        function beforeUnloadHandler(event) {
            if (!conversationActive) {
                return;
            }
            event.preventDefault();
            event.returnValue = '';
        }

        if (selector) {
            selector.addEventListener('change', function () {
                var chosen = selector.value;
                applyLanguage(chosen);
                persistLanguage(chosen).catch(function () {
                    setHint('Could not save the language selection on the server. Your conversation is unchanged.');
                });
            });
            applyLanguage(selector.value);
        }

        /* ---------------- Voice input (server ASR) ---------------- */

        var mediaRecorder = null;
        var mediaStream = null;
        var recordedChunks = [];
        var recordedMimeType = null;
        var recordingTimer = null;
        var asrInFlight = false;

        function micClass(state) {
            micButton.classList.toggle('btn-primary', state === 'listening');
            micButton.classList.toggle('btn-outline-primary', state !== 'listening');
            micButton.classList.toggle('btn-outline-danger', state === 'error');
            micButton.classList.toggle('btn-outline-success', state === 'success');
            micButton.setAttribute('aria-pressed', state === 'listening' ? 'true' : 'false');
            micButton.setAttribute('data-state', state);
        }

        function setMicError(message) {
            micClass('error');
            setHint(message);
            announce(message);
        }

        function stopListening() {
            asrInFlight = false;
            if (recordingTimer) {
                clearTimeout(recordingTimer);
                recordingTimer = null;
            }
            if (mediaRecorder) {
                try {
                    mediaRecorder.onstop = null;
                    mediaRecorder.stop();
                } catch (ignored) {
                    // stopping a recorder that already ended is safe to ignore
                }
                mediaRecorder = null;
            }
            if (mediaStream) {
                mediaStream.getTracks().forEach(function (track) {
                    track.stop();
                });
                mediaStream = null;
            }
            recordedChunks = [];
            micClass('idle');
        }

        function pickRecordMimeType() {
            var candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
            for (var i = 0; i < candidates.length; i++) {
                if (window.MediaRecorder.isTypeSupported(candidates[i])) {
                    return candidates[i];
                }
            }
            return null;
        }

        function attachRecordingStream(stream) {
            mediaStream = stream;
            recordedChunks = [];
            recordedMimeType = pickRecordMimeType();
            var options = recordedMimeType ? {mimeType: recordedMimeType} : undefined;
            mediaRecorder = new MediaRecorder(stream, options);
            mediaRecorder.ondataavailable = function (event) {
                if (event.data && event.data.size > 0) {
                    recordedChunks.push(event.data);
                }
            };
            mediaRecorder.onstop = function () {
                var blob = new Blob(recordedChunks, {type: recordedMimeType || 'audio/webm'});
                recordedChunks = [];
                if (mediaStream) {
                    mediaStream.getTracks().forEach(function (track) {
                        track.stop();
                    });
                    mediaStream = null;
                }
                mediaRecorder = null;
                if (blob.size > 0) {
                    uploadRecording(blob);
                } else {
                    micClass('idle');
                    setHint('No audio was recorded. Talk again or type instead.');
                }
            };
            mediaRecorder.start(250);
            micClass('listening');
            var listeningMessage = 'Listening in ' + LANGUAGES[currentLang].name +
                ' \u2026 speak, then press the mic again to stop (max 30 seconds).';
            setHint(listeningMessage);
            announce(listeningMessage);
            recordingTimer = setTimeout(function () {
                if (mediaRecorder && mediaRecorder.state === 'recording') {
                    setHint('Reached the 30-second limit. Transcribing \u2026');
                    try {
                        mediaRecorder.stop();
                    } catch (ignored) {
                        // recorder may have ended between the check and the stop
                    }
                }
            }, 30000);
        }

        function startRecording() {
            if (asrInFlight) {
                return;
            }
            if (!conversationActive || !currentQuestionId) {
                micClass('unavailable');
                setHint('There is no question right now. You can keep typing.');
                return;
            }
            if (!window.MediaRecorder || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                micClass('unavailable');
                setHint('Voice input is not supported in this browser. You can keep typing.');
                return;
            }
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                stopRecording();
                return;
            }
            navigator.mediaDevices.getUserMedia({audio: true})
                .then(attachRecordingStream)
                .catch(mapPermissionError);
        }

        function stopRecording() {
            if (recordingTimer) {
                clearTimeout(recordingTimer);
                recordingTimer = null;
            }
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                try {
                    mediaRecorder.stop();
                } catch (ignored) {
                    // recorder may have already ended
                }
            } else {
                micClass('idle');
                setHint('Voice input stopped. You can type instead.');
            }
        }

        function uploadRecording(blob) {
            asrInFlight = true;
            micButton.disabled = true;
            micClass('processing');
            setHint('Transcribing your recording \u2026');
            var formData = new FormData();
            formData.append('audio', blob, 'recording.webm');
            formData.append('language', currentLang);
            fetch('/patient/intake/voice/asr', {
                method: 'POST',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                },
                body: formData
            }).then(function (response) {
                return response.json().then(function (body) {
                    return {status: response.status, body: body};
                });
            }).then(function (result) {
                var code = result.body && result.body.status;
                if (result.status === 200 && code === 'TRANSCRIBED' && result.body.transcript) {
                    voiceUsed = true;
                    input.value = result.body.transcript;
                    micClass('success');
                    setHint('Transcribed. Review it, then press Send.');
                    announce('Transcribed your answer. Review it, then press Send.');
                    input.focus();
                } else if (result.status === 200 && code === 'EMPTY') {
                    micClass('idle');
                    setHint('No speech was heard. Talk again or type.');
                } else if (code === 'UNAVAILABLE') {
                    micClass('unavailable');
                    setHint('Voice input is unavailable right now. You can keep typing.');
                    announce('Voice input is unavailable right now. You can keep typing.');
                } else if (code === 'UNSUPPORTED') {
                    micClass('unavailable');
                    setHint('Voice input does not support ' + LANGUAGES[currentLang].name +
                        ' yet. You can keep typing.');
                    announce('Voice input does not support ' + LANGUAGES[currentLang].name + ' yet.');
                } else {
                    micClass('error');
                    setHint('Voice input failed. Try again or type instead.');
                    announce('Voice input failed. Try again or type instead.');
                }
            }).catch(function () {
                micClass('error');
                setHint('Voice input is unavailable right now. You can keep typing.');
                announce('Voice input is unavailable right now. You can keep typing.');
            }).finally(function () {
                asrInFlight = false;
                micButton.disabled = false;
            });
        }

        function mapPermissionError(error) {
            if (error && error.name === 'NotAllowedError') {
                micClass('error');
                setHint('Microphone permission was denied. Allow it in the browser to use voice, or type instead.');
                announce('Microphone permission was denied. You can type instead.');
            } else if (error && error.name === 'NotFoundError') {
                micClass('unavailable');
                setHint('No microphone was found. You can keep typing.');
            } else {
                micClass('error');
                setHint('Voice input could not start. You can keep typing.');
            }
        }

        function toggleMic() {
            if (asrInFlight) {
                return;
            }
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                stopRecording();
                return;
            }
            startRecording();
        }

        if (micButton) {
            if (!conversationActive) {
                micClass('unavailable');
            } else if (!window.MediaRecorder || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                micClass('unavailable');
                setHint('Voice input is not supported in this browser. You can type instead.');
            } else {
                micClass('idle');
                setHint('Press the microphone and speak to answer, or type instead.');
            }
            micButton.addEventListener('click', toggleMic);
        }

        /* ---------------- Text-to-speech (server TTS) ---------------- */

        var currentAudio = null;
        var speakInFlight = false;

        function stopSpeaking() {
            if (currentAudio) {
                try {
                    currentAudio.pause();
                    currentAudio.src = '';
                } catch (ignored) {
                    // audio may already be stopped
                }
                currentAudio = null;
            }
            if (speakButton) {
                speakButton.setAttribute('aria-pressed', 'false');
            }
        }

        function playAudioBlob(blob) {
            var url = URL.createObjectURL(blob);
            stopSpeaking();
            var audio = new Audio(url);
            currentAudio = audio;
            audio.onended = function () {
                currentAudio = null;
                speakButton.setAttribute('aria-pressed', 'false');
                setHint('Played the question in ' + LANGUAGES[currentLang].name + '.');
            };
            audio.onerror = function () {
                currentAudio = null;
                speakButton.setAttribute('aria-pressed', 'false');
                setHint('Reading aloud failed. You can read the question on screen.');
            };
            audio.play().then(function () {
                speakButton.setAttribute('aria-pressed', 'true');
                setHint('Playing the question in ' + LANGUAGES[currentLang].name + '.');
            }).catch(function () {
                currentAudio = null;
                speakButton.setAttribute('aria-pressed', 'false');
                setHint('The audio could not be played. You can read the question on screen.');
            });
        }

        function speakCurrentQuestion() {
            if (speakInFlight) {
                return;
            }
            if (currentAudio) {
                stopSpeaking();
                setHint('Stopped.');
                return;
            }
            if (!conversationActive || !currentDisplayedQuestionText) {
                setHint('There is no question to read yet.');
                return;
            }
            speakButton.disabled = true;
            speakInFlight = true;
            setHint('Getting the audio \u2026');
            fetch('/patient/intake/voice/tts', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                },
                body: JSON.stringify({text: currentDisplayedQuestionText, language: currentLang})
            }).then(function (response) {
                var contentType = response.headers.get('content-type') || '';
                if (response.ok && contentType.indexOf('audio/') === 0) {
                    return response.blob();
                }
                return response.json().then(function (body) {
                    throw new Error(body && body.message ? body.message : 'reading aloud is unavailable');
                });
            }).then(function (blob) {
                playAudioBlob(blob);
            }).catch(function () {
                speakButton.setAttribute('aria-pressed', 'false');
                setHint('Reading aloud is unavailable right now. You can read the question on screen.');
            }).finally(function () {
                speakInFlight = false;
                speakButton.disabled = false;
            });
        }

        if (speakButton) {
            speakButton.addEventListener('click', speakCurrentQuestion);
        }

        function formatFileSize(bytes) {
            if (bytes < 1024) {
                return bytes + ' B';
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024).toFixed(1) + ' KB';
            }
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        }

        function showUploadFeedback(message, isError) {
            var feedback = document.getElementById('documentUploadFeedback');
            feedback.classList.remove('d-none', 'text-success', 'text-danger');
            feedback.classList.add(isError ? 'text-danger' : 'text-success');
            feedback.textContent = message;
        }

        function loadDocuments() {
            if (!currentCaseId) {
                return;
            }
            fetch('/patient/cases/' + encodeURIComponent(currentCaseId) + '/documents', {
                headers: {
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                }
            }).then(function (response) {
                return response.json();
            }).then(function (docs) {
                var container = document.getElementById('documentList');
                container.innerHTML = '';
                if (!docs || docs.length === 0) {
                    var empty = document.createElement('div');
                    empty.className = 'text-muted py-1';
                    empty.textContent = 'No documents uploaded yet.';
                    container.appendChild(empty);
                    return;
                }
                docs.forEach(function (doc) {
                    var item = document.createElement('div');
                    item.className = 'list-group-item d-flex justify-content-between align-items-center';
                    var info = document.createElement('div');
                    var name = document.createElement('div');
                    name.className = 'fw-semibold';
                    name.textContent = doc.originalFilename;
                    var meta = document.createElement('div');
                    meta.className = 'text-muted small';
                    meta.textContent = doc.contentType + ' \u00b7 ' + formatFileSize(doc.fileSize);
                    info.appendChild(name);
                    info.appendChild(meta);
                    var del = document.createElement('button');
                    del.type = 'button';
                    del.className = 'btn btn-sm btn-outline-danger';
                    del.textContent = 'Remove';
                    del.addEventListener('click', function () {
                        fetch('/patient/cases/' + encodeURIComponent(currentCaseId) + '/documents/'
                            + encodeURIComponent(doc.documentId), {
                            method: 'DELETE',
                            headers: {
                                'X-Requested-With': 'XMLHttpRequest',
                                'X-CSRF-TOKEN': csrfToken()
                            }
                        }).then(function (response) {
                            if (response.ok) {
                                loadDocuments();
                            } else {
                                showUploadFeedback('Could not remove the document.', true);
                            }
                        });
                    });
                    item.appendChild(info);
                    item.appendChild(del);
                    container.appendChild(item);
                });
            }).catch(function () {
                showUploadFeedback('Could not load uploaded documents.', true);
            });
        }

        function showDocumentUpload() {
            var section = document.getElementById('documentUploadSection');
            if (!section) {
                return;
            }
            section.classList.remove('d-none');
            loadDocuments();
        }

        function uploadDocument() {
            var fileInput = document.getElementById('documentFileInput');
            var button = document.getElementById('documentUploadButton');
            var file = fileInput.files[0];
            if (!file || !currentCaseId) {
                showUploadFeedback('Please choose a file first.', true);
                return;
            }
            var MAX_BYTES = 10 * 1024 * 1024;
            if (file.size > MAX_BYTES) {
                showUploadFeedback('This file is larger than 10 MB. Please choose a smaller file.', true);
                announce('This file is larger than 10 MB. Please choose a smaller file.');
                return;
            }
            var formData = new FormData();
            formData.append('file', file);
            button.disabled = true;
            showUploadFeedback('Uploading \u2026', false);
            fetch('/patient/cases/' + encodeURIComponent(currentCaseId) + '/documents', {
                method: 'POST',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                },
                body: formData
            }).then(function (response) {
                return response.json().then(function (body) {
                    return {status: response.status, body: body};
                });
            }).then(function (result) {
                if (result.status === 200) {
                    fileInput.value = '';
                    showUploadFeedback('Document uploaded successfully.', false);
                    announce('Document uploaded successfully.');
                    loadDocuments();
                } else {
                    showUploadFeedback(result.body && result.body.error
                        ? result.body.error
                        : 'Upload failed. Please try again.', true);
                }
            }).catch(function () {
                showUploadFeedback('Upload failed. Please try again.', true);
            }).finally(function () {
                button.disabled = false;
            });
        }

        var uploadButton = document.getElementById('documentUploadButton');
        if (uploadButton) {
            uploadButton.addEventListener('click', uploadDocument);
        }
        var fileInput = document.getElementById('documentFileInput');
        if (fileInput) {
            fileInput.addEventListener('change', function () {
                document.getElementById('documentUploadFeedback').classList.add('d-none');
            });
        }

        sendButton.addEventListener('click', submitAnswer);

        input.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                submitAnswer();
            }
        });

        installBeforeUnloadGuard();

        var completedMeta = document.querySelector('meta[name="completed-case"]');
        if (completedMeta && completedMeta.content) {
            currentCaseId = completedMeta.content;
            disableConversationControls();
            appendMessage('assistant', LANGUAGES[currentLang].complete);
            showReviewPanel(currentCaseId);
        } else {
            startConversation();
        }
    });
})();