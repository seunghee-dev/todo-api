package com.example.todoapi.todo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapi.auth.AuthInterceptor;

import jakarta.validation.Valid;

/**
 * UC-3 할 일 추가 API.
 * /todos는 WebConfig에서 열어 두지 않았으므로 AuthInterceptor가 먼저 토큰을 검사한다.
 */
@RestController
public class TodoController {

	private final TodoService todoService;

	public TodoController(TodoService todoService) {
		this.todoService = todoService;
	}

	// userId는 요청 본문이 아니라 인터셉터가 토큰에서 꺼내 request에 담아 둔 값을 쓴다.
	// (JSP 시절 request.getAttribute("userId")에 해당)
	@PostMapping("/todos")
	@ResponseStatus(HttpStatus.CREATED)
	public TodoResponse create(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@Valid @RequestBody TodoCreateRequest request) {
		return todoService.create(userId, request);
	}

}
