const ANSWER_NOTIFICATION_TITLE = 'Ascoder 回答完成'

/**
 * 在用户触发提问或恢复时预先申请浏览器通知权限。
 */
export async function requestAnswerNotificationPermission() {
  if (!supportsBrowserNotification() || Notification.permission !== 'default') {
    return
  }
  try {
    await Notification.requestPermission()
  } catch {
    // 浏览器可能因安全上下文或权限策略拒绝申请，忽略即可。
  }
}

/**
 * 回答完成后发送浏览器通知；未授权或环境不支持时静默跳过。
 */
export function notifyAnswerCompleted(questionText: string) {
  if (!supportsBrowserNotification() || Notification.permission !== 'granted') {
    return
  }
  const body = questionText
    ? `问题「${truncate(questionText, 80)}」已经回答完成。`
    : '你的问题已经回答完成。'
  try {
    new Notification(ANSWER_NOTIFICATION_TITLE, {
      body,
      tag: 'ascoder-answer-completed',
    })
  } catch {
    // 通知发送失败不应影响问答流状态。
  }
}

function supportsBrowserNotification() {
  return typeof window !== 'undefined' && 'Notification' in window
}

function truncate(text: string, maxLength: number) {
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text
}
