package com.example.todoapi.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 할 일 추가 요청 본문. 예: {"title":"우유 사기"}
 * 사용자 id는 일부러 필드로 두지 않는다. 본문에 "userId"를 넣어 보내도 이 객체에 담기지 않고 버려진다.
 * 누구의 할 일인지는 오직 토큰(인터셉터가 꺼내 둔 값)으로만 정한다.
 */
public record TodoCreateRequest(

		@NotBlank(message = "제목은 필수입니다.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.") // Todo.title 컬럼 길이와 맞춘다.
		String title) {
}
