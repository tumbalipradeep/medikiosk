package in.devmedi.kiosk.module.clinical.ayush;

/**
 * One Dashavidha Pariksha assessment question/item.
 *
 * @param id        stable question id (e.g. {@code dashavidha_prakriti})
 * @param parameter the Dashavidha parameter assessed
 * @param text      patient-friendly question text
 * @param required  whether an answer is required (optional items may be skipped)
 * @param order     deterministic ordering/priority within the sequence
 */
public record DashavidhaQuestion(String id,
                                 DashavidhaParameter parameter,
                                 String text,
                                 boolean required,
                                 int order) {
}