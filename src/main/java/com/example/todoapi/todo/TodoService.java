package com.example.todoapi.todo;

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
		// 토큰 서명은 맞아도, 토큰 발급 뒤 DB가 초기화됐으면(메모리 DB라 서버 재시작 시) 사용자가 없을 수 있다.
		// 이때는 다른 인증 실패와 같은 401 응답을 돌려준다.
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, JwtTokenProvider.UNAUTHORIZED_MESSAGE));

		Todo saved = todoRepository.save(new Todo(user, request.title()));
		return TodoResponse.from(saved);
	}

}
