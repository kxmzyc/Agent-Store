# 任务：接入真实LLM，让LangChain Agent脱离离线兜底模式

## 背景

诊断已确认：当前 `/health` 返回 `llmEnabled: false`，所有Agent回复都走的是 `offline_agent()` 固定模板兜底，不是真正的LangChain Agent在调用大模型做推理。这意味着之前所有"Agent验收通过"的测试，验证的只是工具调用的代码路径能否被触发，**没有验证过大模型是否真的在思考、规划、生成回复**——这是大纲第10-11天的核心考核点，必须现在补上。

## 安全前提（必须先做，不能跳过）

API Key是敏感凭据，绝对不能出现在以下任何地方：
- 不能硬编码在任何 `.py`、`.yml`、`.java` 等源代码文件里
- 不能出现在会被 `git add` 的任何文件里
- 不能出现在日志输出里（注意 `print()` 或 `logger` 调用不要不小心把环境变量内容打出来）

正确做法：通过 `.env` 文件 + `.gitignore` 排除 + docker-compose的环境变量注入机制处理。

## 执行步骤

### 第1步：检查当前 .gitignore 是否已排除 .env

```bash
cat .gitignore | grep -E "^\.env$|^\.env\*"
```

如果没有这一行，添加：
```bash
echo ".env" >> .gitignore
```

确认这一步在第2步之前完成。

### 第2步：在项目根目录创建 .env 文件（不要提交进Git）

创建 `smart-mall/.env`（如果已存在则编辑），写入：

```
LLM_API_KEY=__PLACEHOLDER_WILL_BE_PROVIDED_BY_USER__
LLM_BASE_URL=__PLACEHOLDER_WILL_BE_PROVIDED_BY_USER__
```

**这两个占位符的真实值由用户本人直接在终端里手动替换，不要让Codex生成、猜测或询问具体的key/url内容。** Codex只需要确保代码能正确读取这两个环境变量即可，不需要、也不应该接触真实凭据的值。

### 第3步：确认 docker-compose.yml 正确引用 .env

检查 `docker-compose.yml` 里 `agent-service` 服务的环境变量配置部分，应该类似：

```yaml
agent-service:
  build: ./agent-service
  env_file:
    - .env
  environment:
    - LLM_API_KEY=${LLM_API_KEY}
    - LLM_BASE_URL=${LLM_BASE_URL}
```

如果当前是写死的空字符串或者占位符（比如之前诊断提到的"默认是空的"），改成上面这种从 `.env` 读取的方式。如果项目里还没有 `env_file` 这个写法，添加它。

### 第4步：确认 agent-service 代码正确读取这两个环境变量

检查 `agent-service/app/main.py` 里 `llm()` 函数（之前诊断提到在第275行附近）目前判断"是否配置了LLM"的逻辑，确认它读取的环境变量名称和第2步`.env`文件里写的完全一致（`LLM_API_KEY`、`LLM_BASE_URL`，不要出现大小写或命名不一致导致读不到的问题）。

如果项目用的是Anthropic SDK，确认初始化代码类似：
```python
from langchain_anthropic import ChatAnthropic
import os

llm_instance = ChatAnthropic(
    model="claude-sonnet-4-6",
    api_key=os.environ.get("LLM_API_KEY"),
    base_url=os.environ.get("LLM_BASE_URL"),  # 如果走中转站/relay，需要这个参数
)
```

如果项目用的是OpenAI兼容协议（中转站常见做法），确认类似：
```python
from langchain_openai import ChatOpenAI
import os

llm_instance = ChatOpenAI(
    model="claude-sonnet-4-6",  # 或中转站要求的模型名
    api_key=os.environ.get("LLM_API_KEY"),
    base_url=os.environ.get("LLM_BASE_URL"),
)
```

**不要假设具体用哪种方式，先看现有代码已经引入了哪个SDK/库，按现有结构补全，不要引入新的依赖库。**

### 第5步：重新构建并启动agent-service

```bash
docker compose up -d --build agent-service
```

### 第6步：验证LLM真的被启用了

```bash
curl http://127.0.0.1:8000/health
```

确认返回结果里 `llmEnabled` 变成了 `true`（不再是`false`）。

然后实际发一条测试消息，确认走的是真实LLM路径而不是离线兜底：

```bash
curl -X POST http://127.0.0.1:8000/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": 2, "sessionId": "test-llm-verify", "message": "推荐3款适合写代码的机械键盘"}'
```

