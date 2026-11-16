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

	// 토큰 서명은 맞아도, 토큰 발급 뒤 DB가 초기화됐으면(메모리 DB라 서버 재시작 시) 사용자가 없을 수 있다.
	// 이때는 다른 인증 실패와 같은 401 응답을 돌려준다.
	private User findUserOrThrow(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, JwtTokenProvider.UNAUTHORIZED_MESSAGE));
	}

}
