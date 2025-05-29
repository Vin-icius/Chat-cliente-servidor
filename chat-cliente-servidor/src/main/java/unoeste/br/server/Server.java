package unoeste.br.server;

import unoeste.br.server.models.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 12345;
    private static final int MAX_THREADS = 100;
    private ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

    public static Map<Integer, ClientHandler> onlineUsers = Collections.synchronizedMap(new HashMap<>());
    public static Map<String, Integer> userLoginToId = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        Scanner consoleScanner = new Scanner(System.in);

        System.out.println("--- Configuração do Banco de Dados ---");
        System.out.print("Digite o nome de usuário do banco de dados (ex: root): ");
        String dbUser = consoleScanner.nextLine();

        System.out.print("Digite a senha do banco de dados (ex: root): ");
        String dbPassword = consoleScanner.nextLine();
        System.out.println("------------------------------------");


        DatabaseManager.setDatabaseCredentials(dbUser, dbPassword);

        try {
            System.out.println("Testando conexão com o banco de dados...");
            DatabaseManager.getConnection().close();
            System.out.println("Conexão com o banco de dados bem-sucedida.");
        } catch (Exception e) {
            System.err.println("Falha ao conectar ao banco de dados com as credenciais fornecidas: " + e.getMessage());
            System.err.println("Verifique as credenciais e a disponibilidade do banco de dados e tente novamente.");
            consoleScanner.close();
            return;
        }

        new Server().startServer();
        consoleScanner.close();
    }

    public void startServer() {
        System.out.println("Servidor iniciado na porta " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
        } finally {
            pool.shutdown();
            System.out.println("Servidor encerrado.");
        }
    }

    // Método para adicionar um usuário à lista de online
    public static synchronized void addOnlineUser(User user, ClientHandler handler) {
        if (user != null) {
            onlineUsers.put(user.getId(), handler);
            userLoginToId.put(user.getLogin(), user.getId());
            System.out.println("Usuário " + user.getLogin() + " agora está online. Total: " + onlineUsers.size());
        }
    }

    // Método para remover um usuário da lista de online
    public static synchronized void removeOnlineUser(User user) {
        if (user != null) {
            onlineUsers.remove(user.getId());
            userLoginToId.remove(user.getLogin());
            System.out.println("Usuário " + user.getLogin() + " desconectado. Total: " + onlineUsers.size());
        }
    }

    // Método para obter um ClientHandler pelo login do usuário
    public static synchronized ClientHandler getClientHandlerByLogin(String login) {
        Integer userId = userLoginToId.get(login);
        if (userId != null) {
            return onlineUsers.get(userId);
        }
        return null;
    }

    // Método para obter um ClientHandler pelo ID do usuário
    public static synchronized ClientHandler getClientHandlerById(int userId) {
        return onlineUsers.get(userId);
    }

    // Método para verificar se um usuário está online pelo login
    public static synchronized boolean isUserReallyOnline(String login) {
        return userLoginToId.containsKey(login);
    }
}