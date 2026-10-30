package vn.uteb;

import java.util.List;

/** Client history is conversation context, never an authoritative source of catalog facts. */
record AiConversationTurn(String user,String answer,List<String> dishes) {
    AiConversationTurn { dishes=dishes==null?List.of():List.copyOf(dishes); }
}
