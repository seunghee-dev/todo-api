package com.example.todoapi.todo;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapi.auth.AuthInterceptor;

import jakarta.validation.Valid;

/**
 * UC-3 할 일 추가 API, UC-4 내 할 일 목록 조회 API, UC-5 할 일 완료/미완료 변경·삭제 API.
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

	// UC-4 내 할 일 목록 조회. 같은 주소라도 GET이면 이 메서드가 실행된다. (서블릿의 doGet/doPost와 같음)
	// @RequestParam으로 userId를 받지 않으므로 ?userId=5 같은 값은 읽지 않는다. 오직 토큰의 사용자만 본다.
	@GetMapping("/todos")
	public List<TodoResponse> findMyTodos(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return todoService.findMyTodos(userId);
	}

	// UC-5 완료/미완료 변경. 주소의 {id}는 할 일 id다. 사용자 id는 주소나 본문이 아니라 토큰에서만 가져온다.
	@PatchMapping("/todos/{id}")
	public TodoResponse changeCompleted(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long id,
			@Valid @RequestBody TodoCompletedRequest request) {
		return todoService.changeCompleted(userId, id, request.completed());
	}

	// UC-5 삭제. 성공하면 204(본문 없음).
	@DeleteMapping("/todos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
		todoService.delete(userId, id);
	}

}
