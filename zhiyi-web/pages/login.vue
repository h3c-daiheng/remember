<template>
    <div class="min-h-[calc(100vh-8rem)] flex items-center justify-center px-6 py-12">
        <el-card class="w-full max-w-md" shadow="hover">
            <div class="text-center mb-8">
                <BrandLogo
                    size="large"
                    layout="vertical"
                    show-text
                    show-subtitle
                    show-company
                    class="justify-center mb-6"
                />
                <h1 class="text-2xl font-semibold text-gray-900">登录</h1>
            </div>
            <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
                <el-form-item label="用户名">
                    <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
                </el-form-item>
                <el-form-item label="密码">
                    <el-input
                        v-model="form.password"
                        type="password"
                        placeholder="请输入密码"
                        size="large"
                        show-password
                        @keyup.enter="handleLogin"
                    />
                </el-form-item>
                <el-button
                    type="primary"
                    size="large"
                    class="w-full"
                    :loading="loading"
                    @click="handleLogin"
                >
                    登录
                </el-button>
                <p v-if="errorMsg" class="text-red-500 text-sm mt-3 text-center">{{ errorMsg }}</p>
                <p class="text-xs text-gray-400 mt-4 text-center">默认账号 admin / 123456</p>
            </el-form>
        </el-card>
    </div>
</template>

<script setup>
import { loginRequest } from '~/services/auth.service'
import { saveStoredToken } from '~/utils/token'

definePageMeta({
    layout: 'default',
})

useHead({
    title: '登录',
})

usePageTracker()

const route = useRoute()
const { fetchCurrentUser } = useAuth()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
    if (!form.username || !form.password) {
        errorMsg.value = '请输入用户名和密码'
        return
    }
    loading.value = true
    errorMsg.value = ''
    try {
        const data = await loginRequest(form.username, form.password)
        saveStoredToken(data.token)
        await fetchCurrentUser()
        const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/memory'
        await navigateTo(redirect, { replace: true })
    } catch (error) {
        errorMsg.value = error?.message || '登录失败'
    } finally {
        loading.value = false
    }
}
</script>
