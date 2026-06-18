
# 🎭 The Imposter - Real-Time Collaborative Drawing Game

A real-time multiplayer drawing game built with Java WebSockets and HTML5 Canvas.
Players collaborate on a shared whiteboard, but one player — the IMPOSTER — 
is secretly given a different word. Everyone draws the same thing... except the 
Imposter, who tries to fake it. Can the group figure out who the Imposter is?


## 🎮 How to Play

### Step 1 — Create or Join a Room
- One player (the host) opens the website and clicks "Create New Room".
- A unique 4-letter code is generated (e.g., "A4X9").
- The host shares this code with their friends.
- Friends open the same website, type the code in the "Join" box, and click Join.
- The room requires a minimum of 4 players to start.

### Step 2 — Start the Game
- Once everyone has joined, the host clicks "Start Game".
- The server secretly picks one player to be the IMPOSTER.
- Every player receives a private message in the chat:
  - Innocent players see: "Your word: ELEPHANT"
  - The Imposter sees: "You are the IMPOSTER! Fake word: GIRAFFE"
- The Imposter gets a completely different (but similar-themed) word so they 
  can try to blend in with the group.

### Step 3 — The Drawing Phase (6 Minutes)
- The game runs for a total of 6 minutes.
- Players take turns drawing on the shared whiteboard, 45 seconds each.
- When it is YOUR turn:
  - The whiteboard unlocks and your cursor becomes a crosshair.
  - A green "IT IS YOUR TURN! DRAW!" indicator appears.
  - You draw whatever comes to mind for your word using the canvas tools.
  - Your strokes appear live on everyone else's screen in real-time.
- When it is NOT your turn:
  - The whiteboard is locked (you cannot draw).
  - You watch the current player's strokes appear live.
- The key mechanic: Everyone is drawing based on their word. 
  Innocent players all draw the same thing. The Imposter draws 
  something slightly different. This is the clue you need to spot them!
- Turns rotate automatically every 45 seconds through all players 
  and continue cycling until the 6-minute timer runs out.

### Step 4 — Discussion & Voting
- Once the round ends (6 minutes) or players feel ready to vote, 
  they discuss in the chat about whose drawing looked suspicious or different.
- To cast a vote, a player types in the chat:
  /vote [PlayerName]   (example: /vote Player_abc123)
- Once all players have voted, the server announces the result:
  - If the group correctly identified the Imposter: Innocent players win!
  - If the group voted out an innocent player: The Imposter wins!
- The real word is revealed and the game can be restarted.

### Drawing Tools
- Color palette: Black, Red, Blue, Green, Yellow, and White (Eraser)
- Brush size slider to draw thin or thick lines
- The canvas is shared — everyone sees the same whiteboard


## 🏗️ Project Architecture

The project is split into two completely separate parts:

### Backend — Java WebSocket Server (ImposterGame.java)
The Java server is the "brain" of the entire game. It:
- Manages all game rooms (sessions) using unique room codes
- Tracks which players are in which room
- Assigns the Imposter role randomly and secretly
- Enforces 45-second drawing turns with server-side timers
- Receives drawing strokes from the active player and broadcasts 
  them instantly to all other players in the room
- Handles the voting system and announces results
- Uses ConcurrentHashMap and ReentrantLock to safely handle 
  multiple games running simultaneously without race conditions

### Frontend — HTML5 Canvas + JavaScript (index.html)
The frontend is a single HTML file that:
- Connects to the Java server via WebSocket
- Shows the lobby (Create Room / Join Room buttons)
- Renders the shared whiteboard using the HTML5 Canvas API
- Captures mouse movement to draw lines locally
- Sends drawing data to the server as the player draws
- Receives drawing data from other players and renders it
- Locks or unlocks the canvas based on whose turn it is
- Shows a real-time chat/log panel on the right side

### Communication Protocol (WebSocket Messages)
Everything between the server and clients is communicated via 
JSON messages over WebSocket. The key message types are:
- "system"    → Server announcements (who joined, whose turn, timer warnings)
- "chat"      → Player chat messages
- "room_code" → Sent to a player when they successfully create/join a room
- "line"      → A drawing stroke (x1,y1,x2,y2,color,size)
- "turn"      → Tells all clients whose turn it is (locks/unlocks the canvas)
- "clear_canvas" → Clears the whiteboard when a new game starts


## 📁 File Structure

GameDistributed/
├── ImposterGame.java     → Java WebSocket server and all game logic
├── index.html            → Complete frontend (HTML + CSS + JavaScript)
├── Dockerfile            → Docker config for cloud deployment
├── Java-WebSocket.jar    → WebSocket library dependency
├── slf4j-api.jar         → Logging API dependency
├── slf4j-simple.jar      → Logging implementation dependency
└── run.bat               → Windows batch file for local development


## 🚀 Running Locally

Requirements: Java 17 or higher installed on your machine.

1. Open a terminal in the project folder.

2. Compile:
   javac -cp ".;Java-WebSocket.jar;slf4j-api.jar;slf4j-simple.jar" ImposterGame.java

3. Run:
   java -cp ".;Java-WebSocket.jar;slf4j-api.jar;slf4j-simple.jar" ImposterGame

4. In index.html, make sure the WebSocket URL says:
   socket = new WebSocket('ws://localhost:9090');

5. Open index.html in multiple browser tabs to test multiplayer locally.


## ☁️ Production Deployment

Backend → Render.com (or Railway.app)
- The Dockerfile compiles and runs the Java server automatically.
- Connect your GitHub repo to Render and create a new Web Service.
- Once live, you will get a URL like: wss://your-app.onrender.com

Frontend → Vercel (or Netlify)
- Update the WebSocket URL in index.html to your Render URL.
- Deploy index.html to Vercel as a static site.
- Share the Vercel link with your friends and play from anywhere!

Note: On the free tier of Render, the server may sleep after 15 minutes 
of inactivity. The first connection may take up to 50 seconds to wake up.


## 🔧 Tech Stack

- Java 17 — Backend server and game logic
- Java-WebSocket — WebSocket server library
- HTML5 Canvas API — Real-time collaborative drawing
- Vanilla JavaScript — Frontend logic and WebSocket client
- CSS3 — Custom UI styling
- Docker — Backend containerization
- Render — Backend cloud hosting
- Vercel — Frontend static hosting
