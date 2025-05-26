package unoeste.br.server;

import unoeste.br.server.models.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 12345;
    private static final int MAX_THREADS = 100; // Número máximo de threads para clientes
    private ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

    // Mapeia o ID do usuário (do DB) para o ClientHandler correspondente
    // Usamos synchronizedMap para garantir thread-safety em operações simples (get, put, remove)
    public static Map<Integer, ClientHandler> onlineUsers = Collections.synchronizedMap(new HashMap<>());

    // Mapeia o login do usuário para o ID do usuário (do DB)
    public static Map<String, Integer> userLoginToId = Collections.synchronizedMap(new HashMap<>());


    public static void main(String[] args) {
        new Server().startServer();
    }

    public void startServer() {
        System.out.println("Servidor iniciado na porta " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress().getHostAddress());
                // Cada cliente recebe um ClientHandler dedicado
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    // Método para adicionar um usuário à lista de online
    // Sincronizado para garantir que a adição seja atômica e consistente
    public static synchronized void addOnlineUser(User user, ClientHandler handler) {
        if (user != null) {
            onlineUsers.put(user.getId(), handler);
            userLoginToId.put(user.getLogin(), user.getId());
            System.out.println("Usuário " + user.getLogin() + " agora está online. Total: " + onlineUsers.size());
        }
    }

    // Método para remover um usuário da lista de online
    // Sincronizado para garantir que a remoção seja atômica e consistente
    public static synchronized void removeOnlineUser(User user) {
        if (user != null) {
            onlineUsers.remove(user.getId());
            userLoginToId.remove(user.getLogin());
            System.out.println("Usuário " + user.getLogin() + " desconectado. Total: " + onlineUsers.size());
        }
    }

    // Método para obter um ClientHandler pelo login do usuário
    // Sincronizado para garantir leitura consistente do mapa
    public static synchronized ClientHandler getClientHandlerByLogin(String login) {
        Integer userId = userLoginToId.get(login);
        if (userId != null) {
            return onlineUsers.get(userId);
        }
        return null;
    }

    // Método para obter um ClientHandler pelo ID do usuário
    // Sincronizado para garantir leitura consistente do mapa
    public static synchronized ClientHandler getClientHandlerById(int userId) {
        return onlineUsers.get(userId);
    }

    // Método para verificar se um usuário está online pelo login
    public static synchronized boolean isUserReallyOnline(String login) {
        return userLoginToId.containsKey(login);
    }
}