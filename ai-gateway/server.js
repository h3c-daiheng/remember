import http from 'node:http'

/**
 * 简易 ai-gateway：对外暴露智忆契约的两个接口，对内转发到 OpenAI 兼容 API。
 * url / apikey / model 全部走环境变量，不硬编码。
 */
const PORT = process.env.PORT || 4502
const OPENAI_API_BASE = (process.env.OPENAI_API_BASE || '').replace(/\/+$/, '')
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || ''
const OPENAI_CHAT_MODEL = process.env.OPENAI_CHAT_MODEL || 'gpt-4o-mini'
const OPENAI_EMBEDDING_MODEL = process.env.OPENAI_EMBEDDING_MODEL || 'text-embedding-3-small'
const APP_TOKEN = process.env.APP_TOKEN || ''

function sendJson(res, status, body) {
    const json = JSON.stringify(body)
    res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(json),
    })
    res.end(json)
}
const ok = (res, data) => sendJson(res, 200, { code: 0, message: 'success', data })
const fail = (res, status, message) => sendJson(res, status, { code: status, message, data: null })

function checkAuth(req) {
    if (!APP_TOKEN) return true
    const auth = req.headers['authorization'] || ''
    const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : auth.trim()
    return token === APP_TOKEN
}

function readBody(req) {
    return new Promise((resolve, reject) => {
        let data = ''
        req.on('data', (c) => { data += c })
        req.on('end', () => {
            try { resolve(data ? JSON.parse(data) : {}) } catch (e) { reject(e) }
        })
        req.on('error', reject)
    })
}

async function fetchOpenAI(res, path, body, label) {
    if (!OPENAI_API_BASE || !OPENAI_API_KEY) {
        fail(res, 502, `${label}: 未配置 OPENAI_API_BASE / OPENAI_API_KEY`)
        return null
    }
    let resp
    try {
        resp = await fetch(OPENAI_API_BASE + path, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${OPENAI_API_KEY}`,
            },
            body: JSON.stringify(body),
        })
    } catch (e) {
        fail(res, 502, `${label}: 连接上游失败 ${e.message}`)
        return null
    }
    const text = await resp.text()
    let json = null
    try { json = JSON.parse(text) } catch { /* 非JSON */ }
    if (!resp.ok) {
        const msg = json?.error?.message || (text ? text.slice(0, 200) : resp.status)
        fail(res, 502, `${label}: 上游返回 ${resp.status} ${msg}`)
        return null
    }
    return json
}

async function handleChat(req, res) {
    const body = await readBody(req)
    const messages = Array.isArray(body.messages)
        ? body.messages.map((m) => ({ role: m.role, content: m.content }))
        : []
    if (body.systemPrompt) messages.unshift({ role: 'system', content: body.systemPrompt })
    if (body.userPrompt) messages.push({ role: 'user', content: body.userPrompt })
    if (messages.length === 0) return fail(res, 400, 'messages 为空')

    const upstream = {
        model: body.modelCode || OPENAI_CHAT_MODEL,
        messages,
        temperature: typeof body.temperature === 'number' ? body.temperature : 0.3,
    }
    if (body.maxTokens) upstream.max_tokens = body.maxTokens
    if (body.responseFormat) upstream.response_format = { type: body.responseFormat }

    const up = await fetchOpenAI(res, '/chat/completions', upstream, 'Chat')
    if (!up) return
    ok(res, {
        content: up.choices?.[0]?.message?.content || '',
        modelCode: up.model || OPENAI_CHAT_MODEL,
        promptTokens: up.usage?.prompt_tokens ?? 0,
        completionTokens: up.usage?.completion_tokens ?? 0,
        totalTokens: up.usage?.total_tokens ?? 0,
        requestId: up.id || '',
    })
}

async function handleEmbeddings(req, res) {
    const body = await readBody(req)
    const input = Array.isArray(body.input) ? body.input : []
    if (input.length === 0) return fail(res, 400, 'input 为空')

    const up = await fetchOpenAI(res, '/embeddings', {
        model: body.modelCode || OPENAI_EMBEDDING_MODEL,
        input,
    }, 'Embedding')
    if (!up) return
    ok(res, {
        embeddings: Array.isArray(up.data) ? up.data.map((d) => d.embedding || []) : [],
        modelCode: up.model || OPENAI_EMBEDDING_MODEL,
        totalTokens: up.usage?.total_tokens ?? 0,
        requestId: up.id || '',
    })
}

const server = http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/') {
        return sendJson(res, 200, { ok: true, service: 'ai-gateway', upstream: OPENAI_API_BASE || '(未配置)' })
    }
    if (req.method !== 'POST') return fail(res, 405, 'Method Not Allowed')
    if (!checkAuth(req)) return fail(res, 401, '鉴权失败：APP_TOKEN 不匹配')
    try {
        if (req.url === '/v1/chat/completions') return await handleChat(req, res)
        if (req.url === '/v1/embeddings') return await handleEmbeddings(req, res)
        fail(res, 404, 'Not Found: ' + req.url)
    } catch (e) {
        if (!res.headersSent) fail(res, 500, '内部错误: ' + (e?.message || e))
    }
})

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[ai-gateway] listening on :${PORT} upstream=${OPENAI_API_BASE || '(未配置)'} chat=${OPENAI_CHAT_MODEL} embedding=${OPENAI_EMBEDDING_MODEL}`)
})
