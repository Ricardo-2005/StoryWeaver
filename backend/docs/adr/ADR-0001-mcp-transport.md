# ADR-0001：MCP 使用 Spring AI Stateless Streamable HTTP

## 状态

已采纳，Phase 7。

## 决策

服务使用 Spring AI 2.0.0 的 `spring-ai-starter-mcp-server-webmvc`，配置 `protocol=STATELESS`，在 `/mcp` 提供标准 Streamable HTTP 单 POST 请求/响应。客户端通过 Bearer JWT 认证；MCP 传输适配器与 StoryWeaver 应用服务分离。

Spring AI 2.0.0 和当前官方 MCP Java SDK 可协商已发布的 MCP 协议版本（验收使用 `2025-11-25`）。设计稿中出现的 `2026-07-28` 不是本实现依赖所声明的已发布协议版本，因此本仓库不虚报该版本兼容性。

## 原因

- 官方 Starter 已实现 JSON-RPC、能力发现、工具、资源模板、提示词和 Stateless Streamable HTTP，无需维护自定义协议栈。
- Stateless 模式没有连接级会话状态，符合单 POST 和横向扩容目标。
- JWT 与既有 Spring Security 过滤链复用；应用服务继续执行项目所有权校验。
- StoryWeaver 当前不需要服务器向客户端采样、征询或持久连接通知。

## 限制

- 不提供服务器主动 SSE、会话恢复、Sampling、Elicitation 或 Roots 双向操作。
- 客户端必须发送受支持的协议版本和 Bearer JWT。
- 后续升级协议版本时先升级 Spring AI/MCP Java SDK 并补兼容性测试，不在业务层模拟新协议。
