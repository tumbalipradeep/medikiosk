package in.devmedi.kiosk.home;

import in.devmedi.kiosk.module.admin.config.SystemSetting;
import in.devmedi.kiosk.module.admin.config.SystemSettingRepository;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService;
import in.devmedi.kiosk.module.ai.provider.AiFailoverService.AiProviderEnabled;
import in.devmedi.kiosk.module.his.HisIntegrationBoundary;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.HwrCapabilityResponse;
import in.devmedi.kiosk.module.ocr.OcrCapabilityService.OcrCapabilitiesResponse;
import in.devmedi.kiosk.module.ocr.OcrProviderStatus;
import in.devmedi.kiosk.module.voice.config.VoiceProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Public web surface. The site's informational pages ({@code /about},
 * {@code /features}, {@code /privacy}, {@code /contact}) are normal website
 * pages; {@code /contact} is the only one with a backend dependency &mdash; it
 * reads the operator-configured support address from the runtime settings
 * store and renders it read-only.
 */
@Controller
public class HomeController {

    private final SystemSettingRepository settingRepository;
    private final AiFailoverService aiFailoverService;
    private final OcrCapabilityService ocrCapabilityService;
    private final VoiceProperties voiceProperties;
    private final HisIntegrationBoundary hisIntegrationBoundary;

    public HomeController(SystemSettingRepository settingRepository,
                          AiFailoverService aiFailoverService,
                          OcrCapabilityService ocrCapabilityService,
                          VoiceProperties voiceProperties,
                          HisIntegrationBoundary hisIntegrationBoundary) {
        this.settingRepository = settingRepository;
        this.aiFailoverService = aiFailoverService;
        this.ocrCapabilityService = ocrCapabilityService;
        this.voiceProperties = voiceProperties;
        this.hisIntegrationBoundary = hisIntegrationBoundary;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                switch (authority.getAuthority()) {
                    case "ROLE_ADMIN" -> {
                        return "redirect:/admin/home";
                    }
                    case "ROLE_PHYSICIAN" -> {
                        return "redirect:/physician/home";
                    }
                    case "ROLE_PATIENT" -> {
                        return "redirect:/patient/home";
                    }
                    default -> {
                    }
                }
            }
        }

        // Every capability row on the landing page is derived from the same
        // authoritative in-process state the runtime itself uses — nothing on
        // this page is a hard-coded claim.
        List<AiProviderEnabled> aiProviders = aiFailoverService.providerStatuses();
        model.addAttribute("aiProviders", aiProviders);
        model.addAttribute("aiConfigured",
                aiProviders.stream().anyMatch(AiProviderEnabled::enabled));

        OcrCapabilitiesResponse ocr = ocrCapabilityService.ocrCapabilities();
        model.addAttribute("ocrStatus", ocr.overallStatus().name());
        model.addAttribute("ocrOperational", ocr.overallStatus().isOperational());

        HwrCapabilityResponse hwr = ocrCapabilityService.hwrCapability();
        model.addAttribute("hwrStatus", hwr.status().name());
        model.addAttribute("hwrAvailable", hwr.available());

        boolean voiceLive = (voiceProperties.asrUsesBhashini() && voiceProperties.getBhashini().isComplete())
                || (voiceProperties.ttsUsesBhashini() && voiceProperties.getBhashini().isComplete());
        model.addAttribute("voiceLive", voiceLive);

        model.addAttribute("hisLabel", hisIntegrationBoundary.transportLabel());
        model.addAttribute("hisConfigured", hisIntegrationBoundary.isConfigured());
        // RD3: printed-document OCR is implemented against the Bhashini/ULCA
        // pipeline but is honest until credentials verify it end to end.
        model.addAttribute("ocrImplementedNotVerified",
                OcrProviderStatus.IMPLEMENTED_BUT_NOT_CREDENTIAL_VERIFIED.name().equals(ocr.overallStatus().name()));

        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/features")
    public String features() {
        return "features";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        String supportEmail = settingRepository.findByKey("kiosk.support_email")
                .map(SystemSetting::getValue)
                .orElse(null);
        model.addAttribute("supportEmail", supportEmail);
        return "contact";
    }
}
