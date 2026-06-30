# 任务：优化Agent对话体验——延迟、流式输出、Markdown渲染

## 背景与约束（先读完再动手）

LLM链路已经验证可用（DeepSeek模型，通过中转站接入，`llmEnabled: true`），回复质量良好（能基于真实库存数据做预算判断）。现在要解决三个体验问题，**不更换模型**——团队明确因为成本限制必须留在DeepSeek或同等价位的模型，本次任务范围不包括更换LLM提供商或模型型号。

三个问题：
1. 响应耗时20多秒，需要先定位耗时分布，再针对性优化
2. 没有流式输出，用户长时间盯着空白等待，体验差
3. 返回内容里的Markdown语法（`**加粗**`等）没有被前端渲染，星号原样显示

**严禁修改 `backend/Dockerfile` 和 `backend/settings.xml`，这两个文件已固定为单阶段构建方案。如果任务过程中需要重新构建backend镜像，本机先跑 `mvn -q -DskipTests package` 再 `docker compose build backend`，不要在容器内跑Maven。**

---

## 第一部分：定位20秒延迟到底花在哪一步

在做任何优化之前，先加日志埋点，搞清楚耗时分布，不要凭猜测优化。

在 `agent-service/app/main.py` 的 `/agent/chat` 处理函数里，在关键节点前后加时间戳日志（用 `time.perf_counter()`），至少覆盖这几个分段：

```python
import time

t0 = time.perf_counter()
# ... 读取短期/长期记忆 ...
t1 = time.perf_counter()
# ... 第一次LLM调用（决定是否调用工具）...
t2 = time.perf_counter()
# ... 工具调用（如果有，比如search_products实际HTTP请求耗时）...
t3 = time.perf_counter()
# ... 第二次LLM调用（基于工具结果生成最终回复，如果LangChain走的是两轮调用模式）...
t4 = time.perf_counter()
# ... 写入长期记忆/短期记忆持久化 ...
t5 = time.perf_counter()

logger.info(
    f"timing breakdown: memory_load={t1-t0:.2f}s, "
    f"llm_call_1={t2-t1:.2f}s, tool_call={t3-t2:.2f}s, "
    f"llm_call_2={t4-t3:.2f}s, memory_save={t5-t4:.2f}s, "
    f"total={t5-t0:.2f}s"
)
```

重新构建并启动agent-service，发一条测试消息，查看日志里这行timing breakdown，把结果记录下来。**这一步做完后再继续下面的优化部分，不要跳过，否则后续优化是盲目的。**

根据常见情况，大概率会是以下几种之一，按实际日志判断对应优化：

- **如果`llm_call_1`和`llm_call_2`加起来占了大头（比如15秒以上）**：说明确实是模型推理本身慢+网络到中转站的延迟，这种情况下"两次LLM调用"（先判断要不要工具、再基于结果生成回复）是结构性原因，对应下面"减少不必要的二次调用"优化
- **如果`tool_call`耗时异常长**：说明是agent-service调用主后端API（`search_products`/`query_order_status`）这一步慢，需要检查backend这边的接口响应时间，或者是agent-service到backend的网络/DNS解析有问题
- **如果`memory_load`或`memory_save`耗时长**：说明是数据库读写慢，检查MySQL连接池配置或者有没有缺索引

---

## 第二部分：实现流式输出（SSE）

不管延迟优化能压缩到多少，流式输出本身能立刻改善"长时间空白等待"的体感，这是优先级最高的改动。

### 后端：agent-service 改造

把 `/agent/chat` 接口改为支持Server-Sent Events流式响应。FastAPI原生支持，用 `StreamingResponse`：

```python
from fastapi.responses import StreamingResponse
import json

@app.post("/agent/chat/stream")
async def chat_stream(request: ChatRequest):
    async def event_generator():
        # 如果用的是LangChain的astream或者底层OpenAI兼容客户端的stream=True模式
        async for chunk in run_langchain_agent_streaming(request):
            # chunk可能是: 工具调用通知、文本片段、或最终完成标记
            yield f"data: {json.dumps({'type': 'token', 'content': chunk}, ensure_ascii=False)}\n\n"
        yield f"data: {json.dumps({'type': 'done'}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")
```

**关键点**：LangChain的Agent如果走的是"先判断工具调用、再生成最终回复"这种两段式逻辑，流式只能在"生成最终回复"这一段做（工具调用判断阶段本身通常很快返回一个结构化决策，没有逐字生成的必要，不需要也没法做成流式）。如果当前用的是`AgentExecutor`这类高层封装不直接支持流式，考虑改用`.astream_events()`方法（LangChain 0.3.x版本支持），可以拿到包括工具调用开始/结束、token生成等细粒度事件，参考LangChain官方文档关于streaming的部分（如果不确定具体API用法，可以查阅当前项目里`langchain`、`langchain-openai`的具体版本对应的streaming文档，不要凭记忆假设API）。

保留原来的非流式 `/agent/chat` 接口不要删除（前端如果有用到测试脚本`scripts/agent_smoke.py`依赖这个接口，删除会破坏已有验收脚本），新增一个 `/agent/chat/stream` 作为流式版本。

### 前端：ChatWidget.vue 改造

把原来用 `axios.post()` 一次性等待完整响应的逻辑，改为用 `EventSource` 或 `fetch` + `ReadableStream` 处理SSE：

