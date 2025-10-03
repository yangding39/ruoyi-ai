# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RuoYi AI is an enterprise-level AI assistant platform built with Spring Boot 3.4 and Java 17. It provides deep integration with mainstream AI platforms including FastGPT, Coze (扣子), and DIFY, offering advanced RAG technology and multi-model support.

## Architecture

### Core Technology Stack
- **Backend**: Spring Boot 3.4 + Spring AI + Langchain4j
- **Database**: MySQL 8.0 + Redis + Vector databases (Milvus/Weaviate/Qdrant)
- **Security**: Sa-Token + JWT authentication
- **Build Tool**: Maven (multi-module project)
- **Java Version**: 17

### Module Structure
```
ruoyi-ai/
├── ruoyi-admin/          # Web service entry point (main application)
├── ruoyi-modules/        # Core business modules
│   ├── ruoyi-chat/       # Chat and AI integration module
│   ├── ruoyi-system/     # System management module
│   ├── ruoyi-generator/  # Code generation module
│   └── ruoyi-wechat/     # WeChat integration module
├── ruoyi-modules-api/    # API definitions and DTOs
├── ruoyi-common/         # Common utilities and configurations
└── ruoyi-extend/         # Extension modules
    ├── ruoyi-ai-copilot/ # AI copilot features
    └── ruoyi-mcp-server/ # MCP server implementation
```

## Development Commands

### Build and Run
```bash
# Compile the project
mvn clean compile

# Package the application
mvn clean package -DskipTests

# Run with dev profile (default)
mvn spring-boot:run -pl ruoyi-admin

# Run with specific profile
mvn spring-boot:run -pl ruoyi-admin -Dspring.profiles.active=local
```

### Testing
```bash
# Run tests
mvn test

# Run tests for specific profile
mvn test -Dspring.profiles.active=dev

# Skip tests during build
mvn clean package -DskipTests
```

### Database Setup
- SQL files are located in `script/sql/ruoyi-ai.sql`
- Database initialization scripts are in `script/deploy/*/mysql-init/`
- Default database: MySQL 8.0

### Database Design Standards

#### Table Naming Convention
**【MANDATORY】** Table and field names must use lowercase letters or numbers. No starting with numbers. No consecutive underscores with only numbers between them.

**【MANDATORY】** Table naming pattern: `business-module-function` using underscore separation.

**Examples:**
```sql
chat_config         -- Chat business - Config module - Config function
chat_message        -- Chat business - Message module - Message function
chat_model          -- Chat business - Model module - Model function
sys_user           -- System business - User module - User function
knowledge_role     -- Knowledge business - Role module - Role function
external_knowledge_apis  -- External knowledge API configuration
```

**Anti-patterns:**
```sql
chatConfig         -- camelCase not allowed
ChatMessage        -- PascalCase not allowed
chat-model         -- Hyphen not allowed
user1              -- Number suffix discouraged
2user              -- Number prefix forbidden
user__info         -- Double underscore forbidden
```

#### Field Naming Convention
**【MANDATORY】** Field names must use lowercase letters or numbers with underscore separation.

**【RECOMMENDED】** Boolean fields should use `is_xxx` pattern, type `char(1)`, where 1=true, 0=false.

**Examples:** `is_deleted`, `is_enabled`

#### Required Common Fields
**【RECOMMENDED】** All tables should include these common fields:

| Field | Type | Default | Description | Required |
|-------|------|---------|-------------|----------|
| `id` | `bigint(20)` | AUTO_INCREMENT | Primary key ID | Yes |
| `create_time` | `datetime` | NULL | Creation timestamp | Yes |
| `update_time` | `datetime` | NULL | Update timestamp | Yes |
| `create_by` | `bigint(20)` | NULL | Creator user ID | Yes |
| `update_by` | `bigint(20)` | NULL | Updater user ID | Yes |
| `create_dept` | `bigint(20)` | NULL | Creator dept ID | Yes |
| `del_flag` | `char(1)` | '0' | Delete flag (0=exist, 1=deleted) | Recommended |
| `tenant_id` | `varchar(20)` | '000000' | Tenant ID | Multi-tenant required |
| `remark` | `varchar(500)` | NULL | Remark/Notes | Yes |
| `version` | `int(11)` | NULL | Version (optimistic lock) | Optional |

