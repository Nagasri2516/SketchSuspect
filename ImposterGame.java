import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// ============================================================
// 🎭 THE IMPOSTER - Collaborative Canvas Edition
// ============================================================

public class ImposterGame extends WebSocketServer {
    
    // ============ SERVER CONFIG ============
    private static final int PORT = 9090;
    private static final String SERVER_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final int MAX_PLAYERS = 12;
    private static final int MIN_PLAYERS = 4;
    
    // ============ CONCURRENCY: Thread-safe collections ============
    private static final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, WebSocket> clients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> clientNames = new ConcurrentHashMap<>();
    
    // ============ WORD BANK ============
    private static final List<String> WORDS = Arrays.asList(
        "elephant", "giraffe", "dolphin", "penguin", "kangaroo",
        "pizza", "burger", "sushi", "tacos", "pasta",
        "umbrella", "laptop", "bicycle", "guitar", "camera",
        "castle", "dragon", "knight", "wizard", "unicorn",
        "rocket", "planet", "galaxy", "comet", "nebula",
        "pirate", "treasure", "island", "volcano", "ocean"
    );
    private static final Random random = new Random();
    
    public ImposterGame() {
        super(new InetSocketAddress(PORT));
        System.out.println("🎭 THE IMPOSTER - FULL GAME");
        System.out.println("📡 Server ID: " + SERVER_ID);
        System.out.println("📍 ws://localhost:" + PORT);
    }
    
    // ============ WEBSOCKET EVENTS ============
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String userId = "player_" + UUID.randomUUID().toString().substring(0, 6);
        String userName = "Player_" + userId.substring(7);
        
        clients.put(userId, conn);
        clientNames.put(userId, userName);
        
        System.out.println("✅ " + userName + " connected to server!");
        
        sendToUser(userId, "system", "Welcome " + userName + "! Type /create to make a room, or /join [code] to join one.");
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String userId = getUserId(conn);
        if (userId == null) return;
        
        String userName = clientNames.get(userId);
        String sessionId = getSessionId(userId);
        
        if (sessionId != null) {
            GameSession session = sessions.get(sessionId);
            if (session != null) {
                session.removePlayer(userId);
                sendToAll(sessionId, "system", "❌ " + userName + " left the game");
                broadcastPlayerList(sessionId);
                
                if (session.getPlayerCount() == 0) {
                    sessions.remove(sessionId);
                    System.out.println("🧹 Session " + sessionId + " removed (empty)");
                }
            }
        }
        
        clients.remove(userId);
        clientNames.remove(userId);
        System.out.println("❌ " + userName + " disconnected");
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        String userId = getUserId(conn);
        if (userId == null) return;
        
        String userName = clientNames.get(userId);
        String sessionId = getSessionId(userId);
        
        try {
            if (sessionId == null) {
                // Not in a room yet
                if (message.startsWith("/create")) {
                    String code = generateRoomCode();
                    GameSession newSession = new GameSession(code);
                    sessions.put(code, newSession);
                    newSession.addPlayer(userId, userName);
                    sendToUser(userId, "room_code", code);
                    sendToUser(userId, "system", "✅ Room created! Code: " + code);
                    broadcastPlayerList(code);
                } else if (message.startsWith("/join ")) {
                    String code = message.substring(6).toUpperCase().trim();
                    GameSession session = sessions.get(code);
                    if (session != null && session.getStatus() == GameSession.GameStatus.WAITING && session.getPlayerCount() < MAX_PLAYERS) {
                        session.addPlayer(userId, userName);
                        sendToUser(userId, "room_code", code);
                        sendToAll(code, "system", "👤 " + userName + " joined the game! (" + session.getPlayerCount() + " players)");
                        broadcastPlayerList(code);
                        if (session.getPlayerCount() >= MIN_PLAYERS) {
                            sendToAll(code, "system", "🎮 Enough players! Type /start to begin!");
                        }
                    } else {
                        sendToUser(userId, "system", "❌ Invalid room code or room is full/started!");
                    }
                } else {
                    sendToUser(userId, "system", "❌ You must /create or /join a room first!");
                }
                return;
            }
            
            GameSession session = sessions.get(sessionId);
            if (session == null) return;

            // In a room
            if (message.startsWith("/")) {
                if (message.startsWith("/line ")) {
                    // Only broadcast if drawing and it's their turn
                    if (session.getStatus() == GameSession.GameStatus.DRAWING && userId.equals(session.getCurrentDrawerId())) {
                        sendToAll(sessionId, "line", message.substring(6));
                    }
                } else {
                    handleCommand(userId, userName, sessionId, session, message);
                }
            } else {
                if (session.getStatus() != GameSession.GameStatus.GAME_OVER) {
                    sendToAll(sessionId, "chat", "💬 " + userName + ": " + message);
                } else {
                    sendToUser(userId, "system", "⛔ Game is not active!");
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            sendToUser(userId, "system", "❌ Error: " + e.getMessage());
        }
    }
    
    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        System.out.println("✅ Server started successfully!");
    }
    
    // ============ COMMAND HANDLER ============
    
