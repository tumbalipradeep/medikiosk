package in.devmedi.kiosk.module.clinical.ayush;

/**
 * One Ahara-Vihara (diet and lifestyle) assessment question/item.
 *
 * @param id        stable question id (e.g. {@code ahara_vihara_sleep})
 * @param parameter the Ahara-Vihara parameter assessed
 * @param text      patient-friendly question text
 * @param required  whether an answer is required
 * @param order     deterministic ordering/priority within the sequence
 */
public record AharaViharaQuestion(String id,
                                  AharaViharaParameter parameter,
                                  String text,
                                  boolean required,
                                  int order) {
}