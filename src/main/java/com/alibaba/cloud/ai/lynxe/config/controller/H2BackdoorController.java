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
package com.alibaba.cloud.ai.lynxe.config.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * H2 backdoor for debugging: list tables, get schema, run read-only SQL. Enabled only when
 * lynxe.h2.backdoor.enabled=true (default off).
 */
@RestController
@RequestMapping("/api/h2-backdoor")
@CrossOrigin(origins = "*")
public class H2BackdoorController {

	private static final Logger logger = LoggerFactory.getLogger(H2BackdoorController.class);

	private static final String TABLES_SQL = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME";

	private static final String SCHEMA_SQL = "SELECT TABLE_NAME, COLUMN_NAME, TYPE_NAME, ORDINAL_POSITION, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";

	@Value("${lynxe.h2.backdoor.enabled:false}")
	private boolean backdoorEnabled;

	private final JdbcTemplate jdbcTemplate;

	public H2BackdoorController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> ifDisabled() {
		return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}

	/**
	 * List all table names in the H2 PUBLIC schema.
	 */
	@GetMapping("/tables")
	public ResponseEntity<List<String>> getTables() {
		if (!backdoorEnabled) {
			return ifDisabled();
		}
		try {
			List<String> tables = jdbcTemplate.queryForList(TABLES_SQL, String.class);
			return ResponseEntity.ok(tables != null ? tables : List.of());
		}
		catch (Exception e) {
			logger.warn("H2 backdoor /tables failed", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Get column schema for a table. Query param: table (required).
	 */
	@GetMapping("/schema")
	public ResponseEntity<List<Map<String, Object>>> getSchema(@RequestParam String table) {
		if (!backdoorEnabled) {
			return ifDisabled();
		}
		if (table == null || table.isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		try {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(SCHEMA_SQL, table.trim());
			return ResponseEntity.ok(rows != null ? rows : List.of());
		}
		catch (Exception e) {
			logger.warn("H2 backdoor /schema failed for table={}", table, e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Run a read-only SQL query. Only SELECT is allowed. Body: { "sql": "SELECT ..." }.
	 */
	@PostMapping("/query")
	public ResponseEntity<?> query(@RequestBody Map<String, String> body) {
		if (!backdoorEnabled) {
			return ifDisabled();
		}
		String sql = body != null ? body.get("sql") : null;
		if (sql == null || sql.isBlank()) {
			return ResponseEntity.badRequest().body("Missing \"sql\" in request body");
		}
		String trimmed = sql.trim();
		if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)) {
			return ResponseEntity.badRequest().body("Only SELECT statements are allowed");
		}
		try {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmed);
			return ResponseEntity.ok(rows != null ? rows : List.of());
		}
		catch (Exception e) {
			logger.warn("H2 backdoor /query failed: {}", e.getMessage());
			return ResponseEntity.badRequest().body("Query failed: " + e.getMessage());
		}
	}
}
