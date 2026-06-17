# 接口文档 · AuthController（认证）

> 遵循《接口文档生成规范》。
>
> - 基础路径（@RequestMapping）：`/api/auth`
> - 统一返回包装：`Result<T>`
> - 对应服务：`IUserService`（认证与用户共用同一服务）
> - 业务定位：登录鉴权与当前用户信息获取，公开接口（登录）与鉴权接口（profile）混合。

---

## 一、Controller 功能表

| 类功能 | Auth 功能（登录、获取当前登录用户、登出占位） |
| --- | --- |
| **方法一** | `login(LoginDTO)` |
| | 功能：用户名密码登录，校验通过后签发 token |
| | 输入：请求体 `LoginDTO`（username、password） |
| | 输出：`Result<LoginVO>` —— token + 用户信息（UserVO） |
| | 路由：`POST /api/auth/login` |
| **方法二** | `profile()` |
| | 功能：获取当前登录用户信息（从鉴权上下文取） |
| | 输入：无（依赖请求头中的 token） |
| | 输出：`Result<UserVO>` —— 当前用户详情（含角色码、权限） |
| | 路由：`GET /api/auth/profile` |
| **方法三** | `logout()` |
| | 功能：登出（前端清 token 即可，此处仅占位） |
| | 输入：无 |
| | 输出：`Result<Void>` —— 空数据，仅业务码 |
| | 路由：`POST /api/auth/logout` |

---

## 二、功能流程结构图

> 形状约定：圆角=开始/结束，矩形=处理，**棱形=判断/分支**，平行四边形=输入/输出，圆柱=数据存储。

```mermaid
flowchart TD
    classDef startend fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;
    classDef process fill:#fff3e0,stroke:#e65100,color:#bf360c;
    classDef decision fill:#fff8e1,stroke:#f9a825,color:#e65100;
    classDef io fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c;
    classDef store fill:#eceff1,stroke:#455a64,color:#263238;

    START(["开始 · 认证请求"]):::startend
    D1{"请求类型 ?"}:::decision
    START --> D1

    %% 登录分支
    IO1[/"LoginDTO<br/>username · password"/]:::io
    D1 -->|登录 login| IO1
    IO1 --> P1["校验用户名/密码"]:::process
    DB[("sys.sys_user<br/>用户实体表")]:::store
    P1 --> DB
    DB --> D2{"凭证是否有效 ?"}:::decision
    D2 -->|否| ERR["返回错误<br/>(账号/密码不正确)"]:::process
    D2 -->|是| P2["签发 token · 更新 lastLoginAt"]:::process
    P2 --> VO1[/"LoginVO 输出<br/>token + UserVO"/]:::io

    %% 当前用户分支
    D1 -->|当前用户 profile| D3{"token 是否有效 ?"}:::decision
    D3 -->|否| ERR2["401 未认证"]:::process
    D3 -->|是| P3["按上下文取当前用户"]:::process
    P3 --> DB
    DB --> VO2[/"UserVO 输出<br/>(含角色码/权限)"/]:::io

    %% 登出分支
    D1 -->|登出 logout| P4["占位返回<br/>(前端清 token)"]:::process

    END(["结束"]):::startend
    VO1 --> END
    VO2 --> END
    P4 --> END
    ERR --> END
    ERR2 --> END
```

---

## 三、Entity 实体表

> AuthController 不持有独立实体，复用 **User**（表 `sys.sys_user`）。完整字段见《接口文档-UserController.md》第三节。认证主要涉及的字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| username | String | 登录用户名（唯一） |
| password | String | 密码（加密存储，登录时比对） |
| status | Short | 账号状态（禁用时拒绝登录） |
| lastLoginAt | OffsetDateTime | 登录成功后更新 |

---

## 四、关联 DTO / VO

### 入参 · LoginDTO（登录请求体）

| 字段 | 类型 | 校验 | 说明 |
| --- | --- | --- | --- |
| username | String | `@NotBlank` `@Size(3,64)` | 用户名，必填 |
| password | String | `@NotBlank` `@Size(6,64)` | 密码，必填 |

### 出参 · LoginVO（登录结果）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | String | 鉴权令牌，后续请求放入请求头 |
| user | UserVO | 登录用户信息（结构见下） |

### 出参 · UserVO（用户视图对象，profile 与 login 共用）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 用户 id |
| username | String | 用户名 |
| realName | String | 真实姓名 |
| phone | String | 手机号 |
| email | String | 邮箱 |
| avatar | String | 头像 URL |
| status | Short | 状态 |
| lastLoginAt | OffsetDateTime | 最近登录时间 |
| createdAt | OffsetDateTime | 创建时间 |
| roleCodes | List\<String\> | 角色编码列表 |
| permissions | List\<String\> | 权限列表 |
