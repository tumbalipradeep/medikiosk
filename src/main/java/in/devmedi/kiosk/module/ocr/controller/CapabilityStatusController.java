package in.devmedi.kiosk.module.ocr.controller;

import in.devmedi.kiosk.module.ocr.OcrCapabilityService;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.HwrCapabilityResponse;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.OcrCapabilitiesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-clinical capability status for the OCR and handwriting-recognition
 * boundaries. Responses contain no patient data and are safe for anonymous
 * callers to inspect during evaluation or monitoring.
 */
@RestController
@RequestMapping("/api/capabilities")
public class CapabilityStatusController {

    private final OcrCapabilityService capabilityService;

    public CapabilityStatusController(OcrCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @GetMapping("/ocr")
    public OcrCapabilitiesResponse ocr() {
        return capabilityService.ocrCapabilities();
    }

    @GetMapping("/hwr")
    public HwrCapabilityResponse hwr() {
        return capabilityService.hwrCapability();
    }
}