package unoeste.br.server;

import unoeste.br.common.Protocol;
import unoeste.br.common.entities.Status;
import unoeste.br.server.models.Group;
import unoeste.br.server.models.Message;
import unoeste.br.server.models.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private User currentUser;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    private boolean isAuthenticatedAndOnline() {
        return currentUser != null;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Recebido de " + (isAuthenticatedAndOnline() ? currentUser.getLogin() : clientSocket.getInetAddress().getHostAddress()) + ": " + inputLine);
                processCommand(inputLine);
                if (inputLine.equals(Protocol.LOGOUT) && !isAuthenticatedAndOnline()) {
                    break;
                }
            }
        } catch (IOException e) {
            String userIdentifier = isAuthenticatedAndOnline() ? currentUser.getLogin() : clientSocket.getInetAddress().getHostAddress();
            System.err.println("Erro de E/S para o cliente " + userIdentifier + ": " + e.getMessage());
        } finally {
            System.out.println("Finalizando conexão para " + (currentUser != null ? currentUser.getLogin() : "cliente desconhecido"));
            try {
                if (currentUser != null) {
                    DatabaseManager.updateUserStatus(currentUser.getId(), Status.OFFLINE);
                    Server.removeOnlineUser(currentUser);
                    List<Group> userGroups = DatabaseManager.getUserGroups(currentUser.getId());
                    for (Group group : userGroups) {
                        if (DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
                            notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + currentUser.getLogin() + " saiu do grupo " + group.getName() + ".");
                        }
                    }
                    DatabaseManager.removeAllChatRequestsForReceiver(currentUser.getId());
                }
            } catch (SQLException ex) {
                System.err.println("Erro SQL ao finalizar recursos do cliente " + (currentUser != null ? currentUser.getLogin() : "") + ": " + ex.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                } catch (IOException ex) {
                    System.err.println("Erro ao fechar streams/socket do cliente: " + ex.getMessage());
                }
            }
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        try {
            if (!cmd.equals(Protocol.LOGIN) && !cmd.equals(Protocol.REGISTER) && !cmd.equals(Protocol.RECOVER_PASSWORD)) {
                if (!isAuthenticatedAndOnline()) {
                    sendMessage(Protocol.NOT_AUTHORIZED + "Você precisa estar logado para realizar esta ação.");
                    return;
                }
                User userFromDb = DatabaseManager.getUserById(currentUser.getId());
                if (userFromDb == null || userFromDb.getStatus() == Status.OFFLINE) {
                    handleLogoutSilent();
                    sendMessage(Protocol.NOT_AUTHORIZED + "Seu status está OFFLINE. Faça login novamente para prosseguir.");
                    return;
                }
                this.currentUser = userFromDb;
            }

            switch (cmd) {
                case Protocol.REGISTER:
                    handleRegister(args);
                    break;
                case Protocol.LOGIN:
                    handleLogin(args);
                    break;
                case Protocol.RECOVER_PASSWORD:
                    handleRecoverPassword(args);
                    break;
                case Protocol.SET_STATUS:
                    handleSetStatus(args);
                    break;
                case Protocol.LIST_ONLINE_USERS:
                    handleListUsersByStatus(Status.ONLINE);
                    break;
                case Protocol.LIST_BUSY_USERS:
                    handleListUsersByStatus(Status.BUSY);
                    break;
                case Protocol.LIST_AWAY_USERS:
                    handleListUsersByStatus(Status.AWAY);
                    break;
                case Protocol.LIST_GROUPS:
                    handleListGroups();
                    break;
                case Protocol.CREATE_GROUP:
                    handleCreateGroup(args);
                    break;
                case Protocol.ADD_GROUP_MEMBER:
                    handleAddGroupMember(args);
                    break;
                case Protocol.ACCEPT_GROUP_INVITE:
                    handleAcceptGroupInvite(args);
                    break;
                case Protocol.DECLINE_GROUP_INVITE:
                    handleDeclineGroupInvite(args);
                    break;
                case Protocol.JOIN_GROUP_REQUEST:
                    handleJoinGroupRequest(args);
                    break;
                case Protocol.ACCEPT_JOIN_REQUEST:
                    handleAcceptJoinRequest(args);
                    break;
                case Protocol.DECLINE_JOIN_REQUEST:
                    handleDeclineJoinRequest(args);
                    break;
                case Protocol.LEAVE_GROUP:
                    handleLeaveGroup(args);
                    break;
                case Protocol.SEND_MESSAGE:
                    handleSendMessage(args);
                    break;
                case Protocol.REQUEST_CHAT:
                    handleRequestChat(args);
                    break;
                case Protocol.ACCEPT_CHAT_REQUEST:
                    handleAcceptChatRequest(args);
                    break;
                case Protocol.DECLINE_CHAT_REQUEST:
                    handleDeclineChatRequest(args);
                    break;
                case Protocol.LOGOUT:
                    handleLogout();
                    break;
                default:
                    sendMessage(Protocol.ERROR + "Comando desconhecido ou inválido.");
                    break;
            }
        } catch (SQLException e) {
            sendMessage(Protocol.ERROR + "Erro no banco de dados: " + e.getMessage());
            System.err.println("Erro SQL no ClientHandler para " + (currentUser != null ? currentUser.getLogin() : "desconhecido") + ": " + e.getMessage());
        }
    }

    private void handleRegister(String args) throws SQLException {
        String[] userDetails = args.split(",");
        if (userDetails.length != 4) {
            sendMessage(Protocol.ERROR + "Uso: REGISTER <nome_completo>,<login>,<email>,<senha>");
            return;
        }
        String fullName = userDetails[0].trim();
        String login = userDetails[1].trim();
        String email = userDetails[2].trim();
        String password = userDetails[3].trim();

        if (DatabaseManager.getUserByLogin(login) != null) {
            sendMessage(Protocol.ERROR + "Login já existe.");
            return;
        }
        if (DatabaseManager.getUserByEmail(email) != null) {
            sendMessage(Protocol.ERROR + "Email já cadastrado.");
            return;
        }

        User newUser = new User(0, fullName, login, email, password, Status.OFFLINE);
        DatabaseManager.registerUser(newUser);
        sendMessage(Protocol.SUCCESS + "Usuário " + login + " registrado com sucesso! Agora você pode fazer login.");
    }

    private void handleLogin(String args) throws SQLException {
        if (isAuthenticatedAndOnline()) {
            sendMessage(Protocol.ERROR + "Você já está logado como " + currentUser.getLogin() + ".");
            return;
        }

        String[] credentials = args.split(",");
        if (credentials.length != 2) {
            sendMessage(Protocol.ERROR + "Uso: LOGIN <login>,<senha>");
            return;
        }
        String login = credentials[0].trim();
        String password = credentials[1].trim();

        User user = DatabaseManager.getUserByLogin(login);
        if (user == null || !user.getPassword().equals(password)) {
            sendMessage(Protocol.ERROR + "Login ou senha incorretos.");
            return;
        }

        if (Server.isUserReallyOnline(user.getLogin())) {
            sendMessage(Protocol.ERROR + "Usuário " + user.getLogin() + " já está online em outra sessão.");
            return;
        }

        this.currentUser = user;
        DatabaseManager.updateUserStatus(user.getId(), Status.ONLINE);
        Server.addOnlineUser(user, this);
        sendMessage(Protocol.LOGIN_SUCCESS + user.getLogin());
        sendPendingMessages(user.getId());
        DatabaseManager.removeAllChatRequestsForReceiver(user.getId());
    }

    private void sendPendingMessages(int userId) throws SQLException {
        List<Message> pending = DatabaseManager.getPendingMessages(userId);
        if (!pending.isEmpty()) {
            sendMessage(Protocol.SERVER_MSG + "Você tem mensagens pendentes:");
            for (Message msg : pending) {
                User sender = DatabaseManager.getUserById(msg.getSenderId());
                if (sender != null) {
                    sendMessage(Protocol.PRIVATE_MESSAGE + sender.getLogin() + ": " + msg.getContent());
                } else {
                    sendMessage(Protocol.PRIVATE_MESSAGE + "Desconhecido: " + msg.getContent());
                }
                DatabaseManager.markMessageAsReceived(msg.getId());
            }
        }
    }


    private void handleRecoverPassword(String args) throws SQLException {
        String email = args.trim();
        User user = DatabaseManager.getUserByEmail(email);
        if (user == null) {
            sendMessage(Protocol.ERROR + "Nenhum usuário encontrado com este email.");
            return;
        }
        sendMessage(Protocol.RECOVER_PASSWORD_SUCCESS + user.getPassword());
    }

    private void handleSetStatus(String args) throws SQLException {
        try {
            Status newStatus = Status.valueOf(args.trim().toUpperCase());
            DatabaseManager.updateUserStatus(currentUser.getId(), newStatus);
            currentUser.setStatus(newStatus);

            if (newStatus == Status.OFFLINE) {
                Server.removeOnlineUser(currentUser);
                sendMessage(Protocol.SUCCESS + "Seu status foi atualizado para " + newStatus.name() + ". Você está offline e não poderá realizar ações.");
                DatabaseManager.removeAllChatRequestsForReceiver(currentUser.getId());
                DatabaseManager.removeAllChatRequestsForSender(currentUser.getId());
            } else {
                Server.addOnlineUser(currentUser, this);
                sendMessage(Protocol.SUCCESS + "Seu status foi atualizado para " + newStatus.name() + ".");
                if (newStatus == Status.ONLINE) {
                    sendPendingMessages(currentUser.getId());
                }
            }
        } catch (IllegalArgumentException e) {
            sendMessage(Protocol.ERROR + "Status inválido. Opções: ONLINE, OFFLINE, BUSY, AWAY.");
        }
    }

    private void handleListUsersByStatus(Status status) throws SQLException {
        List<User> users = DatabaseManager.getAllUsersByStatus(status);
        if (users.isEmpty()) {
            sendMessage(Protocol.INFO + "Nenhum usuário com status " + status.name() + ".");
        } else {
            String usersList = users.stream()
                    .map(User::getLogin)
                    .collect(Collectors.joining(", "));
            switch (status) {
                case ONLINE:
                    sendMessage(Protocol.LIST_ONLINE_USERS_RESPONSE + usersList);
                    break;
                case BUSY:
                    sendMessage(Protocol.LIST_BUSY_USERS_RESPONSE + usersList);
                    break;
                case AWAY:
                    sendMessage(Protocol.LIST_AWAY_USERS_RESPONSE + usersList);
                    break;
                default:
                    sendMessage(Protocol.INFO + "Usuários com status " + status.name() + ": " + usersList);
                    break;
            }
        }
    }


    private void handleListGroups() throws SQLException {
        List<Group> groups = DatabaseManager.getAllGroups();
        if (groups.isEmpty()) {
            sendMessage(Protocol.LIST_GROUPS_RESPONSE + "Nenhum grupo existente.");
        } else {
            String groupNames = groups.stream()
                    .map(Group::getName)
                    .collect(Collectors.joining(", "));
            sendMessage(Protocol.LIST_GROUPS_RESPONSE + groupNames);
        }
    }

    private void handleCreateGroup(String args) throws SQLException {
        String groupName = args.trim();
        if (groupName.isEmpty()) {
            sendMessage(Protocol.ERROR + "Nome do grupo não pode ser vazio.");
            return;
        }
        if (DatabaseManager.getGroupByName(groupName) != null) {
            sendMessage(Protocol.ERROR + "Já existe um grupo com este nome.");
            return;
        }
        DatabaseManager.createGroup(groupName, currentUser.getId());
        sendMessage(Protocol.SUCCESS + "Grupo '" + groupName + "' criado com sucesso!");
    }

    private void handleAddGroupMember(String args) throws SQLException {
        String[] parts = args.split(",", 2);
        if (parts.length != 2) {
            sendMessage(Protocol.ERROR + "Uso: ADD_MEMBER <nome_grupo>,<login_usuario>");
            return;
        }
        String groupName = parts[0].trim();
        String userLogin = parts[1].trim();

        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (group.getCreatorId() != currentUser.getId()) {
            sendMessage(Protocol.ERROR + "Você não é o criador deste grupo.");
            return;
        }
        User targetUser = DatabaseManager.getUserByLogin(userLogin);
        if (targetUser == null) {
            sendMessage(Protocol.ERROR + "Usuário '" + userLogin + "' não encontrado.");
            return;
        }
        if (DatabaseManager.isGroupMember(group.getId(), targetUser.getId())) {
            sendMessage(Protocol.ERROR + "Usuário '" + userLogin + "' já é membro deste grupo.");
            return;
        }

        DatabaseManager.addGroupMember(group.getId(), targetUser.getId(), false);
        sendMessage(Protocol.SUCCESS + "Convite enviado para " + userLogin + " entrar no grupo " + groupName + ".");

        ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
        if (targetHandler != null) {
            targetHandler.sendMessage(Protocol.GROUP_INVITE + group.getName() + "," + currentUser.getLogin());
        } else {
        }
    }

    private void handleAcceptGroupInvite(String args) throws SQLException {
        String groupName = args.trim();
        if (groupName.isEmpty()) {
            sendMessage(Protocol.ERROR + "Nome do grupo não pode ser vazio.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (!DatabaseManager.isGroupMemberPending(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você não tem um convite pendente para este grupo.");
            return;
        }
        DatabaseManager.updateGroupMemberAcceptance(group.getId(), currentUser.getId(), true);
        sendMessage(Protocol.SUCCESS + "Você aceitou o convite para o grupo '" + groupName + "'.");
        notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + currentUser.getLogin() + " entrou no grupo.");
    }

    private void handleDeclineGroupInvite(String args) throws SQLException {
        String groupName = args.trim();
        if (groupName.isEmpty()) {
            sendMessage(Protocol.ERROR + "Nome do grupo não pode ser vazio.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (!DatabaseManager.isGroupMemberPending(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você não tem um convite pendente para este grupo.");
            return;
        }
        DatabaseManager.removeGroupMember(group.getId(), currentUser.getId());
        sendMessage(Protocol.SUCCESS + "Você recusou o convite para o grupo '" + groupName + "'.");
    }

    private void handleJoinGroupRequest(String args) throws SQLException {
        String groupName = args.trim();
        if (groupName.isEmpty()) {
            sendMessage(Protocol.ERROR + "Nome do grupo não pode ser vazio.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você já é membro deste grupo.");
            return;
        }
        if (DatabaseManager.isGroupMemberPending(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você já tem uma solicitação pendente para este grupo.");
            return;
        }

        DatabaseManager.addGroupMember(group.getId(), currentUser.getId(), false);
        sendMessage(Protocol.SUCCESS + "Sua solicitação para entrar no grupo '" + groupName + "' foi enviada aos membros.");

        List<User> members = DatabaseManager.getGroupMembers(group.getId(), true);
        for (User member : members) {
            ClientHandler memberHandler = Server.getClientHandlerById(member.getId());
            if (memberHandler != null) {
                memberHandler.sendMessage(Protocol.JOIN_GROUP_REQUEST_NOTIFICATION + currentUser.getLogin() + "," + group.getName());
            }
        }
    }

    private void handleAcceptJoinRequest(String args) throws SQLException {
        String[] parts = args.split(",", 2);
        if (parts.length != 2) {
            sendMessage(Protocol.ERROR + "Uso: ACCEPT_JOIN <login_usuario_solicitante>,<nome_grupo>");
            return;
        }
        String requestingUserLogin = parts[0].trim();
        String groupName = parts[1].trim();

        User requestingUser = DatabaseManager.getUserByLogin(requestingUserLogin);
        if (requestingUser == null) {
            sendMessage(Protocol.ERROR + "Usuário solicitante '" + requestingUserLogin + "' não encontrado.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (!DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você não é membro deste grupo.");
            return;
        }
        if (!DatabaseManager.isGroupMemberPending(group.getId(), requestingUser.getId())) {
            sendMessage(Protocol.ERROR + "O usuário '" + requestingUserLogin + "' não tem uma solicitação pendente para este grupo.");
            return;
        }

        DatabaseManager.recordJoinRequestApproval(group.getId(), requestingUser.getId(), currentUser.getId());

        List<User> currentMembers = DatabaseManager.getGroupMembers(group.getId(), true);
        int approvalsCount = DatabaseManager.getNumberOfApprovalsForJoinRequest(group.getId(), requestingUser.getId());
        boolean allAccepted = (approvalsCount == currentMembers.size());

        if (allAccepted) {
            DatabaseManager.updateGroupMemberAcceptance(group.getId(), requestingUser.getId(), true);
            DatabaseManager.clearJoinRequestApprovals(group.getId(), requestingUser.getId());
            sendMessage(Protocol.SUCCESS + "Solicitação de " + requestingUserLogin + " para o grupo " + groupName + " aprovada.");
            ClientHandler requestingHandler = Server.getClientHandlerById(requestingUser.getId());
            if (requestingHandler != null) {
                requestingHandler.sendMessage(Protocol.SERVER_MSG + "Sua solicitação para entrar no grupo " + groupName + " foi aceita!");
            }
            notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + requestingUserLogin + " entrou no grupo " + groupName + ".");
        } else {
            sendMessage(Protocol.INFO + "Sua aprovação para " + requestingUserLogin + " entrar no grupo " + groupName + " foi registrada. Aguardando os demais membros (" + approvalsCount + "/" + currentMembers.size() + ").");
        }
    }

    private void handleDeclineJoinRequest(String args) throws SQLException {
        String[] parts = args.split(",", 2);
        if (parts.length != 2) {
            sendMessage(Protocol.ERROR + "Uso: DECLINE_JOIN <login_usuario_solicitante>,<nome_grupo>");
            return;
        }
        String requestingUserLogin = parts[0].trim();
        String groupName = parts[1].trim();

        User requestingUser = DatabaseManager.getUserByLogin(requestingUserLogin);
        if (requestingUser == null) {
            sendMessage(Protocol.ERROR + "Usuário solicitante '" + requestingUserLogin + "' não encontrado.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (!DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você não é membro deste grupo.");
            return;
        }
        if (!DatabaseManager.isGroupMemberPending(group.getId(), requestingUser.getId())) {
            sendMessage(Protocol.ERROR + "O usuário '" + requestingUserLogin + "' não tem uma solicitação pendente para este grupo.");
            return;
        }

        DatabaseManager.removeGroupMember(group.getId(), requestingUser.getId());
        DatabaseManager.clearJoinRequestApprovals(group.getId(), requestingUser.getId());
        sendMessage(Protocol.SUCCESS + "Solicitação de " + requestingUserLogin + " para o grupo " + groupName + " recusada.");

        ClientHandler requestingHandler = Server.getClientHandlerById(requestingUser.getId());
        if (requestingHandler != null) {
            requestingHandler.sendMessage(Protocol.SERVER_MSG + "Sua solicitação para entrar no grupo " + groupName + " foi negada.");
        }
        notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + requestingUserLogin + " teve sua solicitação para entrar no grupo " + groupName + " negada.");
    }


    private void handleLeaveGroup(String args) throws SQLException {
        String groupName = args.trim();
        if (groupName.isEmpty()) {
            sendMessage(Protocol.ERROR + "Nome do grupo não pode ser vazio.");
            return;
        }
        Group group = DatabaseManager.getGroupByName(groupName);
        if (group == null) {
            sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
            return;
        }
        if (!DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
            sendMessage(Protocol.ERROR + "Você não é membro deste grupo.");
            return;
        }

        DatabaseManager.removeGroupMember(group.getId(), currentUser.getId());
        sendMessage(Protocol.SUCCESS + "Você saiu do grupo '" + groupName + "'.");
        notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + currentUser.getLogin() + " saiu do grupo " + groupName + ".");
    }

    private void handleSendMessage(String args) throws SQLException {
        int colonIndex = args.indexOf(":");
        if (colonIndex == -1) {
            sendMessage(Protocol.ERROR + "Formato de mensagem inválido. Uso: MSG <destino>:<conteudo>");
            return;
        }
        String destination = args.substring(0, colonIndex).trim();
        String content = args.substring(colonIndex + 1).trim();

        if (content.isEmpty()) {
            sendMessage(Protocol.ERROR + "Mensagem não pode ser vazia.");
            return;
        }

        String formattedMessage = currentUser.getLogin() + " (" + DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now()) + "): " + content;

        if (destination.startsWith("@")) {
            handleGroupMessage(destination, content, formattedMessage);
        } else {
            handlePrivateMessage(destination, content, formattedMessage);
        }
    }

    private void handleGroupMessage(String destination, String rawContent, String formattedMessage) throws SQLException {
        String groupPart = destination.substring(1);
        int atIndex = groupPart.indexOf("@");

        if (atIndex == -1) {
            String groupName = groupPart;
            Group group = DatabaseManager.getGroupByName(groupName);
            if (group == null) {
                sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
                return;
            }
            if (!DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
                sendMessage(Protocol.ERROR + "Você não é membro do grupo '" + groupName + "'.");
                return;
            }

            List<User> members = DatabaseManager.getGroupMembers(group.getId(), true);
            for (User member : members) {
                if (member.getId() != currentUser.getId()) {
                    User memberCurrentStatus = DatabaseManager.getUserById(member.getId());
                    if (memberCurrentStatus == null || memberCurrentStatus.getStatus() == Status.OFFLINE) {
                        sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Mensagem para " + member.getLogin() + " (no grupo " + groupName + ") não enviada: usuário offline.");
                        continue;
                    }

                    ClientHandler memberHandler = Server.getClientHandlerById(member.getId());
                    if (memberHandler != null) {
                        if (memberCurrentStatus.getStatus() == Status.BUSY) {
                            String chatRequestStatus = DatabaseManager.getChatRequestStatus(currentUser.getId(), member.getId());
                            if (chatRequestStatus != null && chatRequestStatus.equals("ACCEPTED")) {
                                memberHandler.sendMessage(Protocol.GROUP_MESSAGE + groupName + "@" + formattedMessage);
                                DatabaseManager.saveMessage(currentUser.getId(), member.getId(), group.getId(), rawContent, true);
                            } else if (chatRequestStatus != null && chatRequestStatus.equals("PENDING")) {
                                sendMessage(Protocol.INFO + "Já existe uma solicitação de conversa pendente para " + member.getLogin() + " no grupo " + groupName + ". Aguarde a aceitação.");
                            } else {
                                DatabaseManager.addChatRequest(currentUser.getId(), member.getId());
                                memberHandler.sendMessage(Protocol.CHAT_REQUEST_GROUP_NOTIFICATION + currentUser.getLogin() + "," + group.getName());
                                sendMessage(Protocol.INFO + "Solicitação de conversa enviada para " + member.getLogin() + " no grupo " + groupName + ". Aguardando aceitação.");
                            }
                        } else {
                            memberHandler.sendMessage(Protocol.GROUP_MESSAGE + groupName + "@" + formattedMessage);
                            DatabaseManager.saveMessage(currentUser.getId(), member.getId(), group.getId(), rawContent, true);
                        }
                    } else {
                        sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Mensagem para " + member.getLogin() + " (no grupo " + groupName + ") não enviada: usuário não está mais online.");
                    }
                }
            }
            sendMessage(Protocol.MESSAGE_SENT + "Mensagem enviada para o grupo '" + groupName + "'.");

        } else {
            String groupName = groupPart.substring(0, atIndex);
            String targetLoginsString = groupPart.substring(atIndex + 1);
            String[] targetLogins = targetLoginsString.split(",");

            Group group = DatabaseManager.getGroupByName(groupName);
            if (group == null) {
                sendMessage(Protocol.ERROR + "Grupo '" + groupName + "' não encontrado.");
                return;
            }
            if (!DatabaseManager.isGroupMember(group.getId(), currentUser.getId())) {
                sendMessage(Protocol.ERROR + "Você não é membro do grupo '" + groupName + "'.");
                return;
            }

            for (String targetLogin : targetLogins) {
                targetLogin = targetLogin.trim();
                User targetUser = DatabaseManager.getUserByLogin(targetLogin);
                if (targetUser == null) {
                    sendMessage(Protocol.ERROR + "Usuário '" + targetLogin + "' não encontrado no grupo '" + groupName + "'.");
                    continue;
                }
                if (!DatabaseManager.isGroupMember(group.getId(), targetUser.getId())) {
                    sendMessage(Protocol.ERROR + "Usuário '" + targetLogin + "' não é membro do grupo '" + groupName + "'.");
                    continue;
                }
                if (targetUser.getId() == currentUser.getId()) {
                    sendMessage(Protocol.ERROR + "Você não pode enviar mensagem para si mesmo.");
                    continue;
                }

                User targetUserCurrentStatus = DatabaseManager.getUserById(targetUser.getId());
                if (targetUserCurrentStatus == null || targetUserCurrentStatus.getStatus() == Status.OFFLINE) {
                    sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Mensagem para " + targetLogin + " (no grupo " + groupName + ") não enviada: usuário offline.");
                    continue;
                }

                ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
                if (targetHandler != null) {
                    if (targetUserCurrentStatus.getStatus() == Status.BUSY) {
                        String chatRequestStatus = DatabaseManager.getChatRequestStatus(currentUser.getId(), targetUser.getId());
                        if (chatRequestStatus != null && chatRequestStatus.equals("ACCEPTED")) {
                            targetHandler.sendMessage(Protocol.GROUP_PRIVATE_MESSAGE + groupName + "@" + currentUser.getLogin() + ": " + rawContent);
                            DatabaseManager.saveMessage(currentUser.getId(), targetUser.getId(), group.getId(), rawContent, true);
                        } else if (chatRequestStatus != null && chatRequestStatus.equals("PENDING")) {
                            sendMessage(Protocol.INFO + "Já existe uma solicitação de conversa pendente para " + targetLogin + " no grupo " + groupName + ". Aguarde a aceitação.");
                        } else {
                            DatabaseManager.addChatRequest(currentUser.getId(), targetUser.getId());
                            targetHandler.sendMessage(Protocol.CHAT_REQUEST_GROUP_NOTIFICATION + currentUser.getLogin() + "," + group.getName());
                            sendMessage(Protocol.INFO + "Solicitação de conversa enviada para " + targetLogin + " no grupo " + groupName + ". Aguardando aceitação.");
                        }
                    } else {
                        targetHandler.sendMessage(Protocol.GROUP_PRIVATE_MESSAGE + groupName + "@" + currentUser.getLogin() + ": " + rawContent);
                        DatabaseManager.saveMessage(currentUser.getId(), targetUser.getId(), group.getId(), rawContent, true);
                    }
                    sendMessage(Protocol.MESSAGE_SENT + "Mensagem enviada para " + targetLogin + " no grupo " + groupName + ".");
                } else {
                    sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Mensagem para " + targetLogin + " (no grupo " + groupName + ") não enviada: usuário não está mais online.");
                }
            }
        }
    }

    private void handlePrivateMessage(String destination, String rawContent, String formattedMessage) throws SQLException {
        String[] targetLogins = destination.split(",");
        for (String targetLogin : targetLogins) {
            targetLogin = targetLogin.trim();
            User targetUser = DatabaseManager.getUserByLogin(targetLogin);

            if (targetUser == null) {
                sendMessage(Protocol.ERROR + "Usuário '" + targetLogin + "' não encontrado.");
                continue;
            }
            if (targetUser.getId() == currentUser.getId()) {
                sendMessage(Protocol.ERROR + "Você não pode enviar mensagem para si mesmo.");
                continue;
            }

            User targetUserCurrentStatus = DatabaseManager.getUserById(targetUser.getId());
            if (targetUserCurrentStatus == null || targetUserCurrentStatus.getStatus() == Status.OFFLINE) {
                sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Mensagem para " + targetLogin + " não enviada: usuário está offline.");
                continue;
            }

            if (targetUserCurrentStatus.getStatus() == Status.BUSY) {
                String chatRequestStatus = DatabaseManager.getChatRequestStatus(currentUser.getId(), targetUser.getId());
                if (chatRequestStatus != null && chatRequestStatus.equals("ACCEPTED")) {
                    ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
                    if (targetHandler != null) {
                        targetHandler.sendMessage(Protocol.PRIVATE_MESSAGE + formattedMessage);
                        DatabaseManager.saveMessage(currentUser.getId(), targetUser.getId(), null, rawContent, true);
                        sendMessage(Protocol.MESSAGE_SENT + "Mensagem enviada para " + targetLogin + ".");
                    } else {
                        sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Usuário " + targetLogin + " está online/busy, mas não foi possível entregar a mensagem.");
                    }
                } else if (chatRequestStatus != null && chatRequestStatus.equals("PENDING")) {
                    sendMessage(Protocol.INFO + "Já existe uma solicitação de conversa pendente para " + targetLogin + ". Aguarde a aceitação.");
                } else {
                    DatabaseManager.addChatRequest(currentUser.getId(), targetUser.getId());
                    ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
                    if (targetHandler != null) {
                        targetHandler.sendMessage(Protocol.CHAT_REQUEST_NOTIFICATION + currentUser.getLogin());
                        sendMessage(Protocol.INFO + "Solicitação de conversa enviada para " + targetLogin + ". Aguardando aceitação.");
                    } else {
                        sendMessage(Protocol.ERROR + "Erro interno: usuário BUSY " + targetLogin + " não tem handler online.");
                    }
                }
                continue;
            }
            ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
            if (targetHandler != null) {
                targetHandler.sendMessage(Protocol.PRIVATE_MESSAGE + formattedMessage);
                DatabaseManager.saveMessage(currentUser.getId(), targetUser.getId(), null, rawContent, true);
                sendMessage(Protocol.MESSAGE_SENT + "Mensagem enviada para " + targetLogin + ".");
            } else {
                DatabaseManager.saveMessage(currentUser.getId(), targetUser.getId(), null, rawContent, false);
                DatabaseManager.addPendingMessage(currentUser.getId(), targetUser.getId(), rawContent);
                sendMessage(Protocol.MESSAGE_NOT_DELIVERED + "Usuário " + targetLogin + " está offline. Mensagem será entregue assim que ele se conectar.");
            }
        }
    }

    private void handleRequestChat(String args) throws SQLException {
        String targetLogin = args.trim();
        if (targetLogin.isEmpty()) {
            sendMessage(Protocol.ERROR + "Uso: REQUEST_CHAT <login_do_usuario>");
            return;
        }

        User targetUser = DatabaseManager.getUserByLogin(targetLogin);
        if (targetUser == null) {
            sendMessage(Protocol.ERROR + "Usuário '" + targetLogin + "' não encontrado.");
            return;
        }
        if (targetUser.getId() == currentUser.getId()) {
            sendMessage(Protocol.ERROR + "Você não pode solicitar conversa para si mesmo.");
            return;
        }

        User targetUserCurrentStatus = DatabaseManager.getUserById(targetUser.getId());
        if (targetUserCurrentStatus == null || targetUserCurrentStatus.getStatus() == Status.OFFLINE) {
            sendMessage(Protocol.ERROR + "Usuário " + targetLogin + " está offline.");
            return;
        }
        if (targetUserCurrentStatus.getStatus() == Status.ONLINE || targetUserCurrentStatus.getStatus() == Status.AWAY) {
            sendMessage(Protocol.INFO + "Usuário " + targetLogin + " está " + targetUserCurrentStatus.getStatus().name() + ". Você pode enviar uma mensagem diretamente.");
            return;
        }
        if (targetUserCurrentStatus.getStatus() != Status.BUSY) {
            sendMessage(Protocol.ERROR + "Solicitações de conversa são apenas para usuários com status BUSY.");
            return;
        }

        String existingStatus = DatabaseManager.getChatRequestStatus(currentUser.getId(), targetUser.getId());
        if (existingStatus != null) {
            if (existingStatus.equals("PENDING")) {
                sendMessage(Protocol.INFO + "Já existe uma solicitação de conversa pendente para " + targetLogin + ".");
            } else if (existingStatus.equals("ACCEPTED")) {
                sendMessage(Protocol.INFO + "Você já tem uma conversa aceita com " + targetLogin + ". Pode enviar mensagens diretamente.");
            }
            return;
        }

        DatabaseManager.addChatRequest(currentUser.getId(), targetUser.getId());
        ClientHandler targetHandler = Server.getClientHandlerById(targetUser.getId());
        if (targetHandler != null) {
            targetHandler.sendMessage(Protocol.CHAT_REQUEST_NOTIFICATION + currentUser.getLogin());
            sendMessage(Protocol.SUCCESS + "Solicitação de conversa enviada para " + targetLogin + ".");
        } else {
            sendMessage(Protocol.ERROR + "Usuário " + targetLogin + " está BUSY, mas não está online. Tente novamente mais tarde.");
        }
    }


    private void handleAcceptChatRequest(String args) throws SQLException {
        String senderLogin = args.trim();
        if (senderLogin.isEmpty()) {
            sendMessage(Protocol.ERROR + "Uso: ACCEPT_CHAT_REQUEST <login_do_remetente>");
            return;
        }

        User senderUser = DatabaseManager.getUserByLogin(senderLogin);
        if (senderUser == null) {
            sendMessage(Protocol.ERROR + "Usuário '" + senderLogin + "' não encontrado.");
            return;
        }
        if (currentUser.getStatus() != Status.BUSY) {
            sendMessage(Protocol.ERROR + "Seu status deve ser BUSY para aceitar solicitações de conversa.");
            return;
        }


        String requestStatus = DatabaseManager.getChatRequestStatus(senderUser.getId(), currentUser.getId());
        if (requestStatus == null || !requestStatus.equals("PENDING")) {
            sendMessage(Protocol.ERROR + "Não há solicitação de conversa pendente de " + senderLogin + " para você.");
            return;
        }

        DatabaseManager.updateChatRequestStatus(senderUser.getId(), currentUser.getId(), "ACCEPTED");
        sendMessage(Protocol.SUCCESS + "Solicitação de conversa de " + senderLogin + " aceita. Agora vocês podem conversar!");

        ClientHandler senderHandler = Server.getClientHandlerById(senderUser.getId());
        if (senderHandler != null) {
            senderHandler.sendMessage(Protocol.CHAT_REQUEST_ACCEPTED + currentUser.getLogin());
        }
    }

    private void handleDeclineChatRequest(String args) throws SQLException {
        String senderLogin = args.trim();
        if (senderLogin.isEmpty()) {
            sendMessage(Protocol.ERROR + "Uso: DECLINE_CHAT_REQUEST <login_do_remetente>");
            return;
        }

        User senderUser = DatabaseManager.getUserByLogin(senderLogin);
        if (senderUser == null) {
            sendMessage(Protocol.ERROR + "Usuário '" + senderLogin + "' não encontrado.");
            return;
        }
        if (currentUser.getStatus() != Status.BUSY) {
            sendMessage(Protocol.ERROR + "Seu status deve ser BUSY para recusar solicitações de conversa.");
            return;
        }

        String requestStatus = DatabaseManager.getChatRequestStatus(senderUser.getId(), currentUser.getId());
        if (requestStatus == null || !requestStatus.equals("PENDING")) {
            sendMessage(Protocol.ERROR + "Não há solicitação de conversa pendente de " + senderLogin + " para você.");
            return;
        }

        DatabaseManager.updateChatRequestStatus(senderUser.getId(), currentUser.getId(), "DECLINED");
        DatabaseManager.removeChatRequest(senderUser.getId(), currentUser.getId());
        sendMessage(Protocol.SUCCESS + "Solicitação de conversa de " + senderLogin + " recusada.");

        ClientHandler senderHandler = Server.getClientHandlerById(senderUser.getId());
        if (senderHandler != null) {
            senderHandler.sendMessage(Protocol.CHAT_REQUEST_DECLINED + currentUser.getLogin());
        }
    }

    private void handleLogoutSilent() throws SQLException {
        if (currentUser != null) {
            Server.removeOnlineUser(currentUser);
            DatabaseManager.removeAllChatRequestsForReceiver(currentUser.getId());
            DatabaseManager.removeAllChatRequestsForSender(currentUser.getId());
            this.currentUser = null;
        }
    }

    private void handleLogout() throws SQLException {
        if (isAuthenticatedAndOnline()) {
            sendMessage(Protocol.LOGOUT_SUCCESS);
            User tempUser = this.currentUser;
            this.currentUser = null;
            DatabaseManager.updateUserStatus(tempUser.getId(), Status.OFFLINE);
            Server.removeOnlineUser(tempUser);
            DatabaseManager.removeAllChatRequestsForReceiver(tempUser.getId());
            DatabaseManager.removeAllChatRequestsForSender(tempUser.getId());
            List<Group> userGroups = DatabaseManager.getUserGroups(tempUser.getId());
            for (Group group : userGroups) {
                if (DatabaseManager.isGroupMember(group.getId(), tempUser.getId())) {
                    notifyGroupMembers(group.getId(), Protocol.SERVER_MSG + tempUser.getLogin() + " saiu do grupo " + group.getName() + ".");
                }
            }
        } else {
            sendMessage(Protocol.ERROR + "Você não está logado.");
        }
    }


    public void sendMessage(String message) {
        synchronized (out) {
            out.println(message);
        }
    }

    private void notifyGroupMembers(int groupId, String message) throws SQLException {
        List<User> members = DatabaseManager.getGroupMembers(groupId, true);
        for (User member : members) {
            if (member.getId() != currentUser.getId()) {
                ClientHandler memberHandler = Server.getClientHandlerById(member.getId());
                if (memberHandler != null) {
                    User memberStatus = DatabaseManager.getUserById(member.getId());
                    if (memberStatus != null && memberStatus.getStatus() != Status.OFFLINE) {
                        memberHandler.sendMessage(message);
                    }
                }
            }
        }
    }
}