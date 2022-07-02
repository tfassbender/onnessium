package net.jfabricationgames.onnessium.user.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.jfabricationgames.cdi.CdiContainer;
import net.jfabricationgames.cdi.annotation.Inject;
import net.jfabricationgames.onnessium.network.ClientServerConnectionTestUtil;
import net.jfabricationgames.onnessium.network.client.NetworkClient;
import net.jfabricationgames.onnessium.network.network.Network;
import net.jfabricationgames.onnessium.network.server.NetworkServer;
import net.jfabricationgames.onnessium.network.server.ServerMessageHandlerRegistry;
import net.jfabricationgames.onnessium.user.client.LoginHandler.LoginException;
import net.jfabricationgames.onnessium.user.dto.LoginDto;
import net.jfabricationgames.onnessium.user.dto.SignUpDto;
import net.jfabricationgames.onnessium.util.TestUtils;
import net.jfabricationgames.onnessium.util.Wrapper;

public class LoginHandlerIntegrationTest {
	
	private static NetworkServer server;
	private static NetworkClient client;
	
	private LoginHandler loginHandler = new LoginHandler();
	
	@Inject
	private ServerMessageHandlerRegistry handlerRegistry;
	
	@BeforeAll
	public static void setup() throws Exception {
		TestUtils.mockGdxApplication();
		TestUtils.createCdiContainer();
		
		Network.registerClass(LoginDto.class);
		Network.registerClass(SignUpDto.class);
	}
	
	@BeforeEach
	public void injectDependenciesAndResetHandlers() throws Throwable {
		CdiContainer.injectTo(this);
		
		client = new NetworkClient();
		
		ClientServerConnectionTestUtil.reduceConnectionTimeout(client);
		
		handlerRegistry.removeAllHandlers();
		client.removeAllMessageHandlersForType(LoginDto.class);
		client.removeAllMessageHandlersForType(SignUpDto.class);
		
		server = new NetworkServer();
		server.start(ClientServerConnectionTestUtil.PORT);
	}
	
	@AfterEach
	public void disconnect() {
		client.disconnect();
		server.stop();
	}
	
	@Test
	public void testSignUpSuccessful() throws LoginException {
		handlerRegistry.registerHandler(SignUpDto.class, (connection, dto) -> {
			dto.setSuccessful(true); // respond that the sign up was successful
			connection.sendTCP(dto);
		});
		
		Wrapper<Boolean> responseWrapper = Wrapper.empty();
		loginHandler.signUp("Arthur Dent", "42", ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> responseWrapper.wrapped = true);
	}
	
	@Test
	public void testSignUpNotSuccessful() throws LoginException {
		handlerRegistry.registerHandler(SignUpDto.class, (connection, dto) -> {
			dto.setSuccessful(false); // respond that the sign up was NOT successful
			connection.sendTCP(dto);
		});
		
		LoginException loginException = assertThrows(LoginException.class, () -> loginHandler.signUp("Arthur Dent", "42", //
				ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> {}));
		assertEquals("Sign up failed - Cannot connect to server", loginException.getMessage());
		
		//TODO the test fails, because the LoginHandler does not wait for the response of the server
	}
	
	@Test
	public void testSignUpNotSuccessfulBecauseServerIsNotStarted() throws LoginException {
		// the server is already started, so shut it down
		server.stop();
		
		handlerRegistry.registerHandler(SignUpDto.class, (connection, dto) -> {
			dto.setSuccessful(false); // respond that the sign up was NOT successful
			connection.sendTCP(dto);
		});
		
		LoginException loginException = assertThrows(LoginException.class, () -> loginHandler.signUp("Arthur Dent", "42", //
				ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> {}));
		assertEquals("Sign up failed - Cannot connect to server", loginException.getMessage());
	}
	
	//TODO test what happens when the server doesn't respond in a given time
	
	@Test
	public void testLoginSuccessful() throws LoginException {
		handlerRegistry.registerHandler(LoginDto.class, (connection, dto) -> {
			dto.setSuccessful(true); // respond that the login was successful
			connection.sendTCP(dto);
		});
		
		Wrapper<Boolean> responseWrapper = Wrapper.empty();
		loginHandler.login("Arthur Dent", "42", ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> responseWrapper.wrapped = true);
	}
	
	@Test
	public void testLoginNotSuccessful() throws LoginException {
		handlerRegistry.registerHandler(LoginDto.class, (connection, dto) -> {
			dto.setSuccessful(false); // respond that the login was NOT successful
			connection.sendTCP(dto);
		});
		
		LoginException loginException = assertThrows(LoginException.class, () -> loginHandler.login("Arthur Dent", "42", //
				ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> {}));
		assertEquals("Login failed - Cannot connect to server", loginException.getMessage());
		
		//TODO the test fails, because the LoginHandler does not wait for the response of the server
	}
	
	@Test
	public void testLoginNotSuccessfulBecauseServerIsNotStarted() throws LoginException {
		// the server is already started, so shut it down
		server.stop();
		
		handlerRegistry.registerHandler(LoginDto.class, (connection, dto) -> {
			dto.setSuccessful(false); // respond that the login was NOT successful
			connection.sendTCP(dto);
		});
		
		LoginException loginException = assertThrows(LoginException.class, () -> loginHandler.login("Arthur Dent", "42", //
				ClientServerConnectionTestUtil.HOST, ClientServerConnectionTestUtil.PORT, //
				() -> {}));
		assertEquals("Login failed - Cannot connect to server", loginException.getMessage());
	}
	
	//TODO test what happens when the server doesn't respond in a given time
}
