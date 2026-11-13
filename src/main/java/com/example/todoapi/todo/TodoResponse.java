package com.example.todoapi.todo;

import java.time.LocalDateTime;

/**
 * 할 일 응답. Entity를 그대로 돌려주면 연결된 User(비밀번호 해시 포함)까지 JSON에 실릴 수 있으므로 보여줄 값만 담는다.
 */
public record TodoResponse(Long id, String title, boolean completed, LocalDateTime createdAt) {

	public static TodoResponse from(Todo todo) {
		return new TodoResponse(todo.getId(), todo.getTitle(), todo.isCompleted(), todo.getCreatedAt());
	}

}
