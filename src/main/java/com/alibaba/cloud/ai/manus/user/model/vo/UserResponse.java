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
package com.alibaba.cloud.ai.manus.user.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * User response model for API responses Contains user data with additional metadata
 */
public class UserResponse {

	private boolean success;

	private String message;

	private User data;

	private List<User> users;

	@JsonProperty("total_count")
	private Integer totalCount;

	@JsonProperty("response_time")
	private LocalDateTime responseTime;

	// Constructors
	public UserResponse() {
		this.responseTime = LocalDateTime.now();
	}

	public UserResponse(boolean success, String message) {
		this();
		this.success = success;
		this.message = message;
	}

	public UserResponse(boolean success, String message, User data) {
		this(success, message);
		this.data = data;
	}

	public UserResponse(boolean success, String message, List<User> users) {
		this(success, message);
		this.users = users;
		this.totalCount = users != null ? users.size() : 0;
	}

	// Getters and Setters
	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public User getData() {
		return data;
	}

	public void setData(User data) {
		this.data = data;
	}

	public List<User> getUsers() {
		return users;
	}

	public void setUsers(List<User> users) {
		this.users = users;
		this.totalCount = users != null ? users.size() : 0;
	}

	public Integer getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(Integer totalCount) {
		this.totalCount = totalCount;
	}

	public LocalDateTime getResponseTime() {
		return responseTime;
	}

	public void setResponseTime(LocalDateTime responseTime) {
		this.responseTime = responseTime;
	}

	// Static factory methods
	public static UserResponse success(User user) {
		return new UserResponse(true, "User retrieved successfully", user);
	}

	public static UserResponse success(List<User> users) {
		return new UserResponse(true, "Users retrieved successfully", users);
	}

	public static UserResponse error(String message) {
		return new UserResponse(false, message);
	}

	public static UserResponse notFound() {
		return new UserResponse(false, "User not found");
	}

}
