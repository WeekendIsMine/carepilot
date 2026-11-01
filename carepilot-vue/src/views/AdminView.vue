<template>
  <div class="admin">
    <header class="head">
      <div class="tabs">
        <button :class="{ on: tab === 'kb' }" @click="tab = 'kb'">知识库</button>
        <button :class="{ on: tab === 'product' }" @click="tab = 'product'">商品管理</button>
        <button :class="{ on: tab === 'user' }" @click="tab = 'user'">用户管理</button>
        <button :class="{ on: tab === 'ticket' }" @click="tab = 'ticket'">售后工单</button>
        <button :class="{ on: tab === 'order' }" @click="tab = 'order'">商城订单</button>
      </div>
    </header>

    <KbPanel v-if="tab === 'kb'" />
    <ProductPanel v-else-if="tab === 'product'" />
    <UserPanel v-else-if="tab === 'user'" />
    <TicketPanel v-else-if="tab === 'ticket'" />
    <OrderPanel v-else-if="tab === 'order'" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import KbPanel from './admin/KbPanel.vue'
import ProductPanel from './admin/ProductPanel.vue'
import UserPanel from './admin/UserPanel.vue'
import TicketPanel from './admin/TicketPanel.vue'
import OrderPanel from './admin/OrderPanel.vue'
import '../styles/admin.css'

const tab = ref('kb')
</script>

<style scoped>
.admin { height: 100%; display: flex; flex-direction: column; background: var(--bg); }
.head { padding: 12px 24px 0; background: var(--surface); border-bottom: 1px solid var(--line); }
.tabs { display: flex; gap: 6px; }
.tabs button {
  border: none; background: transparent; padding: 10px 14px; cursor: pointer;
  font-size: 13px; color: var(--text-2); border-bottom: 2px solid transparent;
}
.tabs button.on { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }
</style>