```javascript
async function sendMessageStream(message) {
  const response = await fetch('http://127.0.0.1:8000/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: currentUserId.value, sessionId: sessionId.value, message })
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let assistantMessage = reactive({ role: 'assistant', content: '' })
  messages.value.push(assistantMessage)

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const chunk = decoder.decode(value)
    const lines = chunk.split('\n').filter(l => l.startsWith('data: '))
    for (const line of lines) {
      const data = JSON.parse(line.slice(6))
      if (data.type === 'token') {
        assistantMessage.content += data.content
      } else if (data.type === 'done') {
        // 流结束，可以在这里触发"已查询商品库"之类的工具调用标签展示
      }
    }
  }
}
```

这样用户能看到文字逐步出现，而不是盯着空白等20秒，**这是体感上最直接的改善，即使总耗时没变，等待焦虑会大幅降低**。

---

## 第三部分：渲染Markdown，去掉星号乱码

这是纯前端问题，最简单也最该优先做（改动量最小，立刻见效）。

### 安装Markdown渲染库

```bash
cd frontend
npm install markdown-it
```

### 在 ChatWidget.vue 里渲染

```javascript
import MarkdownIt from 'markdown-it'
const md = new MarkdownIt({
  breaks: true,    // 把单个换行符转成<br>，聊天场景更自然
  linkify: true,   // 自动识别URL转成链接
})

function renderMarkdown(text) {
  return md.render(text)
}
```

```html
<div class="message-content" v-html="renderMarkdown(message.content)"></div>
```

**安全提醒**：用`v-html`渲染需要注意XSS风险。因为这里渲染的内容来源是你自己的LLM API返回，不是用户可任意控制的输入，风险较低，但稳妥起见可以加一层净化：

```bash
npm install dompurify
```

```javascript
import DOMPurify from 'dompurify'

function renderMarkdown(text) {
  const rawHtml = md.render(text)
  return DOMPurify.sanitize(rawHtml)
}
```

### 配套CSS

给Markdown渲染出的元素加基础样式（加粗、列表、代码块等），保持和此前确认的"丝滑精致"设计语言一致，不要引入花哨的代码高亮配色：

```css
.message-content :deep(strong) {
  font-weight: 600;
  color: var(--color-text-primary);
}
.message-content :deep(ul), .message-content :deep(ol) {
  padding-left: 1.2em;
  margin: 0.5em 0;
}
.message-content :deep(code) {
  background: var(--color-surface-subtle, #f5f5f7);
  padding: 0.1em 0.4em;
  border-radius: 4px;
  font-size: 0.9em;
}
.message-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 0.8em 0;
}
```

---

## 第四部分：根据第一部分的耗时分析，做针对性优化（仅在确认瓶颈后执行）

这部分内容**必须等第一部分的timing breakdown日志结果出来后才能确定具体做哪几项**，不要不看日志就动手改。常见可选项：

- **如果是LLM调用本身慢**：检查`agent-service`里调用LLM时有没有设置合理的`max_tokens`上限（生成内容越长越慢，机械键盘推荐这种回复不需要特别长，可以适当限制在300-500 tokens）；检查中转站的网络延迟（`curl -w "%{time_total}\n" -o /dev/null -s 你的中转站base_url`测一下基础网络延迟，排除是不是中转站本身慢）
- **如果是两次LLM调用（工具判断+最终生成）导致翻倍**：考虑用更轻量的方式做"是否需要调用工具"的判断（比如简单的关键词匹配或更小的判断逻辑），只在真正需要生成自然语言回复时才调用完整的LLM，减少一次完整的模型推理往返
- **如果是工具调用（HTTP请求backend）慢**：检查agent-service到backend的网络配置，确认是走Docker内部网络（`http://backend:8080`）而不是绕了弯路

## 验收标准

- [ ] timing breakdown日志已加入并产出至少3次真实请求的耗时分布数据
- [ ] `/agent/chat/stream` 接口可用，前端聊天窗使用流式接口，用户能看到文字逐步出现而非长时间空白等待
- [ ] 原有非流式 `/agent/chat` 接口保留，`scripts/agent_smoke.py` 验收脚本依然能正常运行通过
- [ ] 聊天回复中的Markdown语法（加粗、列表、分割线）正确渲染为视觉样式，不再有裸露的`**`星号
- [ ] Markdown渲染样式符合现有设计token（颜色、圆角变量复用，不引入新的视觉风格）
- [ ] 用浏览器实际测试"推荐3款适合写代码的机械键盘"这条请求，确认流式效果+Markdown渲染同时生效

## 报告格式要求

1. timing breakdown的实际日志数据（至少贴3次请求的结果），并指出瓶颈在哪一段
2. 针对瓶颈做了哪些优化，优化前后耗时对比
3. 流式输出改造的关键代码改动（前后端各贴核心diff）
4. Markdown渲染效果确认（可以描述或者说明如何验证）
5. 如果LangChain的streaming API使用上遇到版本不兼容问题，完整贴出报错信息

## 不在本次范围内

- 不更换LLM模型/提供商，继续使用当前DeepSeek配置
- 不修改 `backend/Dockerfile`、`backend/settings.xml`
- 不改动数据库表结构
- 不引入代码高亮库（如highlight.js）渲染代码块，Markdown渲染只需要支持加粗、列表、分割线这类聊天场景常见的基础格式即可，过度设计没有必要
