(function () {
    'use strict';

    var LANGUAGES = {
        'en-IN': {
            name: 'English',
            greeting: 'Hello! I can help you describe what has been troubling you. ' +
                'Please tell me about your problem in your own words.',
            placeholder: 'Type your message...'
        },
        'hi-IN': {
            name: 'Hindi',
            greeting: 'नमस्ते! मैं आपकी समस्या समझने में मदद कर सकता हूँ। ' +
                'कृपया अपनी समस्या अपने शब्दों में बताइए।',
            placeholder: 'अपना संदेश लिखिए...'
        },
        'te-IN': {
            name: 'Telugu',
            greeting: 'నమస్కారం! మీ సమస్యను వివరించడంలో నేను సహాయపడగలను. ' +
                'దయచేసి మీ సమస్యను మీ స్వంత మాటల్లో చెప్పండి.',
            placeholder: 'మీ సందేశాన్ని టైప్ చేయండి...'
        },
        'ta-IN': {
            name: 'Tamil',
            greeting: 'வணக்கம்! உங்கள் பிரச்சனையை விவரிக்க நான் உதவ முடியும். ' +
                'தயவுசெய்து உங்கள் பிரச்சனையை உங்கள் வார்த்தைகளில் சொல்லுங்கள்.',
            placeholder: 'உங்கள் செய்தியைத் தட்டச்சு செய்க...'
        },
        'kn-IN': {
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
        var micButton = document.getElementById('micButton');
        var speakButton = document.getElementById('speakQuestion');

        var currentLang = 'en-IN';
        var currentBcp47 = 'en-IN';
        var currentQuestionId = null;
        var currentDisplayedQuestionText = '';
        var conversationActive = true;
        var inFlight = false;
        var currentCaseId = null;
        var voiceUsed = false;

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

        function bcp47For(value) {
            if (selector && selector.querySelector('option[value="' + value + '"]')) {
                var option = selector.querySelector('option[value="' + value + '"]');
                return option.getAttribute('data-bcp47') || value;
            }
            return value;
        }

        function setHint(message) {
            if (hint) {
                hint.textContent = message || '';
            }
        }

        function updatePlaceholder() {
            if (!conversationActive) {
                input.placeholder = LANGUAGES[currentLang].name + ' · conversation complete';
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
            if (greeting) {
                greeting.textContent = resolved.greeting;
            }
            updatePlaceholder();
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

        function showQuestion(question) {
            var text = question.text;
            if (question.section === 'DASHAVIDHA') {
                text = 'AYUSH Dashavidha · ' + question.type + '\n' + text;
            } else if (question.section === 'AHARA_VIHARA') {
                text = 'AYUSH Ahara-Vihara · ' + question.type + '\n' + text;
            }
            appendMessage('assistant', text);
            currentQuestionId = question.id;
            currentDisplayedQuestionText = text;
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
                currentCaseId = data.caseId || null;
                completeConversation();
            } else {
                showQuestion(data.question);
            }
        }

        function completeConversation() {
            conversationActive = false;
            currentQuestionId = null;
            currentDisplayedQuestionText = '';
            stopListening();
            appendMessage('assistant', 'The intake conversation is complete. Thank you.');
            input.disabled = true;
            sendButton.disabled = true;
            updatePlaceholder();
            if (currentCaseId) {
                showDocumentUpload();
            }
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
                var chosen = selector.value;
                applyLanguage(chosen);
                persistLanguage(chosen).catch(function () {
                    setHint('Could not save the language selection on the server. Your conversation is unchanged.');
                });
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
            setHint('Listening in ' + LANGUAGES[currentLang].name +
                ' \u2026 speak, then press the mic again to stop (max 30 seconds).');
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
                    input.focus();
                } else if (result.status === 200 && code === 'EMPTY') {
                    micClass('idle');
                    setHint('No speech was heard. Talk again or type.');
                } else if (code === 'UNAVAILABLE') {
                    micClass('unavailable');
                    setHint('Voice input is unavailable right now. You can keep typing.');
                } else if (code === 'UNSUPPORTED') {
                    micClass('unavailable');
                    setHint('Voice input does not support ' + LANGUAGES[currentLang].name +
                        ' yet. You can keep typing.');
                } else {
                    micClass('error');
                    setHint('Voice input failed. Try again or type instead.');
                }
            }).catch(function () {
                micClass('error');
                setHint('Voice input failed. Try again or type instead.');
            }).finally(function () {
                asrInFlight = false;
                micButton.disabled = false;
            });
        }

        function mapPermissionError(error) {
            if (error && error.name === 'NotAllowedError') {
                micClass('error');
                setHint('Microphone permission was denied. Allow it in the browser to use voice, or type instead.');
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
                    loadDocuments();
                } else {
                    showUploadFeedback(result.body && result.body.error ? result.body.error : 'Upload failed.', true);
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

        startConversation();
    });
})();