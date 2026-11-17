package com.example.todoapi.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import jakarta.persistence.EntityManager;

/**
 * 인터셉터~컨트롤러~서비스~H2 DB까지 실제로 연결한 상태에서 POST/GET /todos, PATCH/DELETE /todos/{id}를 검사한다.
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

	@Autowired
	private EntityManager entityManager;

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

	// ===== UC-5 PATCH /todos/{id}, DELETE /todos/{id} =====

	@Test
	void patch_myTodo_changesTrueAndFalse() throws Exception {
		User me = saveUser("me@test.com");
		Todo todo = todoRepository.save(new Todo(me, "우유 사기"));

		patchTodo(me, todo.getId(), "{\"completed\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(todo.getId()))
				.andExpect(jsonPath("$.title").value("우유 사기"))
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.user").doesNotExist());
		assertThat(reloadFromDb(todo.getId()).isCompleted()).isTrue();

		patchTodo(me, todo.getId(), "{\"completed\":false}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false));
		assertThat(reloadFromDb(todo.getId()).isCompleted()).isFalse();
	}

	@Test
	void patch_otherUsersTodo_returns404AndDbUnchanged() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");
		Todo othersTodo = todoRepository.save(new Todo(other, "남의 할 일"));

		patchTodo(me, othersTodo.getId(), "{\"completed\":true}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message").value(TodoService.TODO_NOT_FOUND_MESSAGE));

		Todo reloaded = reloadFromDb(othersTodo.getId());
		assertThat(reloaded.isCompleted()).isFalse();
		assertThat(reloaded.getTitle()).isEqualTo("남의 할 일");
		assertThat(reloaded.getUser().getId()).isEqualTo(other.getId());
	}

	@Test
	void delete_otherUsersTodo_returns404AndStillInDb() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");
		Todo othersTodo = todoRepository.save(new Todo(other, "남의 할 일"));

		deleteTodo(me, othersTodo.getId())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(TodoService.TODO_NOT_FOUND_MESSAGE));

		Todo reloaded = reloadFromDb(othersTodo.getId());
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.getUser().getId()).isEqualTo(other.getId());
	}

	@Test
	void missingIdAndOthersId_returnExactlySameBody() throws Exception {
		User me = saveUser("me@test.com");
		User other = saveUser("other@test.com");
		Todo othersTodo = todoRepository.save(new Todo(other, "남의 할 일"));
		long missingId = 999_999L;

		String patchMissing = patchTodo(me, missingId, "{\"completed\":true}")
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String patchOthers = patchTodo(me, othersTodo.getId(), "{\"completed\":true}")
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String deleteMissing = deleteTodo(me, missingId)
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String deleteOthers = deleteTodo(me, othersTodo.getId())
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		// 응답만 보고는 "없는 id"인지 "남의 id"인지 알 수 없어야 한다.
		assertThat(patchOthers).isEqualTo(patchMissing);
		assertThat(deleteOthers).isEqualTo(deleteMissing);
		assertThat(deleteMissing).isEqualTo(patchMissing);
	}

	@Test
	void delete_myTodo_returns204AndRemovedFromDb() throws Exception {
		User me = saveUser("me@test.com");
		Todo todo = todoRepository.save(new Todo(me, "우유 사기"));

		deleteTodo(me, todo.getId())
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		assertThat(reloadFromDb(todo.getId())).isNull();
	}

	@Test
	void delete_alreadyDeleted_returns404() throws Exception {
		User me = saveUser("me@test.com");
		Todo todo = todoRepository.save(new Todo(me, "우유 사기"));

		deleteTodo(me, todo.getId()).andExpect(status().isNoContent());
		entityManager.flush();
		entityManager.clear();

		deleteTodo(me, todo.getId())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(TodoService.TODO_NOT_FOUND_MESSAGE));
	}

	@Test
	void patch_badBody_returns400AndDbUnchanged() throws Exception {
		User me = saveUser("me@test.com");
		Todo todo = todoRepository.save(new Todo(me, "우유 사기"));

		// 본문 없음
		mockMvc.perform(patch("/todos/" + todo.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(me))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		// 빈 문자열 본문
		patchTodo(me, todo.getId(), "")
				.andExpect(status().isBadRequest());
		// completed 빠짐
		patchTodo(me, todo.getId(), "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("completed 값은 필수입니다."));
		// completed가 null
		patchTodo(me, todo.getId(), "{\"completed\":null}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("completed 값은 필수입니다."));
		// 깨진 JSON
		patchTodo(me, todo.getId(), "{broken")
				.andExpect(status().isBadRequest());

		assertThat(reloadFromDb(todo.getId()).isCompleted()).isFalse();
	}

	@Test
	void nonNumericId_returns400() throws Exception {
		User me = saveUser("me@test.com");

		// 처리 추가 전에는 500("서버 내부 오류가 발생했습니다.")이었다.
		patchTodo(me, "abc", "{\"completed\":true}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("요청 값 'id'의 형식이 올바르지 않습니다."));
		mockMvc.perform(delete("/todos/abc").header(HttpHeaders.AUTHORIZATION, bearer(me)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void patchAndDelete_noToken_returns401() throws Exception {
		User me = saveUser("me@test.com");
		Todo todo = todoRepository.save(new Todo(me, "우유 사기"));

		mockMvc.perform(patch("/todos/" + todo.getId())
				.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));
		mockMvc.perform(delete("/todos/" + todo.getId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		Todo reloaded = reloadFromDb(todo.getId());
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.isCompleted()).isFalse();
	}

	@Test
	void patch_tokenUserNotInDb_returns401() throws Exception {
		User owner = saveUser("owner@test.com");
		Todo todo = todoRepository.save(new Todo(owner, "우유 사기"));
		String token = jwtTokenProvider.createToken(999_999L);

		mockMvc.perform(patch("/todos/" + todo.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"completed\":true}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		assertThat(reloadFromDb(todo.getId()).isCompleted()).isFalse();
	}

	@Test
	void delete_tokenUserNotInDb_returns401() throws Exception {
		User owner = saveUser("owner@test.com");
		Todo todo = todoRepository.save(new Todo(owner, "우유 사기"));
		String token = jwtTokenProvider.createToken(999_999L);

		mockMvc.perform(delete("/todos/" + todo.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		assertThat(reloadFromDb(todo.getId())).isNotNull();
	}

	// 1차 캐시(영속성 컨텍스트)에 남은 객체가 아니라 실제 DB 값을 읽기 위해
	// 쌓인 변경을 DB로 보내고(flush) 캐시를 비운(clear) 뒤 다시 조회한다. 없으면 null.
	private Todo reloadFromDb(Long id) {
		entityManager.flush();
		entityManager.clear();
		return todoRepository.findById(id).orElse(null);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.createToken(user.getId());
	}

	private ResultActions patchTodo(User user, Object id, String json) throws Exception {
		return mockMvc.perform(patch("/todos/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private ResultActions deleteTodo(User user, Object id) throws Exception {
		return mockMvc.perform(delete("/todos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
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
