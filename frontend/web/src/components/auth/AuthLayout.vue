<template>
  <div class="auth-shell">
    <!-- 左侧品牌展示区 -->
    <aside class="auth-brand">
      <div class="brand-bg">
        <!-- 代码符号粒子 -->
        <span v-for="(symbol, i) in symbols" :key="i" class="code-particle"
          :style="particleStyle(i)">{{ symbol }}</span>
      </div>
      <div class="brand-content">
        <img src="../../images/logo.svg" alt="Ascoder" class="brand-logo" />
        <h1 class="brand-title">Ascoder</h1>
        <p class="brand-subtitle">代码智能分析平台</p>
        <div class="brand-features">
          <div class="feature-item" v-for="(feat, i) in features" :key="i"
            :style="{ animationDelay: `${0.6 + i * 0.12}s` }">
            <span class="feature-icon">{{ feat.icon }}</span>
            <span class="feature-text">{{ feat.text }}</span>
          </div>
        </div>
      </div>
      <!-- 底部装饰线 -->
      <div class="brand-deco-line"></div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="auth-main">
      <div class="auth-form-wrapper">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
const symbols = [
  '{ }', '< />', '//', '=>', 'fn', '::', '[]', '&&', '||', '??',
  '0x', '++', '===', '!=', '<<', '>>', '...', 'as', 'if', '++',
  '/*', '*/', '()', '&&', '||', '=>', '::', '[]', '{}', '<>',
]

const features = [
  { icon: '⌘', text: '结构化代码问答' },
  { icon: '◈', text: '智能索引与符号查询' },
  { icon: '⟐', text: '代码证据精准提取' },
]

function particleStyle(index: number) {
  const cols = 6
  const rows = Math.ceil(symbols.length / cols)
  const col = index % cols
  const row = Math.floor(index / cols)
  const x = 8 + col * 16 + (Math.sin(index * 1.7) * 3)
  const y = 6 + row * 14 + (Math.cos(index * 2.3) * 2)
  const delay = (index * 0.18) % 4
  const duration = 3 + (index % 5) * 0.6
  const opacity = 0.08 + (index % 4) * 0.04
  return {
    left: `${x}%`,
    top: `${y}%`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`,
    opacity,
  }
}
</script>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
}

/* ── 左侧品牌区 ── */

.auth-brand {
  position: relative;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: var(--spacing-8);
  padding: var(--spacing-10) var(--spacing-8);
  overflow: hidden;
  background:
    linear-gradient(160deg, #0a0e1a 0%, #111827 40%, #0f172a 100%);
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.code-particle {
  position: absolute;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: var(--font-weight-medium);
  color: rgba(79, 110, 247, 0.35);
  letter-spacing: -0.02em;
  animation: particle-drift linear infinite;
  user-select: none;
}

@keyframes particle-drift {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
    opacity: var(--particle-opacity, 0.1);
  }
  25% {
    transform: translateY(-6px) rotate(1.5deg);
  }
  50% {
    transform: translateY(-2px) rotate(-1deg);
    opacity: calc(var(--particle-opacity, 0.1) * 1.8);
  }
  75% {
    transform: translateY(-8px) rotate(0.5deg);
  }
}

.brand-content {
  position: relative;
  z-index: 2;
  display: grid;
  justify-items: center;
  gap: var(--spacing-5);
  animation: brand-fade-in 800ms var(--ease-spring) both;
}

@keyframes brand-fade-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.brand-logo {
  width: 72px;
  height: 72px;
  filter: brightness(0) invert(1) opacity(0.9);
  animation: logo-glow 4s ease-in-out infinite alternate;
}

@keyframes logo-glow {
  from {
    filter: brightness(0) invert(1) opacity(0.85) drop-shadow(0 0 8px rgba(79, 110, 247, 0));
  }
  to {
    filter: brightness(0) invert(1) opacity(0.95) drop-shadow(0 0 16px rgba(79, 110, 247, 0.3));
  }
}

.brand-title {
  margin: 0;
  font-size: 36px;
  font-weight: var(--font-weight-bold);
  letter-spacing: var(--tracking-tighter);
  color: #f0f0f5;
  line-height: var(--line-height-display);
}

.brand-subtitle {
  margin: 0;
  font-size: var(--font-size-lg);
  color: rgba(148, 163, 194, 0.8);
  letter-spacing: var(--tracking-wide);
  font-weight: var(--font-weight-medium);
}

.brand-features {
  display: grid;
  gap: var(--spacing-3);
  margin-top: var(--spacing-6);
  width: 100%;
  max-width: 260px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.12);
  border-radius: var(--radius-lg);
  background: rgba(79, 110, 247, 0.04);
  color: rgba(148, 163, 194, 0.75);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  animation: feature-slide-in 500ms var(--ease-spring) both;
  transition:
    border-color var(--transition-normal),
    background var(--transition-normal);
}

.feature-item:hover {
  border-color: rgba(79, 110, 247, 0.25);
  background: rgba(79, 110, 247, 0.08);
}

@keyframes feature-slide-in {
  from {
    opacity: 0;
    transform: translateX(-12px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  background: rgba(79, 110, 247, 0.1);
  color: rgba(79, 110, 247, 0.7);
  font-size: 14px;
  flex-shrink: 0;
}

.feature-text {
  line-height: var(--line-height-normal);
}

/* 底部装饰线 */
.brand-deco-line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(79, 110, 247, 0.4), transparent);
  animation: deco-pulse 3s ease-in-out infinite;
}

@keyframes deco-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.8; }
}

/* ── 右侧表单区 ── */

.auth-main {
  display: grid;
  align-content: center;
  justify-items: center;
  padding: var(--spacing-8);
  background:
    radial-gradient(ellipse 70% 50% at 50% 0%, rgba(79, 110, 247, 0.04), transparent 60%),
    var(--bg);
}

.auth-form-wrapper {
  width: 100%;
  max-width: 400px;
  animation: form-fade-in 600ms var(--ease-spring) both;
  animation-delay: 0.15s;
}

@keyframes form-fade-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── 响应式 ── */

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    display: none;
  }

  .auth-main {
    padding: var(--spacing-6) var(--spacing-4);
  }
}

/* ── 暗色主题 ── */

[data-theme="dark"] .auth-main {
  background:
    radial-gradient(ellipse 70% 50% at 50% 0%, rgba(107, 138, 247, 0.05), transparent 60%),
    var(--bg);
}

/* ── Reduced motion ── */

@media (prefers-reduced-motion: reduce) {
  .code-particle,
  .brand-content,
  .feature-item,
  .auth-form-wrapper,
  .brand-logo,
  .brand-deco-line {
    animation: none !important;
  }

  .feature-item {
    opacity: 1;
  }

  .brand-content,
  .auth-form-wrapper {
    opacity: 1;
  }
}
</style>
