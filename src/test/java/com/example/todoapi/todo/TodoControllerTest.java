package com.example.todoapi.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapi.auth.JwtTokenProvider;
import com.example.todoapi.user.User;
import com.example.todoapi.user.UserRepository;

/**
 * 인터셉터~컨트롤러~서비스~H2 DB까지 실제로 연결한 상태에서 POST /todos, GET /todos를 검사한다.
 * @Transactional: 테스트가 끝나면 넣은 데이터를 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TodoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TodoRepository todoRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void noToken_returns401() throws Exception {
		mockMvc.perform(post("/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"우유 사기\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		assertThat(todoRepository.count()).isZero();
	}

	@Test
	void validRequest_returns201WithTodo() throws Exception {
		User user = saveUser("a@test.com");

		createTodo(user, "{\"title\":\"우유 사기\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("우유 사기"))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.createdAt").exists())
				// 사용자 정보(비밀번호 해시 포함)는 응답에 실리지 않는다.
				.andExpect(jsonPath("$.user").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		List<Todo> todos = todoRepository.findAll();
		assertThat(todos).hasSize(1);
		assertThat(todos.get(0).getUser().getId()).isEqualTo(user.getId());
	}

	@Test
	void blankTitle_returns400() throws Exception {
		User user = saveUser("a@test.com");

		createTodo(user, "{\"title\":\"\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("제목은 필수입니다."));
		createTodo(user, "{\"title\":\"   \"}")
				.andExpect(status().isBadRequest());
		createTodo(user, "{}")
				.andExpect(status().isBadRequest());

		assertThat(todoRepository.count()).isZero();
	}

	@Test
	void titleOver200Chars_returns400() throws Exception {
		User user = saveUser("a@test.com");

		createTodo(user, "{\"title\":\"" + "a".repeat(201) + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("제목은 200자 이하여야 합니다."));
	}

	@Test
	void titleExactly200Chars_returns201() throws Exception {
		User user = saveUser("a@test.com");

		createTodo(user, "{\"title\":\"" + "a".repeat(200) + "\"}")
				.andExpect(status().isCreated());
	}

	@Test
	void otherUserIdInBody_isIgnored() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");

		// 본문에 남의 id를 넣어도 토큰의 사용자(me)로 저장돼야 한다.
		createTodo(me, "{\"title\":\"내 할 일\",\"userId\":" + other.getId() + ",\"user\":{\"id\":" + other.getId() + "}}")
				.andExpect(status().isCreated());

		List<Todo> todos = todoRepository.findAll();
		assertThat(todos).hasSize(1);
		assertThat(todos.get(0).getUser().getId()).isEqualTo(me.getId());
	}

	@Test
	void tokenUserNotInDb_returns401() throws Exception {
		// 서명은 올바르지만 DB에 없는 사용자 id가 담긴 토큰.
		String token = jwtTokenProvider.createToken(999_999L);

		mockMvc.perform(post("/todos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"우유 사기\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		assertThat(todoRepository.count()).isZero();
	}

	// ===== UC-4 GET /todos =====

	@Test
	void list_noToken_returns401() throws Exception {
		mockMvc.perform(get("/todos"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));
	}

	@Test
	void list_noTodos_returnsEmptyArray() throws Exception {
		User me = saveUser("me@test.com");

		listTodos(me, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void list_returnsOnlyMyTodos() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");
		todoRepository.save(new Todo(me, "내 할 일 1"));
		todoRepository.save(new Todo(other, "남의 할 일 1"));
		todoRepository.save(new Todo(me, "내 할 일 2"));
		todoRepository.save(new Todo(other, "남의 할 일 2"));

		listTodos(me, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].title", containsInAnyOrder("내 할 일 1", "내 할 일 2")))
				.andExpect(jsonPath("$[*].title", not(hasItem(startsWith("남의")))))
				// 사용자 정보(비밀번호 해시 포함)는 응답에 실리지 않는다.
				.andExpect(jsonPath("$[0].user").doesNotExist())
				.andExpect(jsonPath("$[0].password").doesNotExist());
	}

	@Test
	void list_otherUserIdInQuery_isIgnored() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");
		todoRepository.save(new Todo(me, "내 할 일"));
		todoRepository.save(new Todo(other, "남의 할 일"));

		// 주소에 남의 id를 넣어도 토큰의 사용자(me) 것만 나와야 한다.
		listTodos(me, "?userId=" + other.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("내 할 일"));
	}

	@Test
	void list_sortedNewestFirst() throws Exception {
		User me = saveUser("me@test.com");
		todoRepository.save(new Todo(me, "첫째"));
		todoRepository.save(new Todo(me, "둘째"));
		todoRepository.save(new Todo(me, "셋째"));

		listTodos(me, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].title", contains("셋째", "둘째", "첫째")));
	}

	@Test
	void list_tokenUserNotInDb_returns401() throws Exception {
		String token = jwtTokenProvider.createToken(999_999L);

		mockMvc.perform(get("/todos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));
	}

	private ResultActions listTodos(User user, String query) throws Exception {
		String token = jwtTokenProvider.createToken(user.getId());
		return mockMvc.perform(get("/todos" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private User saveUser(String email) {
		// 이 테스트는 로그인 과정이 아니라 할 일 추가를 검사하므로 비밀번호 해시는 형식만 맞춘 가짜 값을 쓴다.
		return userRepository.save(new User(email, "$2a$10$fakehashfakehashfakehashfakehashfakehashfakehashfake"));
	}

	private ResultActions createTodo(User user, String json) throws Exception {
		String token = jwtTokenProvider.createToken(user.getId());
		return mockMvc.perform(post("/todos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

}
