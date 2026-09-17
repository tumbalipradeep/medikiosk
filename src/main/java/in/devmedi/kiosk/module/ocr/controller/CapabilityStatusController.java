package in.devmedi.kiosk.module.ocr.controller;

import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService.AiProviderEnabled;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.HwrCapabilityResponse;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.OcrCapabilitiesResponse;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, non-clinical capability status for the OCR, handwriting-recognition
 * and conversational-AI boundaries. Responses contain no patient data and are
 * safe for anonymous callers to inspect during evaluation or monitoring.
 */
@RestController
@RequestMapping("/api/capabilities")
public class CapabilityStatusController {

    private final OcrCapabilityService capabilityService;
    private final AiFailoverService aiFailoverService;
    private final VoiceProperties voiceProperties;

    public CapabilityStatusController(OcrCapabilityService capabilityService,
                                      AiFailoverService aiFailoverService,
                                      VoiceProperties voiceProperties) {
        this.capabilityService = capabilityService;
        this.aiFailoverService = aiFailoverService;
        this.voiceProperties = voiceProperties;
    }

    @GetMapping("/ocr")
    public OcrCapabilitiesResponse ocr() {
        return capabilityService.ocrCapabilities();
    }

    @GetMapping("/hwr")
    public HwrCapabilityResponse hwr() {
        return capabilityService.hwrCapability();
    }

    /**
     * Honest conversational-AI capability: which providers are bound and which
     * are enabled (settings + API key present). Configuration-level only; no
     * credential values, no network call, no clinical data.
     */
    @GetMapping("/ai")
    public AiCapabilityResponse ai() {
        List<AiProviderEnabled> providers = aiFailoverService.providerStatuses();
        boolean anyEnabled = providers.stream().anyMatch(AiProviderEnabled::enabled);
        return new AiCapabilityResponse(providers, anyEnabled);
    }

    /** Honest capability summary for the conversational-AI boundary. */
    public record AiCapabilityResponse(List<AiProviderEnabled> providers, boolean anyProviderEnabled) {
    }

    /**
     * Honest ASR/TTS capability. A voice capability is "available" exactly
     * when {@code VoiceConfig} engages a real provider (switch set to a real
     * provider AND its credentials complete) — the same predicate the wiring
     * uses to bind the live service instead of the deterministic UNAVAILABLE
     * fallback. Reports provider names only; never credential values, audio,
     * or patient data. When a capability is not live, {@code setupHint} names
     * the exact environment variables an operator must set — the same
     * constants the startup warning cites.
     */
    @GetMapping("/voice")
    public VoiceCapabilityResponse voice() {
        String asrProvider = voiceProperties.resolvedAsrProvider();
        String ttsProvider = voiceProperties.resolvedTtsProvider();
        boolean asrLive = VoiceProperties.PROVIDER_BHASHINI.equals(asrProvider);
        boolean ttsLive = VoiceProperties.PROVIDER_BHASHINI.equals(ttsProvider);
        return new VoiceCapabilityResponse(asrProvider, asrLive, ttsProvider, ttsLive,
                voiceProperties.setupHint());
    }

    /** Honest capability summary for the patient voice (ASR/TTS) boundary. */
    public record VoiceCapabilityResponse(String asrProvider, boolean asrAvailable,
                                          String ttsProvider, boolean ttsAvailable,
                                          String setupHint) {
    }
}
