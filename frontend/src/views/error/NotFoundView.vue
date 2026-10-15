<template>
  <div class="nf-page">
    <div class="nf-card dash-panel">
      <span class="corner-bl" /><span class="corner-br" />
      <div class="nf-code num-mono">404</div>
      <div class="nf-title">PAGE NOT FOUND</div>
      <div class="nf-sub">您访问的页面不存在或已被移除</div>
      <el-button type="primary" size="large" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.nf-page {
  position: fixed; inset: 0;
  display: flex; align-items: center; justify-content: center;
  background:
    radial-gradient(ellipse at center, rgba(56,189,248,0.12) 0%, transparent 60%),
    linear-gradient(160deg, #050B1F 0%, #0A1432 60%, #11204A 100%);
  color: #E2E8F0;
}
.nf-card {
  width: 480px;
  padding: 60px 40px;
  text-align: center;
}
.nf-code {
  font-size: 96px;
  background: linear-gradient(180deg, #38BDF8 0%, #6366F1 100%);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
}
.nf-title { font-size: 18px; letter-spacing: 6px; color: #94A3B8; margin: 16px 0 8px; }
.nf-sub { font-size: 13px; color: #64748B; margin-bottom: 32px; }

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .nf-card { width: 88vw; padding: 40px 24px; }
  .nf-code { font-size: 72px; }
  .nf-title { font-size: 16px; letter-spacing: 4px; }
  .nf-sub { font-size: 12px; margin-bottom: 24px; }
}
</style>
