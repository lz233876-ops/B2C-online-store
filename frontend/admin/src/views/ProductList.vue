<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { productApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, spuName: '' })

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.spuName) params.spuName = query.spuName
    const page = await productApi.spuPage(params)
    rows.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    await productApi.updateStatus(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function yuan(fen) {
  return fen == null ? '-' : (fen / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="query.spuName" placeholder="商品名称" clearable style="width: 220px"
                @keyup.enter="query.pageNum = 1; load()" />
      <el-button type="primary" @click="query.pageNum = 1; load()">查询</el-button>
      <el-button @click="load">刷新</el-button>
      <div class="hint">
        仅展示后台可见字段（含成本价）；C 端接口 <code>/api/app/product/**</code> 不含成本价
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="spuName" label="商品名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="类目" width="120" />
      <el-table-column prop="brandName" label="品牌" width="100" />
      <el-table-column label="展示价（元）" width="120">
        <template #default="{ row }">{{ yuan(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="冗余库存" width="100" />
      <el-table-column prop="skuCount" label="SKU" width="70" />
      <el-table-column label="审核" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.publishStatus === 2 ? 'success' : 'info'" effect="plain">
            {{ row.publishStatus === 2 ? '已通过' : '未通过(' + row.publishStatus + ')' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上下架" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
            {{ row.status === 1 ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 1 ? 'danger' : 'primary'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p) => { query.pageNum = p; load() }"
    />
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; margin-left: auto; }
.hint code { background: #f5f7fa; padding: 1px 5px; border-radius: 3px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
