import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FontFormatting;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerateSmartMallTestWorkbook {
    private static final Path ROOT = Path.of("G:/claudeproject/Agent Store");
    private static final Path WORK_DIR = ROOT.resolve("outputs/019f552a-52fd-7f31-80fd-c36b3fe03a6f");
    private static final Path TEMPLATE = Path.of("F:/软件测试表格模板包.xlsx");
    private static final Path OUTPUT = WORK_DIR.resolve("Agent-Store-智能商城-软件测试交付包-20260712.xlsx");
    private static final Path PREVIEW_DIR = WORK_DIR.resolve("qa-previews");
    private static final String TEST_DATE = "2026-07-12";

    private static final String NAVY = "17324D";
    private static final String TEAL = "0F766E";
    private static final String ORANGE = "D97706";
    private static final String HEADER = "254C5B";
    private static final String PALE_BLUE = "EAF2F7";
    private static final String PALE_TEAL = "E7F5F3";
    private static final String WHITE = "FFFFFF";
    private static final String TEXT = "1F2937";
    private static final String MUTED = "5B6670";
    private static final String BORDER = "D8E1E8";
    private static final String PASS_BG = "DCFCE7";
    private static final String PASS_FG = "166534";
    private static final String FAIL_BG = "FEE2E2";
    private static final String FAIL_FG = "991B1B";
    private static final String WARN_BG = "FEF3C7";
    private static final String WARN_FG = "92400E";
    private static final String NEUTRAL_BG = "F3F4F6";
    private static final String NEUTRAL_FG = "4B5563";

    private GenerateSmartMallTestWorkbook() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        Files.createDirectories(PREVIEW_DIR);

        String templateSummary = inspectTemplate();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            buildPlan(workbook, styles, templateSummary);
            buildCases(workbook, styles);
            buildEnvironment(workbook, styles);
            buildDefects(workbook, styles);
            buildTestData(workbook, styles);
            buildReport(workbook, styles);

            workbook.setActiveSheet(0);
            workbook.setSelectedTab(0);
            workbook.getProperties().getCoreProperties().setTitle("Agent-Store 智能商城软件测试交付包");
            workbook.getProperties().getCoreProperties().setDescription("测试计划、测试用例、环境账号、缺陷、测试数据和测试报告");
            workbook.getProperties().getCoreProperties().setCreator("Codex / 项目组");

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();
            verifyWorkbook(workbook, evaluator);

            try (OutputStream output = Files.newOutputStream(OUTPUT)) {
                workbook.write(output);
            }
            renderWorkbook(workbook, evaluator);
            writeQaSummary(workbook, evaluator);
        }

        System.out.println("OUTPUT=" + OUTPUT);
        System.out.println("PREVIEWS=" + PREVIEW_DIR);
    }

    private static String inspectTemplate() throws IOException {
        if (!Files.exists(TEMPLATE)) {
            return "模板未找到；已按专业测试文档基线创建";
        }
        try (InputStream input = new FileInputStream(TEMPLATE.toFile()); XSSFWorkbook template = new XSSFWorkbook(input)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < template.getNumberOfSheets(); i++) {
                names.add(template.getSheetName(i));
            }
            return "参考模板：软件测试表格模板包.xlsx（" + template.getNumberOfSheets() + " 个页签：" + String.join("、", names) + "）";
        }
    }

    private static void buildPlan(XSSFWorkbook workbook, Styles styles, String templateSummary) {
        XSSFSheet sheet = workbook.createSheet("测试计划");
        beginSheet(sheet, "智能商城软件测试计划", templateSummary + "；本交付仅保留六个必需页签", 8, styles);
        setWidths(sheet, 16, 22, 25, 28, 28, 28, 22, 22);

        section(sheet, 3, "1. 基本信息", 8, styles);
        String[][] basic = {
                {"项目名称", "智能商城（淘宝 + AI 助手微型生态闭环）", "测试版本", "当前工作区 / 2026-07-12"},
                {"测试周期", "2026-07-12", "测试负责人", "Codex 执行，项目组复核"},
                {"测试环境", "Docker Compose 本机集成环境", "测试结论口径", "严格按首轮结果；复测单独说明"},
                {"依据", "AGENTS.md、源码、接口、运行日志和浏览器操作", "数据策略", "保留脚本生成的隔离夹具，不重置共享卷"}
        };
        int row = 4;
        for (String[] item : basic) {
            Row r = sheet.createRow(row++);
            writeCell(r, 0, item[0], styles.infoLabel);
            mergeAndWrite(sheet, r.getRowNum(), 1, 3, item[1], styles.infoValue);
            writeCell(r, 4, item[2], styles.infoLabel);
            mergeAndWrite(sheet, r.getRowNum(), 5, 7, item[3], styles.infoValue);
            r.setHeightInPoints(30);
        }

        row++;
        section(sheet, row++, "2. 测试范围", 8, styles);
        String[] scopeHeaders = {"范围", "模块", "核心内容", "主要风险", "本轮方法"};
        List<String[]> scope = rows("""
                范围内	用户模块	注册、登录、JWT、Refresh、个人资料、ADMIN 权限	认证旁路、错误码不一致、密码存储	JUnit/RestAssured + REST + 浏览器
                范围内	商品与搜索	分类、CRUD、分页、排序、模糊搜索、图片上传	边界参数、搜索排序、库存状态	集成测试 + REST + 浏览器 + 静态审计
                范围内	购物车与订单	加购、结算、下单、状态机、库存回补、事务	超卖、脏数据、主链路阻断	JUnit + 真实 MySQL 并发 + 浏览器
                范围内	AI 助手	工具调用、订单查询、短期/长期记忆、拒答	LLM 不确定性、跨会话污染、身份边界	真实模型烟测 + REST + 浏览器 + 代码审计
                范围内	部署与数据库	Compose、健康检查、日志、Schema/Seed、ER 文档	全新环境复现、端口与文档口径	Compose/日志/SQL/文档检查
                范围外	明确排除项	真实支付、OAuth、复杂 RBAC、多 SKU、MQ、网关、移动端	避免超出教学大纲	不执行，仅确认未误纳入验收
                """, 5);
        row = writeTable(sheet, row, scopeHeaders, scope, styles, -1);

        row++;
        section(sheet, row++, "3. 测试策略与准入/准出", 8, styles);
        String[] strategyHeaders = {"测试类型", "目标", "执行方式", "工具/证据", "通过标准"};
        List<String[]> strategy = rows("""
                单元/集成	用户、购物车、订单、优惠券、售后	执行后端 Maven 测试	JUnit5、Mockito、H2、RestAssured	全部测试 0 failures / 0 errors
                接口测试	鉴权、错误码、分页、状态机	调用本地 8081/8000 接口	PowerShell/curl、应用日志	状态码、响应体和数据副作用符合规格
                并发测试	100 请求竞争 80 库存	创建隔离用户和商品后并发直购	scripts/concurrent_order_test.py	成功 80、库存不足 20、stock=0、无异常
                AI 烟测	工具、订单、记忆、确认加购、拒答	调用真实模型和真实后端	scripts/agent_smoke.py	所有断言通过；首轮异常单独登记
                浏览器测试	登录、搜索、购物车、结算、订单、AI	Codex 应用内浏览器点击验证	DOM 快照、控制台日志	页面可见且无阻断脚本错误
                静态合规	事务注解、权限边界、ER、Seed、环境变量	源码与文档核对	rg、代码审计	满足 AGENTS.md 明确验收条款
                部署验证	一键启动、健康检查、日志	检查 Compose 和容器状态	Docker 29.6.1 / Compose 5.2.0	四服务 healthy，日志无 ERROR
                """, 5);
        row = writeTable(sheet, row, strategyHeaders, strategy, styles, -1);

        row++;
        section(sheet, row++, "4. 测试安排", 8, styles);
        String[] scheduleHeaders = {"阶段", "日期", "工作内容", "产出", "责任"};
        List<String[]> schedule = rows("""
                准备	2026-07-12	盘点需求、环境、账号、Seed 和测试入口	范围与环境清单	Codex
                自动化	2026-07-12	后端 30 测试、前端构建、Agent 烟测	执行记录和日志	Codex
                运行态	2026-07-12	REST 主链路、100/80 并发、容器健康	接口与并发证据	Codex
                浏览器	2026-07-12	登录、搜索、购物车、结算、订单和 AI	可见流程证据	Codex
                报告	2026-07-12	汇总用例、缺陷、风险和准出结论	本工作簿	Codex / 项目组复核
                """, 5);
        row = writeTable(sheet, row, scheduleHeaders, schedule, styles, -1);

        row++;
        section(sheet, row++, "5. 准入、准出与风险", 8, styles);
        String[] criteriaHeaders = {"类别", "条件", "本轮状态", "说明"};
        List<String[]> criteria = rows("""
                准入	.env 已配置必要密钥且四服务可启动	通过	未记录或泄露任何密钥值
                准入	基础 Seed 与测试账号可用	通过	admin/alice/bob 均可登录
                准出	后端自动化全部通过	通过	30/30，0 failures，0 errors
                准出	100 并发不超卖	通过	成功 80、库存不足 20、最终库存 0
                准出	五大模块主链路可浏览器演示	失败	结算页 reactive 未导入导致空白
                准出	阻断和高严重度缺陷清零	失败	存在结算阻断、跨会话加购和聊天隔离问题
                风险	真实 LLM 工具选择具有非确定性	存在	compare_products 首轮遗漏，复测通过
                风险	共享 MySQL 卷不是全新基线	存在	未执行破坏性重置；全新卷初始化待补测
                风险	前端和 Agent 缺少确定性自动化	存在	前端无 test 脚本，pytest 收集 0 项
                """, 4);
        writeTable(sheet, row, criteriaHeaders, criteria, styles, 2);
        finalizeSheet(sheet, 8, true, 3);
    }

    private static void buildCases(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("测试用例");
        beginSheet(sheet, "智能商城测试用例表", "结果来自 2026-07-12 自动化、REST、并发、浏览器和静态审计；失败项均关联缺陷", 14, styles);
        setWidths(sheet, 15, 16, 28, 9, 14, 24, 36, 24, 34, 34, 11, 12, 34, 13);
        String[] headers = {"用例ID", "模块", "测试点", "优先级", "类型", "前置条件", "操作步骤", "测试数据", "预期结果", "实际结果", "结果", "关联缺陷", "执行方式/证据", "执行日期"};
        List<String[]> cases = rows(TEST_CASES, headers.length);
        int end = writeTable(sheet, 3, headers, cases, styles, 10);
        addListValidation(sheet, 4, end - 1, 3, new String[]{"P0", "P1", "P2", "P3"});
        addListValidation(sheet, 4, end - 1, 10, new String[]{"通过", "失败", "阻塞", "未执行"});
        addResultConditionalFormatting(sheet, 4, end - 1, 10);
        sheet.setAutoFilter(new CellRangeAddress(3, end - 1, 0, headers.length - 1));
        sheet.createFreezePane(2, 4);
        finalizeSheet(sheet, 14, true, 4);
    }

    private static final String TEST_CASES = """
            TC-AUTH-001	用户模块	未登录访问个人资料	P0	安全/接口	服务健康	GET /api/user/profile，不携带 Token	无	返回 401	HTTP 401	通过		REST 实测	2026-07-12
            TC-AUTH-002	用户模块	正确账号登录	P0	功能/接口	Seed 已加载	POST /api/auth/login	alice / 123456	返回 accessToken、refreshToken 和用户信息	HTTP 200，Token 字段完整	通过		REST + 浏览器	2026-07-12
            TC-AUTH-003	用户模块	错误密码统一提示	P0	安全/接口	用户存在	使用错误密码登录	alice / wrong-password	返回 401 且不区分用户是否存在	HTTP 401，消息“用户名或密码错误”	通过		REST 实测	2026-07-12
            TC-AUTH-004	用户模块	Refresh Token 换取 Access Token	P0	接口	已登录	POST /api/auth/refresh	有效 refreshToken	返回新的 accessToken	HTTP 200	通过		REST 实测	2026-07-12
            TC-AUTH-005	用户模块	重复用户名注册	P1	边界/接口	alice 已存在	POST /api/auth/register	username=alice	返回 409	HTTP 409	通过		REST 实测	2026-07-12
            TC-AUTH-006	用户模块	短密码校验	P1	边界/接口	无	注册密码少于 6 位	password=12345	返回 400	HTTP 400	通过		REST 实测	2026-07-12
            TC-AUTH-007	用户模块	注册缺失 password	P0	异常/接口	无	注册请求不传 password	仅 username、phone	返回 400 参数错误	passwordEncoder.encode(null) 导致 HTTP 500	失败	D-003	REST + 源码审计	2026-07-12
            TC-AUTH-008	用户模块	普通用户创建商品	P0	权限/接口	alice 已登录	POST /api/products	合法商品请求体	返回 403	HTTP 403	通过		REST 实测	2026-07-12
            TC-AUTH-009	用户模块	管理员访问后台	P0	权限/浏览器	admin 已登录	登录后进入 /admin	admin / 123456	后台可访问，ADMIN 权限生效	后端权限与管理员登录通过	通过		REST + 源码审计	2026-07-12
            TC-AUTH-010	用户模块	匿名访问管理员商品列表	P0	安全/接口	未登录	GET /api/products/admin	无 Token	按项目要求返回 401	实际返回 403	失败	D-002	REST 实测	2026-07-12
            TC-AUTH-011	用户模块	密码 BCrypt 存储	P0	安全/单元	Seed/注册可用	检查密码编码与匹配	123456	哈希以 $2a$10$ 开头，错误密码不匹配	PasswordEncoderTest 通过，Seed 为 $2a$10$	通过		JUnit + SQL 审计	2026-07-12
            TC-PROD-001	商品与搜索	分类树查询	P1	功能/接口	服务健康	GET /api/categories	无	返回顶级分类及 children	HTTP 200，7 个顶级分类	通过		REST 实测	2026-07-12
            TC-PROD-002	商品与搜索	商品列表分页	P0	功能/接口	有商品数据	GET /api/products?page=1&size=3	page=1,size=3	返回 total 和 3 条 list	HTTP 200，total=65（执行时）	通过		REST 实测	2026-07-12
            TC-PROD-003	商品与搜索	中文模糊搜索	P0	功能/接口	Seed 已加载	搜索“键盘”	keyword=键盘	命中名称或描述含关键字商品	HTTP 200，total=5	通过		REST + 浏览器	2026-07-12
            TC-PROD-004	商品与搜索	页码超过最大页	P1	边界/接口	有商品数据	GET page=999	page=999	返回空列表，不报错	HTTP 200，list=[]	通过		REST 实测	2026-07-12
            TC-PROD-005	商品与搜索	查询不存在商品	P1	异常/接口	无	GET /api/products/999999999	不存在 ID	返回 404	HTTP 404	通过		REST 实测	2026-07-12
            TC-PROD-006	商品与搜索	销量优先展示	P2	功能/浏览器	首页可用	打开商品页观察默认排序	无	高销量商品优先	首页首屏显示高销量商品	通过		浏览器实测	2026-07-12
            TC-PROD-007	商品与搜索	搜索结果价格排序	P1	功能/前端	处于关键词搜索	切换价格升序/降序	keyword=键盘	请求携带 sort 且顺序变化	搜索请求未传 sort，控件不生效	失败	D-008	源码审计	2026-07-12
            TC-PROD-008	商品与搜索	搜索关键字前后空格	P2	边界/接口	有键盘商品	搜索“ 键盘 ”	keyword=%20键盘%20	服务端 trim 后仍命中 5 条	实际 total=0	失败	D-015	REST 实测	2026-07-12
            TC-PROD-009	商品与搜索	商品价格必须为正数	P0	校验/接口	admin 已登录	创建 price<=0 商品	price=0	返回 400	BusinessFlowHttpIntegrationTest 通过	通过		RestAssured	2026-07-12
            TC-PROD-010	商品与搜索	商品库存不得为负	P0	校验/接口	admin 已登录	创建 stock<0 商品	stock=-1	返回 400	BusinessFlowHttpIntegrationTest 通过	通过		RestAssured	2026-07-12
            TC-PROD-011	商品与搜索	本地图片上传与读取	P1	功能/接口	admin 已登录	上传 PNG 并读取返回 URL	cover.png	201 且 /uploads/** 可访问	自动化回归通过	通过		RestAssured	2026-07-12
            TC-PROD-012	商品与搜索	非法分页和路径参数	P1	异常/接口	服务健康	请求 page=abc 与 /products/not-a-number	非法字符串	返回 400	两种请求均返回 500	失败	D-003	REST 实测	2026-07-12
            TC-CART-001	购物车	未登录访问购物车	P0	安全/接口	未登录	GET /api/cart	无	返回 401	HTTP 401	通过		REST 实测	2026-07-12
            TC-CART-002	购物车	加入购物车	P0	功能/接口	测试用户已登录	POST /api/cart	productId=21,quantity=1	201，购物车新增商品	HTTP 201	通过		隔离业务链路	2026-07-12
            TC-CART-003	购物车	重复商品数量合并	P1	功能/集成	购物车已有同商品	再次加购同一商品	相同 productId	保持 user+product 唯一并更新数量	CartControllerIntegrationTest 通过	通过		JUnit 集成测试	2026-07-12
            TC-CART-004	购物车	数量为 0 或负数	P1	边界/接口	已登录	新增/更新非法数量	quantity=0/-1	返回 400	校验测试通过	通过		JUnit/REST	2026-07-12
            TC-CART-005	购物车	缺失 quantity	P0	异常/接口	已登录	POST /api/cart 不传 quantity	仅 productId	返回 400	空值拆箱导致 HTTP 500	失败	D-003	REST + 源码审计	2026-07-12
            TC-CART-006	购物车	数量超过库存	P0	业务/接口	库存有限	加购数量大于库存	quantity=stock+1	返回明确库存不足	CartControllerIntegrationTest 通过	通过		JUnit 集成测试	2026-07-12
            TC-CART-007	购物车	购物车合计实时计算	P1	功能/浏览器	alice 有 2 个购物车项	打开 /cart	129x2 + 399x1	显示 2 件、合计 ¥657	浏览器显示 ¥657.00	通过		浏览器实测	2026-07-12
            TC-CART-008	购物车	售罄商品禁止加购	P0	前端/浏览器	存在 stock=0 测试商品	查看推荐和商品卡	并发测试商品 stock=0	按钮禁用	浏览器按钮 disabled	通过		浏览器实测	2026-07-12
            TC-ORDER-001	订单模块	购物车创建订单	P0	主链路/接口	隔离用户购物车有商品	POST /api/orders	cartItemIds + 地址	201，PENDING_PAYMENT	订单 377 创建成功	通过		隔离 REST 链路	2026-07-12
            TC-ORDER-002	订单模块	空收货地址校验	P0	边界/接口	已登录	创建订单时地址为空	shippingAddress=""	返回 400	HTTP 400	通过		REST 实测	2026-07-12
            TC-ORDER-003	订单模块	浏览器进入结算页	P0	主链路/浏览器	alice 购物车有商品	/cart 点击“去结算”	cartItemIds=2,1	显示商品、优惠、地址和提交按钮	路由跳转后页面空白，reactive 未定义	失败	D-001	浏览器控制台 + CheckoutView.vue:118/136	2026-07-12
            TC-ORDER-004	订单模块	待付款直接确认收货	P0	状态机/接口	订单为 PENDING_PAYMENT	PUT /confirm	orderId=377	拒绝非法跳转，返回 400	HTTP 400	通过		隔离 REST 链路	2026-07-12
            TC-ORDER-005	订单模块	取消待付款订单	P0	状态机/接口	订单为 PENDING_PAYMENT	PUT /cancel	orderId=377	订单变为 CANCELLED	HTTP 200，状态 CANCELLED	通过		隔离 REST 链路	2026-07-12
            TC-ORDER-006	订单模块	取消订单回补库存	P0	事务/接口	订单已扣库存	取消后查询商品	productId=21	库存恢复至下单前	80 -> 扣减 -> 80	通过		REST + 数据校验	2026-07-12
            TC-ORDER-007	订单模块	下单后清理购物车	P1	事务/接口	从购物车下单	下单后查询购物车	测试购物车项	对应项被删除	隔离链路确认已清空	通过		REST 实测	2026-07-12
            TC-ORDER-008	订单模块	100 请求竞争 80 库存	P0	并发/数据库	真实 MySQL，隔离商品库存80	运行 concurrent_order_test.py	requests=100,stock=80	成功80、库存不足20、无异常	80/20/0，耗时约3084ms	通过		真实 MySQL 并发	2026-07-12
            TC-ORDER-009	订单模块	并发后库存/销量/version	P0	并发/数据库	并发用例完成	查询并发商品	productId=66	stock=0,sales=80,version=80	实际 0/80/80	通过		脚本断言 + 数据校验	2026-07-12
            TC-ORDER-010	订单模块	失败事务无脏数据	P0	事务/集成	库存不足或 CAS 冲突	触发失败并查询订单/明细/库存	库存不足场景	复合操作整体回滚	OrderServiceIntegrationTest 通过	通过		JUnit 集成测试	2026-07-12
            TC-ORDER-011	订单模块	合法状态流转	P0	状态机/集成	准备各状态订单	支付、发货、确认收货	PENDING->PAID->SHIPPED->COMPLETED	仅允许合法转换且无重复副作用	OrderServiceIntegrationTest 通过	通过		JUnit 集成测试	2026-07-12
            TC-ORDER-012	订单模块	直购缺失 quantity	P0	异常/接口	已登录	POST /api/orders/direct 不传 quantity	productId + 地址	返回 400	空值拆箱导致 HTTP 500	失败	D-003	REST + 源码审计	2026-07-12
            TC-ORDER-013	订单模块	下单显式 @Transactional 合规	P1	静态合规	阅读 OrderService	检查下单入口注解	AGENTS.md 5.2	下单方法存在 @Transactional	实际使用 TransactionTemplate REQUIRES_NEW，无注解	失败	D-010	源码审计	2026-07-12
            TC-ORDER-014	订单模块	订单明细快照	P0	数据/集成	创建订单	检查 order_item	商品名和价格	保存下单时快照，不受商品修改影响	实体、DDL 和集成测试符合	通过		代码/SQL 审计	2026-07-12
            TC-AI-001	AI助手	健康检查与工具清单	P0	服务/接口	Agent healthy	GET /health	无	200，llmEnabled=true，至少2工具	HTTP 200，8 个工具	通过		REST 实测	2026-07-12
            TC-AI-002	AI助手	未认证调用 Agent	P0	安全/接口	无 Token	POST /agent/chat	任意消息	返回 401	HTTP 401	通过		REST 实测	2026-07-12
            TC-AI-003	AI助手	真实商品搜索推荐	P0	工具调用	alice 已登录	询问500元内编程键盘	“有没有适合敲代码的键盘，预算500”	调用商品工具并返回真实名称/库存	返回 K87、静音键盘和真实库存	通过		Agent 烟测 + 浏览器	2026-07-12
            TC-AI-004	AI助手	查询真实订单状态	P0	工具调用	用户有订单	询问“我上次买的东西到哪了”	alice JWT	调用 query_order_status	烟测 toolsUsed 包含订单工具	通过		Agent 烟测	2026-07-12
            TC-AI-005	AI助手	长期偏好跨新会话	P0	记忆	用户表达机械键盘偏好	新 session 请求推荐	session A/B	新会话仍体现偏好	烟测通过，偏好 top3 可见	通过		Agent 烟测 + 浏览器	2026-07-12
            TC-AI-006	AI助手	非购物问题拒答	P1	安全/行为	Agent 可用	询问天气	“今天天气怎么样”	说明仅支持购物，不编造	烟测通过且未调用工具	通过		Agent 烟测	2026-07-12
            TC-AI-007	AI助手	加购前二次确认	P0	业务安全	用户请求加购	先提议、后发送“确认”	商品ID 1	确认前不写购物车，确认后调用 add_to_cart	烟测通过	通过		Agent 烟测	2026-07-12
            TC-AI-008	AI助手	待确认加购按 session 隔离	P0	会话隔离	同一用户两个 session	A 发起加购，B 仅发送“确认”	session A/B	B 不应继承 A 的待确认状态	B 实际调用 add_to_cart 并成功加购	失败	D-005	定向运行测试 + main.py 审计	2026-07-12
            TC-AI-009	AI助手	商品对比工具稳定选择	P1	非确定性	真实 LLM 已启用	执行完整 agent_smoke	对比商品ID1和2	toolsUsed 含 compare_products	首轮遗漏；同提示复测3/3和整套重跑通过	失败	D-014	真实模型烟测	2026-07-12
            TC-AI-010	AI助手	换账号后清理可见聊天	P0	隐私/前端	Alice 已有聊天	SPA 内退出后 Bob 登录	Alice/Bob	Bob 不可看到 Alice 消息	模块级 messages 未在 logout 清理	失败	D-006	源码审计	2026-07-12
            TC-AI-011	AI助手	Agent 401 自动刷新	P1	认证/前端	Access 过期、Refresh 有效	继续发起 Agent 请求	过期 accessToken	刷新 Token 后重试或跳登录	agentApi/fetch 未挂统一响应刷新	失败	D-007	源码审计	2026-07-12
            TC-AI-012	AI助手	预算筛选覆盖完整结果	P1	工具正确性	符合预算商品排在第6名后	调用 search_products(max_price)	max_price=500	搜索全集后按预算过滤	仅取销量前5再过滤，可能漏商品	失败	D-009	源码审计	2026-07-12
            TC-AI-013	AI助手	会话历史读取	P1	记忆/接口	已有对话	GET /agent/history	合法 sessionId	返回 user/assistant 历史	Agent 烟测通过	通过		Agent 烟测	2026-07-12
            TC-AI-014	AI助手	至少两个工具可调用	P0	框架合规	Agent 启动	检查工具注册与烟测 toolsUsed	无	至少 search_products/query_order_status	实际注册8个工具且多工具烟测通过	通过		健康接口 + 烟测	2026-07-12
            TC-DEP-001	部署与文档	Compose 配置解析	P0	部署	.env 已配置	运行 docker compose config --quiet	当前 .env	命令成功	退出码 0	通过		Compose 实测	2026-07-12
            TC-DEP-002	部署与文档	四服务健康状态	P0	部署	Compose 已启动	运行 docker compose ps	frontend/backend/agent/mysql	全部 Up (healthy)	四服务 healthy，FailingStreak=0	通过		Docker 实测	2026-07-12
            TC-DEP-003	部署与文档	关键服务日志	P1	可观测性	业务与 Agent 已调用	检查 backend/agent 日志	并发和烟测时段	有 INFO 业务日志且无 ERROR	订单成功/库存不足/工具调用可见，无 ERROR	通过		Docker logs	2026-07-12
            TC-DEP-004	部署与文档	后端自动化回归	P0	自动化	JDK/Maven 可用	mvn.cmd -q test	9 个测试套件	全部通过	30 tests，0 failures，0 errors	通过		Surefire 报告	2026-07-12
            TC-DEP-005	部署与文档	前端生产构建	P0	构建	Node 依赖已安装	npm run build	Vue3 项目	构建成功	2328 modules 构建成功；有 >500kB 警告	通过	D-018	Vite build	2026-07-12
            TC-DEP-006	部署与文档	Agent 完整烟测	P0	自动化	真实 LLM 与后端可用	运行 scripts/agent_smoke.py	完整脚本	所有场景通过	重跑完整脚本通过，约78秒	通过	D-014	Python 烟测	2026-07-12
            TC-DEP-007	部署与文档	全新数据卷初始化	P1	部署/数据库	允许新建空卷	从空卷 docker compose up -d --build	全新 mysql_data	自动执行 schema+seed 并健康	本轮未破坏共享卷，未执行	未执行		风险控制	2026-07-12
            TC-DEP-008	部署与文档	Seed 数据规模	P1	数据审计	读取 docs/seed.sql	核对账号、分类和商品	Seed 文件	至少3用户、3分类、20商品	实际3用户、20分类节点、60商品	通过		SQL 静态审计	2026-07-12
            TC-DEP-009	部署与文档	核心表引擎和字符集	P1	数据库合规	读取 schema.sql	核对 CREATE TABLE	24张表	InnoDB、utf8mb4、外键合理	Schema 符合	通过		SQL 静态审计	2026-07-12
            TC-DEP-010	部署与文档	ER 图覆盖全部表	P1	文档合规	读取 schema 与 ER	比较实体数量和字段	24表	ER 展示全部表和关系	ER 仅19实体，缺5表及若干字段	失败	D-011	文档审计	2026-07-12
            TC-DEP-011	部署与文档	数据库设计商品数量口径	P2	文档一致性	读取 seed 与 database-design	比较商品数量	Seed 60	文档与 Seed 一致	文档仍写45，实际60	失败	D-012	文档审计	2026-07-12
            TC-DEP-012	部署与文档	外部端口符合项目规格	P1	配置合规	读取 AGENTS 与 Compose	比较宿主映射	backend 8080、MySQL 3306	按规格映射或明确批准变更	实际 backend 8081、MySQL 3307	失败	D-013	配置审计	2026-07-12
            TC-DEP-013	部署与文档	前端自动化测试入口	P2	自动化	检查 package.json	运行/检查 test 脚本	无	存在基础组件/E2E 回归	无 test/lint 脚本，本轮未执行	未执行	D-016	测试缺口	2026-07-12
            TC-DEP-014	部署与文档	Agent 确定性单元测试	P1	自动化	检查 agent-service	pytest 收集	pytest	覆盖工具授权、会话隔离、偏好 upsert	collected 0 items	未执行	D-017	pytest 实测	2026-07-12
            TC-DEP-015	部署与文档	Swagger 页面可访问	P1	文档/接口	backend healthy	GET /swagger-ui/index.html	宿主 8081	返回 200 页面	Springdoc 已配置，运行服务可访问	通过		配置 + 服务检查	2026-07-12
            """;

    private static void buildEnvironment(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("环境与账号");
        beginSheet(sheet, "测试环境与账号", "所有密钥仅检查是否配置，不在工作簿中记录值；账号仅限本机演示数据库", 8, styles);
        setWidths(sheet, 20, 25, 34, 24, 24, 28, 26, 24);
        int row = 3;
        section(sheet, row++, "1. 服务与运行环境", 8, styles);
        String[] envHeaders = {"组件", "技术/版本", "宿主地址", "容器地址/端口", "状态", "验证方式"};
        List<String[]> env = rows("""
                操作系统	Windows / Asia-Shanghai	本机	—	可用	当前 Codex 工作区
                前端	Vue 3 + Nginx 1.27	http://127.0.0.1/	frontend:80	Healthy	浏览器首页与健康检查
                后端	Spring Boot 3.3.6 / Java 17	http://127.0.0.1:8081	backend:8080	Healthy	公开商品接口/Swagger
                Agent	FastAPI + LangChain / Python 3.11	http://127.0.0.1:8000	agent-service:8000	Healthy	GET /health
                MySQL	MySQL 8.0	127.0.0.1:3307	mysql:3306	Healthy	mysqladmin ping
                Docker	Engine 29.6.1 / Compose 5.2.0	本机 Docker Desktop	Compose network	Healthy	docker compose ps
                浏览器	Codex 应用内浏览器	http://127.0.0.1/	—	可用	DOM 快照与控制台日志
                构建	Maven 3.9.16 / JDK 17 / Node 20 image	本机/容器	—	可用	mvn test / npm run build
                """, 6);
        row = writeTable(sheet, row, envHeaders, env, styles, 4);

        row++;
        section(sheet, row++, "2. 环境变量（只记录用途和配置状态）", 8, styles);
        String[] variableHeaders = {"变量", "服务", "用途", "是否必填", "本轮状态", "安全说明"};
        List<String[]> variables = rows("""
                MYSQL_ROOT_PASSWORD	MySQL/Backend/Agent	数据库 root 密码	是	已配置	值不写入测试文档和 Git
                MYSQL_DATABASE	全部	数据库名	是	已配置	smart_mall
                JWT_SECRET	Backend	JWT 签名，至少32字符	是	已配置	值不披露
                INTERNAL_SERVICE_SECRET	Backend/Agent	服务间认证	是	已配置	值不披露
                LLM_API_KEY	Agent	真实模型调用	真实模型演示必填	已配置	值不披露
                LLM_BASE_URL	Agent	OpenAI 兼容端点	按供应商	已配置	值不披露
                LLM_MODEL	Agent	模型名称	否	已配置	健康接口显示 llmEnabled=true
                """, 6);
        row = writeTable(sheet, row, variableHeaders, variables, styles, 4);

        row++;
        section(sheet, row++, "3. 测试账号", 8, styles);
        String[] accountHeaders = {"账号", "密码", "角色", "手机", "用途", "数据来源", "注意事项"};
        List<String[]> accounts = rows("""
                admin	123456	ADMIN	13800000000	管理后台、商品管理、图片上传	Seed SQL	仅本机演示；禁止复用到生产
                alice	123456	USER	13800000001	购物车、订单、偏好、AI 对话	Seed SQL	包含预置购物车、订单和偏好
                bob	123456	USER	13800000002	用户隔离、换账号场景	Seed SQL	用于跨用户可见性检查
                smoke_rt_815a93bda8	脚本生成	USER	脚本随机	隔离订单链路	本轮运行态测试	订单377已取消，库存已回补
                race_1783841014-1-dcd4daa502	脚本生成	USER	脚本随机	100/80并发验收	并发脚本	商品66库存0，保留作证据
                """, 7);
        row = writeTable(sheet, row, accountHeaders, accounts, styles, -1);

        row++;
        section(sheet, row++, "4. 测试命令与入口", 8, styles);
        String[] commandHeaders = {"用途", "命令/入口", "本轮结果", "证据位置"};
        List<String[]> commands = rows("""
                后端自动化	cd backend; mvn.cmd -q test	30/30 通过	backend/target/surefire-reports
                前端构建	cd frontend; npm.cmd run build	构建通过，存在大 chunk 警告	frontend/dist
                Agent 烟测	python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081	首轮1项间歇失败，整套重跑通过	Agent/Backend logs
                并发测试	python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081 --rounds 1 --requests 100 --stock 80	80成功/20库存不足/0异常	数据库+脚本输出
                容器状态	docker compose ps	四服务 healthy	Docker Desktop
                前端入口	http://127.0.0.1/	可访问	Codex 应用内浏览器
                Swagger	http://127.0.0.1:8081/swagger-ui/index.html	可访问	Springdoc
                """, 4);
        writeTable(sheet, row, commandHeaders, commands, styles, 2);
        finalizeSheet(sheet, 8, true, 3);
    }

    private static void buildDefects(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("缺陷登记");
        beginSheet(sheet, "缺陷登记", "当前周期缺陷在前；历史已关闭项来自 docs/bug-list.md，用于展示缺陷生命周期", 14, styles);
        setWidths(sheet, 12, 14, 30, 10, 10, 12, 20, 40, 28, 34, 38, 34, 18, 25);
        String[] headers = {"缺陷ID", "模块", "标题", "严重度", "优先级", "状态", "发现方式", "复现步骤", "预期", "实际", "证据/位置", "修复建议", "关联用例", "复测结论"};
        List<String[]> defects = rows(DEFECTS, headers.length);
        int end = writeTable(sheet, 3, headers, defects, styles, 5);
        addListValidation(sheet, 4, end - 1, 3, new String[]{"阻断", "高", "中", "低"});
        addListValidation(sheet, 4, end - 1, 4, new String[]{"P0", "P1", "P2", "P3"});
        addListValidation(sheet, 4, end - 1, 5, new String[]{"新建", "处理中", "调查中", "待复测", "已关闭", "已接受"});
        addStatusConditionalFormatting(sheet, 4, end - 1, 5);
        sheet.setAutoFilter(new CellRangeAddress(3, end - 1, 0, headers.length - 1));
        sheet.createFreezePane(2, 4);
        finalizeSheet(sheet, 14, true, 4);
    }

    private static final String DEFECTS = """
            D-001	订单/前端	结算页运行时空白	阻断	P0	新建	浏览器	alice 购物车点击“去结算”	结算页展示商品、地址和提交按钮	页面空白，控制台 ReferenceError: reactive is not defined	frontend/src/views/CheckoutView.vue:118,136	从 vue 导入 reactive，补路由级组件测试	TC-ORDER-003	待修复后复测
            D-002	安全/商品	匿名管理员商品列表返回403而非401	高	P1	新建	REST	无 Token GET /api/products/admin	返回401	返回403	SecurityConfig.java:43；ProductController.java:95	从游客 products GET 白名单中排除 admin 路径	TC-AUTH-010	未修复
            D-003	接口校验	缺失或类型错误参数返回500	高	P1	新建	REST/源码	缺 password、缺 quantity、page=abc、非法路径ID	统一返回400	多处返回500	ApiSupport.java；Auth/Cart/Order 请求DTO	补 @NotNull/@NotBlank 和类型转换异常处理	TC-AUTH-007,TC-PROD-012,TC-CART-005,TC-ORDER-012	未修复
            D-004	服务间认证	内部接口缺认证头返回500	高	P1	新建	REST	不带 X-Internal-Service/Secret 请求内部订单接口	返回401/403	MissingRequestHeaderException 被映射为500	OrderController.java:164-189；ApiSupport.java:257	显式校验头或映射缺头异常	—	未修复
            D-005	AI助手	待确认加购状态未按 session 隔离	高	P1	新建	定向测试	同用户 A 发起加购，B 只发送“确认”	B 不继承 A 的待确认动作	B 调用 add_to_cart 并写入购物车	agent-service/app/main.py:912-929,1528-1532	状态键加入 sessionId，补跨会话测试	TC-AI-008	已复现，未修复
            D-006	AI前端	换账号后可能保留前一用户聊天	高	P1	新建	源码审计	Alice 对话后 SPA 登出，再登录 Bob	清空消息/session/loadedSessions	logout 未重置模块级会话状态	useAgentConversation.js:7-16,70-76；store.js:58-66	登出时统一 reset Agent 会话状态	TC-AI-010	待浏览器复测
            D-007	AI前端	Agent 请求 401 不触发统一刷新	中	P2	新建	源码审计	Access 过期、Refresh 有效时调用 Agent	刷新并重试或跳登录	agentApi 和原生 fetch 仅显示通用错误	frontend/src/api/http.js:20-67；useAgentConversation.js:121-169	复用主 API 单飞 refresh 逻辑	TC-AI-011	未修复
            D-008	搜索	搜索结果排序控件无效	中	P2	新建	源码审计	搜索键盘后切换价格升/降序	请求带 sort 且列表变化	搜索请求未传 sort，后端固定销量排序	ProductListView.vue:280-283,331	搜索接口支持 sort 并透传	TC-PROD-007	未修复
            D-009	AI工具	预算过滤可能漏掉第6名后的商品	中	P2	新建	源码审计	符合预算商品不在销量前5时搜索	返回所有候选中的预算内商品	先 size=5 后本地过滤	main.py:569-575	将 maxPrice 下推后端或扩大后再截断	TC-AI-012	未修复
            D-010	订单合规	下单入口没有显式 @Transactional	中	P2	新建	静态审计	检查 OrderService create/createDirect	符合 AGENTS.md 明确注解要求	使用 TransactionTemplate REQUIRES_NEW	OrderService.java:51-59,108-112	调整事务重试边界并保留显式注解	TC-ORDER-013	功能正确但合规未满足
            D-011	数据库文档	ER 图缺少5张表及部分字段	中	P2	新建	文档审计	比较 schema.sql 与 er-diagram.md	24表全部呈现	ER仅19实体，缺coupon等5表	ER diagram 与 schema.sql	补齐实体、外键、points/discount 字段	TC-DEP-010	未修复
            D-012	数据库文档	商品数量口径过期	低	P3	新建	文档审计	比较 seed.sql 与 database-design.md	商品数量一致	文档写45，Seed实际60	docs/database-design.md:489	更新数量并注明测试夹具会增加记录	TC-DEP-011	未修复
            D-013	部署合规	外部端口与项目规格不一致	中	P2	新建	配置审计	比较 AGENTS.md 与 Compose	backend8080、MySQL3306	实际8081和3307	docker-compose.yml:15,38	统一规格或在验收文档明确批准变更	TC-DEP-012	未修复
            D-014	AI助手	商品对比工具首轮选择间歇失败	中	P2	调查中	真实模型烟测	运行完整 agent_smoke compare 场景	toolsUsed 含 compare_products	首轮内容正确但工具名缺失；复测通过	Agent smoke 2026-07-12	增强路由约束与确定性测试，允许合理工具组合	TC-AI-009,TC-DEP-006	同提示3/3及整套重跑通过
            D-015	搜索	关键字未 trim	低	P3	新建	REST	搜索“ 键盘 ”	按“键盘”处理	返回0条	ProductController.java:109-118	查询前 trim 并补边界测试	TC-PROD-008	未修复
            D-016	前端测试	无前端自动化测试入口	中	P2	新建	配置审计	检查 package.json	至少登录刷新/结算/路由守卫测试	无 test/lint 脚本	frontend/package.json	引入 Vitest + Vue Test Utils，优先覆盖结算页	TC-DEP-013	未执行
            D-017	Agent测试	pytest 收集0项	中	P2	新建	pytest	python -m pytest agent-service	存在确定性工具/记忆/授权测试	collected 0 items	agent-service	用 MockTransport/Mock LLM 补测试	TC-DEP-014	未执行
            D-018	前端性能	生产包单 chunk 超过500kB	低	P3	已接受	构建	运行 npm run build	无明显大包告警	构建成功但报告 >500kB	Vite build 输出	按路由拆分 Admin/ECharts/AI 组件	TC-DEP-005	不阻断本次功能
            D-019	商品前端	详情请求失败可能留下空白页	低	P3	新建	源码审计	访问404商品或模拟网络失败	显示明确错误态	首屏请求缺 try/catch	ProductDetailView.vue:123-139	增加 loading/error/empty 状态	—	未复测
            B001	订单	并发库存验收回归	高	P0	已关闭	历史缺陷	运行100/80并发脚本	无超卖	三轮均80成功/20库存不足	docs/bug-list.md	已用 CAS + 重试验证	TC-ORDER-008	2026-07-11 已关闭
            B002	AI助手	无LLM密钥不能真实模型调用	中	P2	已接受	历史缺陷	清空 LLM_API_KEY 调 Agent	离线仍可演示	规则降级可用	docs/bug-list.md	保留离线降级并明确演示条件	—	已接受
            B003	AI助手	预算正则误把任意数字当上限	中	P2	已关闭	历史缺陷	询问“推荐3款键盘”	不按3元过滤	曾返回空	docs/bug-list.md	仅预算语境识别金额	—	已关闭
            B004	用户	资料部分更新覆盖为null	中	P1	已关闭	历史缺陷	只改头像不传phone	phone保持	曾被清空	docs/bug-list.md	非空字段才更新	—	已关闭
            B005	订单	取消订单库存回补并发安全	高	P0	已关闭	历史缺陷	并发取消/下单	库存一致	曾存在不一致风险	docs/bug-list.md	原子 restoreStock	TC-ORDER-006	已关闭
            B006	购物车	数量不校验库存	中	P1	已关闭	历史缺陷	反复加购超库存	及时拒绝	曾到下单才报错	docs/bug-list.md	add/update 均校验	TC-CART-006	已关闭
            B007	AI助手	LLM正常回复被离线逻辑覆盖	中	P2	已关闭	历史缺陷	模型合理拒答	保留模型回复	曾被替换	docs/bug-list.md	仅空回复或缺工具时回落	—	已关闭
            B008	购物车	加减数量错误无提示	低	P2	已关闭	历史缺陷	点击加号超过库存	友好提示	曾无提示	docs/bug-list.md	try/catch + toast	—	已关闭
            B009	商品	下架商品无法在后台重新上架	中	P1	已关闭	历史缺陷	下架后查看后台	仍可管理	曾消失	docs/bug-list.md	新增 admin 列表	—	已关闭
            B010	订单前端	订单“查看详情”无响应	中	P1	已关闭	历史缺陷	点击查看详情	展示详情	曾无反应	docs/bug-list.md	接入摘要/详情	—	已关闭
            B011	部署	后端镜像依赖预编译JAR	高	P1	已关闭	历史缺陷	clean checkout 构建	镜像内编译	曾 no source files	docs/bug-list.md	多阶段 Maven 构建	—	已关闭
            B012	部署	Docker 上下文包含宿主 node_modules	中	P2	已关闭	历史缺陷	构建前端镜像	不复制宿主依赖	曾覆盖容器依赖	docs/bug-list.md	增加 .dockerignore	—	已关闭
            B013	AI助手	本地 Agent 后端端口错误	中	P2	已关闭	历史缺陷	本地启动 Agent	调用8081	曾调用8080失败	docs/bug-list.md	启动脚本改8081	—	已关闭
            B014	前端	Vite dev代理端口错误	中	P2	已关闭	历史缺陷	npm run dev	/api代理8081	曾代理8080	docs/bug-list.md	vite.config 更新	—	已关闭
            B015	用户	禁用用户仍可刷新且非法Token可能500	高	P1	已关闭	历史缺陷	禁用后refresh/传非法Token	统一401	曾可刷新或500	docs/bug-list.md	检查状态并统一异常	—	已关闭
            B016	部署	存在弱密钥默认值	高	P1	已关闭	历史缺陷	不设环境变量启动	拒绝启动	曾有默认值	docs/bug-list.md	变量必填且JWT>=32字符	—	已关闭
            B017	商品	缺少本地图片上传	中	P1	已关闭	历史缺陷	管理员上传PNG	201并可读取	曾仅支持URL	docs/bug-list.md	新增上传接口与静态映射	TC-PROD-011	已关闭
            """;

    private static void buildTestData(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("测试数据");
        beginSheet(sheet, "测试数据", "基线来自 docs/seed.sql；运行脚本生成的数据与 Seed 分开记录，避免误认为干净基线", 10, styles);
        setWidths(sheet, 14, 18, 28, 22, 20, 20, 28, 28, 24, 24);
        int row = 3;
        section(sheet, row++, "1. Seed 账号", 10, styles);
        String[] userHeaders = {"用户ID", "用户名", "角色", "手机号", "初始积分", "密码哈希", "明文测试密码", "用途"};
        List<String[]> users = rows("""
                n:1	admin	ADMIN	13800000000	n:0	$2a$10$...	123456	管理权限
                n:2	alice	USER	13800000001	n:300	$2a$10$...	123456	购物/订单/偏好
                n:3	bob	USER	13800000002	n:120	$2a$10$...	123456	隔离测试
                """, 8);
        row = writeTable(sheet, row, userHeaders, users, styles, -1);

        row++;
        section(sheet, row++, "2. 分类基线（20个分类节点）", 10, styles);
        String[] categoryHeaders = {"ID", "分类名称", "父级ID", "层级", "排序"};
        List<String[]> categories = rows("""
                n:1	数码电子		一级	n:1
                n:2	电脑办公		一级	n:2
                n:3	家居生活		一级	n:3
                n:4	键盘鼠标	n:2	二级	n:1
                n:5	手机配件	n:1	二级	n:2
                n:6	智能家居	n:3	二级	n:1
                n:7	服饰鞋包		一级	n:4
                n:8	美妆个护		一级	n:5
                n:9	运动户外		一级	n:6
                n:10	图书文创		一级	n:7
                n:11	厨房餐具	n:3	二级	n:2
                n:12	母婴用品	n:3	二级	n:3
                n:13	男装女装	n:7	二级	n:1
                n:14	鞋靴箱包	n:7	二级	n:2
                n:15	护肤彩妆	n:8	二级	n:1
                n:16	个人护理	n:8	二级	n:2
                n:17	健身装备	n:9	二级	n:1
                n:18	户外露营	n:9	二级	n:2
                n:19	技术图书	n:10	二级	n:1
                n:20	文具手账	n:10	二级	n:2
                """, 5);
        row = writeTable(sheet, row, categoryHeaders, categories, styles, -1);

        row++;
        section(sheet, row++, "3. 代表性商品基线", 10, styles);
        String[] productHeaders = {"商品ID", "分类ID", "商品名称", "价格", "库存", "销量", "状态", "版本", "测试用途"};
        List<String[]> products = rows("""
                n:1	n:4	极客机械键盘 K87	n:399	n:80	n:320	上架	n:0	搜索/AI/订单快照
                n:2	n:4	静音办公机械键盘	n:459	n:45	n:210	上架	n:0	AI 对比/预算
                n:3	n:4	电竞鼠标 M9	n:199	n:120	n:500	上架	n:0	销量排序
                n:4	n:2	27 英寸 4K 显示器	n:1699	n:35	n:96	上架	n:0	高价边界
                n:5	n:2	轻薄办公笔记本	n:5299	n:25	n:88	上架	n:0	高价边界
                n:6	n:1	降噪蓝牙耳机	n:699	n:60	n:260	上架	n:0	通用搜索
                n:7	n:1	运动智能手表	n:899	n:50	n:150	上架	n:0	分类过滤
                n:8	n:5	65W 氮化镓充电器	n:129	n:150	n:410	上架	n:0	购物车预置
                n:9	n:5	编织数据线套装	n:39	n:300	n:800	上架	n:0	首页热销
                n:10	n:6	智能台灯 Pro	n:259	n:70	n:170	上架	n:0	描述搜索
                n:11	n:6	智能插座 Mini	n:59	n:180	n:390	上架	n:0	分页/排序
                n:12	n:3	人体工学椅	n:799	n:32	n:90	上架	n:0	库存边界
                n:13	n:3	升降电脑桌	n:1299	n:20	n:74	上架	n:0	低库存
                n:14	n:4	热插拔客制化键盘	n:599	n:40	n:230	上架	n:0	长期偏好
                n:15	n:4	入门薄膜键盘	n:69	n:200	n:620	上架	n:0	预算敏感
                n:16	n:1	便携蓝牙音箱	n:239	n:85	n:160	上架	n:0	通用商品
                n:17	n:2	USB-C 扩展坞	n:189	n:95	n:240	上架	n:0	分类过滤
                n:18	n:3	桌面收纳架	n:119	n:110	n:140	上架	n:0	描述命中“键盘”
                n:19	n:6	扫地机器人 Lite	n:1099	n:18	n:65	上架	n:0	低库存
                n:20	n:5	磁吸充电宝	n:169	n:130	n:350	上架	n:0	分页/排序
                n:21	n:2	抢购测试商品	n:9.9	n:80	n:0	上架	n:0	100/80并发基线
                """, 9);
        row = writeTable(sheet, row, productHeaders, products, styles, -1);

        row++;
        section(sheet, row++, "4. 边界与异常数据", 10, styles);
        String[] boundaryHeaders = {"数据集ID", "对象", "输入", "边界/等价类", "期望", "关联用例"};
        List<String[]> boundaries = rows("""
                TD-B001	注册密码	12345	长度<6	400	TC-AUTH-006
                TD-B002	注册密码	缺失字段	Null	400（当前500）	TC-AUTH-007
                TD-B003	登录密码	wrong-password	错误凭证	401统一文案	TC-AUTH-003
                TD-B004	页码	999	超过最大页	200空列表	TC-PROD-004
                TD-B005	页码	abc	非法类型	400（当前500）	TC-PROD-012
                TD-B006	搜索词	键盘	正常中文	命中5条	TC-PROD-003
                TD-B007	搜索词	“ 键盘 ”	前后空格	trim后命中（当前0）	TC-PROD-008
                TD-B008	商品价格	0	非正数	400	TC-PROD-009
                TD-B009	商品库存	-1	负数	400	TC-PROD-010
                TD-B010	购物数量	0/-1	非法数量	400	TC-CART-004
                TD-B011	购物数量	缺失	Null	400（当前500）	TC-CART-005
                TD-B012	并发	100请求/80库存	竞争边界	80成功、20不足	TC-ORDER-008
                TD-B013	Agent预算	500	预算语义	仅推荐<=500	TC-AI-003
                TD-B014	Agent问题	今天天气怎么样	非购物域	礼貌拒答	TC-AI-006
                """, 6);
        row = writeTable(sheet, row, boundaryHeaders, boundaries, styles, -1);

        row++;
        section(sheet, row++, "5. 本轮运行夹具与结果", 10, styles);
        String[] fixtureHeaders = {"类型", "标识", "用户/商品/订单", "关键输入", "最终状态", "用途/保留策略"};
        List<String[]> fixtures = rows("""
                隔离订单	运行态 smoke	userId=9 / productId=21 / orderId=377	数量1、地址测试	订单 CANCELLED，库存80	验证创建、非法确认、取消和回补；保留证据
                并发轮次	1783841014-1-dcd4daa502	userId=10 / productId=66 / 80订单	100请求、库存80	stock=0,sales=80,version=80	验证无超卖；脚本策略保留
                Agent偏好	alice	机械键盘、预算敏感、办公学习	跨新session	weight=2.0	验证长期记忆
                Agent对比	商品1与2	K87 / 静音办公键盘	真实模型	首轮工具名遗漏，复测通过	记录非确定性风险
                """, 6);
        writeTable(sheet, row, fixtureHeaders, fixtures, styles, -1);
        finalizeSheet(sheet, 10, true, 3);
    }

    private static void buildReport(XSSFWorkbook workbook, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("测试报告");
        beginSheet(sheet, "智能商城测试报告", "测试日期 2026-07-12；统计由“测试用例”和“缺陷登记”公式联动", 8, styles);
        setWidths(sheet, 20, 18, 20, 18, 22, 18, 24, 28);

        Row metrics1 = sheet.createRow(3);
        metric(metrics1, 0, "用例总数", "COUNTA('测试用例'!$A$5:$A$200)", styles);
        metric(metrics1, 2, "通过", "COUNTIF('测试用例'!$K$5:$K$200,\"通过\")", styles);
        metric(metrics1, 4, "失败", "COUNTIF('测试用例'!$K$5:$K$200,\"失败\")", styles);
        metric(metrics1, 6, "未执行", "COUNTIF('测试用例'!$K$5:$K$200,\"未执行\")", styles);
        metrics1.setHeightInPoints(34);

        Row metrics2 = sheet.createRow(4);
        metric(metrics2, 0, "严格通过率", "IF((B4-H4)=0,0,D4/(B4-H4))", styles);
        metrics2.getCell(1).setCellStyle(styles.metricPercent);
        metric(metrics2, 2, "阻断未关闭", "COUNTIFS('缺陷登记'!$D$5:$D$80,\"阻断\",'缺陷登记'!$F$5:$F$80,\"<>已关闭\")", styles);
        metric(metrics2, 4, "高严重度未关闭", "COUNTIFS('缺陷登记'!$D$5:$D$80,\"高\",'缺陷登记'!$F$5:$F$80,\"<>已关闭\")", styles);
        writeCell(metrics2, 6, "准出结论", styles.metricLabel);
        writeCell(metrics2, 7, "不通过", styles.fail);
        metrics2.setHeightInPoints(34);

        int row = 6;
        section(sheet, row++, "1. 模块执行概览", 8, styles);
        String[] moduleHeaders = {"模块", "用例数", "通过", "失败", "阻塞", "未执行", "通过率", "说明"};
        Row header = sheet.createRow(row++);
        for (int i = 0; i < moduleHeaders.length; i++) writeCell(header, i, moduleHeaders[i], styles.tableHeader);
        String[][] modules = {
                {"用户模块", "注册、JWT、权限"},
                {"商品与搜索", "分页、搜索、校验"},
                {"购物车", "数量、库存、前端合计"},
                {"订单模块", "事务、状态机、100/80并发"},
                {"AI助手", "工具、订单、记忆、隔离"},
                {"部署与文档", "Compose、构建、Schema、文档"}
        };
        for (String[] module : modules) {
            Row r = sheet.createRow(row);
            writeCell(r, 0, module[0], row % 2 == 0 ? styles.bodyAlt : styles.body);
            setFormula(r, 1, "COUNTIF('测试用例'!$B$5:$B$200,A" + (row + 1) + ")", styles.number);
            setFormula(r, 2, "COUNTIFS('测试用例'!$B$5:$B$200,A" + (row + 1) + ",'测试用例'!$K$5:$K$200,\"通过\")", styles.number);
            setFormula(r, 3, "COUNTIFS('测试用例'!$B$5:$B$200,A" + (row + 1) + ",'测试用例'!$K$5:$K$200,\"失败\")", styles.number);
            setFormula(r, 4, "COUNTIFS('测试用例'!$B$5:$B$200,A" + (row + 1) + ",'测试用例'!$K$5:$K$200,\"阻塞\")", styles.number);
            setFormula(r, 5, "COUNTIFS('测试用例'!$B$5:$B$200,A" + (row + 1) + ",'测试用例'!$K$5:$K$200,\"未执行\")", styles.number);
            setFormula(r, 6, "IF((B" + (row + 1) + "-F" + (row + 1) + ")=0,0,C" + (row + 1) + "/(B" + (row + 1) + "-F" + (row + 1) + "))", styles.percent);
            writeCell(r, 7, module[1], row % 2 == 0 ? styles.bodyAlt : styles.body);
            r.setHeightInPoints(30);
            row++;
        }

        row++;
        section(sheet, row++, "2. 关键执行证据", 8, styles);
        String[] evidenceHeaders = {"检查项", "结果", "实际证据", "判定"};
        List<String[]> evidence = rows("""
                后端自动化	30 tests / 0 failures / 0 errors	9个 Surefire 套件全部通过	通过
                前端生产构建	2328 modules	构建成功；存在 >500kB chunk 警告	通过（有风险）
                Docker Compose	四服务 healthy	frontend80、backend8081、agent8000、mysql3307	通过
                并发库存	100请求 / 80库存	成功80、库存不足20、unexpected0、stock0、sales80、version80	通过
                Agent 完整烟测	整套重跑通过	商品、方案、详情、对比、订单、记忆、加购、拒答	通过（首轮不稳定）
                浏览器主链路	登录/搜索/购物车/订单/AI可用	结算页因 reactive 未导入空白	失败
                数据库/文档	Schema 24表、Seed 60商品	ER少5表，数据库设计数量过期	失败
                """, 4);
        row = writeTable(sheet, row, evidenceHeaders, evidence, styles, 3);

        row++;
        section(sheet, row++, "3. 主要缺陷与风险", 8, styles);
        String[] riskHeaders = {"级别", "问题", "影响", "当前处置", "发布建议"};
        List<String[]> risks = rows("""
                阻断	结算页 reactive 未导入	购物车结算和立即购买主链路不可用	D-001 新建	必须修复并浏览器复测
                高	Agent 待确认加购跨 session	可能在错误会话执行写操作	D-005 新建	必须隔离并补确定性测试
                高	换账号聊天状态未清理	存在跨用户可见性/隐私风险	D-006 新建	必须在 logout reset
                高	缺失/非法参数返回500	暴露服务内部错误且不符合接口规范	D-003 新建	统一校验和400映射
                中	真实模型工具选择非确定性	答辩脚本可能偶发失败	D-014 调查中	约束路由并保留重试/证据
                中	ER和端口与规格不一致	静态验收或答辩可能扣分	D-011/D-013	修正文档或统一配置
                风险	未从全新 MySQL 卷重建	一键启动的干净环境复现尚未验证	用例未执行	答辩前使用隔离卷补测
                """, 5);
        row = writeTable(sheet, row, riskHeaders, risks, styles, -1);

        row++;
        section(sheet, row++, "4. 结论与复测门槛", 8, styles);
        List<String[]> conclusions = rows("""
                当前结论	不通过：核心后端、并发与AI能力基本可用，但结算主链路存在阻断缺陷，且有两项高严重度会话隔离问题。
                修复优先级	P0：D-001；P1：D-002/D-003/D-004/D-005/D-006；其余按答辩风险排序。
                必须复测	购物车结算、立即购买、创建并支付订单；Alice/Bob 聊天隔离；跨 session 加购确认；异常参数400。
                回归要求	后端30测试保持全绿；100/80并发再次得到80/20；Agent完整烟测连续通过；四服务保持healthy。
                准出条件	阻断和高严重度缺陷全部关闭；五大模块浏览器主链路可演示；全新数据卷启动验证通过。
                """, 2);
        for (String[] item : conclusions) {
            Row r = sheet.createRow(row++);
            writeCell(r, 0, item[0], styles.infoLabel);
            mergeAndWrite(sheet, r.getRowNum(), 1, 7, item[1], styles.note);
            r.setHeightInPoints(38);
        }
        finalizeSheet(sheet, 8, false, 3);
    }

    private static void beginSheet(XSSFSheet sheet, String title, String subtitle, int columns, Styles styles) {
        sheet.setDisplayGridlines(false);
        sheet.setZoom(90);
        sheet.setTabColor(color(TEAL));
        mergeAndWrite(sheet, 0, 0, columns - 1, title, styles.title);
        sheet.getRow(0).setHeightInPoints(34);
        mergeAndWrite(sheet, 1, 0, columns - 1, subtitle, styles.subtitle);
        sheet.getRow(1).setHeightInPoints(30);
    }

    private static void section(XSSFSheet sheet, int rowIndex, String title, int columns, Styles styles) {
        mergeAndWrite(sheet, rowIndex, 0, columns - 1, title, styles.section);
        sheet.getRow(rowIndex).setHeightInPoints(27);
    }

    private static int writeTable(XSSFSheet sheet, int headerRow, String[] headers, List<String[]> data, Styles styles, int statusColumn) {
        Row header = sheet.createRow(headerRow);
        header.setHeightInPoints(32);
        for (int col = 0; col < headers.length; col++) {
            writeCell(header, col, headers[col], styles.tableHeader);
        }
        int rowIndex = headerRow + 1;
        for (String[] values : data) {
            Row row = sheet.createRow(rowIndex);
            row.setHeightInPoints(rowHeight(values));
            for (int col = 0; col < headers.length; col++) {
                String value = col < values.length ? values[col] : "";
                CellStyle base = rowIndex % 2 == 0 ? styles.bodyAlt : styles.body;
                Cell cell = row.createCell(col);
                setTypedValue(cell, value);
                cell.setCellStyle(styleForHeader(headers[col], value, base, styles));
                if (col == statusColumn) {
                    cell.setCellStyle(statusStyle(value, styles));
                }
            }
            rowIndex++;
        }
        return rowIndex;
    }

    private static float rowHeight(String[] values) {
        int longest = 0;
        for (String value : values) longest = Math.max(longest, value == null ? 0 : value.length());
        if (longest > 150) return 84;
        if (longest > 95) return 72;
        if (longest > 55) return 60;
        if (longest > 30) return 48;
        return 38;
    }

    private static CellStyle styleForHeader(String header, String value, CellStyle base, Styles styles) {
        if (value != null && value.startsWith("d:")) return styles.date;
        if (value != null && value.startsWith("n:")) {
            if (header.contains("价格") || header.contains("金额") || header.contains("预算")) return styles.currency;
            return styles.number;
        }
        return base;
    }

    private static CellStyle statusStyle(String value, Styles styles) {
        if (value == null) return styles.body;
        return switch (value) {
            case "通过", "已关闭", "Healthy", "已配置" -> styles.pass;
            case "失败", "不通过", "阻断" -> styles.fail;
            case "阻塞", "调查中", "处理中", "待复测", "存在", "通过（有风险）", "通过（首轮不稳定）" -> styles.warn;
            case "未执行", "已接受" -> styles.neutral;
            default -> styles.body;
        };
    }

    private static void metric(Row row, int startCol, String label, String formula, Styles styles) {
        writeCell(row, startCol, label, styles.metricLabel);
        setFormula(row, startCol + 1, formula, styles.metricValue);
    }

    private static void mergeAndWrite(XSSFSheet sheet, int row, int startCol, int endCol, String value, CellStyle style) {
        Row targetRow = sheet.getRow(row);
        if (targetRow == null) targetRow = sheet.createRow(row);
        for (int col = startCol; col <= endCol; col++) {
            Cell cell = targetRow.getCell(col);
            if (cell == null) cell = targetRow.createCell(col);
            cell.setCellStyle(style);
        }
        targetRow.getCell(startCol).setCellValue(value);
        if (endCol > startCol) sheet.addMergedRegion(new CellRangeAddress(row, row, startCol, endCol));
    }

    private static void writeCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        setTypedValue(cell, value);
        cell.setCellStyle(style);
    }

    private static void setFormula(Row row, int col, String formula, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private static void setTypedValue(Cell cell, String value) {
        if (value == null || value.isEmpty()) {
            cell.setBlank();
        } else if (value.startsWith("n:")) {
            cell.setCellValue(Double.parseDouble(value.substring(2)));
        } else if (value.startsWith("d:")) {
            LocalDate date = LocalDate.parse(value.substring(2));
            Date javaDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            cell.setCellValue(javaDate);
        } else if (value.startsWith("f:")) {
            cell.setCellFormula(value.substring(2));
        } else {
            cell.setCellValue(value);
        }
    }

    private static void addListValidation(XSSFSheet sheet, int firstRow, int lastRow, int column, String[] values) {
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        XSSFDataValidationConstraint constraint = (XSSFDataValidationConstraint) helper.createExplicitListConstraint(values);
        XSSFDataValidation validation = (XSSFDataValidation) helper.createValidation(
                constraint, new CellRangeAddressList(firstRow, lastRow, column, column));
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        validation.createErrorBox("输入无效", "请从下拉列表中选择有效值。");
        sheet.addValidationData(validation);
    }

    private static void addResultConditionalFormatting(XSSFSheet sheet, int firstRow, int lastRow, int column) {
        addTextRule(sheet, firstRow, lastRow, column, "通过", PASS_BG, PASS_FG);
        addTextRule(sheet, firstRow, lastRow, column, "失败", FAIL_BG, FAIL_FG);
        addTextRule(sheet, firstRow, lastRow, column, "阻塞", WARN_BG, WARN_FG);
        addTextRule(sheet, firstRow, lastRow, column, "未执行", NEUTRAL_BG, NEUTRAL_FG);
    }

    private static void addStatusConditionalFormatting(XSSFSheet sheet, int firstRow, int lastRow, int column) {
        addTextRule(sheet, firstRow, lastRow, column, "已关闭", PASS_BG, PASS_FG);
        addTextRule(sheet, firstRow, lastRow, column, "新建", FAIL_BG, FAIL_FG);
        addTextRule(sheet, firstRow, lastRow, column, "处理中", WARN_BG, WARN_FG);
        addTextRule(sheet, firstRow, lastRow, column, "调查中", WARN_BG, WARN_FG);
        addTextRule(sheet, firstRow, lastRow, column, "已接受", NEUTRAL_BG, NEUTRAL_FG);
    }

    private static void addTextRule(XSSFSheet sheet, int firstRow, int lastRow, int column, String text, String bg, String fg) {
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        String col = columnName(column);
        ConditionalFormattingRule rule = formatting.createConditionalFormattingRule(
                "$" + col + (firstRow + 1) + "=\"" + text + "\"");
        PatternFormatting pattern = rule.createPatternFormatting();
        pattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        pattern.setFillForegroundColor(color(bg).getIndexed());
        FontFormatting font = rule.createFontFormatting();
        font.setFontColorIndex(color(fg).getIndexed());
        formatting.addConditionalFormatting(new CellRangeAddress[]{new CellRangeAddress(firstRow, lastRow, column, column)}, rule);
    }

    private static String columnName(int zeroBased) {
        StringBuilder result = new StringBuilder();
        int value = zeroBased + 1;
        while (value > 0) {
            int rem = (value - 1) % 26;
            result.insert(0, (char) ('A' + rem));
            value = (value - 1) / 26;
        }
        return result.toString();
    }

    private static void setWidths(XSSFSheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
        }
    }

    private static void finalizeSheet(XSSFSheet sheet, int columns, boolean landscape, int freezeRows) {
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        PrintSetup setup = sheet.getPrintSetup();
        setup.setLandscape(landscape);
        setup.setFitWidth((short) 1);
        setup.setFitHeight((short) 0);
        sheet.getHeader().setCenter("&B智能商城软件测试文档");
        sheet.getFooter().setLeft("生成日期：" + TEST_DATE);
        sheet.getFooter().setRight("第 &P 页 / 共 &N 页");
        if (freezeRows > 0 && sheet.getPaneInformation() == null) sheet.createFreezePane(0, freezeRows);
        sheet.setRepeatingRows(new CellRangeAddress(0, Math.min(2, freezeRows - 1), -1, -1));
        sheet.setMargin(XSSFSheet.LeftMargin, 0.3);
        sheet.setMargin(XSSFSheet.RightMargin, 0.3);
        sheet.setMargin(XSSFSheet.TopMargin, 0.5);
        sheet.setMargin(XSSFSheet.BottomMargin, 0.5);
    }

    private static List<String[]> rows(String block, int expectedColumns) {
        List<String[]> result = new ArrayList<>();
        for (String line : block.strip().split("\\R")) {
            if (line.isBlank()) continue;
            String[] columns = line.strip().split("\\t", -1);
            if (columns.length != expectedColumns) {
                throw new IllegalArgumentException("Expected " + expectedColumns + " columns but got " + columns.length + ": " + line);
            }
            result.add(columns);
        }
        return result;
    }

    private static void verifyWorkbook(XSSFWorkbook workbook, FormulaEvaluator evaluator) {
        List<String> required = List.of("测试计划", "测试用例", "环境与账号", "缺陷登记", "测试数据", "测试报告");
        if (workbook.getNumberOfSheets() != required.size()) {
            throw new IllegalStateException("Expected 6 sheets, got " + workbook.getNumberOfSheets());
        }
        for (int i = 0; i < required.size(); i++) {
            if (!required.get(i).equals(workbook.getSheetName(i))) {
                throw new IllegalStateException("Unexpected sheet order at " + i + ": " + workbook.getSheetName(i));
            }
        }

        List<String> formulaErrors = new ArrayList<>();
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            XSSFSheet sheet = workbook.getSheetAt(s);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalStateException("Blank sheet: " + sheet.getSheetName());
            }
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        try {
                            var value = evaluator.evaluate(cell);
                            if (value != null && value.getCellType() == CellType.ERROR) {
                                formulaErrors.add(sheet.getSheetName() + "!" + cell.getAddress() + "=" + FormulaError.forInt(value.getErrorValue()).getString());
                            }
                        } catch (RuntimeException ex) {
                            formulaErrors.add(sheet.getSheetName() + "!" + cell.getAddress() + "=" + ex.getMessage());
                        }
                    }
                }
            }
        }
        if (!formulaErrors.isEmpty()) {
            throw new IllegalStateException("Formula errors: " + formulaErrors);
        }

        int caseCount = workbook.getSheet("测试用例").getLastRowNum() - 3;
        if (caseCount < 60) throw new IllegalStateException("Too few test cases: " + caseCount);
        int defectCount = workbook.getSheet("缺陷登记").getLastRowNum() - 3;
        if (defectCount < 25) throw new IllegalStateException("Too few defects/history entries: " + defectCount);
    }

    private static void renderWorkbook(XSSFWorkbook workbook, FormulaEvaluator evaluator) throws IOException {
        DataFormatter formatter = new DataFormatter();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            XSSFSheet sheet = workbook.getSheetAt(index);
            BufferedImage image = renderSheet(sheet, formatter, evaluator);
            String fileName = String.format("%02d-%s.png", index + 1, sheet.getSheetName());
            ImageIO.write(image, "png", PREVIEW_DIR.resolve(fileName).toFile());
        }
    }

    private static BufferedImage renderSheet(XSSFSheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastRow = sheet.getLastRowNum();
        int lastCol = 0;
        for (Row row : sheet) {
            lastCol = Math.max(lastCol, Math.max(0, row.getLastCellNum() - 1));
        }
        int[] widths = new int[lastCol + 1];
        int totalWidth = 0;
        for (int col = 0; col <= lastCol; col++) {
            widths[col] = Math.max(70, Math.min(260, (int) Math.round(sheet.getColumnWidth(col) / 256.0 * 7.2)));
            totalWidth += widths[col];
        }
        int[] heights = new int[lastRow + 1];
        int totalHeight = 0;
        for (int row = 0; row <= lastRow; row++) {
            Row poiRow = sheet.getRow(row);
            float points = poiRow == null ? sheet.getDefaultRowHeightInPoints() : poiRow.getHeightInPoints();
            heights[row] = Math.max(22, Math.min(90, Math.round(points * 1.45f)));
            totalHeight += heights[row];
        }

        BufferedImage image = new BufferedImage(Math.max(900, totalWidth + 2), Math.max(300, totalHeight + 2), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        int y = 0;
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            int x = 0;
            for (int col = 0; col <= lastCol; col++) {
                CellRangeAddress merged = mergedRegion(sheet, rowIndex, col);
                if (merged != null && (merged.getFirstRow() != rowIndex || merged.getFirstColumn() != col)) {
                    x += widths[col];
                    continue;
                }
                int cellWidth = widths[col];
                int cellHeight = heights[rowIndex];
                if (merged != null) {
                    cellWidth = 0;
                    for (int c = merged.getFirstColumn(); c <= merged.getLastColumn(); c++) cellWidth += widths[c];
                    cellHeight = 0;
                    for (int r = merged.getFirstRow(); r <= merged.getLastRow(); r++) cellHeight += heights[r];
                }
                Cell cell = row == null ? null : row.getCell(col);
                CellStyle style = cell == null ? null : cell.getCellStyle();
                graphics.setColor(fillColor(style));
                graphics.fillRect(x, y, cellWidth, cellHeight);
                graphics.setColor(hex(BORDER));
                graphics.drawLine(x, y + cellHeight - 1, x + cellWidth, y + cellHeight - 1);

                if (cell != null) {
                    String text = formatter.formatCellValue(cell, evaluator);
                    drawText(graphics, text, x + 6, y + 4, cellWidth - 12, cellHeight - 8, style, sheet.getWorkbook());
                }
                x += widths[col];
            }
            y += heights[rowIndex];
        }
        graphics.dispose();
        return image;
    }

    private static CellRangeAddress mergedRegion(XSSFSheet sheet, int row, int col) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(row, col)) return region;
        }
        return null;
    }

    private static void drawText(Graphics2D graphics, String text, int x, int y, int width, int height, CellStyle style, XSSFWorkbook workbook) {
        if (text == null || text.isEmpty() || width <= 4 || height <= 4) return;
        Font poiFont = workbook.getFontAt(style == null ? 0 : style.getFontIndexAsInt());
        int fontSize = Math.max(9, Math.min(18, poiFont.getFontHeightInPoints()));
        int fontStyle = poiFont.getBold() ? java.awt.Font.BOLD : java.awt.Font.PLAIN;
        java.awt.Font awtFont = new java.awt.Font("Microsoft YaHei", fontStyle, fontSize);
        graphics.setFont(awtFont);
        graphics.setColor(fontColor(poiFont));
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = wrap(text, metrics, width);
        int maxLines = Math.max(1, height / Math.max(1, metrics.getHeight()));
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(lines.size() - 1);
            while (last.length() > 1 && metrics.stringWidth(last + "…") > width) last = last.substring(0, last.length() - 1);
            lines.set(lines.size() - 1, last + "…");
        }
        int lineY = y + metrics.getAscent();
        HorizontalAlignment alignment = style == null ? HorizontalAlignment.LEFT : style.getAlignment();
        for (String line : lines) {
            int lineX = x;
            if (alignment == HorizontalAlignment.CENTER) lineX = x + Math.max(0, (width - metrics.stringWidth(line)) / 2);
            if (alignment == HorizontalAlignment.RIGHT) lineX = x + Math.max(0, width - metrics.stringWidth(line));
            graphics.drawString(line, lineX, lineY);
            lineY += metrics.getHeight();
        }
    }

    private static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char ch = paragraph.charAt(i);
                if (metrics.stringWidth(current.toString() + ch) > width && !current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(ch);
            }
            if (!current.isEmpty()) lines.add(current.toString());
        }
        return lines;
    }

    private static Color fillColor(CellStyle style) {
        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor fill = xssfStyle.getFillForegroundXSSFColor();
            if (fill != null && fill.getRGB() != null && xssfStyle.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
                byte[] rgb = fill.getRGB();
                return new Color(Byte.toUnsignedInt(rgb[0]), Byte.toUnsignedInt(rgb[1]), Byte.toUnsignedInt(rgb[2]));
            }
        }
        return Color.WHITE;
    }

    private static Color fontColor(Font font) {
        if (font instanceof XSSFFont xssfFont) {
            XSSFColor color = xssfFont.getXSSFColor();
            if (color != null && color.getRGB() != null) {
                byte[] rgb = color.getRGB();
                return new Color(Byte.toUnsignedInt(rgb[0]), Byte.toUnsignedInt(rgb[1]), Byte.toUnsignedInt(rgb[2]));
            }
        }
        return hex(TEXT);
    }

    private static void writeQaSummary(XSSFWorkbook workbook, FormulaEvaluator evaluator) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder summary = new StringBuilder();
        summary.append("Workbook: ").append(OUTPUT).append('\n');
        summary.append("Sheets: ").append(workbook.getNumberOfSheets()).append('\n');
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            summary.append(i + 1).append(". ").append(sheet.getSheetName())
                    .append(" rows=").append(sheet.getLastRowNum() + 1)
                    .append(" cols=").append(maxColumns(sheet)).append('\n');
        }
        XSSFSheet report = workbook.getSheet("测试报告");
        summary.append("Report total=").append(formatter.formatCellValue(report.getRow(3).getCell(1), evaluator)).append('\n');
        summary.append("Report pass=").append(formatter.formatCellValue(report.getRow(3).getCell(3), evaluator)).append('\n');
        summary.append("Report fail=").append(formatter.formatCellValue(report.getRow(3).getCell(5), evaluator)).append('\n');
        summary.append("Report notExecuted=").append(formatter.formatCellValue(report.getRow(3).getCell(7), evaluator)).append('\n');
        summary.append("Report passRate=").append(formatter.formatCellValue(report.getRow(4).getCell(1), evaluator)).append('\n');
        Files.writeString(WORK_DIR.resolve("qa-summary.txt"), summary.toString(), StandardCharsets.UTF_8);
    }

    private static int maxColumns(XSSFSheet sheet) {
        int max = 0;
        for (Row row : sheet) max = Math.max(max, row.getLastCellNum());
        return max;
    }

    private static Color hex(String value) {
        return new Color(Integer.parseInt(value.substring(0, 2), 16), Integer.parseInt(value.substring(2, 4), 16), Integer.parseInt(value.substring(4, 6), 16));
    }

    private static XSSFColor color(String value) {
        return new XSSFColor(hex(value), new DefaultIndexedColorMap());
    }

    private static final class Styles {
        final XSSFCellStyle title;
        final XSSFCellStyle subtitle;
        final XSSFCellStyle section;
        final XSSFCellStyle tableHeader;
        final XSSFCellStyle body;
        final XSSFCellStyle bodyAlt;
        final XSSFCellStyle infoLabel;
        final XSSFCellStyle infoValue;
        final XSSFCellStyle note;
        final XSSFCellStyle pass;
        final XSSFCellStyle fail;
        final XSSFCellStyle warn;
        final XSSFCellStyle neutral;
        final XSSFCellStyle number;
        final XSSFCellStyle currency;
        final XSSFCellStyle date;
        final XSSFCellStyle percent;
        final XSSFCellStyle metricLabel;
        final XSSFCellStyle metricValue;
        final XSSFCellStyle metricPercent;

        Styles(XSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();
            title = make(workbook, NAVY, WHITE, true, 18, HorizontalAlignment.LEFT, true);
            subtitle = make(workbook, PALE_BLUE, MUTED, false, 10, HorizontalAlignment.LEFT, true);
            section = make(workbook, TEAL, WHITE, true, 12, HorizontalAlignment.LEFT, false);
            tableHeader = make(workbook, HEADER, WHITE, true, 10, HorizontalAlignment.CENTER, true);
            body = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.LEFT, true);
            bodyAlt = make(workbook, "F7FAFC", TEXT, false, 10, HorizontalAlignment.LEFT, true);
            infoLabel = make(workbook, PALE_TEAL, TEAL, true, 10, HorizontalAlignment.LEFT, true);
            infoValue = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.LEFT, true);
            note = make(workbook, "FFF8E8", TEXT, false, 10, HorizontalAlignment.LEFT, true);
            pass = make(workbook, PASS_BG, PASS_FG, true, 10, HorizontalAlignment.CENTER, true);
            fail = make(workbook, FAIL_BG, FAIL_FG, true, 10, HorizontalAlignment.CENTER, true);
            warn = make(workbook, WARN_BG, WARN_FG, true, 10, HorizontalAlignment.CENTER, true);
            neutral = make(workbook, NEUTRAL_BG, NEUTRAL_FG, true, 10, HorizontalAlignment.CENTER, true);
            number = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.RIGHT, false);
            number.setDataFormat(format.getFormat("#,##0"));
            currency = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.RIGHT, false);
            currency.setDataFormat(format.getFormat("¥#,##0.00"));
            date = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.CENTER, false);
            date.setDataFormat(format.getFormat("yyyy-mm-dd"));
            percent = make(workbook, WHITE, TEXT, false, 10, HorizontalAlignment.RIGHT, false);
            percent.setDataFormat(format.getFormat("0.0%"));
            metricLabel = make(workbook, PALE_BLUE, NAVY, true, 10, HorizontalAlignment.LEFT, true);
            metricValue = make(workbook, WHITE, NAVY, true, 16, HorizontalAlignment.RIGHT, false);
            metricValue.setDataFormat(format.getFormat("#,##0"));
            metricPercent = make(workbook, WHITE, NAVY, true, 16, HorizontalAlignment.RIGHT, false);
            metricPercent.setDataFormat(format.getFormat("0.0%"));
        }

        private static XSSFCellStyle make(XSSFWorkbook workbook, String fill, String fontColor, boolean bold, int size,
                                           HorizontalAlignment alignment, boolean wrap) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(color(fill));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(wrap);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBottomBorderColor(color(BORDER));
            XSSFFont font = workbook.createFont();
            font.setFontName("Microsoft YaHei");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(color(fontColor));
            style.setFont(font);
            return style;
        }
    }
}
