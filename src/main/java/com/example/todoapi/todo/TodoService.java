package com.example.todoapi.todo;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapi.auth.JwtTokenProvider;
import com.example.todoapi.common.ApiException;
import com.example.todoapi.user.User;
import com.example.todoapi.user.UserRepository;

/**
 * 할 일 관련 비즈니스 로직.
 */
@Service
public class TodoService {

	public static final String TODO_NOT_FOUND_MESSAGE = "할 일을 찾을 수 없습니다.";

	private final TodoRepository todoRepository;
	private final UserRepository userRepository;

	public TodoService(TodoRepository todoRepository, UserRepository userRepository) {
		this.todoRepository = todoRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public TodoResponse create(Long userId, TodoCreateRequest request) {
		User user = findUserOrThrow(userId);

		Todo saved = todoRepository.save(new Todo(user, request.title()));
		return TodoResponse.from(saved);
	}

	// readOnly = true: 조회만 하는 작업이라고 알려 준다. (데이터를 바꾸지 않음)
	@Transactional(readOnly = true)
	public List<TodoResponse> findMyTodos(Long userId) {
		// 사용자가 없는데 그냥 조회하면 0건이라 "할 일이 없음(200 [])"으로 잘못 보이므로 먼저 확인한다.
		findUserOrThrow(userId);

		return todoRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(TodoResponse::from)
				.toList();
	}

	// UC-5 완료/미완료 변경. 트랜잭션 안에서 Entity 값을 바꾸면 끝날 때 UPDATE가 자동으로 나간다.
	@Transactional
	public TodoResponse changeCompleted(Long userId, Long todoId, boolean completed) {
		Todo todo = findMyTodoOrThrow(userId, todoId);
		todo.changeCompleted(completed);
		return TodoResponse.from(todo);
	}

	// UC-5 삭제. 테이블에서 행을 완전히 지운다.
	@Transactional
	public void delete(Long userId, Long todoId) {
		Todo todo = findMyTodoOrThrow(userId, todoId);
		todoRepository.delete(todo);
	}

	// 완료/삭제가 공통으로 거치는 확인 순서: 1) 사용자가 있는가(401) 2) 그 사용자의 할 일인가(404)
	// 없는 할 일과 남의 할 일은 같은 쿼리 결과(빈 값)이므로 같은 404로 응답한다.
	// 남의 할 일에 403을 주면 "그 id는 존재한다"는 정보가 새어 나가기 때문이다.
	private Todo findMyTodoOrThrow(Long userId, Long todoId) {
		findUserOrThrow(userId);
		return todoRepository.findByIdAndUserId(todoId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TODO_NOT_FOUND_MESSAGE));
	}

	// 토큰 서명은 맞아도, 토큰 발급 뒤 DB가 초기화됐으면(메모리 DB라 서버 재시작 시) 사용자가 없을 수 있다.
	// 이때는 다른 인증 실패와 같은 401 응답을 돌려준다.
	private User findUserOrThrow(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, JwtTokenProvider.UNAUTHORIZED_MESSAGE));
	}

}
