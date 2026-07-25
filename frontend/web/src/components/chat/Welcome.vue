<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, GitBranch, ShieldCheck, Workflow, FileSearch } from 'lucide-vue-next'
import { useQuestionStore } from '../../stores/question'
import { useProjectSpaceStore } from '../../stores/projectSpace'
import { useAppleSpring } from '../../composables/useAppleMotion'
import { questionPreview } from '../../utils/format'

const router = useRouter()
const questionStore = useQuestionStore()
const projectSpaceStore = useProjectSpaceStore()

const stageRef = ref<HTMLElement | null>(null)
// 欢迎页整体弹簧入场：从下方 12px 弹入，临界阻尼无回弹
useAppleSpring(
  stageRef,
  { opacity: [0, 1], y: [12, 0] },
  { bounce: 0, duration: 0.45 },
)

const selectedSpaceName = computed(() => {
  if (!questionStore.form.projectSpaceId) return null
  const space = projectSpaceStore.spaces.find((s) => s.id === questionStore.form.projectSpaceId)
  return space?.name ?? null
})

/** 建议问题：点击后填入 composer 草稿并聚焦 */
interface Suggestion {
  icon: typeof GitBranch
  title: string
  prompt: string
}

const suggestions = computed<Suggestion[]>(() => [
  {
    icon: GitBranch,
    title: '梳理调用链路',
    prompt: '帮我梳理这个方法完整的调用链路，从入口到最底层实现',
  },
  {
    icon: ShieldCheck,
    title: '检查安全隐患',
    prompt: '检查这段代码是否存在 SQL 注入、XSS 或敏感信息泄露等安全隐患',
  },
  {
    icon: Workflow,
    title: '理解业务流程',
    prompt: '这段代码实现了什么业务流程？用流程图说明关键步骤',
  },
  {
    icon: FileSearch,
    title: '定位实现位置',
    prompt: '某个功能的实现在仓库的哪些文件里？给我文件路径和关键代码',
  },
])

function applySuggestion(prompt: string) {
  // 写入 composer 草稿，用户可在输入框微调后发送
  try {
    localStorage.setItem('ascoder:composer-draft', prompt)
  } catch {
    // 忽略 localStorage 不可用
  }
  questionStore.form.text = prompt
  // 通知 composer 恢复草稿（composer 在 onMounted 时监听此事件）
  window.dispatchEvent(new CustomEvent('ascoder:restore-draft', { detail: prompt }))
}

function goToProjects() {
  router.push('/projects')
}

// 最近的几条历史提问（快捷入口）
const recentQuestions = computed(() =>
  questionStore.recentQuestions
    .slice(0, 4)
    .map((q) => ({ id: q.id, text: q.text }))
)

function openQuestion(questionId: number) {
  const spaceId = questionStore.form.projectSpaceId
  if (!spaceId) return
  router.push({ name: 'chat', query: { spaceId: String(spaceId), questionId: String(questionId) } })
}
</script>

<template>
  <div ref="stageRef" class="welcome-stage">
    <!-- 品牌视觉锚点：呼吸光标 -->
    <div class="brand-mark" aria-hidden="true">
      <span class="brand-glyph">{</span>
      <span class="brand-cursor" />
      <span class="brand-glyph brand-glyph-close">}</span>
    </div>

    <div class="welcome-copy-block">
      <h2 class="welcome-title">今天想了解哪段代码？</h2>
      <p v-if="!questionStore.form.projectSpaceId" class="welcome-copy">请从空间详情页进入聊天工作台。</p>
      <p v-else class="welcome-copy">项目空间：<strong>{{ selectedSpaceName }}</strong>，直接用自然语言提问。</p>
    </div>

    <!-- 建议卡片：引导用户快速开始 -->
    <div v-if="questionStore.form.projectSpaceId" class="suggestion-grid">
      <button
        v-for="(item, i) in suggestions"
        :key="i"
        class="suggestion-card"
        type="button"
        :style="{ animationDelay: `${0.1 + i * 0.06}s` }"
        @click="applySuggestion(item.prompt)"
      >
        <span class="suggestion-icon">
          <component :is="item.icon" aria-hidden="true" :size="18" :stroke-width="1.8" />
        </span>
        <span class="suggestion-body">
          <strong class="suggestion-title">{{ item.title }}</strong>
          <small class="suggestion-prompt">{{ item.prompt }}</small>
        </span>
      </button>
    </div>

    <!-- 最近提问快捷入口 -->
    <div v-if="recentQuestions.length" class="recent-block">
      <p class="recent-kicker">最近提问</p>
      <div class="recent-list">
        <button
          v-for="q in recentQuestions"
          :key="q.id"
          class="recent-item"
          type="button"
          @click="openQuestion(q.id)"
        >
          {{ questionPreview(q.text) }}
        </button>
      </div>
    </div>

    <el-button
      v-if="!questionStore.form.projectSpaceId"
      type="primary"
      title="返回项目空间"
      aria-label="返回项目空间"
      @click="goToProjects"
    >
      <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
      返回项目空间
    </el-button>
  </div>
