package in.devmedi.kiosk.module.clinical.i18n;

import in.devmedi.kiosk.module.clinical.dialogue.QuestionSource;
import in.devmedi.kiosk.module.voice.language.SupportedLanguage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Curated offline translations of the deterministic clinical questions.
 *
 * <p>The clinical conversation is fully offline and deterministic: the canonical
 * question text comes from the QuestionPlanner/Dashavidha/Ahara-Vihara planners
 * in English. Language selection is presentation-layer state, and for every
 * supported non-English language this service provides a carefully written,
 * respectful plain-language translation of each of the 25 canonical questions.
 * When a translation is present and the patient language is not English, the
 * conversation controller serves the translated text and records it honestly as
 * {@link QuestionSource#TRANSLATED} alongside the patient-language BCP-47 tag.
 *
 * <p>Translations are curated by hand and frozen in this file - they are never
 * generated at request time, so the record is reproducible offline. There is no
 * machine translation and no claim that any AI produced these words. Asking an
 * LLM or provider at runtime is deliberately out of scope: the wording shown
 * must be stable, reviewable, and independent of connectivity.</p>
 */
@Service
public class QuestionLocalizationService {

    private static final int TRANSLATED_COUNT = 25;
    private static final Map<SupportedLanguage, Map<String, String>> TRANSLATIONS = Map.of(
            SupportedLanguage.HINDI, byId(
                    "chief_complaint_symptom", "आज आप यहाँ जो मुख्य समस्या या लक्षण लेकर आए हैं, वह क्या है?",
                    "hpi_onset", "यह सबसे पहले कब शुरू हुआ?",
                    "hpi_provocation_palliation", "इससे राहत किस चीज़ से मिलती है और किस चीज़ से यह और बढ़ जाता है?",
                    "hpi_quality", "आप इस समस्या के होने का अहसास या गुणवत्ता कैसे बताएँगे?",
                    "hpi_region_radiation", "यह दर्द या समस्या बिल्कुल कहाँ है, और क्या यह कहीं और भी फैलती है?",
                    "hpi_severity", "0 से 10 के पैमाने पर अभी यह कितनी गंभीर है?",
                    "hpi_timing_duration", "क्या यह लगातार रहती है या आती-जाती रहती है? हर बार कब तक रहती है?",
                    "dashavidha_prakriti", "अपनी सामान्य शारीरिक प्रकृति बताइए — जैसे आप आमतौर पर गर्मी या ठंड कैसे महसूस करते हैं, आपका शरीर का ढाँचा, या आपकी त्वचा और बालों की स्थिति।",
                    "dashavidha_vikriti", "अपनी सामान्य सेहत की तुलना में, अपनी वर्तमान समस्या से जुड़ा कोई बदलाव या असंतुलन बताइए।",
                    "dashavidha_sara", "आपकी कुल ताक़त और सहनशक्ति कैसी है — और आपकी त्वचा, नाखूनों और शरीर के ऊतकों की सामान्य स्थिति कैसी है?",
                    "dashavidha_samhanana", "आपका शारीरिक ढाँचा और बनावट कैसी है — जैसे पतला, मध्यम, या मज़बूत?",
                    "dashavidha_pramana", "अपनी सामान्य स्थिति की तुलना में, अपनी ऊँचाई, वज़न और शरीर के माप कैसे बताएँगे?",
                    "dashavidha_satmya", "कौन से भोजन, पेय और दैनिक आदतें आपको अच्छी लगती हैं — और कौन सी आपको अच्छी नहीं लगतीं?",
                    "dashavidha_sattva", "आपका सामान्य मनोभाव, मानसिक दृष्टिकोण और कठिन परिस्थितियों को संभालने का तरीका कैसा है?",
                    "dashavidha_ahara_shakti", "आपकी सामान्य भूख और पाचन कैसा है — आम भोजन को आप कितनी अच्छी तरह सहन और पचा पाते हैं?",
                    "dashavidha_vyayama_shakti", "आपकी शारीरिक सहनशक्ति कैसी है — जैसे थकान होने से पहले आप कितनी देर कसरत या काम कर पाते हैं?",
                    "dashavidha_vaya", "इनमें से आपकी अवस्था सबसे अच्छी बताती है: बचपन, युवावस्था, मध्य आयु, या वृद्ध अवस्था?",
                    "ahara_vihara_ahara", "आम दिन में आप आमतौर पर क्या खाते-पीते हैं?",
                    "ahara_vihara_meal_pattern", "आपके भोजन का सामान्य समय या भोजन की दिनचर्या क्या है?",
                    "ahara_vihara_appetite", "आपकी सामान्य भूख कैसी है?",
                    "ahara_vihara_hydration", "आप आमतौर पर कितना पानी या अन्य तरल पदार्थ पीते हैं?",
                    "ahara_vihara_sleep", "आप आमतौर पर कितने घंटे सोते हैं, और आपकी नींद कैसी है?",
                    "ahara_vihara_physical_activity", "आम दिन में आप शारीरिक रूप से कितने सक्रिय रहते हैं?",
                    "ahara_vihara_daily_routine", "आपकी आम दैनिक दिनचर्या कैसी दिखती है?",
                    "ahara_vihara_habits", "क्या आप कोई ऐसी नियमित जीवनशैली की आदत बताना चाहेंगे जो हमें जाननी चाहिए?"),
            SupportedLanguage.TELUGU, byId(
                    "chief_complaint_symptom", "ఈరోజు మిమ్మల్ని ఇక్కడికి తీసుకొచ్చిన ప్రధాన సమస్య లేదా లక్షణం ఏమిటి?",
                    "hpi_onset", "ఇది మొదట ఎప్పుడు మొదలైంది?",
                    "hpi_provocation_palliation", "దీనివల్ల ఏమి చేస్తే తగ్గుతుంది, ఏమి చేస్తే ఎక్కువవుతుంది?",
                    "hpi_quality", "ఈ సమస్య యొక్క అనుభూతి లేదా స్వభావాన్ని మీరు ఎలా వర్ణిస్తారు?",
                    "hpi_region_radiation", "ఇది సరిగ్గా ఎక్కడ ఉంది, మరియు అది వేరే ప్రాంతానికి వ్యాపిస్తుందా?",
                    "hpi_severity", "ప్రస్తుతం 0 నుండి 10 మధ్య దీని తీవ్రత ఎంత?",
                    "hpi_timing_duration", "ఇది నిరంతరంగా ఉంటుందా లేదా వస్తూ పోతూ ఉంటుందా? ప్రతి సారి ఎంతసేపు ఉంటుంది?",
                    "dashavidha_prakriti", "మీ సాధారణ శరీర ప్రకృతిని వివరించండి — ఉదాహరణకు మీరు సాధారణంగా వేడి లేదా చలి అనుభవిస్తారా, మీ శరీర తీరు, లేదా మీ చర్మం మరియు జుట్టు స్వభావం.",
                    "dashavidha_vikriti", "మీ సాధారణ ఆరోగ్యంతో పోలిస్తే మీ ప్రస్తుత సమస్యకు సంబంధించిన మార్పు లేదా అసమతుల్యతను వివరించండి.",
                    "dashavidha_sara", "మీ మొత్తం బలం మరియు ఓర్పు ఎలా ఉంది — మరియు మీ చర్మం, గోళ్ళు, శరీర కణజాలాల సాధారణ పరిస్థితి ఎలా ఉంది?",
                    "dashavidha_samhanana", "మీ శరీర తీరు మరియు నిర్మాణం ఎలా ఉంది — ఉదాహరణకు సన్నగా, మధ్యస్థంగా, లేదా బలిష్టంగా?",
                    "dashavidha_pramana", "మీ సాధారణ స్థితితో పోలిస్తే మీ ఎత్తు, బరువు మరియు శరీర కొలతలు ఎలా ఉన్నాయో వివరించండి?",
                    "dashavidha_satmya", "ఏ ఆహారాలు, పానీయాలు, దినచర్య అలవాట్లు మీకు బాగా సరిపోతాయి — మరియు ఏవి సరిపోవు?",
                    "dashavidha_sattva", "మీ సాధారణ మానసిక స్థితి, దృక్పథం, మరియు కష్టమైన పరిస్థితులను ఎదుర్కొనే విధానం ఎలా ఉంటుంది?",
                    "dashavidha_ahara_shakti", "మీ సాధారణ ఆకలి మరియు జీర్ణశక్తి ఎలా ఉంది — సాధారణ భోజనాన్ని ఎంత బాగా జీర్ణం చేసుకోగలరు?",
                    "dashavidha_vyayama_shakti", "మీ శారీరక సహనం ఎలా ఉంది — ఉదాహరణకు అలసట రాకముందు మీరు ఎంతసేపు వ్యాయామం లేదా పని చేయగలరు?",
                    "dashavidha_vaya", "మీ జీవిత దశను ఇందులో ఏది బాగా వర్ణిస్తుంది: బాల్యం, యుక్త వయసు, మధ్య వయసు, లేదా వృద్ధాప్యం?",
                    "ahara_vihara_ahara", "సాధారణ రోజులో మీరు సాధారణంగా ఏమి తింటారు మరియు తాగుతారు?",
                    "ahara_vihara_meal_pattern", "మీ భోజన సమయం లేదా భోజన విధానం ఏమిటి?",
                    "ahara_vihara_appetite", "మీ సాధారణ ఆకలి ఎలా ఉంటుంది?",
                    "ahara_vihara_hydration", "మీరు సాధారణంగా ఎంత నీరు లేదా ఇతర ద్రవాలు తాగుతారు?",
                    "ahara_vihara_sleep", "మీరు సాధారణంగా ఎన్ని గంటలు నిద్రపోతారు, మరియు మీ నిద్ర ఎలా ఉంటుంది?",
                    "ahara_vihara_physical_activity", "సాధారణ రోజులో మీరు ఎంత శారీరకంగా చురుకుగా ఉంటారు?",
                    "ahara_vihara_daily_routine", "మీ సాధారణ రోజువారీ దినచర్య ఎలా ఉంటుంది?",
                    "ahara_vihara_habits", "మీరు చెప్పదలచిన సాధారణ జీవనశైలి అలవాట్లు ఏమైనా ఉన్నాయా?"),
            SupportedLanguage.TAMIL, byId(
                    "chief_complaint_symptom", "இன்று உங்களை இங்கு அழைத்து வந்த முக்கியப் பிரச்சனை அல்லது அறிகுறி என்ன?",
                    "hpi_onset", "இது முதலில் எப்போது தொடங்கியது?",
                    "hpi_provocation_palliation", "எதைச் செய்தால் இது குறைகிறது, எதைச் செய்தால் இது அதிகரிக்கிறது?",
                    "hpi_quality", "இந்தப் பிரச்சனையின் உணர்வு அல்லது தன்மையை எப்படி விவரிப்பீர்கள்?",
                    "hpi_region_radiation", "இது சரியாக எங்கு உள்ளது, வேறு எங்காவது பரவுகிறதா?",
                    "hpi_severity", "0 முதல் 10 வரையிலான அளவில் இப்போது இது எவ்வளவு கடுமையானது?",
                    "hpi_timing_duration", "இது தொடர்ந்து இருக்கிறதா அல்லது வந்து போகிறதா? ஒவ்வொரு முறையும் எவ்வளவு நேரம் இருக்கிறது?",
                    "dashavidha_prakriti", "உங்கள் பொதுவான உடல் அமைப்பை விவரிக்கவும் — உதாரணமாக நீங்கள் வழக்கமாக சூடு அல்லது குளிர் எப்படி உணர்கிறீர்கள், உங்கள் உடல் தோற்றம், அல்லது உங்கள் தோல் மற்றும் முடியின் தன்மை.",
                    "dashavidha_vikriti", "உங்கள் வழக்கமான ஆரோக்கிய நிலையிலிருந்து, உங்கள் தற்போதைய பிரச்சனை தொடர்பான மாற்றம் அல்லது சமநிலைக் குறைவை விவரிக்கவும்.",
                    "dashavidha_sara", "உங்கள் ஒட்டுமொத்த பலமும் சகிப்புத்தன்மையும் எப்படி உள்ளது — மேலும் உங்கள் தோல், நகங்கள், உடல் திசுக்களின் பொதுவான நிலை எப்படி உள்ளது?",
                    "dashavidha_samhanana", "உங்கள் உடல் வடிவமும் கட்டமைப்பும் எப்படி உள்ளது — உதாரணமாக மெலிதான, நடுத்தர, அல்லது உறுதியான?",
                    "dashavidha_pramana", "உங்கள் வழக்கமான நிலையுடன் ஒப்பிடும்போது உங்கள் உயரம், எடை, உடல் அளவுகள் எப்படி உள்ளன?",
                    "dashavidha_satmya", "எந்த உணவுகள், பானங்கள், தினசரி பழக்கங்கள் உங்களுக்கு நன்றாக இணைகின்றன — எவை ஒத்துப்போவதில்லை?",
                    "dashavidha_sattva", "உங்கள் வழக்கமான மனநிலை, மனத் தன்மை, கடினமான சூழ்நிலைகளைக் கையாளும் விதம் எப்படி உள்ளது?",
                    "dashavidha_ahara_shakti", "உங்கள் வழக்கமான பசி மற்றும் செரிமானம் எப்படி உள்ளது — சாதாரண உணவை எவ்வளவு நன்றாக தாங்கி ஜீரணிக்கிறீர்கள்?",
                    "dashavidha_vyayama_shakti", "உங்கள் உடல் சகிப்புத்தன்மை எப்படி உள்ளது — உதாரணமாக சோர்வு ஏற்படுவதற்கு முன்பு எவ்வளவு நேரம் உடற்பயிற்சி அல்லது வேலை செய்ய முடியும்?",
                    "dashavidha_vaya", "உங்கள் வாழ்க்கை நிலையை இவற்றில் எது சிறப்பாக விவரிக்கிறது: குழந்தைப் பருவம், இளமை, நடுத்தர வயது, அல்லது முதுமை?",
                    "ahara_vihara_ahara", "வழக்கமான நாளில் நீங்கள் பொதுவாக என்ன சாப்பிடுகிறீர்கள், குடிக்கிறீர்கள்?",
                    "ahara_vihara_meal_pattern", "உங்கள் வழக்கமான உணவு நேரம் அல்லது உணவு முறை என்ன?",
                    "ahara_vihara_appetite", "உங்கள் வழக்கமான பசி எப்படி இருக்கிறது?",
                    "ahara_vihara_hydration", "நீங்கள் வழக்கமாக எவ்வளவு தண்ணீர் அல்லது பிற திரவங்களைக் குடிக்கிறீர்கள்?",
                    "ahara_vihara_sleep", "நீங்கள் வழக்கமாக எத்தனை மணி நேரம் தூங்குகிறீர்கள், உங்கள் தூக்கம் எப்படி இருக்கிறது?",
                    "ahara_vihara_physical_activity", "வழக்கமான நாளில் நீங்கள் எவ்வளவு உடல் செயல்பாடுடன் இருக்கிறீர்கள்?",
                    "ahara_vihara_daily_routine", "உங்கள் வழக்கமான தினசரி வழக்கம் எப்படி இருக்கும்?",
                    "ahara_vihara_habits", "நீங்கள் சொல்ல விரும்பும் வழக்கமான வாழ்க்கை முறைப் பழக்கங்கள் ஏதேனும் உள்ளதா?"),
            SupportedLanguage.KANNADA, byId(
                    "chief_complaint_symptom", "ಇಂದು ನಿಮ್ಮನ್ನು ಇಲ್ಲಿಗೆ ಕರೆದುತಂದ ಪ್ರಮುಖ ಸಮಸ್ಯೆ ಅಥವಾ ಲಕ್ಷಣ ಯಾವುದು?",
                    "hpi_onset", "ಇದು ಮೊದಲು ಯಾವಾಗ ಪ್ರಾರಂಭವಾಯಿತು?",
                    "hpi_provocation_palliation", "ಯಾವುದರಿಂದ ಇದು ಕಡಿಮೆಯಾಗುತ್ತದೆ, ಯಾವುದರಿಂದ ಇದು ಹೆಚ್ಚಾಗುತ್ತದೆ?",
                    "hpi_quality", "ಈ ಸಮಸ್ಯೆಯ ಅನುಭವ ಅಥವಾ ಸ್ವಭಾವವನ್ನು ನೀವು ಹೇಗೆ ವಿವರಿಸುತ್ತೀರಿ?",
                    "hpi_region_radiation", "ಇದು ನಿಖರವಾಗಿ ಎಲ್ಲಿದೆ, ಮತ್ತು ಅದು ಬೇರೆ ಜಾಗಕ್ಕೆ ಹರಡುತ್ತದೆಯೇ?",
                    "hpi_severity", "0 ರಿಂದ 10 ರ ಪ್ರಮಾಣದಲ್ಲಿ ಈಗ ಇದು ಎಷ್ಟು ತೀವ್ರವಾಗಿದೆ?",
                    "hpi_timing_duration", "ಇದು ನಿರಂತರವಾಗಿದೆಯೇ ಅಥವಾ ಬಂದು ಹೋಗುತ್ತಲೇ ಇದೆಯೇ? ಪ್ರತಿ ಬಾರಿ ಎಷ್ಟು ಹೊತ್ತು ಇರುತ್ತದೆ?",
                    "dashavidha_prakriti", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ದೇಹದ ಸ್ವಭಾವವನ್ನು ವಿವರಿಸಿ — ಉದಾಹರಣೆಗೆ ನೀವು ಸಾಮಾನ್ಯವಾಗಿ ಬಿಸಿ ಅಥವಾ ಚಳಿ ಎಂದು ಹೇಗೆ ಅನುಭವಿಸುತ್ತೀರಿ, ನಿಮ್ಮ ದೇಹದ ತೀರು, ಅಥವಾ ನಿಮ್ಮ ಚರ್ಮ ಮತ್ತು ಕೂದಲಿನ ಸ್ಥಿತಿ.",
                    "dashavidha_vikriti", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಆರೋಗ್ಯದೊಂದಿಗೆ ಹೋಲಿಸಿದರೆ, ನಿಮ್ಮ ಪ್ರಸ್ತುತ ಸಮಸ್ಯೆಗೆ ಸಂಬಂಧಿಸಿದ ಯಾವುದೇ ಬದಲಾವಣೆ ಅಥವಾ ಅಸಮತೋಲನವನ್ನು ವಿವರಿಸಿ.",
                    "dashavidha_sara", "ನಿಮ್ಮ ಒಟ್ಟಾರೆ ಶಕ್ತಿ ಮತ್ತು ಸಹನಶಕ್ತಿ ಹೇಗಿದೆ — ಮತ್ತು ನಿಮ್ಮ ಚರ್ಮ, ಉಗುರು, ದೇಹದ ಅಂಗಾಂಶಗಳ ಸಾಮಾನ್ಯ ಸ್ಥಿತಿ ಹೇಗಿದೆ?",
                    "dashavidha_samhanana", "ನಿಮ್ಮ ದೇಹದ ಆಕಾರ ಮತ್ತು ರಚನೆ ಹೇಗಿದೆ — ಉದಾಹರಣೆಗೆ ತೆಳುವಾದ, ಮಧ್ಯಮ, ಅಥವಾ ಗಟ್ಟಿಮುಟ್ಟಾದ?",
                    "dashavidha_pramana", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಸ್ಥಿತಿಗೆ ಹೋಲಿಸಿದರೆ ನಿಮ್ಮ ಎತ್ತರ, ತೂಕ, ದೇಹದ ಅಳತೆಗಳು ಹೇಗಿವೆ?",
                    "dashavidha_satmya", "ಯಾವ ಆಹಾರಗಳು, ಪಾನೀಯಗಳು, ದೈನಂದಿನ ಅಭ್ಯಾಸಗಳು ನಿಮಗೆ ಬಾಣಸುವಂತಿವೆ — ಮತ್ತು ಯಾವುವು ಸರಿಹೊಂದುವುದಿಲ್ಲ?",
                    "dashavidha_sattva", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಮನಸ್ಥಿತಿ, ಮಾನಸಿಕ ದೃಷ್ಟಿಕೋನ, ಮತ್ತು ಕಷ್ಟದ ಸಂದರ್ಭಗಳನ್ನು ನಿಭಾಯಿಸುವ ವಿಧಾನ ಹೇಗಿದೆ?",
                    "dashavidha_ahara_shakti", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಹಸಿವು ಮತ್ತು ಜೀರ್ಣಶಕ್ತಿ ಹೇಗಿದೆ — ಸಾಮಾನ್ಯ ಊಟವನ್ನು ನೀವು ಎಷ್ಟು ಚೆನ್ನಾಗಿ ಸಹಿಸಿ ಜೀರ್ಣಿಸಿಕೊಳ್ಳುತ್ತೀರಿ?",
                    "dashavidha_vyayama_shakti", "ನಿಮ್ಮ ದೈಹಿಕ ಸಹನೆ ಹೇಗಿದೆ — ಉದಾಹರಣೆಗೆ ಸುಸ್ತಾಗುವ ಮೊದಲು ಎಷ್ಟು ಹೊತ್ತು ವ್ಯಾಯಾಮ ಅಥವಾ ಕೆಲಸ ಮಾಡಬಲ್ಲಿರಿ?",
                    "dashavidha_vaya", "ನಿಮ್ಮ ಜೀವನದ ಹಂತವನ್ನು ಇವುಗಳಲ್ಲಿ ಯಾವುದು ಚೆನ್ನಾಗಿ ವಿವರಿಸುತ್ತದೆ: ಬಾಲ್ಯ, ಯೌವನ, ಮಧ್ಯ ವಯಸ್ಸು, ಅಥವಾ ವೃದ್ಧಾಪ್ಯ?",
                    "ahara_vihara_ahara", "ಸಾಮಾನ್ಯ ದಿನದಲ್ಲಿ ನೀವು ಸಾಮಾನ್ಯವಾಗಿ ಏನು ತಿನ್ನುತ್ತೀರಿ ಮತ್ತು ಕುಡಿಯುತ್ತೀರಿ?",
                    "ahara_vihara_meal_pattern", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಊಟದ ಸಮಯ ಅಥವಾ ಊಟದ ಪದ್ಧತಿ ಏನು?",
                    "ahara_vihara_appetite", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ಹಸಿವು ಹೇಗಿರುತ್ತದೆ?",
                    "ahara_vihara_hydration", "ನೀವು ಸಾಮಾನ್ಯವಾಗಿ ಎಷ್ಟು ನೀರು ಅಥವಾ ಇತರ ದ್ರವಗಳನ್ನು ಕುಡಿಯುತ್ತೀರಿ?",
                    "ahara_vihara_sleep", "ನೀವು ಸಾಮಾನ್ಯವಾಗಿ ಎಷ್ಟು ಗಂಟೆ ನಿದ್ರೆ ಮಾಡುತ್ತೀರಿ, ಮತ್ತು ನಿಮ್ಮ ನಿದ್ರೆ ಹೇಗಿದೆ?",
                    "ahara_vihara_physical_activity", "ಸಾಮಾನ್ಯ ದಿನದಲ್ಲಿ ನೀವು ದೈಹಿಕವಾಗಿ ಎಷ್ಟು ಸಕ್ರಿಯರಾಗಿದ್ದೀರಿ?",
                    "ahara_vihara_daily_routine", "ನಿಮ್ಮ ಸಾಮಾನ್ಯ ದೈನಂದಿನ ದಿನಚರಿ ಹೇಗಿದೆ?",
                    "ahara_vihara_habits", "ನೀವು ಹೇಳಲು ಬಯಸುವ ಸಾಧಾರಣ ಜೀವನಶೈಲಿ ಅಭ್ಯಾಸಗಳು ಏನಾದರೂ ಇವೆಯಾ?"));

    /**
     * @param language the patient's selected language
     * @return the per-language question map (never null)
     */
    public Map<String, String> translationsFor(SupportedLanguage language) {
        return TRANSLATIONS.getOrDefault(language, Map.of());
    }

    /**
     * Returns the curated translation for a question id, or {@code null} when the
     * question has no translation in that language (for example English, where the
     * canonical text is authoritative).
     *
     * @param questionId stable question id
     * @param language   the target presentation language
     * @return translated question text, or {@code null}
     */
    public String translation(String questionId, SupportedLanguage language) {
        if (language == null || language == SupportedLanguage.ENGLISH) {
            return null;
        }
        return TRANSLATIONS.getOrDefault(language, Map.of()).get(questionId);
    }

    /**
     * @return the number of canonical questions that have translations (25)
     */
    public int translatedQuestionCount() {
        return TRANSLATED_COUNT;
    }

    private static Map<String, String> byId(String... pairs) {
        if (pairs.length != TRANSLATED_COUNT * 2) {
            throw new IllegalStateException("Translation set must cover exactly "
                    + TRANSLATED_COUNT + " questions");
        }
        HashMap<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}