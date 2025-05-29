package unoeste.br.common;

public class Protocol {
    // Comandos do Cliente
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String RECOVER_PASSWORD = "RECOVER_PASSWORD";
    public static final String SET_STATUS = "SET_STATUS";
    public static final String LIST_ONLINE_USERS = "LIST_ONLINE_USERS";
    public static final String LIST_BUSY_USERS = "LIST_BUSY_USERS";
    public static final String LIST_AWAY_USERS = "LIST_AWAY_USERS";
    public static final String LIST_GROUPS = "LIST_GROUPS";
    public static final String CREATE_GROUP = "CREATE_GROUP";
    public static final String ADD_GROUP_MEMBER = "ADD_MEMBER";
    public static final String ACCEPT_GROUP_INVITE = "ACCEPT_INVITE";
    public static final String DECLINE_GROUP_INVITE = "DECLINE_INVITE";
    public static final String JOIN_GROUP_REQUEST = "JOIN_GROUP_REQUEST";
    public static final String ACCEPT_JOIN_REQUEST = "ACCEPT_JOIN";
    public static final String DECLINE_JOIN_REQUEST = "DECLINE_JOIN";
    public static final String LEAVE_GROUP = "LEAVE_GROUP";
    public static final String SEND_MESSAGE = "MSG";
    public static final String REQUEST_CHAT = "REQUEST_CHAT";
    public static final String ACCEPT_CHAT_REQUEST = "ACCEPT_CHAT_REQUEST";
    public static final String DECLINE_CHAT_REQUEST = "DECLINE_CHAT_REQUEST";
    public static final String LOGOUT = "LOGOUT";


    // Respostas do Servidor
    public static final String SUCCESS = "SUCCESS: ";
    public static final String ERROR = "ERROR: ";
    public static final String INFO = "INFO: ";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS: ";
    public static final String RECOVER_PASSWORD_SUCCESS = "PASSWORD: ";
    public static final String LIST_ONLINE_USERS_RESPONSE = "ONLINE_USERS: ";
    public static final String LIST_BUSY_USERS_RESPONSE = "BUSY_USERS: ";
    public static final String LIST_AWAY_USERS_RESPONSE = "AWAY_USERS: ";
    public static final String LIST_GROUPS_RESPONSE = "GROUPS: ";
    public static final String PRIVATE_MESSAGE = "PRIVATE_MSG: ";
    public static final String GROUP_MESSAGE = "GROUP_MSG: ";
    public static final String GROUP_PRIVATE_MESSAGE = "GROUP_PRIVATE_MSG: ";
    public static final String MESSAGE_SENT = "MESSAGE_SENT: ";
    public static final String MESSAGE_NOT_DELIVERED = "MESSAGE_NOT_DELIVERED: ";
    public static final String SERVER_MSG = "SERVER_MSG: ";
    public static final String GROUP_INVITE = "GROUP_INVITE: ";
    public static final String JOIN_GROUP_REQUEST_NOTIFICATION = "JOIN_REQ: ";
    public static final String CHAT_REQUEST_NOTIFICATION = "CHAT_REQ: ";
    public static final String CHAT_REQUEST_GROUP_NOTIFICATION = "CHAT_REQ_GROUP: ";
    public static final String CHAT_REQUEST_ACCEPTED = "CHAT_REQ_ACCEPTED: ";
    public static final String CHAT_REQUEST_DECLINED = "CHAT_REQ_DECLINED: ";
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    public static final String NOT_AUTHORIZED = "NOT_AUTHORIZED: ";


    // Outros
    public static final String ACCEPT = "SIM";
    public static final String DENY = "NAO";
}