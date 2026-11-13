package com.example.todoapi.todo;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * todos 테이블에 접근하는 DAO. save(), findById() 같은 기본 메서드는 JpaRepository에 이미 들어 있다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {
}
