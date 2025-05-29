package unoeste.br.client;

import java.io.PrintWriter;
import java.util.Scanner;

public class MessageSender implements Runnable {

    private PrintWriter out;
    private Scanner consoleScanner;
    private ChatClient client;

    public MessageSender(PrintWriter out, Scanner consoleScanner, ChatClient client) {
        this.out = out;
        this.consoleScanner = consoleScanner;
        this.client = client;
    }

    @Override
    public void run() {
        String userInput;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread de envio interrompida.");
            }
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}