    private void handleCommand(String userId, String userName, String sessionId, GameSession session, String command) {
        String[] parts = command.split(" ");
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "/start":
                session.startGame(this);
                break;
            case "/vote":
                if (parts.length > 1) {
                    String accused = parts[1];
                    session.castVote(userId, accused, this);
                } else {
                    sendToUser(userId, "system", "❌ Usage: /vote [player_name]");
                }
                break;
            case "/help":
                sendToUser(userId, "system", "📝 Commands: /create, /join [code], /start, /vote [name], /help");
                break;
            default:
                if (!cmd.equals("/line")) {
                    sendToUser(userId, "system", "❌ Unknown command: " + cmd);
                }
        }
    }
    
    // ============ BROADCAST HELPERS ============
    
    public void sendToUser(String userId, String type, String message) {
        WebSocket conn = clients.get(userId);
        if (conn != null && conn.isOpen()) {
            String json = "{\"type\":\"" + type + "\",\"message\":\"" + message.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
            conn.send(json);
        }
    }
    
    public void sendToAll(String sessionId, String type, String message) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return;
        
        for (String userId : session.getPlayers()) {
            sendToUser(userId, type, message);
        }
    }
    
    private void broadcastPlayerList(String sessionId) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return;
        
        String list = String.join(", ", session.getPlayerNames());
        sendToAll(sessionId, "system", "👥 Players: " + list + " (" + session.getPlayerCount() + " total)");
    }
    
    private String getUserId(WebSocket conn) {
        for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
            if (entry.getValue().equals(conn)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private String getSessionId(String userId) {
        for (Map.Entry<String, GameSession> entry : sessions.entrySet()) {
            if (entry.getValue().hasPlayer(userId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    // ============ GAME SESSION ============
    
    static class GameSession {
        private final String sessionId;
        private GameStatus status;
        private String imposterId;
        private String currentDrawerId;
        private String currentWord;
        
        private final ConcurrentHashMap<String, String> players = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> scores = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> playerWords = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> votes = new ConcurrentHashMap<>();
        private final CopyOnWriteArraySet<String> votedPlayers = new CopyOnWriteArraySet<>();
        
        private final ReentrantReadWriteLock gameLock = new ReentrantReadWriteLock();
        private final ReentrantLock turnLock = new ReentrantLock();
        private final ReentrantLock voteLock = new ReentrantLock();
        
        private Timer roundTimer;
        private Timer turnTimer;
        private boolean isVoting = false;
        
        enum GameStatus { WAITING, STARTING, DRAWING, VOTING, ROUND_END, GAME_OVER }
        
        public GameSession(String sessionId) {
            this.sessionId = sessionId;
            this.status = GameStatus.WAITING;
        }
        
        public void addPlayer(String userId, String userName) {
            gameLock.writeLock().lock();
            try {
                players.putIfAbsent(userId, userName);
                scores.putIfAbsent(userId, 0);
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        public void removePlayer(String userId) {
            gameLock.writeLock().lock();
            try {
                players.remove(userId);
                scores.remove(userId);
                playerWords.remove(userId);
                votes.remove(userId);
                votedPlayers.remove(userId);
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        public boolean hasPlayer(String userId) { return players.containsKey(userId); }
        public Set<String> getPlayers() { return players.keySet(); }
        public List<String> getPlayerNames() { return new ArrayList<>(players.values()); }
        public int getPlayerCount() { return players.size(); }
        public GameStatus getStatus() { return status; }
        public String getCurrentDrawerId() { return currentDrawerId; }
        
        public void startGame(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (status != GameStatus.WAITING && status != GameStatus.ROUND_END && status != GameStatus.GAME_OVER) {
                    server.sendToAll(sessionId, "system", "⛔ Game already started!");
                    return;
                }
                
                if (players.size() < MIN_PLAYERS) {
                    server.sendToAll(sessionId, "system", "⚠️ Need at least " + MIN_PLAYERS + " players!");
                    return;
                }
                
                status = GameStatus.STARTING;
                server.sendToAll(sessionId, "clear_canvas", "");
                server.sendToAll(sessionId, "system", "🎮 GAME STARTED!");
                
                new Timer().schedule(new TimerTask() {
                    public void run() {
                        startRound(server);
                    }
                }, 2000);
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void startRound(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                status = GameStatus.DRAWING;
                votes.clear();
                votedPlayers.clear();
                playerWords.clear();
                isVoting = false;
                
                List<String> playerIds = new ArrayList<>(players.keySet());
                imposterId = playerIds.get(random.nextInt(playerIds.size()));
                
                currentWord = WORDS.get(random.nextInt(WORDS.size()));
                String imposterWord = WORDS.get(random.nextInt(WORDS.size()));
                while (imposterWord.equals(currentWord)) {
                    imposterWord = WORDS.get(random.nextInt(WORDS.size()));
                }
                
                for (String userId : players.keySet()) {
                    if (userId.equals(imposterId)) {
                        playerWords.put(userId, imposterWord);
                        server.sendToUser(userId, "system", "🔴 You are the IMPOSTER! Fake word: " + imposterWord);
                    } else {
                        playerWords.put(userId, currentWord);
                        server.sendToUser(userId, "system", "🟢 Your word: " + currentWord);
                    }
                }
                
                // Set round timer for 6 minutes
                if (roundTimer != null) roundTimer.cancel();
                roundTimer = new Timer();
                roundTimer.schedule(new TimerTask() {
                    public void run() {
                        server.sendToAll(sessionId, "system", "⏰ 6 Minutes up! Time to vote!");
                        startVoting(server);
                    }
                }, 6 * 60 * 1000);
                
                currentDrawerId = playerIds.get(0);
                startTurn(server, currentDrawerId);
                
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void startTurn(ImposterGame server, String userId) {
            server.sendToAll(sessionId, "system", "✏️ " + getPlayerName(userId) + "'s turn! (45s)");
            server.sendToAll(sessionId, "turn", userId);
            
            if (turnTimer != null) turnTimer.cancel();
            turnTimer = new Timer();
            turnTimer.schedule(new TimerTask() {
                public void run() {
                    nextTurn(server);
                }
            }, 45000); // 45 seconds per turn
        }
        
        private void nextTurn(ImposterGame server) {
            turnLock.lock();
            try {
                if (status != GameStatus.DRAWING) return;
                
                List<String> playerIds = new ArrayList<>(players.keySet());
                int currentIndex = playerIds.indexOf(currentDrawerId);
                int nextIndex = (currentIndex + 1) % playerIds.size();
                currentDrawerId = playerIds.get(nextIndex);
                
                startTurn(server, currentDrawerId);
            } finally {
                turnLock.unlock();
            }
        }
        
        public void castVote(String voterId, String accusedName, ImposterGame server) {
            voteLock.lock();
            try {
                if (!isVoting || status != GameStatus.VOTING) {
                    server.sendToUser(voterId, "system", "⛔ Not in voting phase!");
                    return;
                }
                
                if (votedPlayers.contains(voterId)) {
                    server.sendToUser(voterId, "system", "⛔ You already voted!");
                    return;
                }
                
                String accusedId = null;
                for (Map.Entry<String, String> entry : players.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(accusedName)) {
                        accusedId = entry.getKey();
                        break;
                    }
                }
                
                if (accusedId == null) {
                    server.sendToUser(voterId, "system", "⛔ Player not found: " + accusedName);
                    return;
                }
                
                votes.put(voterId, accusedId);
                votedPlayers.add(voterId);
                server.sendToAll(sessionId, "system", "🗳️ " + getPlayerName(voterId) + " voted!");
                
                if (votedPlayers.size() >= players.size()) {
                    endVoting(server);
                }
            } finally {
                voteLock.unlock();
            }
        }
        
        private void startVoting(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (status != GameStatus.DRAWING) return;
                status = GameStatus.VOTING;
                isVoting = true;
                
                if (turnTimer != null) turnTimer.cancel();
                if (roundTimer != null) roundTimer.cancel();
                
                server.sendToAll(sessionId, "turn", "none"); // lock canvas
                server.sendToAll(sessionId, "system", "🗳️ VOTING TIME! Use /vote [name]");
                
                new Timer().schedule(new TimerTask() {
                    int remaining = 30;
                    public void run() {
                        if (remaining <= 0) {
                            cancel();
                            endVoting(server);
                        } else if (remaining % 10 == 0 || remaining <= 5) {
                            server.sendToAll(sessionId, "system", "⏱️ " + remaining + "s to vote!");
                        }
                        remaining--;
                    }
                }, 0, 1000);
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void endVoting(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (status != GameStatus.VOTING) return;
                isVoting = false;
                status = GameStatus.GAME_OVER;
                
                Map<String, Integer> voteCounts = new HashMap<>();
                for (String accusedId : votes.values()) {
                    voteCounts.put(accusedId, voteCounts.getOrDefault(accusedId, 0) + 1);
                }
                
                String eliminated = null;
                int maxVotes = 0;
                for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
                    if (entry.getValue() > maxVotes) {
                        maxVotes = entry.getValue();
                        eliminated = entry.getKey();
                    }
                }
                
                if (eliminated != null) {
                    boolean imposterCaught = eliminated.equals(imposterId);
                    if (imposterCaught) {
                        server.sendToAll(sessionId, "system", "🎉 " + getPlayerName(eliminated) + " was the IMPOSTER! Innocent players win!");
                    } else {
                        server.sendToAll(sessionId, "system", "😈 " + getPlayerName(eliminated) + " was innocent! The IMPOSTER survives! Imposter was: " + getPlayerName(imposterId));
                    }
                } else {
                    server.sendToAll(sessionId, "system", "🤷 Nobody was voted out! Imposter survives! Imposter was: " + getPlayerName(imposterId));
                }
                
                server.sendToAll(sessionId, "system", "🎮 Type /start to play again!");
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private String getPlayerName(String userId) {
            return players.getOrDefault(userId, userId);
        }
    }
    
    public static void main(String[] args) {
        ImposterGame server = new ImposterGame();
        server.run();
    }
}