**Field Descriptions:**
- **`id`**: Primary key using Snowflake algorithm (bigint)
- **`create_time`**: Creation timestamp for audit trail
- **`update_time`**: Last update timestamp for cache invalidation
- **`create_by`**: Creator user ID for permission control
- **`update_by`**: Updater user ID for audit trail
- **`del_flag`**: Logical delete flag (0=normal, 1=deleted)
- **`tenant_id`**: Tenant isolation field for multi-tenancy
- **`remark`**: Business notes and comments

#### SQL Update Management

**Directory Structure:**
```
script/
├── sql/
│   ├── ruoyi-ai.sql          # Initial DB schema
│   └── update/               # Incremental updates
│       ├── 2024-05-24-chat-message-billing-type.sql
│       ├── 2024-07-13-chat-model-priority.sql
│       └── 2025-01-XX-external-knowledge-api.sql
└── deploy/
    └── deploy/
        └── mysql-init/
            └── ruoyi-ai.sql  # Docker init (sync with main)
```

**【MANDATORY】** Incremental SQL updates must be placed in `script/sql/update/` directory.

**【MANDATORY】** Update SQL file naming: `YYYY-MM-DD-feature-description.sql`

**【MANDATORY】** Each update SQL file must include:
- Header comment explaining the change
- Change date and author
- Specific DDL/DML statements

**Example:**
```sql
-- Add billing_type field to chat_message table
-- Change date: 2024-05-24
-- Author: Zhang San
-- Description: Support message billing type differentiation

ALTER TABLE chat_message
    ADD COLUMN billing_type char NULL COMMENT '计费类型（1-token计费，2-次数计费，null-普通消息）';
```

**【MANDATORY】** When database changes occur, you MUST:
1. Add incremental SQL patch to `script/sql/update/`
2. Sync changes to init file `script/sql/ruoyi-ai.sql`
3. Sync changes to Docker init `script/deploy/deploy/mysql-init/ruoyi-ai.sql`

**Note:** Ensure all three files maintain consistent final state.

## Configuration

### Application Profiles
- **dev** (default): Development environment
- **local**: Local development with debug logging
- **prod**: Production environment

### Main Configuration File
- Primary config: `ruoyi-admin/src/main/resources/application.yml`
- Environment configs: `application-{profile}.yml`
- Default port: 6039

### Key Configuration Areas
- **AI Integration**: Spring AI configuration for OpenAI and other providers
- **MCP Server**: Model Context Protocol server configuration
- **Vector Databases**: Support for Milvus, Weaviate, Qdrant
- **Security**: Sa-Token configuration with JWT support
- **WebSocket**: Real-time communication configuration

## AI Platform Integration

### Supported Platforms
- **FastGPT**: Native API integration with knowledge base retrieval
- **Coze (扣子)**: Official SDK integration for ByteDance's platform
- **DIFY**: Complete compatibility with Java client
- **OpenAI**: GPT-4, Azure OpenAI integration
- **Local Models**: Ollama, vLLM support

### Chat Module (`ruoyi-chat`)
- Main chat service implementation
- AI model management and switching
- Streaming response handling (SSE/WebSocket)
- Multi-modal processing (text, images, documents)

## Development Guidelines

### Module Dependencies
- `ruoyi-admin` is the main entry point and depends on core modules
- `ruoyi-chat` handles all AI-related functionality
- `ruoyi-system` provides user management and system administration
- Always check existing module dependencies before adding new ones

### Security Considerations
- All chat endpoints are secured through Sa-Token
- Some endpoints are excluded from authentication (see application.yml security.excludes)
- JWT tokens are used for API authentication

### AI Development
- Use Spring AI abstractions when possible
- Implement streaming responses for real-time chat
- Follow existing patterns in the chat module for AI integrations
- Vector database operations are handled through Langchain4j

## Extension Points

### MCP Server (`ruoyi-mcp-server`)
- Standalone MCP server implementation
- Configured via `mcp-server.json`
- Supports both SSE and stdio connections

### AI Copilot (`ruoyi-ai-copilot`)
- AI-powered coding assistant features
- Based on Spring AI Alibaba framework

## Coding Standards

### Package Structure and Naming
- Follow the established package hierarchy: `org.ruoyi.{module}.{layer}`
- **Controllers**: `org.ruoyi.{module}.controller` - REST API endpoints
- **Services**: `org.ruoyi.{module}.service` - Business logic interfaces and implementations
- **Mappers**: `org.ruoyi.{module}.mapper` - Data access layer (MyBatis)
- **Domain Objects**: `org.ruoyi.{module}.domain` - Entity classes and data models
- **DTOs**: `org.ruoyi.common.core.domain.{dto,vo,bo}` - Data transfer objects

### Data Transfer Objects (DTO) Conventions

