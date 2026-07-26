<template>
  <div class="auth-shell">
    <!-- 左侧品牌展示区 -->
    <aside class="auth-brand">
      <div class="brand-bg">
        <!-- 代码符号粒子 -->
        <span v-for="(symbol, i) in symbols" :key="i" class="code-particle"
          :style="particleStyle(i)">{{ symbol }}</span>
        <!-- 渐变光晕 -->
        <div class="brand-glow brand-glow-top"></div>
        <div class="brand-glow brand-glow-bottom"></div>
      </div>
      <div class="brand-content">
        <img src="../../images/logo.svg" alt="Ascoder" class="brand-logo" />
        <p class="brand-tagline">代码智能分析平台</p>
        <p class="brand-copy">对代码仓库进行结构化问答、索引与分析，<br/>让代码理解触手可及。</p>
        <div class="brand-divider"></div>
        <div class="brand-features">
          <div class="feature-item" v-for="(feat, i) in features" :key="i"
            :style="{ animationDelay: `${0.5 + i * 0.1}s` }">
            <span class="feature-icon">{{ feat.icon }}</span>
            <span class="feature-text">{{ feat.text }}</span>
          </div>
        </div>
      </div>
      <!-- 底部渐变装饰线 -->
      <div class="brand-deco-line"></div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="auth-main">
      <div class="auth-form-wrapper">
        <!-- 移动端 logo（桌面端隐藏） -->
        <div class="mobile-brand">
          <img src="../../images/logo.svg" alt="Ascoder" class="mobile-logo" />
        </div>
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
  const col = index % cols
  const row = Math.floor(index / cols)
  const x = 8 + col * 16 + (Math.sin(index * 1.7) * 3)
  const y = 6 + row * 14 + (Math.cos(index * 2.3) * 2)
  const delay = (index * 0.18) % 4
  const duration = 3 + (index % 5) * 0.6
  const opacity = 0.06 + (index % 4) * 0.03
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
  grid-template-columns: 2fr 3fr;
  min-height: 100vh;
}

/* ── 左侧品牌区 ── */

.auth-brand {
  position: relative;
  display: grid;
  align-content: center;
  justify-items: center;
  padding: var(--spacing-10) var(--spacing-8);
  overflow: hidden;
  background: #080c16;
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

/* 渐变光晕：呼应 logo 的蓝→紫→粉渐变 */
.brand-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
}

.brand-glow-top {
  top: 5%;
  left: 50%;
  width: 400px;
  height: 400px;
  transform: translateX(-50%);
  background: radial-gradient(
    circle,
    rgba(99, 102, 241, 0.18) 0%,
    rgba(168, 85, 247, 0.1) 35%,
    rgba(236, 72, 153, 0.05) 65%,
    transparent 100%
  );
  animation: glow-breathe 6s ease-in-out infinite alternate;
}

.brand-glow-bottom {
  bottom: 10%;
  left: 30%;
  width: 280px;
  height: 280px;
  background: radial-gradient(
    circle,
    rgba(99, 102, 241, 0.08) 0%,
    rgba(168, 85, 247, 0.04) 50%,
    transparent 100%
  );
  animation: glow-breathe 8s ease-in-out infinite alternate-reverse;
}

@keyframes glow-breathe {
  from {
    opacity: 0.6;
    transform: translateX(-50%) scale(0.92);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) scale(1.08);
  }
}

.code-particle {
  position: absolute;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: var(--font-weight-medium);
  color: rgba(148, 163, 194, 0.2);
  letter-spacing: -0.02em;
  animation: particle-drift linear infinite;
  user-select: none;
}

@keyframes particle-drift {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  25% {
    transform: translateY(-6px) rotate(1.5deg);
  }
  50% {
    transform: translateY(-2px) rotate(-1deg);
    opacity: 1.6;
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
  gap: var(--spacing-4);
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

/* Logo：保留原始渐变彩色，放大展示 */
.brand-logo {
  width: min(360px, 85%);
  height: auto;
  object-fit: contain;
  animation: logo-float 5s ease-in-out infinite alternate;
}

@keyframes logo-float {
  from {
    transform: translateY(0);
    filter: drop-shadow(0 4px 24px rgba(99, 102, 241, 0.2));
  }
  to {
    transform: translateY(-4px);
    filter: drop-shadow(0 8px 40px rgba(168, 85, 247, 0.25));
  }
}

.brand-tagline {
  margin: 0;
  font-size: var(--font-size-lg);
  color: rgba(148, 163, 194, 0.65);
  letter-spacing: var(--tracking-wide);
  font-weight: var(--font-weight-medium);
}

.brand-copy {
  margin: 0;
  max-width: 32ch;
  font-size: var(--font-size-sm);
  color: rgba(148, 163, 194, 0.4);
  line-height: var(--line-height-relaxed);
  text-align: center;
}

.brand-divider {
  width: 48px;
  height: 2px;
  border-radius: var(--radius-full);
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.4), rgba(168, 85, 247, 0.4));
  margin: var(--spacing-2) 0;
}

.brand-features {
  display: grid;
  gap: var(--spacing-3);
  margin-top: var(--spacing-3);
  width: 100%;
  max-width: 260px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  border: 1px solid rgba(148, 163, 194, 0.06);
  border-radius: var(--radius-lg);
  background: rgba(148, 163, 194, 0.02);
  color: rgba(148, 163, 194, 0.55);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  animation: feature-slide-in 500ms var(--ease-spring) both;
  transition:
    border-color var(--transition-normal),
    background var(--transition-normal);
}

.feature-item:hover {
  border-color: rgba(148, 163, 194, 0.12);
  background: rgba(148, 163, 194, 0.05);
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
  background: rgba(148, 163, 194, 0.05);
  color: rgba(148, 163, 194, 0.45);
  font-size: 14px;
  flex-shrink: 0;
}

.feature-text {
  line-height: var(--line-height-normal);
}

/* 底部渐变装饰线：呼应 logo 的蓝→紫→粉渐变 */
.brand-deco-line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(99, 102, 241, 0.5) 25%,
    rgba(168, 85, 247, 0.5) 50%,
    rgba(236, 72, 153, 0.4) 75%,
    transparent 100%
  );
  animation: deco-pulse 3s ease-in-out infinite;
}

@keyframes deco-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.9; }
}

/* ── 右侧表单区 ── */

.auth-main {
  display: grid;
  align-content: center;
  justify-items: center;
  padding: var(--spacing-8);
  background:
    radial-gradient(ellipse 70% 50% at 50% 0%, rgba(99, 102, 241, 0.03), transparent 60%),
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

/* 移动端 logo：桌面端隐藏 */
.mobile-brand {
  display: none;
  justify-content: center;
  margin-bottom: var(--spacing-6);
}

.mobile-logo {
  width: min(200px, 60%);
  height: auto;
  object-fit: contain;
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    display: none;
  }

  .mobile-brand {
    display: flex;
  }

  .auth-main {
    padding: var(--spacing-6) var(--spacing-4);
  }
}

/* ── 暗色主题 ── */

[data-theme="dark"] .auth-main {
  background:
    radial-gradient(ellipse 70% 50% at 50% 0%, rgba(107, 138, 247, 0.04), transparent 60%),
    var(--bg);
}

/* ── Reduced motion ── */

@media (prefers-reduced-motion: reduce) {
  .code-particle,
  .brand-content,
  .feature-item,
  .auth-form-wrapper,
  .brand-logo,
  .brand-deco-line,
  .brand-glow {
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
