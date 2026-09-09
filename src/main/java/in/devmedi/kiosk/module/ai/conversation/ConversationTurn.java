package in.devmedi.kiosk.module.ai.conversation;

/**
 * One completed question/answer pair kept in the compact conversation state.
 */
public record ConversationTurn(String question, String answer) {
}