**这条测试消息故意用之前诊断出的bug场景（"推荐3款"这种纯数字会被预算正则误伤）来测试**——如果LLM真的在工作，它应该能理解"3款"是数量不是预算，正常返回商品推荐；如果还是返回"未找到符合条件的商品"，说明#1号bug（预算正则）和LLM未启用这两个问题是叠加的，需要一起修（见下面"同时要修的bug"部分）。

查看日志，确认不再出现之前那行：
```bash
docker compose logs agent-service --tail 50 | grep -i "offline fallback"
```
如果这行完全消失（或者只在LLM调用真的失败时才偶尔出现，而不是每次都出现），说明真实LLM链路已经跑通。

### 第7步（重要）：同时修复 #1 预算正则误伤的bug

既然现在LLM真的会被调用了，之前诊断出的预算正则bug（`agent-service/app/main.py:222`附近）必须一起修，否则LLM正确理解了"3款"是数量，但工具调用前的预处理逻辑依然会错误地把"3"当成预算上限传给`search_products`，导致同样返回空结果。

把现有的：
```python
match = re.search(r"(\d+(?:\.\d+)?)\s*(?:元|块|预算|以内)?", message)
```

改成要求数字后必须紧跟预算相关词才生效（不再是可选）：
```python
match = re.search(r"(\d+(?:\.\d+)?)\s*(?:元|块钱|块|预算以内|以内|左右)", message)
```

这样"推荐3款键盘"不会匹配（因为"3"后面跟的是"款"，不在预算关键词列表里），"预算500""500元以内"这种才会正确触发预算过滤。

### 第8步：同时修复 #6 LLM正常回复被离线逻辑覆盖的bug

之前诊断指出 `main.py:350-352` 附近的逻辑：当LLM已经正常配置、且本轮对话LLM合理地选择不调用任何工具（比如礼貌拒答一个非购物问题，或者单纯的闲聊回复）时，代码会错误地判定"没用工具=失败"，转而用离线兜底模板覆盖掉LLM本来生成的、得体的回复。

检查这部分判断逻辑，应该改成：**只有当LLM调用本身抛出异常或返回错误时才回退到离线兜底，不能仅凭"这一轮没有调用工具"就判定为需要兜底**——LLM不调用工具可能是它合理的决策（比如用户问"今天天气怎么样"，正确行为就是礼貌说明自己是购物助手，而不是被迫调用一个不相关的工具）。

## 验收标准

- [ ] `.env` 文件已创建，且确认不会被 `git status` 显示为待提交文件（验证：`git status` 看不到 `.env`）
- [ ] `docker compose up -d --build agent-service` 成功，无报错
- [ ] `curl http://127.0.0.1:8000/health` 返回 `llmEnabled: true`
- [ ] 测试"推荐3款适合写代码的机械键盘"，返回真实商品推荐，不再误判为预算过滤导致空结果
- [ ] 日志里 `offline fallback` 不再是每次对话都出现
- [ ] 测试一个非购物相关问题（如"今天天气怎么样"），确认回复是LLM生成的得体拒答，且不同次提问的措辞有自然差异（不再是固定模板字符串），证明是模型在动态生成而不是规则匹配
- [ ] 测试同一个商品类问题问两次，确认两次回复的措辞有所不同（哪怕核心商品信息一致，组织语言的方式应该不同），这是"真正由LLM生成"和"模板拼接"的关键区别

## 报告格式要求

完成后请汇报：
1. `.env`文件是否创建成功，`.gitignore`是否正确排除（不要在报告里粘贴.env的实际内容）
2. `/health`接口返回的`llmEnabled`字段值
3. "推荐3款机械键盘"这条测试的实际返回内容
4. 日志里`offline fallback`出现的频率变化（修复前 vs 修复后）
5. #1预算正则和#6离线覆盖这两个bug的具体代码改动（diff形式）
6. 如果LLM调用本身报错（比如API Key或base_url配置不对导致连不上），完整贴出报错信息，不要只说"配置好像不对"

## 不在本次任务范围内

- 不要在这次任务里同时处理 #2（资料覆盖）、#3（库存回滚并发）、#4（端口文档不一致）、#5（购物车不校验库存）这几个bug，那些是独立问题，留到下一轮单独处理，避免一次改动范围太大不好排查问题
- 不要修改前端 `ChatWidget.vue` 的传参逻辑（诊断已确认前端没问题）
- 不要引入新的LLM SDK或更换现有框架，只在现有LangChain代码结构基础上补全配置
