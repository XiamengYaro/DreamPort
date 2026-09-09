<template>
  <div class="min-h-screen p-4 pt-24 pb-20">
    <div class="max-w-5xl mx-auto">
      <div class="text-center mb-6">
        <h1 class="text-3xl font-bold tracking-tight text-white">任务中心</h1>
        <p class="mt-1 text-stone-400">完成任务赚积分,积分兑换游戏内奖励</p>
      </div>

      <div v-if="loading" class="card p-12 text-center text-stone-400">加载中...</div>

      <template v-else>
        <!-- 余额 + 签到 -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div class="card p-6 text-center">
            <div class="text-3xl font-bold text-orange-400">{{ center.balance }}</div>
            <div class="text-xs text-stone-400 mt-1">我的积分</div>
          </div>
          <div class="card p-6">
            <h3 class="text-sm font-semibold text-white mb-3">网页签到</h3>
            <button class="btn-primary w-full text-sm" :disabled="center.signin?.web"
              :class="center.signin?.web ? 'opacity-50' : ''" @click="doSignin('web')">
              {{ center.signin?.web ? '今日已签到' : '立即签到 +5 积分' }}
            </button>
            <p class="text-xs text-stone-500 mt-2">连续签到有额外加成任务</p>
          </div>
          <div class="card p-6">
            <h3 class="text-sm font-semibold text-white mb-3">游戏内签到</h3>
            <div class="text-sm" :class="center.signin?.game ? 'text-emerald-400' : 'text-stone-400'">
              {{ center.signin?.game ? '今日已签到 ✓' : '进服输入 /xmw signin 签到' }}
            </div>
            <p class="text-xs text-stone-500 mt-2">+10 积分,双端都签有加成</p>
          </div>
        </div>

        <!-- 任务列表 -->
        <div class="card p-6 mb-6">
          <div class="flex gap-2 mb-4">
            <button v-for="(label, key) in periodTabs" :key="key" @click="activePeriod = key"
              class="px-4 py-2 rounded-xl text-sm font-medium transition-colors"
              :class="activePeriod === key ? 'bg-orange-500/15 text-orange-400' : 'text-stone-400 hover:text-white hover:bg-white/5'">
              {{ label }}
            </button>
          </div>
          <div class="space-y-3">
            <div v-for="t in currentTasks" :key="t.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800">
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <div class="text-white font-medium text-sm">{{ t.name }}</div>
                  <div class="text-xs text-stone-500 mt-0.5">{{ t.desc }}</div>
                </div>
                <div class="flex items-center gap-3 shrink-0">
                  <span class="text-orange-400 font-semibold text-sm">+{{ t.points }}</span>
                  <button v-if="t.completed && !t.claimed" class="btn-primary text-xs" @click="claim(t)">
                    领取
                  </button>
                  <span v-else-if="t.claimed" class="text-xs text-emerald-400">已领取</span>
                </div>
              </div>
              <div class="mt-2 h-1.5 rounded-full bg-stone-800 overflow-hidden">
                <div class="h-full bg-gradient-to-r from-orange-500 to-amber-400 rounded-full transition-[width]"
                  :style="{ width: Math.min(100, Math.round(t.progress / t.target * 100)) + '%' }"></div>
              </div>
              <div class="text-xs text-stone-500 mt-1">{{ t.progress }} / {{ t.target }}</div>
            </div>
            <p v-if="currentTasks.length === 0" class="text-stone-500 text-sm text-center py-6">该周期暂无任务</p>
          </div>
        </div>

        <!-- 兑换商店 -->
        <div class="card p-6">
          <h3 class="text-lg font-semibold text-white mb-4">积分兑换</h3>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div v-for="r in shop" :key="r.id" class="p-4 rounded-xl bg-stone-900/40 border border-stone-800 flex flex-col">
              <div class="text-white font-medium text-sm mb-1">{{ r.name }}</div>
              <div class="text-xs text-stone-500 flex-1">{{ r.desc }}</div>
              <div class="flex items-center justify-between mt-3">
                <span class="text-orange-400 font-semibold">{{ r.cost }} 积分</span>
                <button class="btn-primary text-xs" :disabled="center.balance < r.cost" @click="redeem(r)">
                  {{ center.balance >= r.cost ? '兑换' : '积分不足' }}
                </button>
              </div>
            </div>
            <p v-if="shop.length === 0" class="text-stone-500 text-sm col-span-full text-center py-6">商店暂未上架商品</p>
          </div>
          <p class="text-xs text-stone-500 mt-4">兑换成功后奖励会发送到游戏内邮箱,进服输入 /mail 领取。</p>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from 'vue'
import api from '@/services/api'

const notify = inject('notify') as any
const loading = ref(true)
const center = ref<any>({ balance: 0, signin: {}, tasks: {} })
const shop = ref<any[]>([])
const activePeriod = ref('daily')

const periodTabs = { daily: '每日任务', weekly: '每周任务', monthly: '每月任务' }

const currentTasks = computed(() => (center.value.tasks || {})[activePeriod.value] || [])

const loadCenter = async () => {
  const r: any = await api.getTasksCenter()
  if (r.success) center.value = r.data
}

const loadShop = async () => {
  const r: any = await api.getPointsShop()
  if (r.success) shop.value = r.data.rewards || []
}

const doSignin = async () => {
  try {
    const r: any = await api.postPointsSignin()
    if (r.success) {
      notify && notify.success(r.message || '签到成功')
      await loadCenter()
    } else {
      notify && notify.error(r.message || '签到失败')
    }
  } catch (e: any) {
    notify && notify.error(e.message || '签到失败')
  }
}

const claim = async (t: any) => {
  try {
    const r: any = await api.postPointsClaim(t.id)
    if (r.success) {
      notify && notify.success('已领取 ' + r.data.points + ' 积分')
      t.claimed = true
      await loadCenter()
    } else {
      notify && notify.error(r.message || '领取失败')
    }
  } catch (e: any) {
    notify && notify.error(e.message || '领取失败')
  }
}

const redeem = async (r: any) => {
  try {
    const res: any = await api.postPointsRedeem(r.id)
    if (res.success) {
      notify && notify.success('兑换成功!奖励已发送到游戏内邮箱,进服输入 /mail 领取')
      await Promise.all([loadCenter(), loadShop()])
    } else {
      notify && notify.error(res.message || '兑换失败')
    }
  } catch (e: any) {
    notify && notify.error(e.message || '兑换失败')
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadCenter(), loadShop()])
  } finally {
    loading.value = false
  }
})
</script>
