package com.example.todoapi.todo;

import jakarta.validation.constraints.NotNull;

/**
 * 할 일 완료/미완료 변경 요청 본문. 예: {"completed": true}
 * boolean이 아니라 Boolean을 쓴다. boolean이면 "completed"가 빠졌을 때 false로 채워져 실수로 미완료가 되므로,
 * null로 받아 @NotNull로 400을 돌려준다.
 * 사용자 id는 일부러 필드로 두지 않는다. 누구의 할 일인지는 오직 토큰으로만 정한다.
 */
public record TodoCompletedRequest(

		@NotNull(message = "completed 값은 필수입니다.")
		Boolean completed) {
}
