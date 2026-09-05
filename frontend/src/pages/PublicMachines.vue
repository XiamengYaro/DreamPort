<template>
  <div class="p-6 pt-24 pb-20">
    <div class="max-w-6xl mx-auto">
      <!-- 标题 -->
      <div class="card p-6 mb-6">
        <h1 class="text-2xl font-bold text-white">公共机器</h1>
        <p class="text-stone-400">玩家可上传机器坐标和信息，需管理员审核通过后方可展示</p>
      </div>

      <!-- 筛选栏 -->
      <div class="flex gap-2 mb-6 flex-wrap">
        <select v-model="currentWorld" class="input w-auto px-3 py-2 rounded-xl border border-stone-700 bg-stone-800/50 text-stone-300 text-sm">
          <option value="all">所有世界</option>
          <option value="survival">生存世界</option>
          <option value="factory">工厂世界</option>
          <option value="resource">资源世界</option>
        </select>
        <select v-model="currentType" class="input w-auto px-3 py-2 rounded-xl border border-stone-700 bg-stone-800/50 text-stone-300 text-sm">
          <option value="all">所有类型</option>
          <option value="redstone">红石机器</option>
          <option value="mob_grinder">刷怪塔</option>
          <option value="farm">农场</option>
          <option value="automation">自动化设备</option>
          <option value="transport">交通系统</option>
          <option value="other">其他</option>
        </select>
        <button @click="showSubmitModal = true" class="ml-auto btn-primary flex items-center gap-2">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          提交机器
        </button>
      </div>

      <!-- 机器列表 -->
      <div class="card p-6">
        <div v-if="loading" class="text-center py-8 text-stone-500">
          <svg class="animate-spin h-8 w-8 mx-auto mb-2 text-orange-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          加载中...
        </div>
        <div v-else-if="filteredList.length === 0" class="text-center py-8 text-stone-500">暂无数据</div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div v-for="machine in filteredList" :key="machine.id" class="bg-stone-800/50 rounded-xl border border-stone-700 overflow-hidden hover:border-orange-500/50 transition-all">
            <!-- 截图 -->
            <div v-if="machine.screenshotUrl" class="aspect-video bg-stone-900 relative">
              <img :src="machine.screenshotUrl" class="w-full h-full object-cover" alt="机器截图" />
              <div class="absolute top-2 right-2 flex gap-2">
                <span class="px-2 py-1 rounded text-xs bg-blue-500/90 text-white backdrop-blur">{{ getMachineTypeName(machine.type) }}</span>
                <span :class="getMachineWorldClass(machine.world)" class="px-2 py-1 rounded text-xs backdrop-blur">{{ getMachineWorldName(machine.world) }}</span>
              </div>
            </div>
            <div v-else class="aspect-video bg-gradient-to-br from-stone-700 to-stone-800 flex items-center justify-center">
              <span class="text-stone-500">暂无截图</span>
            </div>
            
            <!-- 内容 -->
            <div class="p-4">
              <h3 class="font-bold text-white text-lg mb-2">{{ machine.name }}</h3>
              <div class="space-y-2 text-sm">
                <div class="flex items-center gap-2 text-stone-400">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  {{ machine.x }}, {{ machine.y }}, {{ machine.z }}
                </div>
                <div class="flex items-center gap-2 text-stone-400">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                  建造者：{{ machine.builder }}
                </div>
                <div class="mt-3 pt-3 border-t border-stone-700">
                  <p class="text-stone-300 text-sm">{{ machine.usage }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 提交弹窗 -->
      <div v-if="showSubmitModal" class="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50" @click.self="showSubmitModal = false">
        <div class="card w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto">
          <h3 class="text-lg font-semibold text-white mb-4">提交公共机器</h3>
          <div class="space-y-4">
            <div>
              <label class="block text-sm text-stone-300 mb-1">机器名称 <span class="text-red-400">*</span></label>
              <input v-model="submitForm.name" type="text" class="input w-full" placeholder="请输入机器名称" required />
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">机器类型 <span class="text-red-400">*</span></label>
              <select v-model="submitForm.type" class="input w-full">
                <option value="">请选择类型</option>
                <option value="redstone">红石机器</option>
                <option value="mob_grinder">刷怪塔</option>
                <option value="farm">农场</option>
                <option value="automation">自动化设备</option>
                <option value="transport">交通系统</option>
                <option value="other">其他</option>
              </select>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">所在世界 <span class="text-red-400">*</span></label>
              <select v-model="submitForm.world" class="input w-full">
                <option value="">请选择世界</option>
                <option value="survival">生存世界</option>
                <option value="factory">工厂世界</option>
                <option value="resource">资源世界</option>
              </select>
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="block text-sm text-stone-300 mb-1">X 坐标 <span class="text-red-400">*</span></label>
                <input v-model.number="submitForm.x" type="number" class="input w-full" placeholder="0" required />
              </div>
              <div>
                <label class="block text-sm text-stone-300 mb-1">Y 坐标 <span class="text-red-400">*</span></label>
                <input v-model.number="submitForm.y" type="number" class="input w-full" placeholder="0" required />
              </div>
              <div>
                <label class="block text-sm text-stone-300 mb-1">Z 坐标 <span class="text-red-400">*</span></label>
                <input v-model.number="submitForm.z" type="number" class="input w-full" placeholder="0" required />
              </div>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">用途描述 <span class="text-red-400">*</span></label>
              <textarea v-model="submitForm.usage" class="input w-full" rows="3" placeholder="请详细描述机器的功能和用途" required></textarea>
            </div>
            <div>
              <label class="block text-sm text-stone-300 mb-1">机器截图（可选）</label>
              <input ref="screenshotInput" type="file" accept="image/*" @change="handleScreenshotUpload" class="input w-full" />
              <p v-if="screenshotFile" class="text-xs text-emerald-400 mt-1">已选择：{{ screenshotFile.name }}</p>
            </div>
          </div>
          <div class="flex gap-3 mt-6">
            <button @click="showSubmitModal = false" class="btn-ghost flex-1">取消</button>
            <button @click="submitMachine" class="btn-primary flex-1" :disabled="submitting">{{ submitting ? '提交中...' : '提交' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import api from '@/services/api'

const loading = ref(false)
const machines = ref<any[]>([])
const currentWorld = ref('all')
const currentType = ref('all')
const showSubmitModal = ref(false)
const submitting = ref(false)
const screenshotInput = ref<HTMLInputElement>()
const screenshotFile = ref<File | null>(null)

const submitForm = ref({
  name: '',
  type: '',
  world: '',
  x: 0,
  y: 0,
  z: 0,
  usage: ''
})

const filteredList = computed(() => {
  let result = machines.value
  
  if (currentWorld.value !== 'all') {
    result = result.filter(m => m.world === currentWorld.value)
  }
  
  if (currentType.value !== 'all') {
    result = result.filter(m => m.type === currentType.value)
  }
  
  return result
})

onMounted(async () => {
  await loadMachines()
})

const loadMachines = async () => {
  loading.value = true
  try {
    const r: any = await api.getMachineList()
    if (r.success) {
      machines.value = r.data.list || []
    }
  } catch (e) {
    console.error('Failed to load public machines:', e)
  }
  loading.value = false
}

const handleScreenshotUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    screenshotFile.value = target.files[0]
  }
}

const submitMachine = async () => {
  // 验证必填项
  if (!submitForm.value.name || !submitForm.value.type || !submitForm.value.world || 
      !submitForm.value.usage || submitForm.value.x === undefined || submitForm.value.y === undefined || submitForm.value.z === undefined) {
    alert('请填写所有必填项')
    return
  }

  submitting.value = true
  try {
    // 先上传截图
    let screenshotUrl: string | null = null
    if (screenshotFile.value) {
      const uploadResult: any = await api.uploadMachineScreenshot(screenshotFile.value)
      if (uploadResult.success) {
        screenshotUrl = uploadResult.data.url
      }
    }

    // 提交机器信息
    const data = { ...submitForm.value }
    if (screenshotUrl) {
      data.screenshotUrl = screenshotUrl
    }
    
    const r: any = await api.submitMachine(data)
    if (r.success) {
      showSubmitModal.value = false
      resetForm()
      alert('提交成功！等待管理员审核通过后即可查看')
    }
  } catch (e) {
    console.error('Failed to submit machine:', e)
    alert('提交失败，请重试')
  }
  submitting.value = false
}

const resetForm = () => {
  submitForm.value = { name: '', type: '', world: '', x: 0, y: 0, z: 0, usage: '' }
  screenshotFile.value = null
  if (screenshotInput.value) {
    screenshotInput.value.value = ''
  }
}

// 获取显示名称
const getMachineTypeName = (type: string) => {
  const map: Record<string, string> = {
    redstone: '红石机器',
    mob_grinder: '刷怪塔',
    farm: '农场',
    automation: '自动化设备',
    transport: '交通系统',
    other: '其他'
  }
  return map[type] || type
}

const getMachineWorldName = (world: string) => {
  const map: Record<string, string> = {
    survival: '生存世界',
    factory: '工厂世界',
    resource: '资源世界'
  }
  return map[world] || world
}

const getMachineWorldClass = (world: string) => {
  const map: Record<string, string> = {
    survival: 'bg-green-500/90 text-white',
    factory: 'bg-blue-500/90 text-white',
    resource: 'bg-purple-500/90 text-white'
  }
  return map[world] || 'bg-stone-500/90 text-white'
}
</script>

<style scoped>
.input {
  background-color: rgb(51, 65, 85);
  border-color: rgb(51, 65, 85);
  color: white;
}

.input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.tab-btn {
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  transition: all 0.2s;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid transparent;
}

.tab-btn:hover {
  color: white;
  background: rgba(51, 65, 85, 0.8);
}

.tab-btn.active {
  color: #fb923c;
  background: rgba(251, 146, 60, 0.15);
  border-color: rgba(251, 146, 60, 0.3);
}
</style>
