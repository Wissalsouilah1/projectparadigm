# Multi-Client Chat Application (Java Sockets + JavaFX)

TCP group chat: one central server, many clients. Java 21 (virtual threads), JavaFX 21, Maven.

## Structure
```
tcp-server/  chat.model (ChatServer, ServerListener, ServerConfig) | chat.view (TCPServer)
tcp-client/  chat.model (ChatClient, ClientListener, ClientConfig) | chat.view (TCPClient)
docs/        class, deployment and sequence diagrams (PlantUML: paste into plantuml.com)
```
The `model` packages never import JavaFX; the views talk to them through `ServerListener` / `ClientListener`.

## Build (JDK 21+, Maven 3.9+)
```
cd tcp-server && mvn clean package   # -> target/tcp-server-1.0-shaded.jar
cd tcp-client && mvn clean package   # -> target/tcp-client-1.0-shaded.jar
```

## Run
```
java -jar tcp-server/target/tcp-server-1.0-shaded.jar
java -jar tcp-client/target/tcp-client-1.0-shaded.jar localhost 3000
```
Or from the IDE/Maven: `mvn javafx:run` (client: `mvn javafx:run -Djavafx.args="localhost 3000"`).
Main classes: `chat.view.TCPServer` / `chat.view.TCPClient` (the JAR entry point is `chat.Launcher`, which avoids the "JavaFX runtime components are missing" error).

Default settings are in `src/main/resources/config.properties`; a `config.properties` next to the JAR overrides them without recompiling. Client command-line arguments override both.

## Features
- Username login; empty username = READ-ONLY MODE
- Enter / SEND to post, `allUsers` lists active users (answer only to the asker), `end` / `bye` / LEAVE disconnects
- Online status label + coloured circle (red when offline)
- Server: virtual thread per client, live user ListView with random colours, activity log
- Duplicate usernames get a suffix (ahmed, ahmed_2); empty messages are ignored

## Protocol (DataInputStream.readUTF / DataOutputStream.writeUTF)
- Client -> server: first message = username (blank = read-only), then chat lines or commands (`allUsers`, `end`, `bye`)
- Server -> client: ready-to-display text, chat messages formatted `[HH:mm:ss] user: text`
- The server sends a chat message to all *other* clients; the sending client shows its own message locally.

## Concurrency design
- `ConcurrentHashMap<Socket, DataOutputStream>` and `ConcurrentHashMap<Socket, String>` are shared by all client threads.
- Every write to a client goes through one method that locks that client's stream.
- A failed write to one recipient closes only that recipient; the sender is never affected.
- `finally` always unregisters the client and announces that it left.