#### Business Objects (Bo)
- Suffix: `Bo` (e.g., `SchemaBo`)
- Purpose: Input parameters for business operations
- Validation: Use `@NotNull`, `@NotBlank` with validation groups (`AddGroup.class`, `EditGroup.class`)
- Mapping: Use `@AutoMapper` for entity conversion
- Example:
```java
@Data
@AutoMapper(target = Schema.class, reverseConvertGenerate = false)
public class SchemaBo implements Serializable {
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    @NotBlank(message = "模型名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;
}
```

#### View Objects (Vo)
- Suffix: `Vo` (e.g., `SchemaVo`)
- Purpose: Output data for API responses
- Mapping: Use `@AutoMapper(target = Entity.class)`
- Always implement `Serializable`
- Include `@Serial` for serialVersionUID

#### Response Wrapper
- Use the common `R<T>` class for all API responses
- Success: `R.ok()`, `R.ok(data)`, `R.ok(message, data)`
- Failure: `R.fail(message)`, `R.fail(code, message)`
- Standard status codes: 200 (success), 500 (failure)

### Controller Standards

#### Annotations
- Class level: `@RestController`, `@RequestMapping("/path")`, `@RequiredArgsConstructor`
- Method level: `@PostMapping`, `@GetMapping`, `@PutMapping`, `@DeleteMapping`
- Security: `@SaIgnore` for public endpoints
- Validation: `@Validated` on class and `@Validated @RequestBody` on parameters

#### Method Structure
```java
@PostMapping("/operation")
public R<ResponseVo> operation(@Validated @RequestBody RequestBo request) {
    // Business logic through service layer
    ResponseVo result = service.performOperation(request);
    return R.ok(result);
}
```

### Service Layer Standards

#### Interface Definition
- Interface: `I{Entity}Service` (e.g., `ISysTenantService`)
- Implementation: `{Entity}ServiceImpl`
- Use `@RequiredArgsConstructor` for dependency injection
- Methods should be descriptive and follow business domain terminology

#### Service Implementation
- Extend appropriate base services when available
- Use `@Transactional` for database operations
- Handle exceptions appropriately
- Return appropriate DTO objects (Vo for queries, success indicators for operations)

### Data Access Layer (Mapper)

#### MyBatis Mappers
- Interface: Extend `BaseMapperPlus<Entity, Vo>`
- Annotation: `@Mapper`
- Generic types: `<Entity, ViewObject>`
- XML mappings: Located in `src/main/resources/mapper/`

Example:
```java
@Mapper
public interface SchemaMapper extends BaseMapperPlus<Schema, SchemaVo> {
    // Custom query methods if needed
}
```

### Entity/Domain Standards

#### Entity Classes
- Use Lombok annotations: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Implement `Serializable` with `@Serial` for serialVersionUID
- Use MyBatis-Plus annotations: `@TableName`, `@TableId`, `@TableField`
- Follow database naming conventions (snake_case) mapped to camelCase

#### Validation Groups
- `AddGroup.class` - for create operations
- `EditGroup.class` - for update operations
- Apply to validation annotations for conditional validation

### Security and Authentication

#### Sa-Token Integration
- Use `@SaIgnore` for public endpoints
- Access user info via `LoginHelper.getLoginUser()`
- Token management through Sa-Token framework
- JWT integration for token generation

#### API Security
- Most endpoints require authentication except those listed in `security.excludes`
- Use appropriate HTTP methods (GET for queries, POST for creation, PUT for updates, DELETE for removal)
- Validate all input parameters

### Error Handling and Validation

#### Input Validation
- Use Jakarta Validation annotations
- Group validations by operation type (Add/Edit groups)
- Provide meaningful error messages in Chinese
- Validate at controller level with `@Validated`

#### Exception Handling
- Use the framework's built-in exception handling
- Return appropriate HTTP status codes
- Provide user-friendly error messages

### AI Integration Standards

#### Chat Module Structure
- Place AI-related functionality in `ruoyi-chat` module
- Use Spring AI abstractions when possible
- Implement streaming responses using SSE or WebSocket
- Follow existing patterns for model management and switching

#### MCP Integration
- Configuration in `mcp-server.json`
- Support both SSE and stdio connections
- Follow MCP protocol standards

## Deployment

### Docker Support
- Docker configurations available in `script/deploy/build-docker-images/`
- Separate containers for backend, web, and admin components
- Vector database Docker compose files in `script/docker/`

### Environment Variables
- Profiles are managed through Maven profiles
- Database connections configured per environment
- AI API keys and endpoints configurable per profile