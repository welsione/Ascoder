import { computed, reactive, ref, type ComputedRef, type Reactive, type Ref } from 'vue'

/**
 * 从异常或任意值中提取错误信息，统一各 Store 的错误兜底逻辑。
 */
export function extractErrorMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

/**
 * useCrudStore 创建选项，描述表单初始状态、是否启用选中项跟踪等。
 */
export interface UseCrudStoreOptions<T, F extends object> {
  /** 表单初始状态工厂，每次 resetForm 都会重新生成一份 */
  initialForm: () => F
  /** 是否启用选中项跟踪（selectedId / selected） */
  trackSelected?: boolean
  /** 仅用于类型推导，无运行时作用 */
  items?: T[]
}

/**
 * useCrudStore 返回的通用 CRUD 能力集合。
 */
export interface UseCrudStoreReturn<T, F extends object> {
  items: Ref<T[]>
  loading: Ref<boolean>
  createLoading: Ref<boolean>
  error: Ref<string>
  form: Reactive<F>
  selectedId: Ref<number | null>
  selected: ComputedRef<T | null>
  resetForm: () => void
  fetchAll: (apiFn: () => Promise<T[]>, fallback: string) => Promise<void>
  create: (
    apiFn: (form: F) => Promise<T>,
    validateFn: (form: F) => string | null,
    fallback: string,
    onSuccess?: (created: T, form: F) => Promise<void> | void
  ) => Promise<T | null>
  wrapAction: <R>(
    id: number,
    apiFn: () => Promise<R>,
    fallback: string
  ) => Promise<R | null>
  busyIds: Ref<Set<number>>
  isBusy: (id: number) => boolean
}

/**
 * 通用 CRUD Store 组合式函数，封装列表加载、创建、错误提取、
 * per-item busy 跟踪等重复样板。各业务 Store 在其基础上叠加特有逻辑。
 */
export function useCrudStore<T, F extends object>(options: UseCrudStoreOptions<T, F>): UseCrudStoreReturn<T, F> {
  const items = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const createLoading = ref(false)
  const error = ref('')
  const busyIds = ref<Set<number>>(new Set())

  const form = reactive(options.initialForm()) as Reactive<F>

  const selectedId = ref<number | null>(null)
  const selected = computed<T | null>(() =>
    options.trackSelected
      ? (items.value.find((item) => (item as { id?: number }).id === selectedId.value) ?? null)
      : null
  )

  function resetForm() {
    const fresh = options.initialForm()
    // 逐字段回填，保持表单响应性
    const target = form as Record<string, unknown>
    const source = fresh as Record<string, unknown>
    for (const key of Object.keys(source)) {
      target[key] = source[key]
    }
  }

  async function fetchAll(apiFn: () => Promise<T[]>, fallback: string): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      items.value = await apiFn()
    } catch (err) {
      error.value = extractErrorMessage(err, fallback)
    } finally {
      loading.value = false
    }
  }

  async function create(
    apiFn: (form: F) => Promise<T>,
    validateFn: (form: F) => string | null,
    fallback: string,
    onSuccess?: (created: T, form: F) => Promise<void> | void
  ): Promise<T | null> {
    // Reactive<F> 在运行时与 F 结构等价，此处强转以满足回调签名
    const formValue = form as unknown as F
    const validationError = validateFn(formValue)
    if (validationError) {
      error.value = validationError
      return null
    }
    createLoading.value = true
    error.value = ''
    try {
      const created = await apiFn(formValue)
      if (onSuccess) await onSuccess(created, formValue)
      return created
    } catch (err) {
      error.value = extractErrorMessage(err, fallback)
      return null
    } finally {
      createLoading.value = false
    }
  }

  async function wrapAction<R>(
    id: number,
    apiFn: () => Promise<R>,
    fallback: string
  ): Promise<R | null> {
    busyIds.value = new Set(busyIds.value).add(id)
    error.value = ''
    try {
      return await apiFn()
    } catch (err) {
      error.value = extractErrorMessage(err, fallback)
      return null
    } finally {
      const next = new Set(busyIds.value)
      next.delete(id)
      busyIds.value = next
    }
  }

  function isBusy(id: number): boolean {
    return busyIds.value.has(id)
  }

  return {
    items,
    loading,
    createLoading,
    error,
    form,
    selectedId,
    selected,
    resetForm,
    fetchAll,
    create,
    wrapAction,
    busyIds,
    isBusy,
  }
}
