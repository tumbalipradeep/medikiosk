(function () {
    'use strict';

    var LANGUAGES = {
        en: {
            name: 'English',
            greeting: 'Hello! I can help you describe what has been troubling you. ' +
                'Please tell me about your problem in your own words.',
            placeholder: 'Type your message...'
        },
        hi: {
            name: 'Hindi',
            greeting: 'नमस्ते! मैं आपकी समस्या समझने में मदद कर सकता हूँ। ' +
                'कृपया अपनी समस्या अपने शब्दों में बताइए।',
            placeholder: 'अपना संदेश लिखिए...'
        },
        te: {
            name: 'Telugu',
            greeting: 'నమస్కారం! మీ సమస్యను వివరించడంలో నేను సహాయపడగలను. ' +
                'దయచేసి మీ సమస్యను మీ స్వంత మాటల్లో చెప్పండి.',
            placeholder: 'మీ సందేశాన్ని టైప్ చేయండి...'
        },
        ta: {
            name: 'Tamil',
            greeting: 'வணக்கம்! உங்கள் பிரச்சனையை விவரிக்க நான் உதவ முடியும். ' +
                'தயவுசெய்து உங்கள் பிரச்சனையை உங்கள் வார்த்தைகளில் சொல்லுங்கள்.',
            placeholder: 'உங்கள் செய்தியைத் தட்டச்சு செய்க...'
        },
        kn: {
            name: 'Kannada',
            greeting: 'ನಮಸ್ಕಾರ! ನಿಮ್ಮ ಸಮಸ್ಯೆಯನ್ನು ವಿವರಿಸಲು ನಾನು ಸಹಾಯ ಮಾಡಬಹುದು. ' +
                'ದಯವಿಟ್ಟು ನಿಮ್ಮ ಸಮಸ್ಯೆಯನ್ನು ನಿಮ್ಮ ಮಾತುಗಳಲ್ಲಿ ಹೇಳಿ.',
            placeholder: 'ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ಟೈಪ್ ಮಾಡಿ...'
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

        var currentLang = 'en';
        var currentQuestionId = null;
        var conversationActive = true;
        var inFlight = false;

        function csrfHeaderName() {
            var meta = document.querySelector('meta[name="_csrf_header"]');
            return meta ? meta.content : 'X-CSRF-TOKEN';
        }

        function csrfToken() {
            var meta = document.querySelector('meta[name="_csrf"]');
            return meta ? meta.content : '';
        }

        function postJson(url, body) {
            return fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    'X-CSRF-TOKEN': csrfToken()
                },
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        }

        function updatePlaceholder() {
            if (!conversationActive) {
                input.placeholder = LANGUAGES[currentLang].name + ' · conversation complete';
                return;
            }
            input.placeholder = LANGUAGES[currentLang].placeholder;
        }

        function applyLanguage(lang) {
            var cfg = LANGUAGES[lang] || LANGUAGES.en;
            currentLang = lang;
            badge.textContent = cfg.name;
            badge.setAttribute('data-lang', lang);
            if (greeting) {
                greeting.textContent = cfg.greeting;
            }
            updatePlaceholder();
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

        function showQuestion(question) {
            var text = question.text;
            if (question.section === 'DASHAVIDHA') {
                text = 'AYUSH Dashavidha · ' + question.type + '\n' + text;
            } else if (question.section === 'AHARA_VIHARA') {
                text = 'AYUSH Ahara-Vihara · ' + question.type + '\n' + text;
            }
            appendMessage('assistant', text);
            currentQuestionId = question.id;
            updatePlaceholder();
            if (input) {
                input.focus();
            }
        }

        function appendUrgentWarning(redFlags) {
            var text = redFlags.map(function (flag) {
                return flag.title + ': ' + flag.message;
            }).join('\n');
            var template = document.createElement('div');
            template.className = 'chat-message urgent';
            var bubble = document.createElement('div');
            bubble.className = 'chat-bubble';
            var title = document.createElement('div');
            title.className = 'urgent-title';
            title.textContent = 'Urgent attention required';
            var span = document.createElement('span');
            span.className = 'chat-text';
            span.textContent = text;
            bubble.appendChild(title);
            bubble.appendChild(span);
            template.appendChild(bubble);
            chatMessages.appendChild(template);
            scrollToBottom();
        }

        function handleStep(data) {
            if (data.urgency === 'URGENT' && data.redFlags && data.redFlags.length > 0) {
                appendUrgentWarning(data.redFlags);
            }
            if (data.completed || !data.question) {
                completeConversation();
            } else {
                showQuestion(data.question);
            }
        }

        function completeConversation() {
            conversationActive = false;
            currentQuestionId = null;
            appendMessage('assistant', 'The intake conversation is complete. Thank you.');
            input.disabled = true;
            sendButton.disabled = true;
            updatePlaceholder();
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
                    } else {
                        completeConversation();
                    }
                })
                .catch(function () {
                    appendMessage('assistant', 'Sorry, I could not start the conversation. Please refresh the page.');
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
            appendMessage('patient', text);
            input.value = '';
            scrollToBottom();

            postJson('/patient/intake/conversation/answer', {
                questionId: answeredId,
                answer: text
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
                .catch(function () {
                    appendMessage('assistant', 'Sorry, something went wrong. Please try again.');
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

        if (selector) {
            selector.addEventListener('change', function () {
                applyLanguage(selector.value);
            });
            applyLanguage(selector.value);
        }

        sendButton.addEventListener('click', submitAnswer);

        input.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                submitAnswer();
            }
        });

        function uiOnlyToggle(id, action) {
            document.getElementById(id).addEventListener('click', function () {
                var pressed = this.getAttribute('aria-pressed') === 'true';
                this.setAttribute('aria-pressed', pressed ? 'false' : 'true');
                if (action) {
                    action(this, !pressed);
                }
            });
        }

        uiOnlyToggle('micButton', function (btn, active) {
            btn.classList.toggle('btn-primary', active);
            btn.classList.toggle('btn-outline-primary', !active);
            hint.textContent = active ? 'Voice input is coming in a later checkpoint.' : '';
        });

        uiOnlyToggle('speakQuestion', function (btn, active) {
            btn.classList.toggle('btn-outline-primary', active);
            hint.textContent = active ? 'Text-to-speech is coming in a later checkpoint.' : '';
        });

        startConversation();
    });
})();