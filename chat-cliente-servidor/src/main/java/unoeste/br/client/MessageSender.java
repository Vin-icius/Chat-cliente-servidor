package unoeste.br.client;

import java.io.PrintWriter;
import java.util.Scanner;

// Esta classe é mais conceitual para um cliente de console.
// No ChatClient.java, o envio já é tratado no loop principal.
// Em uma GUI, esta thread seria crucial.
public class MessageSender implements Runnable {

    private PrintWriter out;
    private Scanner consoleScanner;
    private ChatClient client; // Referência ao cliente para acessar o status de login, etc.

    public MessageSender(PrintWriter out, Scanner consoleScanner, ChatClient client) {
        this.out = out;
        this.consoleScanner = consoleScanner;
        this.client = client;
    }

    @Override
    public void run() {
        String userInput;
        while (!Thread.currentThread().isInterrupted()) {
            // Em um console, o Scanner.nextLine() é bloqueante.
            // Isso significa que esta thread ficaria esperando a entrada do usuário.
            // Para um cenário de chat puro, o loop em ChatClient.java já é mais direto.
            // Esta thread seria mais valiosa se a entrada viesse de uma fila de mensagens
            // ou de uma GUI não bloqueante.
            try {
                // Não é ideal ter o scanner aqui se o ChatClient principal já usa um
                // consoleScanner para a mesma entrada.
                // O ideal é que o ChatClient passe as mensagens para uma fila que esta thread lê.
                // Por simplicidade, mantemos o loop principal do ChatClient para input.
                Thread.sleep(100); // Evitar loop de CPU intenso
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread de envio interrompida.");
            }
        }
    }

    // Método para enviar uma mensagem via esta thread (se fosse usada)
    public void sendMessage(String message) {
        out.println(message);
    }
}
