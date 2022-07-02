package net.jfabricationgames.onnessium.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.esotericsoftware.kryonet.Client;

import net.jfabricationgames.cdi.CdiContainer;
import net.jfabricationgames.cdi.annotation.Inject;
import net.jfabricationgames.onnessium.network.client.NetworkClient;
import net.jfabricationgames.onnessium.network.network.ConnectException;
import net.jfabricationgames.onnessium.network.network.Network;
import net.jfabricationgames.onnessium.network.server.NetworkServer;
import net.jfabricationgames.onnessium.network.server.ServerMessageHandlerRegistry;
import net.jfabricationgames.onnessium.util.Pair;
import net.jfabricationgames.onnessium.util.TestUtils;
import net.jfabricationgames.onnessium.util.Wrapper;

/**
 * Create a client and a server to test a simple communication between both.
 */
public class ClientServerCommunicationIntegrationTest {
	
	private static NetworkClient client;
	private static NetworkServer server;
	
	private Throwable clientConnectException;
	
	@Inject
	private ServerMessageHandlerRegistry handlerRegistry;
	
	@BeforeAll
	public static void startServerAndClient() throws Exception {
		TestUtils.mockGdxApplication();
		TestUtils.createCdiContainer();
		
		Network.registerClass(SimpleMessage.class);
	}
	
	@BeforeEach
	public void injectDependencies() throws Throwable {
		if (client != null) {
			client.disconnect();
		}
		if (server != null) {
			server.stop();
		}
		
		// create a new client and server for each of the tests, to make them independent
		Pair<NetworkClient, NetworkServer> clientAndServer = ClientServerConnectionTestUtil.createConnection();
		client = clientAndServer.getKey();
		server = clientAndServer.getValue();
		
		CdiContainer.injectTo(this);
		handlerRegistry.removeAllHandlers();
		client.removeAllMessageHandlersForType(SimpleMessage.class);
	}
	
	@Test
	public void testSendMessageToServer() throws InterruptedException {
		// received messages are store it in the messageWrapper
		Wrapper<String> messageWrapper = Wrapper.empty();
		handlerRegistry.registerHandler(SimpleMessage.class, (connection, message) -> messageWrapper.wrapped = message.message);
		
		String message = "simple message content";
		client.send(new SimpleMessage().setMessage(message));
		
		Thread.sleep(10); // wait for the message to arrive
		
		assertEquals(message, messageWrapper.wrapped);
	}
	
	@Test
	public void testSendMessageToServerAndHandleResponse() throws InterruptedException {
		// echo the simple message back to the client
		handlerRegistry.registerHandler(SimpleMessage.class, (connection, message) -> connection.sendTCP(message));
		// received messages (on client side) are store it in the messageWrapper
		Wrapper<String> messageWrapper = Wrapper.empty();
		client.addMessageHandler(SimpleMessage.class, response -> messageWrapper.wrapped = response.message);
		
		// received messages (on client side) are stored in the messageWrapper
		String message = "simple message content";
		client.send(new SimpleMessage().setMessage(message));
		
		Thread.sleep(10); // wait for the message to arrive
		
		assertEquals(message, messageWrapper.wrapped);
	}
	
	@Test
	public void testSendMessageToServerAndHandleSingleResponse() throws InterruptedException {
		// echo the simple message back to the client
		handlerRegistry.registerHandler(SimpleMessage.class, (connection, message) -> connection.sendTCP(message));
		
		// received messages (on client side) are stored in the messageWrapper
		Wrapper<String> messageWrapper = Wrapper.empty();
		String message1 = "simple message content";
		String message2 = "another message";
		
		// send a message with a response handler, for a single response
		client.send(new SimpleMessage().setMessage(message1), response -> messageWrapper.wrapped = response.message, SimpleMessage.class);
		// the second message is not handled, because the handler was removed after handling the response
		client.send(new SimpleMessage().setMessage(message2));
		
		Thread.sleep(10); // wait for the message to arrive
		
		assertEquals(message1, messageWrapper.wrapped);
	}
	
	@Test
	public void testSendUnregisteredMessage() throws InterruptedException {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> client.send(new UnregisteredMessage()));
		assertTrue(exception.getMessage().contains("not registered"));
		assertTrue(exception.getMessage().contains(UnregisteredMessage.class.getSimpleName()));
	}
	
	@Test
	public void testSendServerSideUnregisteredMessage() throws Exception {
		// register the message on the client side, but not on the server side
		Field field = NetworkClient.class.getDeclaredField("client");
		field.setAccessible(true);
		Client kryoClient = (Client) field.get(client);
		field.setAccessible(false);
		kryoClient.getKryo().register(UnregisteredMessage.class);
		
		// echo the simple message back to the client
		handlerRegistry.registerHandler(SimpleMessage.class, (connection, message) -> connection.sendTCP(message));
		// received messages (on client side) are store it in the messageWrapper
		Wrapper<String> messageWrapper = Wrapper.empty();
		client.addMessageHandler(SimpleMessage.class, response -> messageWrapper.wrapped = response.message);
		
		client.send(new UnregisteredMessage());
		
		Thread.sleep(10); // wait for the message to arrive
		
		assertNull(messageWrapper.wrapped); // the message was not returned, because the server couldn't handle it
	}
	
	@Test
	public void testConnectToNotStartedServer() throws Exception {
		// client and server were already started, so shut them down
		client.disconnect();
		server.stop();
		
		client = new NetworkClient();
		
		// reduce the connection timeout, so the test doesn't take too long
		Field field = NetworkClient.class.getDeclaredField("connectionTimeout");
		field.setAccessible(true);
		field.set(client, 100);
		field.setAccessible(false);
		
		clientConnectException = null;
		
		client.connect(ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT) //
				.exceptionally(t -> {
					clientConnectException = t;
					return null;
				}) //
				.get();
		
		assertNotNull(clientConnectException);
		assertEquals(ConnectException.class, clientConnectException.getCause().getClass());
	}
	
	public static class SimpleMessage {
		public String message;
		
		public SimpleMessage setMessage(String message) {
			this.message = message;
			return this;
		}
	}
	
	public static class UnregisteredMessage {}
}
