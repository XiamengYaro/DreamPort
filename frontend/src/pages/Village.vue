<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <!-- 标题 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">村民族谱</h1>
        <p class="text-stone-400">村民交易信息汇总</p>
      </div>

      <!-- 世界筛选 -->
      <div class="flex gap-2 mb-6 flex-wrap">
        <button @click="currentWorld = 'all'" class="tab-btn" :class="{ active: currentWorld === 'all' }">
          全部
        </button>
        <button @click="currentWorld = 'survival'" class="tab-btn" :class="{ active: currentWorld === 'survival' }">
          生存世界
        </button>
        <button @click="currentWorld = 'resource'" class="tab-btn" :class="{ active: currentWorld === 'resource' }">
          资源世界
        </button>
        <button @click="currentWorld = 'industrial'" class="tab-btn" :class="{ active: currentWorld === 'industrial' }">
          工业世界
        </button>
      </div>

      <!-- 提交按钮 -->
      <div class="mb-6">
        <button @click="showSubmitModal = true" class="btn-primary">
          + 提交村民交易
        </button>
      </div>

      <!-- 交易列表 -->
      <div class="card p-6">
        <div v-if="loading" class="text-center py-8 text-stone-500">
          <svg class="animate-spin h-8 w-8 mx-auto mb-2 text-orange-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          加载中...
        </div>
        <div v-else-if="filteredList.length === 0" class="text-center py-8 text-stone-500">暂无数据</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="text-left text-stone-400 text-sm border-b border-stone-700">
                <th class="pb-3 pr-4">世界</th>
                <th class="pb-3 pr-4">坐标</th>
                <th class="pb-3 pr-4">输入物品</th>
                <th class="pb-3 pr-4">输出物品</th>
                <th class="pb-3 pr-4">价格</th>
                <th class="pb-3">提交者</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="trade in filteredList" :key="trade.id" class="border-b border-stone-800 hover:bg-white/5">
                <td class="py-3 pr-4">
                  <span class="px-2 py-1 rounded text-xs" :class="getWorldClass(trade.world)">
                    {{ getWorldName(trade.world) }}
                  </span>
                </td>
                <td class="py-3 pr-4 text-white font-mono text-sm">
                  {{ trade.x }}, {{ trade.y }}, {{ trade.z }}
                </td>
                <td class="py-3 pr-4 text-white">{{ trade.itemInput }}</td>
                <td class="py-3 pr-4 text-orange-400 font-medium">{{ trade.itemOutput }}</td>
                <td class="py-3 pr-4 text-green-400">${{ trade.price }}</td>
                <td class="py-3 text-stone-400 text-sm">{{ trade.playerName }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 提交弹窗 -->
      <div v-if="showSubmitModal" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showSubmitModal = false">
        <div class="card w-full max-w-md p-6">
          <h3 class="text-lg font-semibold text-white mb-4">提交村民交易</h3>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-stone-300 mb-1">世界</label>
              <select v-model="submitForm.world" class="input w-full">
                <option value="survival">生存世界</option>
                <option value="resource">资源世界</option>
                <option value="industrial">工业世界</option>
              </select>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <div>
                <label class="block text-sm text-stone-300 mb-1">X</label>
                <input v-model.number="submitForm.x" type="number" class="input w-full" placeholder="0" />
              </div>
              <div>
                <label class="block text-sm text-stone-300 mb-1">Y</label>
                <input v-model.number="submitForm.y" type="number" class="input w-full" placeholder="0" />
              </div>
              <div>
                <label class="block text-sm text-stone-300 mb-1">Z</label>
                <input v-model.number="submitForm.z" type="number" class="input w-full" placeholder="0" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">输入物品</label>
              <input v-model="submitForm.itemInput" type="text" class="input w-full" placeholder="如：绿宝石 x32" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">输出物品</label>
              <input v-model="submitForm.itemOutput" type="text" class="input w-full" placeholder="如：附魔书" />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">价格</label>
              <input v-model.number="submitForm.price" type="number" step="0.01" class="input w-full" placeholder="0.00" />
            </div>
          </div>
          <div class="flex gap-3 mt-6">
            <button @click="showSubmitModal = false" class="btn-ghost flex-1">取消</button>
            <button @click="submitTrade" class="btn-primary flex-1" :disabled="submitting">
              {{ submitting ? '提交中...' : '提交' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/services/api'

const loading = ref(false)
const trades = ref<any[]>([])
const currentWorld = ref('all')
const showSubmitModal = ref(false)
const submitting = ref(false)

const submitForm = ref({
  world: 'survival',
  x: 0,
  y: 0,
  z: 0,
  itemInput: '',
  itemOutput: '',
  price: 0
})

const filteredList = computed(() => {
  if (currentWorld.value === 'all') return trades.value
  return trades.value.filter(t => t.world === currentWorld.value)
})

onMounted(async () => {
  await loadTrades()
})

const loadTrades = async () => {
  loading.value = true
  try {
    const r: any = await api.getVillageList()
    if (r.success) {
      trades.value = r.data.list || []
    }
  } catch (e) {
    console.error('Failed to load village trades:', e)
  }
  loading.value = false
}

const submitTrade = async () => {
  if (!submitForm.value.itemInput || !submitForm.value.itemOutput) return
  submitting.value = true
  try {
    const r: any = await api.submitVillageTrade(submitForm.value)
    if (r.success) {
      showSubmitModal.value = false
      submitForm.value = { world: 'survival', x: 0, y: 0, z: 0, itemInput: '', itemOutput: '', price: 0 }
      alert('提交成功，等待管理员审核')
    }
  } catch (e) {
    console.error('Failed to submit trade:', e)
    alert('提交失败')
  }
  submitting.value = false
}

const getWorldName = (world: string) => {
  const map: Record<string, string> = { survival: '生存世界', resource: '资源世界', industrial: '工业世界' }
  return map[world] || world
}

const getWorldClass = (world: string) => {
  const map: Record<string, string> = {
    survival: 'bg-green-500/20 text-green-400',
    resource: 'bg-blue-500/20 text-blue-400',
    industrial: 'bg-purple-500/20 text-purple-400'
  }
  return map[world] || 'bg-stone-500/20 text-stone-400'
}
</script>
