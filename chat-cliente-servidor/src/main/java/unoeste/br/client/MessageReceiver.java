package unoeste.br.client;

import unoeste.br.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;

public class MessageReceiver implements Runnable {

    private BufferedReader in;
    private ChatClient client;

    public MessageReceiver(BufferedReader in, ChatClient client) {
        this.in = in;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            String serverResponse;
            while ((serverResponse = in.readLine()) != null) {
                System.out.println();
                processServerResponse(serverResponse);

                System.out.print("> ");
                System.out.flush();
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("Erro ao receber mensagem do servidor: " + e.getMessage());
            }
        } finally {
            System.out.println("\nConexão com o servidor perdida. Encerrando cliente.");
            client.closeClientResources();
            Thread.currentThread().interrupt();
        }
    }

    private void processServerResponse(String response) {
        if (response.startsWith(Protocol.SUCCESS)) {
            System.out.println(response);
        } else if (response.startsWith(Protocol.ERROR)) {
            System.err.println(response);
        } else if (response.startsWith(Protocol.NOT_AUTHORIZED)) {
            System.err.println(response);
        } else if (response.startsWith(Protocol.INFO)) {
            System.out.println(response);
        }
        else if (response.startsWith(Protocol.LOGIN_SUCCESS)) {
            String login = response.substring(Protocol.LOGIN_SUCCESS.length());
            client.setLoggedInUser(login);
            System.out.println("Login bem-sucedido como: " + login);
        } else if (response.startsWith(Protocol.RECOVER_PASSWORD_SUCCESS)) {
            String password = response.substring(Protocol.RECOVER_PASSWORD_SUCCESS.length());
            System.out.println("Sua senha é: " + password);
        } else if (response.startsWith(Protocol.LIST_ONLINE_USERS_RESPONSE)) {
            String users = response.substring(Protocol.LIST_ONLINE_USERS_RESPONSE.length());
            System.out.println("Usuários online: " + users);
        } else if (response.startsWith(Protocol.LIST_BUSY_USERS_RESPONSE)) {
            String users = response.substring(Protocol.LIST_BUSY_USERS_RESPONSE.length());
            System.out.println("Usuários ocupados (BUSY): " + users);
        } else if (response.startsWith(Protocol.LIST_AWAY_USERS_RESPONSE)) {
            String users = response.substring(Protocol.LIST_AWAY_USERS_RESPONSE.length());
            System.out.println("Usuários ausentes (AWAY): " + users);
        }
        else if (response.startsWith(Protocol.LIST_GROUPS_RESPONSE)) {
            String groups = response.substring(Protocol.LIST_GROUPS_RESPONSE.length());
            System.out.println("Grupos existentes: " + groups);
        } else if (response.startsWith(Protocol.PRIVATE_MESSAGE)) {
            String msg = response.substring(Protocol.PRIVATE_MESSAGE.length());
            System.out.println("[PRIVADO] " + msg);
        } else if (response.startsWith(Protocol.GROUP_MESSAGE)) {
            String msgContent = response.substring(Protocol.GROUP_MESSAGE.length());
            System.out.println("[GRUPO] " + msgContent);
        } else if (response.startsWith(Protocol.GROUP_PRIVATE_MESSAGE)) {
            String msgContent = response.substring(Protocol.GROUP_PRIVATE_MESSAGE.length());
            System.out.println("[GRUPO PRIVADO] " + msgContent);
        }
        else if (response.startsWith(Protocol.MESSAGE_SENT)) {
            System.out.println(response);
        } else if (response.startsWith(Protocol.MESSAGE_NOT_DELIVERED)) {
            System.out.println(response);
        } else if (response.startsWith(Protocol.SERVER_MSG)) {
            System.out.println("[INFO] " + response.substring(Protocol.SERVER_MSG.length()));
        } else if (response.startsWith(Protocol.GROUP_INVITE)) {
            String inviteInfo = response.substring(Protocol.GROUP_INVITE.length());
            String[] parts = inviteInfo.split(",");
            if (parts.length == 2) {
                System.out.println("[CONVITE] Você foi convidado para o grupo '" + parts[0].trim() + "' por " + parts[1].trim() + ".");
                System.out.println("Digite 'ACCEPT_INVITE " + parts[0].trim() + "' ou 'DECLINE_INVITE " + parts[0].trim() + "'.");
            }
        } else if (response.startsWith(Protocol.JOIN_GROUP_REQUEST_NOTIFICATION)) {
            String requestInfo = response.substring(Protocol.JOIN_GROUP_REQUEST_NOTIFICATION.length());
            String[] parts = requestInfo.split(",");
            if (parts.length == 2) {
                System.out.println("[SOLICITAÇÃO] O usuário '" + parts[0].trim() + "' solicitou entrada no grupo '" + parts[1].trim() + "'.");
                System.out.println("Digite 'ACCEPT_JOIN " + parts[0].trim() + "," + parts[1].trim() + "' ou 'DECLINE_JOIN " + parts[0].trim() + "," + parts[1].trim() + "'.");
            }
        } else if (response.startsWith(Protocol.CHAT_REQUEST_NOTIFICATION)) {
            String senderLogin = response.substring(Protocol.CHAT_REQUEST_NOTIFICATION.length()).trim();
            System.out.println("[SOLICITAÇÃO DE CHAT PRIVADO] O usuário '" + senderLogin + "' deseja iniciar uma conversa com você.");
            System.out.println("Digite 'ACCEPT_CHAT_REQUEST " + senderLogin + "' ou 'DECLINE_CHAT_REQUEST " + senderLogin + "'.");
        } else if (response.startsWith(Protocol.CHAT_REQUEST_GROUP_NOTIFICATION)) {
            String requestInfo = response.substring(Protocol.CHAT_REQUEST_GROUP_NOTIFICATION.length()).trim();
            String[] parts = requestInfo.split(",", 2);
            if (parts.length == 2) {
                String senderLogin = parts[0].trim();
                String groupName = parts[1].trim();
                System.out.println("[SOLICITAÇÃO DE CHAT DE GRUPO - " + groupName + "] O usuário '" + senderLogin + "' deseja iniciar uma conversa com você.");
                System.out.println("Digite 'ACCEPT_CHAT_REQUEST " + senderLogin + "' ou 'DECLINE_CHAT_REQUEST " + senderLogin + "'.");
            }
        } else if (response.startsWith(Protocol.CHAT_REQUEST_ACCEPTED)) {
            String targetLogin = response.substring(Protocol.CHAT_REQUEST_ACCEPTED.length()).trim();
            System.out.println("[CHAT ACEITO] Sua solicitação de conversa com '" + targetLogin + "' foi aceita! Você pode enviar mensagens agora.");
        } else if (response.startsWith(Protocol.CHAT_REQUEST_DECLINED)) {
            String targetLogin = response.substring(Protocol.CHAT_REQUEST_DECLINED.length()).trim();
            System.out.println("[CHAT RECUSADO] Sua solicitação de conversa com '" + targetLogin + "' foi recusada.");
        }
        else if (response.equals(Protocol.LOGOUT_SUCCESS)) {
            System.out.println("Você foi desconectado. Encerrando cliente.");
            client.setLoggedInUser(null);
            Thread.currentThread().interrupt();
        } else {
            System.out.println("Servidor: " + response);
        }
    }
}