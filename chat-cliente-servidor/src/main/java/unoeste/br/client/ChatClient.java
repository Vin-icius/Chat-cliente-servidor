package unoeste.br.client;

import unoeste.br.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {

    private static String SERVER_ADDRESS; // Endereço do servidor
    private static int SERVER_PORT;             // Porta do servidor

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner consoleScanner;
    private String loggedInUser = null; // Para manter o controle do usuário logado

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.startClient();
    }

    public void startClient() {
        consoleScanner = new Scanner(System.in); // Inicializa o Scanner aqui para usá-lo antes da conexão

        // Solicita o endereço do servidor ao usuário
        System.out.print("Digite o endereço do servidor (ex: localhost): ");
        SERVER_ADDRESS = consoleScanner.nextLine();

        // Solicita a porta do servidor ao usuário
        while (true) {
            System.out.print("Digite a porta do servidor (ex: 12345): ");
            String portInput = consoleScanner.nextLine();
            try {
                SERVER_PORT = Integer.parseInt(portInput);
                if (SERVER_PORT > 0 && SERVER_PORT <= 65535) {
                    break;
                } else {
                    System.err.println("Porta inválida. Deve ser um número entre 1 e 65535.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Entrada inválida. Por favor, digite um número para a porta.");
            }
        }

        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // consoleScanner já foi inicializado

            System.out.println("Conectado ao servidor de chat em " + SERVER_ADDRESS + ":" + SERVER_PORT);
            System.out.println("Digite 'help' para ver os comandos disponíveis.");

            // Inicia a thread para receber mensagens do servidor
            MessageReceiver messageReceiver = new MessageReceiver(in, this);
            Thread receiverThread = new Thread(messageReceiver);
            receiverThread.start();

            // Loop principal para enviar comandos do usuário
            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = consoleScanner.nextLine();

                if (userInput.equalsIgnoreCase("help")) {
                    displayHelp();
                    continue;
                }

                if (userInput.equalsIgnoreCase(Protocol.LOGOUT)) {
                    out.println(userInput);
                    try {
                        receiverThread.join(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Interrupção ao aguardar thread de recebimento.");
                    }
                    break;
                }

                out.println(userInput);
            }

        } catch (IOException e) {
            System.err.println("Erro ao conectar ou comunicar com o servidor: " + e.getMessage());
        } finally {
            try {
                if (consoleScanner != null) consoleScanner.close();
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                System.out.println("Cliente encerrado.");
            } catch (IOException e) {
                System.err.println("Erro ao fechar recursos do cliente: " + e.getMessage());
            }
        }
    }

    public void setLoggedInUser(String username) {
        this.loggedInUser = username;
    }

    public String getLoggedInUser() {
        return loggedInUser;
    }

    // Método para ser chamado pela thread de recebimento para fechar o cliente
    public void closeClientResources() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar recursos do cliente: " + e.getMessage());
        }
    }

    private void displayHelp() {
        System.out.println("\n--- Comandos Disponíveis ---");
        System.out.println("  " + Protocol.REGISTER + " <nome_completo>,<login>,<email>,<senha>");
        System.out.println("  " + Protocol.LOGIN + " <login>,<senha>");
        System.out.println("  " + Protocol.RECOVER_PASSWORD + " <email>");
        System.out.println("  " + Protocol.SET_STATUS + " <ONLINE|OFFLINE|BUSY|AWAY>");
        System.out.println("  " + Protocol.LIST_ONLINE_USERS + " (lista usuários ONLINE)");
        System.out.println("  " + Protocol.LIST_BUSY_USERS + " (lista usuários BUSY)");
        System.out.println("  " + Protocol.LIST_AWAY_USERS + " (lista usuários AWAY)");
        System.out.println("  " + Protocol.LIST_GROUPS);
        System.out.println("  " + Protocol.CREATE_GROUP + " <nome_do_grupo>");
        System.out.println("  " + Protocol.ADD_GROUP_MEMBER + " <nome_do_grupo>,<login_do_usuario>");
        System.out.println("  " + Protocol.ACCEPT_GROUP_INVITE + " <nome_do_grupo>");
        System.out.println("  " + Protocol.DECLINE_GROUP_INVITE + " <nome_do_grupo>");
        System.out.println("  " + Protocol.JOIN_GROUP_REQUEST + " <nome_do_grupo>");
        System.out.println("  " + Protocol.ACCEPT_JOIN_REQUEST + " <login_usuario_solicitante>,<nome_grupo>");
        System.out.println("  " + Protocol.DECLINE_JOIN_REQUEST + " <login_usuario_solicitante>,<nome_grupo>");
        System.out.println("  " + Protocol.LEAVE_GROUP + " <nome_do_grupo>");
        System.out.println("  " + Protocol.SEND_MESSAGE + " <destino>:<conteudo>");
        System.out.println("      Destino pode ser: ");
        System.out.println("          - <login_usuario> (mensagem privada)");
        System.out.println("          - <login1>,<login2> (mensagem privada para múltiplos)");
        System.out.println("          - @<nome_grupo> (mensagem para todos no grupo)");
        System.out.println("          - @<nome_grupo>@<login_usuario> (mensagem privada dentro do grupo)");
        System.out.println("          - @<nome_grupo>@<login1>,<login2> (mensagem privada para múltiplos dentro do grupo)");
        System.out.println("  " + Protocol.REQUEST_CHAT + " <login_do_usuario> (para BUSY users em chat privado)");
        System.out.println("  " + Protocol.ACCEPT_CHAT_REQUEST + " <login_do_remetente>");
        System.out.println("  " + Protocol.DECLINE_CHAT_REQUEST + " <login_do_remetente>");
        System.out.println("  " + Protocol.LOGOUT);
        System.out.println("  help (para exibir esta lista novamente)");
        System.out.println("---------------------------\n");
    }
}