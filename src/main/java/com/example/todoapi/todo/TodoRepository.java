package com.example.todoapi.todo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * todos 테이블에 접근하는 DAO. save(), findById() 같은 기본 메서드는 JpaRepository에 이미 들어 있다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

	// 메서드 이름을 보고 아래 쿼리를 자동으로 만든다.
	// SELECT * FROM todos WHERE user_id = ? ORDER BY created_at DESC, id DESC
	// 같은 시각에 만든 할 일은 id가 큰 것(나중에 만든 것)을 먼저 보여 줘서 순서가 항상 같게 한다.
	List<Todo> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	// SELECT * FROM todos WHERE id = ? AND user_id = ?
	// 할 일을 꺼낸 뒤 주인을 비교하지 않고, 처음부터 "내 것 중에서" 찾는다.
	// 없는 할 일과 남의 할 일 모두 빈 Optional이 되므로 코드에서 둘을 구별할 수 없다.
	Optional<Todo> findByIdAndUserId(Long id, Long userId);

}
