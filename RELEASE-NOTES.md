# Lynxe AI Agent Management System - Release Notes
## Version 4.10.5

### 🚀 新增功能

#### 1. 聊天流取消支持 (Chat Stream Cancellation)
- 实现了后端对活动聊天流的取消支持
- 在 `LynxeController` 和 `DirectApiService` 中添加了取消机制
- 前端组件 `useMessageDialog` 和 `useTaskStop` 进行了重构以跟踪和管理流状态
- 添加了 `activeStreamAbortController` 和 `currentStreamId` 来管理流的生命周期

#### 2. 异步执行工具 (Async Execution Tool)
- 新增 `StartAsyncExecutionTool` 实现了异步执行模式
- 支持注册函数的后台执行（即发即弃模式）
- 提供立即返回成功状态而无需等待结果的功能
- 包含错误处理和结果映射机制

#### 3. 令牌限制服务 (Token Limit Service)
- 新增 `TokenLimitService` 用于管理不同LLM模型的令牌限制
- 支持多种主流模型的上下文和输出限制：
  - Qwen系列模型 (qwen3-coder-plus, qwen3-coder-flash)
  - Gemini系列模型 (gemini-1.5-pro, gemini-1.5-flash)
  - GPT系列模型 (gpt-4o, gpt-4-turbo, gpt-4, gpt-3.5-turbo)
- 实现了精确匹配、不区分大小写匹配和前缀匹配逻辑

#### 4. 递归调用链跟踪 (Recursive Call Chain Tracking)
- 在 `DynamicAgent` 及相关类中增强了递归调用链跟踪功能
- 添加了 `RecursiveCallLimitExceededException` 用于处理递归调用超限
- 实现了调用链深度监控和限制机制

#### 5. 令牌计数功能 (Token Counting Functionality)
- 从字符计数改为令牌计数方式
- 添加了会话令牌限制配置
- 集成了 `jtokkit` 库进行精确的令牌计算

### ⚡ 性能改进

#### 1. 聊天压缩机制 (Chat Compression Mechanism)
- 实现了可配置的聊天压缩阈值 (`chatCompressionThreshold`)
- 添加了压缩保留比例配置 (`chatCompressionRetentionRatio`)
- 默认设置为70%阈值和30%保留率
- 替换了原有的 `conversationMemoryMaxChars` 配置

#### 2. 文件读取控制 (File Reading Control)
- 添加了 `maxLinesForFullRead` 配置项，默认值为700
- 更新了 `ReadFileOperator` 和 `ReadExternalLinkFileOperator` 以使用此属性
- 提供了更好的文件读取控制和性能优化

#### 3. 统一加载状态管理 (Unified Loading State Management)
- 将 `isLoading` 重命名为 `isRunning` 以统一任务执行跟踪
- 在多个组件中更新了状态管理逻辑，包括 `useMessageDialog`、`ExecutionController` 和 `InputArea`

### 🔧 配置增强

#### 1. MCP连接管理 (MCP Connection Management)
- 增强了MCP连接管理和UI集成
- 添加了 `McpConnectionStatus` 和 `ConnectionStatusInfo` 类型定义
- 改进了 `McpCacheManager` 和 `McpConnectionFactory` 功能

#### 2. 执行上下文改进 (Execution Context Improvements)
- 增强了 `ExecutionContext` 以支持额外的消息初始化
- 更新了 `DynamicAgent` 和 `ConfigurableDynaAgent` 以支持额外消息

### 🐛 错误修复

#### 1. 错误报告增强 (Enhanced Error Reporting)
- 在 `BaseAgent` 和 `DynamicAgent` 中增强了带有额外上下文的错误报告
- 改进了输出格式化和空白处理以提高可读性

#### 2. 模型上下文限制支持 (Model Context Limit Support)
- 在 `DynamicAgent` 及相关类中增强了模型上下文限制跟踪
- 实现了对不同模型上下文窗口的动态适应

### 📝 其他改进

#### 1. 代码重构 (Code Refactoring)
- 重构了多处代码以提高可读性和一致性
- 改进了注释和代码结构

#### 2. 国际化支持 (Internationalization)
- 添加了新的国际化条目，包括 `maxLinesForFullRead` 和异步执行工具
- 更新了中英文本地化文件

### 版本信息
- 主版本号：4
- 次版本号：10
- 修订版本号：5
- 发布日期：2026年1月26日

### 技术栈更新
- Spring Boot: 3.5.6
- Spring AI: 最新版本
- 各种依赖库更新至最新稳定版本