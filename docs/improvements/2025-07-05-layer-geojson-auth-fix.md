# 2025-07-05 项目改进说明

## 概述

修复了图层管理中「灾害事件图层」和「预警图层」加载时的报错问题，同时消除了界面上的 Vue 运行时警告。

---

## 问题背景

在完成美国区域图层测试后，发现其他图层（灾害事件图层、预警图层）在加载时控制台报错，提示"返回的不是有效 GeoJSON"。这些图层虽然功能流程正常，但数据始终无法渲染到地图上。

## 根因分析

数据库中的「灾害事件图层」和「预警图层」配置的数据源 URL 为：

- 灾害事件图层：`/api/disasters/geojson`
- 预警图层：`/api/alerts/geojson`

这两个接口存在两个问题：

1. **需要登录认证**：接口受 Spring Security 保护，需要在请求头中携带 `Authorization: Bearer <token>`。
2. **返回格式为 Result 包装**：后端统一返回 `Result { code: number, data: any, message: string }` 格式，真正的 GeoJSON 数据在 `data` 字段中。

而原有的 `fetchWithAuth` 函数使用原生 `fetch` API 直接请求，拿到了整个 `Result` 包装对象，而非 `data` 内的 GeoJSON 数据，导致 OpenLayers 无法解析。

## 修改内容

### 1. `frontend/src/views/layer/LayerView.vue`

**修改点 A：引入统一请求实例**

```diff
 import { apiLayerList, apiLayerCreate, apiLayerUpdate, apiLayerDelete, apiLayerPublish } from '@/api/layer'
 import { apiRegionsGeoJson, apiRiversGeoJson, apiSettlementsGeoJson } from '@/api/gis'
+import request from '@/utils/request'
```

导入项目统一的 axios 实例 `request`，该实例已配置：
- 自动在请求头中携带 JWT token（通过请求拦截器）
- 自动解包后端 `Result` 包装，直接返回 `data` 字段内容（通过响应拦截器）

**修改点 B：重构 fetchWithAuth 函数**

```diff
 function fetchWithAuth(url) {
-  const userStore = useUserStore()
-  const headers = {}
-  if (userStore.token && url && url.startsWith('/api/')) {
-    headers.Authorization = `Bearer ${userStore.token}`
+  if (url && url.startsWith('/api/')) {
+    const path = url.replace(/^\/api/, '')
+    return request.get(path)
   }
-  return fetch(url, { headers }).then(r => {
+  return fetch(url, { headers: {} }).then(r => {
     if (!r.ok) throw new Error(`HTTP ${r.status}`)
     return r.json()
   })
 }
```

改动说明：
- 对 `/api/` 开头的后端接口，改用 `request.get()` 发起请求，自动获得鉴权与解包能力
- 对非 `/api/` 的外部 GeoJSON 资源（如 GeoServer WFS），保持原有 fetch 逻辑不变
- 移除了手动拼接 Authorization 头的逻辑（已由 request 拦截器处理）

**修改点 C：清理未使用的导入**

```diff
-import { useUserStore } from '@/store/user'
```

因 fetchWithAuth 不再手动读取 token，移除 `useUserStore` 的导入。

### 2. `frontend/src/layouts/AuroraLayout.vue`

**修改点：恢复 coordLabel 计算属性**

```diff
-// const coordLabel = computed(() => '104.0000°E · 35.0000°N · EPSG:3857')
+const coordLabel = computed(() => '')
```

模板中引用了 `coordLabel` 变量，但该变量被注释掉了，导致 Vue 运行时抛出 `Property "coordLabel" was accessed during render but is not defined` 警告。现恢复为一个空字符串计算属性，消除警告。

---

## 影响范围

| 文件 | 影响 |
|------|------|
| `frontend/src/views/layer/LayerView.vue` | 灾害事件图层、预警图层加载恢复正常 |
| `frontend/src/layouts/AuroraLayout.vue` | 消除渲染警告，无功能影响 |

## 验证方式

1. 清除浏览器缓存后刷新页面（`Ctrl + F5`）
2. 进入图层管理页面，打开「灾害事件图层」和「预警图层」
3. 确认两个图层均能正常加载并渲染到地图上，不再出现 GeoJSON 相关报错
4. 确认浏览器控制台无 `coordLabel` 相关警告

## 备注

- 如果图层数据源 URL 在数据库中被配置为 `/api/disasters/section` 或 `/api/alerts/section`，需在「图层数据维护」Tab 中改为 `/api/disasters/geojson` 和 `/api/alerts/geojson`。
- 外部 GeoJSON 源（如 GeoServer 的 WFS 服务）不受此次修改影响，仍使用原生 fetch 请求。
