/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.manus.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.alibaba.cloud.ai.manus.user.model.po.UserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByUsername(String username);

	Optional<UserEntity> findByEmail(String email);

	List<UserEntity> findByStatus(String status);

	@Query("SELECT u FROM UserEntity u WHERE u.displayName LIKE %:displayName%")
	List<UserEntity> findByDisplayNameContaining(String displayName);

	@Query("SELECT COUNT(u) FROM UserEntity u WHERE u.status = :status")
	long countByStatus(String status);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

}