</template>

<style scoped>
.welcome-stage {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: var(--spacing-6);
  min-height: 100%;
  padding: var(--spacing-8) var(--spacing-5);
  text-align: center;
}

/* 品牌视觉锚点：呼吸的光标 + 大括号，呼应代码主题 */
.brand-mark {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", Menlo, Consolas, monospace;
  font-size: 44px;
  font-weight: 300;
  color: var(--chat-accent);
  opacity: 0.92;
}

.brand-glyph {
  display: inline-block;
  animation: brand-float 4s ease-in-out infinite;
}

.brand-glyph-close {
  animation-delay: 0.3s;
}

.brand-cursor {
  display: inline-block;
  width: 3px;
  height: 36px;
  margin: 0 4px;
  border-radius: var(--radius-full);
  background: linear-gradient(180deg, var(--chat-accent), rgba(79, 110, 247, 0.4));
  animation: brand-blink 1.1s steps(2, end) infinite, brand-grow 4s ease-in-out infinite;
  transform-origin: center;
}

@keyframes brand-blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0.25; }
}

@keyframes brand-grow {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(1.18); }
}

@keyframes brand-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

.welcome-copy-block {
  display: grid;
  justify-items: center;
  gap: var(--spacing-3);
}

.welcome-title {
  margin: 0;
  max-width: 18ch;
  font-size: 32px;
  line-height: var(--line-height-display);
  font-weight: 700;
  letter-spacing: var(--tracking-tighter);
}

.welcome-copy {
  max-width: 46ch;
  margin: 0;
  color: var(--muted);
  font-size: var(--font-size-lg);
  line-height: var(--line-height-normal);
}

.welcome-copy strong {
  color: var(--text);
  font-weight: var(--font-weight-semibold);
}

/* 建议卡片：逐个弹入 stagger */
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-3);
  width: min(680px, 100%);
}

.suggestion-card {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: start;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  text-align: left;
  cursor: pointer;
  color: var(--subtle);
  opacity: 0;
  transform: translateY(8px);
  animation: suggestion-in 0.5s var(--ease-spring) forwards;
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal),
    background var(--transition-normal),
    transform var(--press-duration) var(--ease-snappy);
}

.suggestion-card:hover {
  border-color: var(--chat-accent);
  box-shadow: var(--shadow-panel);
  background: var(--chat-accent-soft);
  transform: translateY(-2px);
}

.suggestion-card:active {
  transform: scale(var(--press-scale));
}

@keyframes suggestion-in {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.suggestion-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: var(--radius-md);
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.suggestion-body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.suggestion-title {
  color: var(--text);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.suggestion-prompt {
  color: var(--muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 最近提问 */
.recent-block {
  display: grid;
  justify-items: center;
  gap: var(--spacing-3);
  width: min(680px, 100%);
}

.recent-kicker {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--chat-timestamp);
  font-weight: var(--font-weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
}

.recent-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  justify-content: center;
}

.recent-item {
  max-width: 100%;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  background: var(--surface);
  color: var(--subtle);
  font-size: var(--font-size-sm);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition:
    border-color var(--transition-fast),
    color var(--transition-fast),
    background var(--transition-fast),
    transform var(--press-duration) var(--ease-snappy);
}

.recent-item:hover {
  border-color: var(--chat-accent);
  color: var(--chat-accent);
  background: var(--chat-accent-soft);
}

.recent-item:active {
  transform: scale(var(--press-scale));
}

@media (prefers-reduced-motion: reduce) {
  .brand-glyph,
  .brand-cursor {
    animation: none;
  }
  .suggestion-card {
    animation: none;
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 600px) {
  .suggestion-grid {
    grid-template-columns: 1fr;
  }
  .brand-mark {
    font-size: 36px;
  }
  .welcome-title {
    font-size: 26px;
  }
}
</style>
