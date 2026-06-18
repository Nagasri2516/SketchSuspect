import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ImposterGame extends WebSocketServer {
    
    private static final int PORT = 9090;
    private static final int MIN_PLAYERS = 4;
    
    // ============ SINGLE GAME SESSION ============
    private static final String SESSION_ID = "game_room_1";
    private static GameSession gameSession = null;
    
    // ============ THREAD-SAFE CLIENT TRACKING ============
    private static final ConcurrentHashMap<String, WebSocket> clients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> clientNames = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> clientSessions = new ConcurrentHashMap<>();
    
    // ============ WORD BANK ============
    private static final List<String> WORDS = Arrays.asList(
        "elephant", "giraffe", "dolphin", "penguin", "kangaroo",
        "pizza", "burger", "sushi", "tacos", "pasta",
        "umbrella", "laptop", "bicycle", "guitar", "camera",
        "castle", "dragon", "knight", "wizard", "unicorn"
    );
    private static final Random random = new Random();
    
    public ImposterGame() {
        super(new InetSocketAddress(PORT));
        System.out.println("🎭 THE IMPOSTER - SINGLE SESSION MODE");
        System.out.println("📍 ws://localhost:" + PORT);
        System.out.println("📍 http://localhost:" + PORT);
        System.out.println("");
        
        // Create the single game session
        gameSession = new GameSession(SESSION_ID);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String userId = "player_" + UUID.randomUUID().toString().substring(0, 6);
        String userName = "Player_" + userId.substring(7);
        
        // Store client
        clients.put(userId, conn);
        clientNames.put(userId, userName);
        clientSessions.put(userId, SESSION_ID);
        
        // Add player to the single session
        gameSession.addPlayer(userId, userName);
        
        System.out.println("✅ " + userName + " joined! Total: " + gameSession.getPlayerCount());
        System.out.println("📊 Players: " + String.join(", ", gameSession.getPlayerNames()));
        
        // Send welcome to new player
        sendToUser(userId, "system", "Welcome " + userName + "! (" + 
            gameSession.getPlayerCount() + " players online)");
        
        // Broadcast to ALL players
        sendToAll("system", "👤 " + userName + " joined the game! (" + 
            gameSession.getPlayerCount() + " players)");
        
        // Update player list for everyone
        broadcastPlayerList();
        
        if (gameSession.getPlayerCount() >= MIN_PLAYERS) {
            sendToAll("system", "🎮 " + gameSession.getPlayerCount() + 
                " players! Type /start to begin!");
        }
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String userId = getUserId(conn);
        if (userId == null) return;
        
        String userName = clientNames.get(userId);
        
        gameSession.removePlayer(userId);
        
        System.out.println("❌ " + userName + " left. Remaining: " + gameSession.getPlayerCount());
        
        sendToAll("system", "❌ " + userName + " left the game (" + 
            gameSession.getPlayerCount() + " players remain)");
        
        broadcastPlayerList();
        
        clients.remove(userId);
        clientNames.remove(userId);
        clientSessions.remove(userId);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        String userId = getUserId(conn);
        if (userId == null) return;
        
        String userName = clientNames.get(userId);
        
        System.out.println("📨 [" + userName + "]: " + message);
        
        try {
            if (message.startsWith("/")) {
                handleCommand(userId, userName, message);
            } else {
                // ============ BROADCAST TO ALL PLAYERS ============
                String chatMessage = "💬 " + userName + ": " + message;
                sendToAll("chat", chatMessage);
                System.out.println("📢 Broadcast to " + gameSession.getPlayerCount() + 
                    " players: " + chatMessage);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            sendToUser(userId, "system", "❌ Error: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        System.out.println("✅ Server started!");
        System.out.println("📊 All players join the same session!");
        System.out.println("");
        System.out.println("📝 Commands:");
        System.out.println("  /start  - Start the game");
        System.out.println("  /draw [text] - Submit your drawing");
        System.out.println("  /vote [name] - Vote for the imposter");
        System.out.println("  /help   - Show help");
        System.out.println("");
    }
    
    // ============ COMMAND HANDLER ============
    
    private void handleCommand(String userId, String userName, String command) {
        String[] parts = command.split(" ");
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "/start":
                gameSession.startGame(this);
                break;
                
            case "/draw":
                if (parts.length > 1) {
                    String drawing = command.substring(5);
                    gameSession.submitDrawing(userId, drawing, this);
                } else {
                    sendToUser(userId, "system", "❌ Usage: /draw [your drawing]");
                }
                break;
                
            case "/vote":
                if (parts.length > 1) {
                    String accused = parts[1];
                    gameSession.castVote(userId, accused, this);
                } else {
                    sendToUser(userId, "system", "❌ Usage: /vote [player_name]");
                }
                break;
                
            case "/help":
                sendToUser(userId, "system", "📝 Commands: /start, /draw [text], /vote [name], /help");
                break;
                
            default:
                sendToUser(userId, "system", "❌ Unknown: " + cmd + ". Type /help");
        }
    }
    
    // ============ BROADCAST HELPERS ============
    
    public void sendToUser(String userId, String type, String message) {
        WebSocket conn = clients.get(userId);
        if (conn != null && conn.isOpen()) {
            String json = "{\"type\":\"" + type + "\",\"message\":\"" + 
                message.replace("\"", "\\\"") + "\"}";
            conn.send(json);
        }
    }
    
    public void sendToAll(String type, String message) {
        int count = 0;
        for (String userId : gameSession.getPlayers()) {
            sendToUser(userId, type, message);
            count++;
        }
        System.out.println("📢 Sent to " + count + " players: " + message);
    }
    
    private void broadcastPlayerList() {
        String list = String.join(", ", gameSession.getPlayerNames());
        String message = "👥 Players: " + list + " (" + gameSession.getPlayerCount() + " total)";
        sendToAll("system", message);
    }
    
    private String getUserId(WebSocket conn) {
        for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
            if (entry.getValue().equals(conn)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    // ============ GAME SESSION ============
    
    static class GameSession {
        private final String sessionId;
        private GameStatus status;
        private int roundNumber;
        private String imposterId;
        private String currentDrawerId;
        private String currentDrawWord;
        
        private final ConcurrentHashMap<String, String> players = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> scores = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> playerWords = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> drawings = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> votes = new ConcurrentHashMap<>();
        private final CopyOnWriteArraySet<String> votedPlayers = new CopyOnWriteArraySet<>();
        
        private final ReentrantReadWriteLock gameLock = new ReentrantReadWriteLock();
        private final ReentrantLock turnLock = new ReentrantLock();
        private final ReentrantLock voteLock = new ReentrantLock();
        
        private Timer timer;
        private boolean gameStarted = false;
        private boolean isVoting = false;
        
        enum GameStatus { WAITING, STARTING, DRAWING, VOTING, ROUND_END, GAME_OVER }
        
        public GameSession(String sessionId) {
            this.sessionId = sessionId;
            this.status = GameStatus.WAITING;
            this.roundNumber = 0;
        }
        
        public void addPlayer(String userId, String userName) {
            gameLock.writeLock().lock();
            try {
                players.putIfAbsent(userId, userName);
                scores.putIfAbsent(userId, 0);
                status = GameStatus.WAITING;
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
                drawings.remove(userId);
                votes.remove(userId);
                votedPlayers.remove(userId);
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        public Set<String> getPlayers() {
            return players.keySet();
        }
        
        public List<String> getPlayerNames() {
            return new ArrayList<>(players.values());
        }
        
        public int getPlayerCount() {
            return players.size();
        }
        
        public void startGame(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (gameStarted) {
                    server.sendToAll("system", "⛔ Game already started!");
                    return;
                }
                
                if (players.size() < MIN_PLAYERS) {
                    server.sendToAll("system", "⚠️ Need " + MIN_PLAYERS + 
                        " players! (" + players.size() + "/" + MIN_PLAYERS + ")");
                    return;
                }
                
                gameStarted = true;
                roundNumber = 0;
                status = GameStatus.STARTING;
                
                server.sendToAll("system", "🎮 GAME STARTED with " + players.size() + " players!");
                server.sendToAll("system", "📢 A secret imposter is among you!");
                
                new Timer().schedule(new TimerTask() {
                    public void run() {
                        startNewRound(server);
                    }
                }, 2000);
                
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void startNewRound(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (roundNumber >= 5) {
                    endGame(server);
                    return;
                }
                
                roundNumber++;
                status = GameStatus.DRAWING;
                drawings.clear();
                votes.clear();
                votedPlayers.clear();
                playerWords.clear();
                isVoting = false;
                
                if (roundNumber % 2 == 1 || imposterId == null) {
                    List<String> playerIds = new ArrayList<>(players.keySet());
                    imposterId = playerIds.get(random.nextInt(playerIds.size()));
                    server.sendToAll("system", "🕵️ Round " + roundNumber + " - Imposter selected!");
                }
                
                String baseWord = WORDS.get(random.nextInt(WORDS.size()));
                String imposterWord = WORDS.get(random.nextInt(WORDS.size()));
                while (imposterWord.equals(baseWord)) {
                    imposterWord = WORDS.get(random.nextInt(WORDS.size()));
                }
                
                for (String userId : players.keySet()) {
                    if (userId.equals(imposterId)) {
                        playerWords.put(userId, imposterWord);
                        server.sendToUser(userId, "system", "🔴 You are the IMPOSTER! Word: " + imposterWord);
                    } else {
                        playerWords.put(userId, baseWord);
                        server.sendToUser(userId, "system", "🟢 Your word: " + baseWord);
                    }
                }
                
                String firstDrawer = players.keySet().iterator().next();
                currentDrawerId = firstDrawer;
                currentDrawWord = playerWords.get(firstDrawer);
                
                server.sendToAll("system", "✏️ " + getPlayerName(firstDrawer) + " draws first!");
                server.sendToUser(firstDrawer, "system", "🎨 Draw: " + currentDrawWord);
                server.sendToUser(firstDrawer, "system", "📝 /draw [your drawing]");
                
                startTimer(server);
                
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void startTimer(ImposterGame server) {
            if (timer != null) timer.cancel();
            timer = new Timer();
            
            timer.schedule(new TimerTask() {
                int remaining = 30;
                public void run() {
                    if (remaining <= 0) {
                        timer.cancel();
                        server.sendToAll("system", "⏰ Time's up!");
                        nextTurn(server);
                    } else if (remaining % 10 == 0 || remaining <= 5) {
                        server.sendToAll("system", "⏱️ " + remaining + "s remaining");
                        remaining--;
                    } else {
                        remaining--;
                    }
                }
            }, 0, 1000);
        }
        
        private void nextTurn(ImposterGame server) {
            turnLock.lock();
            try {
                List<String> playerIds = new ArrayList<>(players.keySet());
                int currentIndex = playerIds.indexOf(currentDrawerId);
                int nextIndex = (currentIndex + 1) % playerIds.size();
                String nextDrawer = playerIds.get(nextIndex);
                
                if (drawings.size() >= players.size()) {
                    startVoting(server);
                    return;
                }
                
                currentDrawerId = nextDrawer;
                currentDrawWord = playerWords.get(nextDrawer);
                
                server.sendToAll("system", "✏️ " + getPlayerName(nextDrawer) + "'s turn!");
                server.sendToUser(nextDrawer, "system", "🎨 Draw: " + currentDrawWord);
                server.sendToUser(nextDrawer, "system", "📝 /draw [your drawing]");
                startTimer(server);
                
            } finally {
                turnLock.unlock();
            }
        }
        
        public void submitDrawing(String userId, String drawing, ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                if (!gameStarted || status == GameStatus.GAME_OVER) {
                    server.sendToUser(userId, "system", "⛔ Game not active!");
                    return;
                }
                
                if (status != GameStatus.DRAWING) {
                    server.sendToUser(userId, "system", "⛔ Not in drawing phase!");
                    return;
                }
                
                if (!userId.equals(currentDrawerId)) {
                    server.sendToUser(userId, "system", "⛔ Not your turn!");
                    return;
                }
                
                drawings.put(userId, drawing);
                server.sendToAll("system", "✅ " + getPlayerName(userId) + " submitted: " + drawing);
                
                if (drawings.size() >= players.size()) {
                    startVoting(server);
                } else {
                    nextTurn(server);
                }
                
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void startVoting(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                status = GameStatus.VOTING;
                isVoting = true;
                votes.clear();
                votedPlayers.clear();
                
                server.sendToAll("system", "🗳️ VOTING TIME!");
                server.sendToAll("system", "📝 Vote: /vote [player_name]");
                server.sendToAll("system", "📊 All drawings:");
                
                for (String userId : players.keySet()) {
                    String drawing = drawings.getOrDefault(userId, "(no drawing)");
                    server.sendToAll("system", "  " + getPlayerName(userId) + " drew: " + drawing);
                }
                
                new Timer().schedule(new TimerTask() {
                    int remaining = 20;
                    public void run() {
                        if (remaining <= 0) {
                            timer.cancel();
                            endVoting(server);
                        } else if (remaining % 5 == 0) {
                            server.sendToAll("system", "⏱️ " + remaining + "s to vote!");
                            remaining--;
                        } else {
                            remaining--;
                        }
                    }
                }, 0, 1000);
                
            } finally {
                gameLock.writeLock().unlock();
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
                    server.sendToUser(voterId, "system", "⛔ Already voted!");
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
                
                if (voterId.equals(accusedId)) {
                    server.sendToUser(voterId, "system", "⛔ Can't vote for yourself!");
                    return;
                }
                
                votes.put(voterId, accusedId);
                votedPlayers.add(voterId);
                server.sendToAll("system", "🗳️ " + getPlayerName(voterId) + 
                    " voted for " + getPlayerName(accusedId));
                
                if (votedPlayers.size() >= players.size()) {
                    endVoting(server);
                }
                
            } finally {
                voteLock.unlock();
            }
        }
        
        private void endVoting(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                isVoting = false;
                status = GameStatus.ROUND_END;
                
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
                        server.sendToAll("system", "🎉 " + getPlayerName(eliminated) + 
                            " was the IMPOSTER!");
                        for (String userId : players.keySet()) {
                            if (!userId.equals(imposterId)) {
                                scores.merge(userId, 20, Integer::sum);
                            }
                        }
                    } else {
                        server.sendToAll("system", "😈 " + getPlayerName(eliminated) + 
                            " was innocent! IMPOSTER survives!");
                        scores.merge(imposterId, 30, Integer::sum);
                    }
                }
                
                server.sendToAll("system", "📊 SCORES:");
                for (String userId : players.keySet()) {
                    server.sendToAll("system", "  " + getPlayerName(userId) + 
                        ": " + scores.get(userId) + " points");
                }
                
                new Timer().schedule(new TimerTask() {
                    public void run() {
                        startNewRound(server);
                    }
                }, 5000);
                
            } finally {
                gameLock.writeLock().unlock();
            }
        }
        
        private void endGame(ImposterGame server) {
            gameLock.writeLock().lock();
            try {
                status = GameStatus.GAME_OVER;
                server.sendToAll("system", "🏆 GAME OVER!");
                server.sendToAll("system", "📊 FINAL SCORES:");
                
                List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
                sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                for (Map.Entry<String, Integer> entry : sortedScores) {
                    server.sendToAll("system", "  🏅 " + getPlayerName(entry.getKey()) + 
                        ": " + entry.getValue() + " points");
                }
                
                server.sendToAll("system", "🎮 Type /start to play again!");
                gameStarted = false;
                roundNumber = 0;
                
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