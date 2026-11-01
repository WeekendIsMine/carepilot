import { ref } from 'vue'

/**
 * 后台列表的通用分页加载：rows / page / total / loading 一把梭。
 * 之前 5 个 tab 各写一遍同样的 useState + load 逻辑，抽出来只维护一份。
 *
 * @param fetcher ({page,size}) => Promise<{records,total}>
 */
export function usePagedList(fetcher, size = 10) {
  const rows = ref([])
  const page = ref(1)
  const total = ref(0)
  const loading = ref(false)

  async function load(reset = false) {
    if (reset === true) page.value = 1
    loading.value = true
    try {
      const res = await fetcher({ page: page.value, size })
      rows.value = res.records || []
      total.value = res.total || 0
    } finally {
      loading.value = false
    }
  }

  return { rows, page, total, loading, size, load }
}
