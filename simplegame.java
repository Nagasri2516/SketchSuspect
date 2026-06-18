import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class simplegame {
    
    private static final int PORT = 9090;
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private static final List<String> players = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) throws Exception {
        System.out.println("🎭 SIMPLE GAME SERVER");
        System.out.println("📍 http://localhost:" + PORT);
        System.out.println("");
        
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("✅ Server running on port " + PORT);
        
        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userId;
        
        ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                String request = in.readLine();
                if (request == null) return;
                
                if (request.contains("GET /")) {
                    sendHTML();
                } else {
                    handleWebSocket(request);
                }
                
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        private void handleWebSocket(String request) {
            try {
                // Extract user ID
                userId = "user_" + UUID.randomUUID().toString().substring(0, 4);
                players.add(userId);
                playerNames.put(userId, "Player_" + userId.substring(5));
                clients.put(userId, out);
                
                System.out.println("✅ User joined: " + userId);
                
                // Send WebSocket handshake
                out.write("HTTP/1.1 101 Switching Protocols\r\n");
                out.write("Upgrade: websocket\r\n");
                out.write("Connection: Upgrade\r\n");
                out.write("Sec-WebSocket-Accept: dGhlIHNhbXBsZSBub25jZQ==\r\n");
                out.write("\r\n");
                out.flush();
                
                // Send welcome message
                sendMessage("system", "Welcome to the game! Players: " + players.size());
                
                // Keep connection alive
                while (true) {
                    String line = in.readLine();
                    if (line == null) break;
                    System.out.println("Received: " + line);
                }
                
            } catch (Exception e) {
                System.err.println("WebSocket error: " + e.getMessage());
            } finally {
                clients.remove(userId);
                players.remove(userId);
                playerNames.remove(userId);
                System.out.println("❌ User left: " + userId);
            }
        }
        
        private void sendMessage(String type, String content) {
            try {
                String json = "{\"type\":\"" + type + "\",\"message\":\"" + content + "\"}";
                out.write(json + "\n");
                out.flush();
            } catch (Exception e) {
                System.err.println("Send error: " + e.getMessage());
            }
        }
        
        private void sendHTML() throws IOException {
            String html = getHTML();
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Content-Type: text/html\r\n");
            out.write("Content-Length: " + html.length() + "\r\n");
            out.write("\r\n");
            out.write(html);
            out.flush();
            socket.close();
        }
        
        private String getHTML() {
            return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <title>Simple Game</title>\n" +
            "    <style>\n" +
            "        body { font-family: Arial; background: #1a1a2e; color: white; padding: 20px; text-align: center; }\n" +
            "        h1 { color: #ffd700; }\n" +
            "        .status { padding: 20px; background: rgba(255,255,255,0.1); border-radius: 10px; margin: 20px auto; max-width: 500px; }\n" +
            "        .connected { color: #4CAF50; }\n" +
            "        .disconnected { color: #f44336; }\n" +
            "        #messages { height: 200px; overflow-y: auto; background: rgba(0,0,0,0.3); padding: 10px; border-radius: 5px; margin: 20px auto; max-width: 500px; text-align: left; }\n" +
            "        .chat-input { max-width: 500px; margin: 10px auto; display: flex; gap: 10px; }\n" +
            "        .chat-input input { flex: 1; padding: 10px; border-radius: 5px; border: none; }\n" +
            "        .chat-input button { padding: 10px 20px; background: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; }\n" +
            "        #status { padding: 10px; margin: 10px auto; max-width: 500px; border-radius: 5px; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <h1>🎭 Simple Game Test</h1>\n" +
            "    <div id=\"status\" class=\"disconnected\">⚪ Connecting...</div>\n" +
            "    <div class=\"status\">\n" +
            "        <p>Players: <span id=\"playerCount\">0</span></p>\n" +
            "        <button id=\"testBtn\" onclick=\"sendTest()\">Send Test Message</button>\n" +
            "    </div>\n" +
            "    <div id=\"messages\"></div>\n" +
            "    <div class=\"chat-input\">\n" +
            "        <input id=\"msgInput\" placeholder=\"Type a message...\">\n" +
            "        <button onclick=\"sendMessage()\">Send</button>\n" +
            "    </div>\n" +
            "    <script>\n" +
            "        let socket;\n" +
            "        let connected = false;\n" +
            "        const statusDiv = document.getElementById('status');\n" +
            "        const messagesDiv = document.getElementById('messages');\n" +
            "        const playerCount = document.getElementById('playerCount');\n" +
            "        \n" +
            "        function connect() {\n" +
            "            const url = 'ws://localhost:9090';\n" +
            "            console.log('Connecting to: ' + url);\n" +
            "            \n" +
            "            try {\n" +
            "                socket = new WebSocket(url);\n" +
            "                \n" +
            "                socket.onopen = function() {\n" +
            "                    connected = true;\n" +
            "                    statusDiv.textContent = '🟢 Connected!';\n" +
            "                    statusDiv.className = 'connected';\n" +
            "                    addMessage('System', 'Connected to server!');\n" +
            "                };\n" +
            "                \n" +
            "                socket.onmessage = function(e) {\n" +
            "                    try {\n" +
            "                        const data = JSON.parse(e.data);\n" +
            "                        console.log('Received:', data);\n" +
            "                        if (data.type === 'system') {\n" +
            "                            addMessage('Server', data.message);\n" +
            "                            if (data.message && data.message.includes('Players:')) {\n" +
            "                                const count = data.message.match(/\\d+/);\n" +
            "                                if (count) playerCount.textContent = count[0];\n" +
            "                            }\n" +
            "                        }\n" +
            "                    } catch (err) {\n" +
            "                        addMessage('Raw', e.data);\n" +
            "                    }\n" +
            "                };\n" +
            "                \n" +
            "                socket.onclose = function() {\n" +
            "                    connected = false;\n" +
            "                    statusDiv.textContent = '🔴 Disconnected';\n" +
            "                    statusDiv.className = 'disconnected';\n" +
            "                    addMessage('System', 'Disconnected from server');\n" +
            "                    setTimeout(connect, 3000);\n" +
            "                };\n" +
            "                \n" +
            "                socket.onerror = function(err) {\n" +
            "                    console.error('WebSocket error:', err);\n" +
            "                    addMessage('Error', 'WebSocket error occurred');\n" +
            "                };\n" +
            "            } catch (err) {\n" +
            "                console.error('Connection error:', err);\n" +
            "                addMessage('Error', 'Failed to connect: ' + err.message);\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function sendMessage() {\n" +
            "            const input = document.getElementById('msgInput');\n" +
            "            const msg = input.value.trim();\n" +
            "            if (!msg || !socket || socket.readyState !== WebSocket.OPEN) {\n" +
            "                addMessage('System', 'Not connected!');\n" +
            "                return;\n" +
            "            }\n" +
            "            socket.send('{\"type\":\"chat\",\"message\":\"' + msg + '\"}');\n" +
            "            addMessage('You', msg);\n" +
            "            input.value = '';\n" +
            "        }\n" +
            "        \n" +
            "        function sendTest() {\n" +
            "            if (socket && socket.readyState === WebSocket.OPEN) {\n" +
            "                socket.send('{\"type\":\"test\",\"message\":\"Hello from client!\"}');\n" +
            "                addMessage('You', 'Test message sent!');\n" +
            "            } else {\n" +
            "                addMessage('System', 'Not connected!');\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function addMessage(from, msg) {\n" +
            "            const div = document.createElement('div');\n" +
            "            div.textContent = '[' + from + '] ' + msg;\n" +
            "            messagesDiv.appendChild(div);\n" +
            "            messagesDiv.scrollTop = messagesDiv.scrollHeight;\n" +
            "        }\n" +
            "        \n" +
            "        document.getElementById('msgInput').addEventListener('keypress', function(e) {\n" +
            "            if (e.key === 'Enter') sendMessage();\n" +
            "        });\n" +
            "        \n" +
            "        // Auto-connect\n" +
            "        connect();\n" +
            "        console.log('Page loaded!');\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
        }
    }
}