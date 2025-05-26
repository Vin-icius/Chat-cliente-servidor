package unoeste.br.server;

import unoeste.br.common.entities.Status;
import unoeste.br.server.models.Group;
import unoeste.br.server.models.Message;
import unoeste.br.server.models.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/chat_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root"; // Substitua
    private static final String PASS = "root";   // Substitua

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    public static void registerUser(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, login, email, password, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getFullName());
            pstmt.setString(2, user.getLogin());
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getPassword());
            pstmt.setString(5, user.getStatus().name());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public static User getUserByLogin(String login) throws SQLException {
        String sql = "SELECT * FROM users WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, login);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("login"),
                            rs.getString("email"),
                            rs.getString("password"),
                            Status.valueOf(rs.getString("status").toUpperCase())
                    );
                }
            }
        }
        return null;
    }

    public static User getUserByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("login"),
                            rs.getString("email"),
                            rs.getString("password"),
                            Status.valueOf(rs.getString("status").toUpperCase())
                    );
                }
            }
        }
        return null;
    }

    public static User getUserById(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("login"),
                            rs.getString("email"),
                            rs.getString("password"),
                            Status.valueOf(rs.getString("status").toUpperCase())
                    );
                }
            }
        }
        return null;
    }


    public static void updateUserStatus(int userId, Status status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    public static List<User> getAllUsersByStatus(Status status) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE status = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("login"),
                            rs.getString("email"),
                            rs.getString("password"),
                            Status.valueOf(rs.getString("status").toUpperCase())
                    ));
                }
            }
        }
        return users;
    }


    public static void createGroup(String groupName, int creatorId) throws SQLException {
        String sql = "INSERT INTO groups_table (name, creator_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, groupName);
            pstmt.setInt(2, creatorId);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int groupId = generatedKeys.getInt(1);
                    // O criador já é automaticamente membro
                    addGroupMember(groupId, creatorId, true);
                }
            }
        }
    }

    public static Group getGroupByName(String groupName) throws SQLException {
        String sql = "SELECT * FROM groups_table WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Group(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("creator_id")
                    );
                }
            }
        }
        return null;
    }

    public static List<Group> getAllGroups() throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM groups_table";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                groups.add(new Group(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("creator_id")
                ));
            }
        }
        return groups;
    }

    public static void addGroupMember(int groupId, int userId, boolean accepted) throws SQLException {
        String sql = "INSERT INTO group_members (group_id, user_id, accepted) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE accepted = VALUES(accepted)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            pstmt.setBoolean(3, accepted);
            pstmt.executeUpdate();
        }
    }

    public static boolean isGroupMember(int groupId, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND accepted = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static boolean isGroupMemberPending(int groupId, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND accepted = FALSE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static void updateGroupMemberAcceptance(int groupId, int userId, boolean accepted) throws SQLException {
        String sql = "UPDATE group_members SET accepted = ? WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, accepted);
            pstmt.setInt(2, groupId);
            pstmt.setInt(3, userId);
            pstmt.executeUpdate();
        }
    }

    public static void removeGroupMember(int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
        // Também remover entradas da tabela join_requests se existirem
        String deleteJoinRequestsSql = "DELETE FROM join_requests WHERE group_id = ? AND requesting_user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteJoinRequestsSql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }


    public static List<User> getGroupMembers(int groupId, boolean acceptedOnly) throws SQLException {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.* FROM users u JOIN group_members gm ON u.id = gm.user_id WHERE gm.group_id = ?";
        if (acceptedOnly) {
            sql += " AND gm.accepted = TRUE";
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    members.add(new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("login"),
                            rs.getString("email"),
                            rs.getString("password"),
                            Status.valueOf(rs.getString("status").toUpperCase())
                    ));
                }
            }
        }
        return members;
    }

    public static List<Group> getUserGroups(int userId) throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT gt.* FROM groups_table gt JOIN group_members gm ON gt.id = gm.group_id WHERE gm.user_id = ? AND gm.accepted = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    groups.add(new Group(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("creator_id")
                    ));
                }
            }
        }
        return groups;
    }


    public static void saveMessage(int senderId, int receiverId, Integer groupId, String content, boolean isRead) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, group_id, content, is_read) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            if (receiverId != 0) { // Pode ser 0 para mensagens de grupo broadcast
                pstmt.setInt(2, receiverId);
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            if (groupId != null) {
                pstmt.setInt(3, groupId);
            } else {
                pstmt.setNull(3, java.sql.Types.INTEGER);
            }
            pstmt.setString(4, content);
            pstmt.setBoolean(5, isRead);
            pstmt.executeUpdate();
        }
    }

    public static void addPendingMessage(int senderId, int receiverId, String content) throws SQLException {
        String sql = "INSERT INTO pending_messages (sender_id, receiver_id, content) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        }
    }

    public static List<Message> getPendingMessages(int receiverId) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM pending_messages WHERE receiver_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiverId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new Message(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            null, // Não tem group_id em pending_messages
                            rs.getString("content"),
                            rs.getTimestamp("timestamp").toLocalDateTime(),
                            false // Sempre false para pendentes
                    ));
                }
            }
        }
        return messages;
    }

    public static void markMessageAsReceived(int messageId) throws SQLException {
        String sql = "DELETE FROM pending_messages WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.executeUpdate();
        }
    }

    public static void recordJoinRequestApproval(int groupId, int requestingUserId, int approvingUserId) throws SQLException {
        String sql = "INSERT INTO join_requests (group_id, requesting_user_id, approving_user_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE approving_user_id = VALUES(approving_user_id)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, requestingUserId);
            pstmt.setInt(3, approvingUserId);
            pstmt.executeUpdate();
        }
    }

    public static boolean hasMemberApprovedJoinRequest(int groupId, int requestingUserId, int approvingUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM join_requests WHERE group_id = ? AND requesting_user_id = ? AND approving_user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, requestingUserId);
            pstmt.setInt(3, approvingUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static int getNumberOfApprovalsForJoinRequest(int groupId, int requestingUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM join_requests WHERE group_id = ? AND requesting_user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, requestingUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public static void clearJoinRequestApprovals(int groupId, int requestingUserId) throws SQLException {
        String sql = "DELETE FROM join_requests WHERE group_id = ? AND requesting_user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, requestingUserId);
            pstmt.executeUpdate();
        }
    }

    // --- Métodos para Solicitações de Conversa (BUSY) ---

    public static void addChatRequest(int senderId, int receiverId) throws SQLException {
        String sql = "INSERT INTO chat_requests (sender_id, receiver_id, status) VALUES (?, ?, 'PENDING') ON DUPLICATE KEY UPDATE status = 'PENDING', request_time = CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.executeUpdate();
        }
    }

    public static String getChatRequestStatus(int senderId, int receiverId) throws SQLException {
        String sql = "SELECT status FROM chat_requests WHERE sender_id = ? AND receiver_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        }
        return null; // Nenhuma solicitação existente
    }

    public static void updateChatRequestStatus(int senderId, int receiverId, String status) throws SQLException {
        String sql = "UPDATE chat_requests SET status = ? WHERE sender_id = ? AND receiver_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, senderId);
            pstmt.setInt(3, receiverId);
            pstmt.executeUpdate();
        }
    }

    public static void removeChatRequest(int senderId, int receiverId) throws SQLException {
        String sql = "DELETE FROM chat_requests WHERE sender_id = ? AND receiver_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.executeUpdate();
        }
    }

    // NOVO: Método para remover todas as solicitações onde o usuário é o RECEIVER
    public static void removeAllChatRequestsForReceiver(int receiverId) throws SQLException {
        String sql = "DELETE FROM chat_requests WHERE receiver_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, receiverId);
            pstmt.executeUpdate();
        }
    }

    // NOVO: Método para remover todas as solicitações onde o usuário é o SENDER
    public static void removeAllChatRequestsForSender(int senderId) throws SQLException {
        String sql = "DELETE FROM chat_requests WHERE sender_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.executeUpdate();
        }
    }


    public static List<String> getPendingChatRequestsForUser(int userId) throws SQLException {
        List<String> senders = new ArrayList<>();
        String sql = "SELECT u.login FROM chat_requests cr JOIN users u ON cr.sender_id = u.id WHERE cr.receiver_id = ? AND cr.status = 'PENDING'";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    senders.add(rs.getString("login"));
                }
            }
        }
        return senders;
    }
}