<template>
  <div class="AddSpacePage">
    <a-form layout="vertical" :model="formData" @finish="handleSubmit">
      <a-form-item label="空间名称" name="spaceName">
        <a-input v-model:value="formData.spaceName" placeholder="请输入空间名称" allow-clear />
      </a-form-item>
      <a-form-item label="空间级别" name="spaceLevel">
        <a-select
          v-model:value="formData.spaceLevel"
          :options="SPACE_LEVEL_OPTIONS"
          placeholder="请输入空间级别"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <div v-if="loginUserStore.loginUser.userRole === 'admin'">
        <a-form-item label="空间尺寸" name="maxSize">
          <a-input v-model:value="formData.maxSize" placeholder="请输入空间最大尺寸" allow-clear />
        </a-form-item>
        <a-form-item label="空间大小" name="maxCount">
          <a-input v-model:value="formData.maxCount" placeholder="请输入空间最大容纳照片数量" allow-clear />
        </a-form-item>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%" :loading="loading">
          提交
        </a-button>
      </a-form-item>

      <a-card title="空间级别介绍">
        <a-typography-paragraph>
          * 目前仅支持开通普通版，如需升级空间，请联系
          <a href="https://bilibili.com" target="_blank">哈基米</a>。
        </a-typography-paragraph>
        <a-typography-paragraph v-for="spaceLevel in spaceLevelList">
          {{ spaceLevel.text }}： 大小 {{ formatSize(spaceLevel.maxSize) }}， 数量
          {{ spaceLevel.maxCount }}
        </a-typography-paragraph>
      </a-card>
    </a-form>

  </div>
</template>

<script setup lang="ts">

import { onMounted, reactive, ref } from 'vue'
import { SPACE_LEVEL_ENUM, SPACE_LEVEL_OPTIONS } from '@/constants/space'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost
} from '@/api/spaceController'
import { message } from 'ant-design-vue'
import router from '@/router'
import { formatSize } from '@/utils'
import { useRoute } from 'vue-router'
import { useLoginUserStore } from '@/stores/user'

const loginUserStore = useLoginUserStore()

const formData = reactive<API.SpaceAddRequest | API.SpaceUpdateRequest>({
  spaceName: '',
  spaceLevel: SPACE_LEVEL_ENUM.COMMON,
})
const loading = ref(false)

const handleSubmit = async (values: any) => {
  const spaceId = oldSpace.value?.id
  loading.value = true
  let res
  // 更新
  if (spaceId) {
    res = await updateSpaceUsingPost({
      id: spaceId,
      ...formData,
    })
  } else {
    // 创建
    res = await addSpaceUsingPost({
      ...formData,
    })
  }
  if (res.data.code === 0 && res.data.data) {
    message.success('操作成功')
    let path = `/space/${spaceId ?? res.data.data}`
    router.push({
      path,
    })
  } else {
    message.error('操作失败，' + res.data.message)
  }
  loading.value = false
}

const spaceLevelList = ref<API.SpaceLevel[]>([])

// 获取空间级别
const fetchSpaceLevelList = async () => {
  const res = await listSpaceLevelUsingGet()
  if (res.data.code === 0 && res.data.data) {
    spaceLevelList.value = res.data.data
  } else {
    message.error('加载空间级别失败，' + res.data.message)
  }
}

onMounted(() => {
  fetchSpaceLevelList()
})


const route = useRoute()
const oldSpace = ref<API.SpaceVo>()

// 获取老数据
const getOldSpace = async () => {
  // 获取数据
  const id = route.query?.id
  if (id) {
    const res = await getSpaceVoByIdUsingGet({
      id: id,
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      oldSpace.value = data
      formData.spaceName = data.spaceName
      formData.spaceLevel = data.spaceLevel
      formData.maxSize = data.maxSize
      formData.maxCount = data.maxCount
    }
  }
}

// 页面加载时，请求老数据
onMounted(() => {
  getOldSpace()
})


</script>

<style scoped>

</style>
